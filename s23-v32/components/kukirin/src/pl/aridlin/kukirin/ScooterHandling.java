package pl.aridlin.kukirin;
/** Grip on ordinary turns; a bounded power-slide on hard turns, with fast recovery. */
public final class ScooterHandling {
 public static final double TOP_SPEED=.72,REVERSE_SPEED=.16;
 /** A modest 15% reduction on the descent only; jump launch and ascent stay unchanged. */
 public static double fallingGravity(double verticalSpeed,double gravity,boolean occupied){return occupied&&verticalSpeed<=0?gravity*.85:gravity;}
 public record Motion(double x,double z){public double speed(){return Math.hypot(x,z);}}
 public static Motion step(double x,double z,double yaw,double input,boolean ground,boolean occupied){return step(x,z,yaw,input,ground,occupied,0);}
 public static Motion step(double x,double z,double yaw,double input,boolean ground,boolean occupied,double drift){
  return step(x,z,yaw,input,ground,occupied,drift,TOP_SPEED,.045,1);
 }
 public static Motion step(double x,double z,double yaw,double input,boolean ground,boolean occupied,double drift,double topSpeed,double braking,double acceleration){
  return step(x,z,yaw,input,ground,occupied,drift,topSpeed,braking,acceleration,.85,.045);
 }
 public static Motion step(double x,double z,double yaw,double input,boolean ground,boolean occupied,double drift,double topSpeed,double braking,double acceleration,double normalGrip,double driftGrip){
  double speed=Math.hypot(x,z),heading=Math.toRadians(yaw),fx=-Math.sin(heading),fz=Math.cos(heading);
  if(!ground){double drag=input<0?.96:.9995;return new Motion(x*drag,z*drag);}
  if(!occupied)return scale(x,z,speed,Math.max(0,speed-.025));
  boolean reverse=x*fx+z*fz<-.01;double current=speed>1e-7?Math.atan2(-x,z):heading,target=heading+(reverse?Math.PI:0);
  double delta=Math.atan2(Math.sin(target-current),Math.cos(target-current));
  // Preserve lateral momentum while Ctrl is held; bound slip to a recoverable angle.
  double grip=normalGrip+(driftGrip-normalGrip)*Math.clamp(drift,0,1);
  double remaining=Math.clamp(delta*(1-grip),-Math.toRadians(55),Math.toRadians(55)),angle=target-remaining;
  double next=speed;
  if(input>0){if(reverse)next=Math.max(0,speed-braking);else next=speed>topSpeed?Math.max(topSpeed,speed*.987):Math.min(topSpeed,speed+.027*acceleration*(1-.65*Math.min(1,speed/topSpeed)));}
  else if(input<0)next=Math.max(0,speed-braking);
  else next=speed<.006?0:speed*.994;
  return new Motion(-Math.sin(angle)*next,Math.cos(angle)*next);
 }
 public static Motion wallKick(double x,double z,double yaw,double steering,double nx,double nz,double limit){
  double speed=Math.hypot(x,z),dot=x*nx+z*nz,tx=x-dot*nx,tz=z-dot*nz,tangent=Math.hypot(tx,tz);
  if(tangent<.025){double sign=steering<0?-1:1;tx=-nz*sign;tz=nx*sign;tangent=1;}
  double dx=nx*.78+tx/tangent*.63,dz=nz*.78+tz/tangent*.63,length=Math.hypot(dx,dz),next=Math.min(limit,Math.max(.35,speed+.12));
  return new Motion(dx/length*next,dz/length*next);
 }
 public static float driftNext(float drift,double speed,double steer,boolean ground){
  return driftNext(drift,speed,steer,ground,false);
 }
 public static float driftNext(float drift,double speed,double steer,boolean ground,boolean held){
  return driftNext(drift,speed,steer,ground,held,.42);
 }
 public static float driftNext(float drift,double speed,double steer,boolean ground,boolean held,double recovery){
  float target=held&&ground&&speed>.16&&(Math.abs(steer)>5||drift>.02)?(float)Math.clamp((speed-.16)/.24,0,1):0;
  return drift+(target-drift)*(target>drift?.28f:(float)recovery);
 }
 public static Motion airSteer(Motion m,double steer){double a=Math.toRadians(steer)*.045,c=Math.cos(a),s=Math.sin(a);return new Motion(m.x*c-m.z*s,m.x*s+m.z*c);}
 public static Motion wallSlide(Motion requested,Motion clipped,boolean collision){double speed=requested.speed(),tangent=clipped.speed();if(!collision||tangent<speed*.30||tangent<1e-6)return clipped;double factor=speed*.97/tangent;return new Motion(clipped.x*factor,clipped.z*factor);}
 private static Motion scale(double x,double z,double speed,double next){return speed<1e-8?new Motion(0,0):new Motion(x*next/speed,z*next/speed);}
 public static float turnRate(double speed){if(!Double.isFinite(speed))return 1.2f;double t=Math.max(0,speed)/TOP_SPEED;return (float)(t<1?11.2-7.616*t:Math.max(1.344,3.584/(1+.85*(t-1))));}
 public static float steeringYawStep(double speed,double steer,int turningEnchant,boolean ground,boolean flight){double input=Math.clamp(steer/28.,-1,1),gain=1+.10*Math.clamp(turningEnchant,0,3);double limit=turnRate(speed)*gain*(ground?1:flight?.85:.35);return (float)(input*Math.min(ground?11.2:4,limit));}
}
