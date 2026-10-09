package pl.aridlin.psychiatrykroles.runtime;

import java.util.ArrayDeque;

/** A per-player rolling-window packet cap, independent of server tick rate. */
public final class SignalRate {
    public static final int MAX_PACKETS=5;
    public static final long WINDOW_NANOS=1_000_000_000L;
    private final ArrayDeque<Long> sends=new ArrayDeque<>();
    public boolean available(long now){
        while(!sends.isEmpty()&&now-sends.peekFirst()>=WINDOW_NANOS)sends.removeFirst();
        return sends.size()<MAX_PACKETS;
    }
    public void record(long now){
        Schema.require(available(now),"Signal packet rate");sends.addLast(now);
    }
}
