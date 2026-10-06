package pl.aridlin.kukirin;
/** A boost is one impulse followed by a finite speed taper, never a motor hold. */
public final class ScooterOverspeed {
 public static final int NORMAL_TICKS=100,SPEAR_TICKS=40;
 private double peak,target,lastSpeed;private long start;private int duration;private boolean active;
 public void begin(double speed,double limit,long tick,int ticks){peak=speed;target=limit;start=tick;duration=Math.max(1,ticks);lastSpeed=speed;active=speed>limit;}
 public ScooterHandling.Motion apply(ScooterHandling.Motion motion,double limit,boolean braking,long tick){
  double speed=motion.speed();
  if(braking||speed<=limit+1e-6){active=false;lastSpeed=speed;return motion;}
  if(!active||Math.abs(target-limit)>1e-6||speed>lastSpeed+.002)begin(speed,limit,tick,NORMAL_TICKS);
  double progress=Math.clamp((double)(tick-start+1)/duration,0,1),allowed=peak+(target-peak)*progress;
  double next=Math.min(speed,allowed);lastSpeed=next;if(progress>=1)active=false;
  return new ScooterHandling.Motion(motion.x()*next/speed,motion.z()*next/speed);
 }
}
