package pl.aridlin.kukirin;
/** Pure clock math shared by all clients for a server-owned playback session. */
public final class MusicTimeline {
 public static double position(long elapsedMillis,long receivedNanos,long nowNanos,int durationTicks,boolean loop) {
  return position(elapsedMillis,receivedNanos,nowNanos,durationTicks,loop,false);
 }
 public static double position(long elapsedMillis,long receivedNanos,long nowNanos,int durationTicks,boolean loop,boolean paused) {
  double elapsed=Math.max(0,elapsedMillis)/1000.0+(paused?0:Math.max(0,nowNanos-receivedNanos)/1_000_000_000.0);
  double duration=Math.max(0,durationTicks)/20.0;
  return duration>0?(loop?elapsed%duration:Math.min(elapsed,duration)):elapsed;
 }
 public static double drift(double left,double right,int durationTicks,boolean loop) {
  double difference=Math.abs(left-right),duration=durationTicks/20.0;
  return loop&&duration>0?Math.min(difference%duration,duration-difference%duration):difference;
 }
 private MusicTimeline(){}
}
