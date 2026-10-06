import pl.aridlin.kukirin.*;
import java.util.*;
/** Candidate bytecode checks; the model parser and live fixture are independent gates. */
public final class HandlingGeometryQA {
 static int passed;
 static void check(boolean condition,String name){if(!condition)throw new AssertionError(name);passed++;System.out.println("CHECK\t"+name);}
 static boolean near(double a,double b,double tolerance){return Math.abs(a-b)<=tolerance;}
 public static void main(String[] args){
  for(double distance:new double[]{.01,.15,.72,200/72d})for(double requested:new double[]{7,28}){
   var left=ScooterHandling.steering(distance,distance,requested,0,true,false,1,false);
   var right=ScooterHandling.steering(distance,distance,-requested,0,true,false,1,false);
   check(left.yawDegrees()>0&&left.wheelDegrees()>0&&right.yawDegrees()<0&&right.wheelDegrees()<0,"signed ground steering "+distance+" "+requested);
   check(near(left.yawDegrees(),-right.yawDegrees(),1e-6)&&near(left.wheelDegrees(),-right.wheelDegrees(),1e-6),"left/right mirror symmetry "+distance+" "+requested);
   double chordRadius=distance/(2*Math.sin(Math.toRadians(left.yawDegrees())/2));
   double rigRadius=ScooterHandling.WHEELBASE/Math.tan(Math.toRadians(left.wheelDegrees()));
   check(Math.abs(chordRadius/rigRadius-1)<1e-6,"actual chord curvature agrees with rig wheel angle "+distance+" "+requested);
   check(Math.abs(left.wheelDegrees())<=28.00001&&Math.abs(left.yawDegrees())<=16.00001,"bounded physical and visible steering "+distance+" "+requested);
   for(var turn:new ScooterHandling.Steering[]{left,right})System.out.printf(Locale.ROOT,"TURN\t%.9f\t%.9f\t%.9f\t%.9f\n",distance,turn.wheelDegrees(),turn.yawDegrees(),ScooterHandling.steeringAnimationTime(turn.wheelDegrees()));
  }
  var stopped=ScooterHandling.steering(0,0,28,0,true,false,1,false);check(stopped.yawDegrees()==0&&stopped.wheelDegrees()==28,"stationary front wheel turns without spinning chassis");
  for(double invalid:new double[]{Double.NaN,Double.POSITIVE_INFINITY}){var safe=ScooterHandling.steering(invalid,.72,28,0,true,false,1,false);check(safe.wheelDegrees()==0&&safe.yawDegrees()==0,"nonfinite motion fails closed");}
  for(boolean ground:new boolean[]{true,false})for(double input:new double[]{0,1}){
   var taper=new ScooterOverspeed();var motion=new ScooterHandling.Motion(0,200/72d);double previous=motion.speed();boolean monotonic=true;
   for(int tick=0;tick<100;tick++){
    motion=ScooterHandling.step(motion.x(),motion.z(),0,input,ground,true);
    motion=ScooterFlight.accelerate(motion,0,input,previous,.72,false,false,ground);
    motion=taper.apply(motion,.72,false,tick);
    monotonic&=motion.speed()<=previous+1e-7;
    if(tick==0)check(motion.speed()>2.7,"boost does not snap to cruising cap "+ground+" "+input);
    if(tick==49)check(motion.speed()>1.70&&motion.speed()<1.80,"five-second taper midpoint "+ground+" "+input);
    previous=motion.speed();
   }
   check(monotonic,"all hundred boost-taper ticks are monotonic "+ground+" "+input);
   check(motion.speed()<=.720001&&motion.speed()>.70,"five-second taper reaches normal cap "+ground+" "+input);
  }
  var spear=new ScooterOverspeed();var motion=new ScooterHandling.Motion(0,100/72d);spear.begin(motion.speed(),.72,0,ScooterOverspeed.SPEAR_TICKS);
  for(int tick=0;tick<40;tick++){
   motion=ScooterHandling.step(motion.x(),motion.z(),0,1,true,true);motion=spear.apply(motion,.72,false,tick);
   if(tick==0)check(motion.speed()>1.35,"spear gets one 100 km/h impulse without instant cap");
   if(tick==19)check(motion.speed()>1.04&&motion.speed()<1.07,"one-shot spear tapers at one second");
  }
  check(near(motion.speed(),.72,1e-6),"spear returns to normal cap after forty ticks");
  for(int tick=40;tick<80;tick++)motion=spear.apply(ScooterHandling.step(motion.x(),motion.z(),0,1,true,true),.72,false,tick);
  check(motion.speed()<=.720001,"held throttle never re-applies spear impulse");
  var brake=new ScooterOverspeed();motion=new ScooterHandling.Motion(0,2);for(int tick=0;tick<30;tick++)motion=brake.apply(ScooterHandling.step(motion.x(),motion.z(),0,-1,true,true),.72,true,tick);check(motion.speed()<.7,"brake overrides boost taper and removes speed");
  var rocket=ScooterFlight.accelerate(new ScooterHandling.Motion(0,3),0,1,3,.72,false,true,true);check(near(rocket.speed(),3,1e-9),"rocket branch does not snap preexisting overspeed to its cap");
  var flight=ScooterFlight.accelerate(new ScooterHandling.Motion(0,3),0,1,3,.72,true,false,true);check(near(flight.speed(),3,1e-9),"flight branch does not snap preexisting overspeed to its cap");
  System.out.println("TOTAL\t"+passed);
 }
}
