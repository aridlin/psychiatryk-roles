package pl.aridlin.kukirin;
import java.util.function.LongSupplier;
import java.util.concurrent.TimeUnit;
/** One monotonic server-owned session clock. Pausing preserves the session and exact playhead. */
public final class MusicSessionClock {
 private final LongSupplier now;
 private final long began;
 private long suspendedNanos;
 private long pauseAt;
 private boolean paused;
 public MusicSessionClock(){this(System::nanoTime);}
 MusicSessionClock(LongSupplier now){this.now=now;this.began=now.getAsLong();}
 public synchronized boolean paused(){return paused;}
 public synchronized long elapsedMillis(){long endpoint=paused?pauseAt:now.getAsLong();return Math.max(0,TimeUnit.NANOSECONDS.toMillis(endpoint-began-suspendedNanos));}
 public synchronized boolean setPaused(boolean value){if(paused==value)return false;long time=now.getAsLong();if(value)pauseAt=time;else suspendedNanos+=Math.max(0,time-pauseAt);paused=value;return true;}
}
