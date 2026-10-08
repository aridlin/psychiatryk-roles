import com.sun.net.httpserver.HttpServer;
import java.io.*;
import java.net.*;
import java.nio.*;
import java.security.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import pl.aridlin.kukirin.ScooterHttpAudioStream;

public final class StreamCheck {
  private static int checks;
  private static void check(boolean condition, String text) { checks++; if (!condition) throw new AssertionError(text); }
  private static byte[] wav(int count, boolean rf64) throws Exception {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    ByteBuffer h = ByteBuffer.allocate(rf64 ? 80 : 44).order(ByteOrder.LITTLE_ENDIAN);
    h.put((rf64 ? "RF64" : "RIFF").getBytes()); h.putInt(rf64 ? -1 : 36 + count); h.put("WAVE".getBytes());
    if (rf64) { h.put("ds64".getBytes()); h.putInt(28); h.putLong(72 + count); h.putLong(count); h.putLong(count / 2); h.putInt(0); }
    h.put("fmt ".getBytes()); h.putInt(16); h.putShort((short)1); h.putShort((short)1); h.putInt(22050); h.putInt(44100);
    h.putShort((short)2); h.putShort((short)16); h.put("data".getBytes()); h.putInt(rf64 ? -1 : count);
    out.write(h.array()); byte[] payload = new byte[count];
    for (int i=0; i<count; i++) payload[i]=(byte)(i * 13); out.write(payload); return out.toByteArray();
  }
  private static String sha(byte[] bytes) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
  public static void main(String[] args) throws Exception {
    System.setProperty("goplanska.music.allowLocalHttp", "true");
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.setExecutor(Executors.newCachedThreadPool(r -> { Thread t=new Thread(r); t.setDaemon(true); return t; }));
    byte[] normal = wav(32768, false), rf64 = wav(16384, true);
    CountDownLatch released = new CountDownLatch(1); AtomicLong sent = new AtomicLong(); AtomicBoolean authorized = new AtomicBoolean();
    server.createContext("/slow.wav", exchange -> {
      authorized.set("Bearer fixture-token".equals(exchange.getRequestHeaders().getFirst("Authorization")));
      exchange.sendResponseHeaders(200, normal.length);
      try (OutputStream output=exchange.getResponseBody()) {
        output.write(normal,0,4140); output.flush(); sent.set(4140);
        try { released.await(10, TimeUnit.SECONDS); } catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
        output.write(normal,4140,normal.length-4140); sent.set(normal.length);
      } catch (IOException expectedOnCancel) {}
    });
    server.createContext("/normal.wav", exchange -> { exchange.sendResponseHeaders(200,normal.length); try(OutputStream out=exchange.getResponseBody()){out.write(normal);} });
    server.createContext("/rf64.wav", exchange -> { exchange.sendResponseHeaders(200,rf64.length); try(OutputStream out=exchange.getResponseBody()){out.write(rf64);} });
    server.createContext("/redirect.wav", exchange -> { exchange.getResponseHeaders().add("Location","/normal.wav");exchange.sendResponseHeaders(302,-1);exchange.close(); });
    server.start(); String base="http://127.0.0.1:"+server.getAddress().getPort();
    try {
      long start=System.nanoTime();
      ScooterHttpAudioStream slow=ScooterHttpAudioStream.open(base+"/slow.wav","fixture-token",sha(normal)).get(2,TimeUnit.SECONDS);
      check(TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start)<1900,"header open waits for whole track");
      ByteBuffer early=slow.read(4096); check(early.remaining()==4096,"early PCM read");
      check(sent.get()<normal.length,"early PCM returned before complete download"); check(authorized.get(),"bearer retained");
      slow.closeAsync(); released.countDown(); check(slow.read(4096).remaining()==0,"cancel closes future reads");
      for(boolean largeHeader:new boolean[]{false,true}) {
        byte[] expected=largeHeader?rf64:normal; String route=largeHeader?"/rf64.wav":"/normal.wav";
        AtomicBoolean ended=new AtomicBoolean();
        ScooterHttpAudioStream stream=ScooterHttpAudioStream.open(base+route,"",sha(expected)).get(2,TimeUnit.SECONDS).configure(()->false,()->ended.set(true));
        check(stream.getFormat().getFrameSize()==2,"PCM format");
        int read=0; ByteBuffer chunk;
        do { chunk=stream.read(1024);read+=chunk.remaining();check(chunk.remaining()<=1024,"bounded read buffer"); }while(chunk.hasRemaining());
        check(read==expected.length-(largeHeader?80:44),"all complete PCM frames"); check(ended.get(),"natural end reported"); stream.close();
      }
      AtomicBoolean repeat=new AtomicBoolean(true); AtomicBoolean ended=new AtomicBoolean();
      ScooterHttpAudioStream loop=ScooterHttpAudioStream.open(base+"/rf64.wav","",sha(rf64)).get(2,TimeUnit.SECONDS).configure(repeat::get,()->ended.set(true));
      ByteBuffer twoPass=loop.read(32768); check(twoPass.remaining()==32768,"stream loop reopens and preserves PCM continuity");
      check(!ended.get(),"loop not reported as ended");repeat.set(false);check(loop.read(1024).remaining()==0,"live loop toggle stops at next boundary");check(ended.get(),"nonloop end");
      ScooterHttpAudioStream bad=ScooterHttpAudioStream.open(base+"/normal.wav","","0".repeat(64)).get(2,TimeUnit.SECONDS);
      bad.read(32768); boolean rejected=false;try{bad.read(1024);}catch(IOException expected){rejected=true;}check(rejected,"content hash mismatch rejected at EOF");
      rejected=false;try{ScooterHttpAudioStream.open(base+"/redirect.wav","",sha(normal)).get(2,TimeUnit.SECONDS);}catch(ExecutionException expected){rejected=true;}check(rejected,"redirect rejected");
      rejected=false;try{ScooterHttpAudioStream.open("http://example.invalid/a.wav","",sha(normal)).get(2,TimeUnit.SECONDS);}catch(ExecutionException expected){rejected=true;}check(rejected,"nonlocal plain HTTP rejected");
      System.out.println("{\"success\":true,\"assertions\":"+checks+",\"starts_before_complete_download\":true,\"bounded_pcm_buffers\":true,\"rf64_supported\":true,\"loop_and_cancel_verified\":true,\"native_game_runtime_claimed\":false}");
    } finally { released.countDown();server.stop(0); }
  }
}
