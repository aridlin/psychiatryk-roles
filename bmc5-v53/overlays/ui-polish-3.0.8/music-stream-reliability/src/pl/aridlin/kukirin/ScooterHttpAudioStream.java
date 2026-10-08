package pl.aridlin.kukirin;

import net.minecraft.client.sounds.AudioStream;
import java.io.*;
import java.net.*;
import java.nio.*;
import java.security.*;
import java.util.HexFormat;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;
import javax.sound.sampled.AudioFormat;

/** Incremental authenticated PCM playback. Neither media nor PCM accumulates in RAM. */
public final class ScooterHttpAudioStream implements AudioStream {
    private static final ThreadPoolExecutor IO = new ThreadPoolExecutor(2, 2, 0, TimeUnit.SECONDS,
        new LinkedBlockingQueue<>(), runnable -> {
            Thread thread = new Thread(runnable, "Scooter PCM stream"); thread.setDaemon(true); return thread;
        }, new ThreadPoolExecutor.AbortPolicy());
    private final URI uri;
    private final String bearer, expectedSha;
    private volatile HttpURLConnection connection;
    private volatile InputStream input;
    private MessageDigest digest;
    private AudioFormat format;
    private long remaining, dataOffset, dataLength, bytesRead, startFrame;
    private boolean fullDigest;
    private volatile double positionSeconds;
    public double positionSeconds() { return positionSeconds; }
    public CompletableFuture<ScooterHttpAudioStream> prepare(double seconds) {
        return CompletableFuture.supplyAsync(() -> {
            synchronized (this) {
                try { if (Math.abs(positionSeconds - seconds) > 0.025) seek(seconds); return this; }
                catch (Exception failure) { closeAsync(); throw new CompletionException(problem("seek", failure)); }
            }
        }, IO);
    }
    public CompletableFuture<ScooterHttpAudioStream> prepare(java.util.function.DoubleSupplier seconds) {
        return CompletableFuture.supplyAsync(() -> {
            synchronized (this) {
                try {
                    seek(seconds.getAsDouble());
                    // Discard only a small catch-up suffix after HTTP preparation, never the whole prefix.
                    for (int pass = 0; pass < 3; pass++) {
                        double target = seconds.getAsDouble(), delta = target - positionSeconds;
                        if (delta < -0.1) { seek(target); continue; } // shared loop wrapped while opening
                        if (delta <= 0.01) break;
                        if (delta > 2) { seek(target); continue; }
                        long frames = (long)(delta * format.getFrameRate());
                        long bytes = Math.min(remaining, frames * format.getFrameSize());
                        while (bytes > 0) { int got = read((int)Math.min(bytes,32768)).remaining(); if (got == 0) break; bytes -= got; }
                    }
                    return this;
                } catch (Exception failure) { closeAsync(); throw new CompletionException(problem("seek", failure)); }
            }
        }, IO);
    }
    private volatile boolean closed;
    private final java.util.concurrent.atomic.AtomicBoolean closing = new java.util.concurrent.atomic.AtomicBoolean();
    private BooleanSupplier repeat = () -> false;
    private Runnable ended = () -> {};
    private java.util.function.Consumer<IOException> failed = error -> {};
    /** Safe machine-readable transport reason, never URL/header/token text. */
    public static final class Failure extends IOException {
        public final String code;
        private Failure(String code) { super("Remote PCM " + code); this.code = code; }
    }
    private static Failure problem(String stage, Exception failure) {
        return failure instanceof Failure known ? known : new Failure(stage + "_" + failure.getClass().getSimpleName());
    }

    public static CompletableFuture<ScooterHttpAudioStream> open(String url, String bearer, String sha) {
        try {
            URI uri = URI.create(url); ScooterMusicHttpCache.validateUri(uri);
            if (!sha.matches("[a-f0-9]{64}") || !uri.getPath().endsWith("/" + sha + ".wav") || bearer.length() > 512
                || bearer.indexOf('\r') >= 0 || bearer.indexOf('\n') >= 0) throw new IOException();
            CompletableFuture<ScooterHttpAudioStream> result = new CompletableFuture<>();
            IO.execute(() -> {
                ScooterHttpAudioStream stream = new ScooterHttpAudioStream(uri, bearer, sha);
                try { stream.reopen(); result.complete(stream); }
                catch (Exception failure) { stream.closeAsync(); result.completeExceptionally(problem("open", failure)); }
            });
            return result;
        } catch (Exception failure) { return CompletableFuture.failedFuture(problem("open", failure)); }
    }

