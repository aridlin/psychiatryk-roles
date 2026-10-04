package pl.aridlin.kukirin;
/** Pure rental rules, independent of entities, rendering and production saves. */
public final class RentalPolicy {
 public static final double MAX_BLOCKS_PER_TICK=30.0/3.6/20.0;
 public static final int INITIAL_BATTERY_TICKS=20*60*10; // half of a 20-minute battery
 public static final long CLEANUP_TICKS=20*120;
 public static double cappedSpeed(double requested){return Math.min(MAX_BLOCKS_PER_TICK,Math.max(0,requested));}
 public static boolean cleanup(boolean ridden,long now,long lastRendered,long emptySince){return !ridden&&(now-lastRendered>=CLEANUP_TICKS||emptySince>=0&&now-emptySince>=CLEANUP_TICKS);}
 public static int drain(int remaining,boolean ridden){return Math.max(0,remaining-(ridden?1:0));}
 private RentalPolicy(){}
}
