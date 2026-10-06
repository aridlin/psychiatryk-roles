package pl.aridlin.kukirin;
/** Flight uses the same block/tick units as ground handling: 72 km/h per block/tick. */
public final class ScooterFlight {
 public static final double FLIGHT_LIMIT=180/72d,ROCKET_LIMIT=200/72d;
 public static final int ROCKET_TICKS=400;
 public static final double NORMAL_RAMP_END=67/72d;
 public static final int HIGH_SPEED_RAMP_TICKS=120*20;
 public static ScooterHandling.Motion accelerate(ScooterHandling.Motion motion,double yaw,double input,double oldSpeed,double normalLimit,boolean flight,boolean rocket,boolean ground){
  double speed=motion.speed(),limit=rocket?ROCKET_LIMIT:flight?FLIGHT_LIMIT:normalLimit;
  if(rocket){double base=Math.max(speed,oldSpeed);double next=base>=limit?base:Math.min(limit,base+(ground?.095:.12));double a=Math.toRadians(yaw);if(speed<.01)return new ScooterHandling.Motion(-Math.sin(a)*next,Math.cos(a)*next);return new ScooterHandling.Motion(motion.x()*next/speed,motion.z()*next/speed);}
  if(flight&&input>0&&oldSpeed>=normalLimit-.001){double edge=Math.max(normalLimit,NORMAL_RAMP_END);double base=Math.max(speed,oldSpeed);
   double gain=base<edge?.027*(1-.65*Math.min(1,base/edge)):(FLIGHT_LIMIT-edge)/HIGH_SPEED_RAMP_TICKS;
   double next=base>=limit?base:Math.min(limit,base+gain);if(speed>.001)return new ScooterHandling.Motion(motion.x()*next/speed,motion.z()*next/speed);}
  return motion;
 }
 public static double vertical(double old,double gravity,boolean flight,boolean rocket,boolean upward){
  if(rocket&&upward)return Math.min(.9,old+.055);
  if(flight&&old<=0)return Math.max(-.09,old-gravity*.11);
  return old-gravity;
 }
 private ScooterFlight(){}
}
