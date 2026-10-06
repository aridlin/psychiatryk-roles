package pl.aridlin.saveqa;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

/** Test-only delay and failure injection at the real storage I/O boundary. */
public final class DiskFaults {
    public static final String PLAYER_UUID = "3370e9d1-f003-4bce-bdd1-1f93032c9b2a";
    public static final String SECOND_PLAYER_UUID = "3370e9d1-f003-4bce-bdd1-1f93032c9b2b";
    public static volatile long playerDelayMs, sableDelayMs, sableFlushDelayMs;
    public static final AtomicInteger playerBegins = new AtomicInteger();
    public static final AtomicInteger playerEnds = new AtomicInteger();
    public static final AtomicInteger sableBegins = new AtomicInteger();
    public static final AtomicInteger sableEnds = new AtomicInteger();
    public static final AtomicInteger playerFailures = new AtomicInteger();
    public static final AtomicInteger sableFailures = new AtomicInteger();
    public static volatile boolean persistentPlayerFailure, persistentSableFailure;
    public static boolean isQaPlayer(java.nio.file.Path path) {
        String name = path.getFileName().toString();
        return name.startsWith(PLAYER_UUID) || name.startsWith(SECOND_PLAYER_UUID);
    }
    public static void beforePlayer() throws IOException {
        playerBegins.incrementAndGet();
        delay(playerDelayMs);
        if (persistentPlayerFailure || consume(playerFailures)) {
            throw new IOException("SAVE_QA injected player disk write failure");
        }
    }
    public static void beforeSable() throws IOException {
        sableBegins.incrementAndGet();
        delay(sableDelayMs);
        if (persistentSableFailure || consume(sableFailures)) {
            throw new IOException("SAVE_QA injected Sable disk write failure");
        }
    }
    public static void delay(long milliseconds) throws IOException {
        if (milliseconds <= 0) return;
        try { Thread.sleep(milliseconds); }
        catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IOException("SAVE_QA disk operation interrupted", interrupted);
        }
    }
    private static boolean consume(AtomicInteger failures) {
        for (;;) {
            int count = failures.get();
            if (count <= 0) return false;
            if (failures.compareAndSet(count, count - 1)) return true;
        }
    }
}