    private ScooterHttpAudioStream(URI uri, String bearer, String sha) {
        this.uri = uri; this.bearer = bearer; this.expectedSha = sha;
    }

    public ScooterHttpAudioStream configure(BooleanSupplier repeat, Runnable ended) {
        return configure(repeat, ended, error -> {});
    }
    public ScooterHttpAudioStream configure(BooleanSupplier repeat, Runnable ended, java.util.function.Consumer<IOException> failed) {
        this.repeat = repeat; this.ended = ended; this.failed = failed; return this;
    }

    private byte[] exact(int count) throws IOException {
        byte[] bytes = input.readNBytes(count); bytesRead += bytes.length;
        if (bytes.length != count) throw new EOFException("Incomplete PCM stream");
        return bytes;
    }

    private static long u32(byte[] data, int offset) {
        return Integer.toUnsignedLong(ByteBuffer.wrap(data, offset, 4).order(ByteOrder.LITTLE_ENDIAN).getInt());
    }

    private void skip(long count) throws IOException {
        byte[] scratch = new byte[8192];
        while (count > 0 && !closed) {
            int got = input.read(scratch, 0, (int)Math.min(count, scratch.length));
            if (got < 0) throw new EOFException("Incomplete PCM header");
            count -= got; bytesRead += got;
        }
    }

    private void reopen() throws Exception {
        if (closed) throw new IOException("PCM stream closed");
        HttpURLConnection opened = (HttpURLConnection)uri.toURL().openConnection();
        opened.setConnectTimeout(6000); opened.setReadTimeout(12000); opened.setInstanceFollowRedirects(false);
        opened.setRequestProperty("Accept", "audio/wav");
        if (!bearer.isEmpty()) opened.setRequestProperty("Authorization", "Bearer " + bearer);
        connection = opened;
        int response = opened.getResponseCode();
        if (response != 200) throw new Failure("http_" + response);
        digest = MessageDigest.getInstance("SHA-256"); fullDigest = true; bytesRead = 0; positionSeconds = 0; startFrame = 0;
        input = new DigestInputStream(new BufferedInputStream(opened.getInputStream(), 32768), digest);
        byte[] header = exact(12);
        boolean rf64 = header[0] == 'R' && header[1] == 'F' && header[2] == '6' && header[3] == '4';
        if (!(rf64 || header[0] == 'R' && header[1] == 'I' && header[2] == 'F' && header[3] == 'F')
            || header[8] != 'W' || header[9] != 'A' || header[10] != 'V' || header[11] != 'E')
            throw new Failure("not_pcm_wav");
        AudioFormat parsed = null; long rf64Data = -1, headerBytes = 12;
        while (!closed) {
            byte[] chunk = exact(8); long length = u32(chunk, 4); headerBytes += 8;
            String type = new String(chunk, 0, 4, java.nio.charset.StandardCharsets.US_ASCII);
            if (type.equals("data")) {
                if (parsed == null) throw new IOException("PCM format missing");
                remaining = rf64 && length == 0xffffffffL ? rf64Data : length;
                if (remaining <= 0 || remaining % parsed.getFrameSize() != 0) throw new IOException("Invalid PCM frames");
                if (format != null && !format.matches(parsed)) throw new IOException("PCM loop format changed");
                format = parsed; dataOffset = bytesRead; dataLength = remaining; return;
            }
            // Metadata headers are bounded; track payload size and duration are unrestricted.
            if (length > 1048576 || headerBytes + length > 1048576) throw new IOException("PCM header too large");
            if (type.equals("fmt ")) {
                if (length < 16) throw new IOException("Invalid PCM format");
                byte[] value = exact((int)length); ByteBuffer b = ByteBuffer.wrap(value).order(ByteOrder.LITTLE_ENDIAN);
                int encoding = Short.toUnsignedInt(b.getShort()), channels = Short.toUnsignedInt(b.getShort());
                long rate = Integer.toUnsignedLong(b.getInt()); b.getInt();
                int frameSize = Short.toUnsignedInt(b.getShort()), bits = Short.toUnsignedInt(b.getShort());
                if (encoding != 1 || channels < 1 || channels > 2 || bits != 16
                    || frameSize != channels * 2 || rate < 8000 || rate > 192000) throw new Failure("unsupported_pcm_format");
                parsed = new AudioFormat((float)rate, bits, channels, true, false);
            } else if (type.equals("ds64") && rf64) {
                if (length < 28) throw new IOException("Invalid RF64 header");
                byte[] value = exact((int)length); rf64Data = ByteBuffer.wrap(value).order(ByteOrder.LITTLE_ENDIAN).getLong(8);
            } else skip(length);
            if ((length & 1) != 0) skip(1);
            headerBytes += length + (length & 1);
        }
        throw new IOException("PCM stream closed");
    }

    private void seek(double seconds) throws Exception {
        if (closed || !Double.isFinite(seconds) || seconds < 0) throw new IOException("Invalid PCM seek");
        long frame = Math.min(dataLength / format.getFrameSize(), (long)(seconds * format.getFrameRate()));
        if (frame == 0 && positionSeconds == 0) return;
        long byteOffset = frame * format.getFrameSize();
        if (connection != null) connection.disconnect();
        if (input != null) input.close();
        positionSeconds = frame / (double)format.getFrameRate(); startFrame = frame;
        remaining = dataLength - byteOffset; fullDigest = byteOffset == 0;
        if (remaining == 0) { input = InputStream.nullInputStream(); connection = null; return; }
        HttpURLConnection opened = (HttpURLConnection)uri.toURL().openConnection();
        opened.setConnectTimeout(6000); opened.setReadTimeout(12000); opened.setInstanceFollowRedirects(false);
        opened.setRequestProperty("Accept", "audio/wav");
        if (!bearer.isEmpty()) opened.setRequestProperty("Authorization", "Bearer " + bearer);
        long start = dataOffset + byteOffset, end = dataOffset + dataLength - 1;
        opened.setRequestProperty("Range", "bytes=" + start + "-" + end);
        connection = opened;
        int response = opened.getResponseCode();
        if (response != 206) throw new Failure("range_http_" + response);
        String range = opened.getHeaderField("Content-Range");
        long responseBytes = opened.getContentLengthLong();
        if (range == null || !range.matches("bytes " + start + "-" + end + "/[0-9]+")
            || responseBytes >= 0 && responseBytes != remaining) throw new Failure("range_mismatch");
        // Suffix reads cannot prove the hash of omitted bytes. The requested URI is
        // restricted to the approved TLS host and this exact content-addressed SHA.
        input = new BufferedInputStream(opened.getInputStream(), 32768);
        fullDigest = false;
    }

    public AudioFormat getFormat() { return format; }

    private void completePass() throws IOException {
        byte[] scratch = new byte[8192];
        while (!closed && input.read(scratch) != -1) {}
        if (closed) return;
        if (fullDigest && !HexFormat.of().formatHex(digest.digest()).equals(expectedSha)) throw new Failure("content_hash_mismatch");
        input.close(); if (connection != null) connection.disconnect(); input = null; connection = null;
    }

    public synchronized ByteBuffer read(int requested) throws IOException {
        if (requested < 0 || requested > 1048576) throw new IllegalArgumentException("PCM buffer size out of bounds");
        int frameSize = format.getFrameSize(), capacity = requested - requested % frameSize;
        ByteBuffer buffer = ByteBuffer.allocateDirect(closed ? 0 : capacity);
        byte[] scratch = new byte[Math.min(32768, Math.max(frameSize, capacity))];
        try {
            while (!closed && buffer.hasRemaining()) {
                if (remaining == 0) {
                    completePass();
                    if (!closed && repeat.getAsBoolean()) {
                        try { reopen(); } catch (Exception failure) { throw problem("loop", failure); }
                    } else { ended.run(); closeAsync(); break; }
                }
                if (closed) break;
                int amount = (int)Math.min(Math.min(remaining, buffer.remaining()), scratch.length);
                int count = input.read(scratch, 0, amount);
                if (count < 0) throw new EOFException("Remote PCM ended early");
                buffer.put(scratch, 0, count); remaining -= count;
                positionSeconds = (startFrame + (dataLength - startFrame * frameSize - remaining) / frameSize) / (double)format.getFrameRate();
            }
            return buffer.flip();
        } catch (IOException failure) {
            Failure reported = problem("read", failure);
            if (!closed) failed.accept(reported);
            closeAsync(); throw reported;
        }
    }

    public void closeAsync() {
        closed = true;
        if (!closing.compareAndSet(false, true)) return;
        Thread closer = new Thread(this::closeTransport, "Scooter PCM close"); closer.setDaemon(true); closer.start();
    }

    private void closeTransport() {
        InputStream in = input; HttpURLConnection opened = connection;
        if (opened != null) opened.disconnect();
        if (in != null) try { in.close(); } catch (IOException ignored) {}
    }

    public void close() { closed = true; if (closing.compareAndSet(false, true)) closeTransport(); }
}
