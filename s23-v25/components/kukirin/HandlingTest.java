import pl.aridlin.kukirin.ScooterHandling;
public class HandlingTest{
 static void check(boolean ok,String what){if(!ok)throw new AssertionError(what);System.out.println("PASS "+what);}
 public static void main(String[] args){
 var air=new ScooterHandling.Motion(0,.65);for(int i=0;i<40;i++)air=ScooterHandling.step(air.x(),air.z(),90,1,false,true);check(air.speed()>.63&&Math.abs(air.x())<1e-9,"2-second jump preserves >97% momentum without airborne motor/turning");
 var coast=ScooterHandling.step(0,.6,45,0,true,true);check(coast.speed()>.595&&Math.abs(coast.x())>.1,"grippy corner changes direction without deleting speed");
 var fast=new ScooterHandling.Motion(0,0);for(int i=0;i<100;i++)fast=ScooterHandling.step(fast.x(),fast.z(),0,1,true,true);check(fast.speed()<=.72&&fast.speed()>.70,"acceleration reaches capped cruising speed");
 for(int i=0;i<14;i++)fast=ScooterHandling.step(fast.x(),fast.z(),0,-1,true,true);check(fast.speed()<.10,"brakes stop instead of sustained slide");
 float drift=0;var slide=new ScooterHandling.Motion(0,.65);double yaw=0;for(int i=0;i<25;i++){yaw+=5;drift=ScooterHandling.driftNext(drift,slide.speed(),28,true);slide=ScooterHandling.step(slide.x(),slide.z(),yaw,1,true,true,drift);}double heading=Math.toDegrees(Math.atan2(-slide.x(),slide.z()));double slip=Math.abs(yaw-heading);check(slip>10&&slip<30&&slide.speed()>.64,"hard-turn power slide is bounded and preserves speed");
 for(int i=0;i<12;i++){drift=ScooterHandling.driftNext(drift,slide.speed(),0,true);slide=ScooterHandling.step(slide.x(),slide.z(),yaw,1,true,true,drift);}heading=Math.toDegrees(Math.atan2(-slide.x(),slide.z()));check(Math.abs(yaw-heading)<1&&drift<.005,"grip recovers quickly after hard turn");
 var airTurn=new ScooterHandling.Motion(0,.65);for(int i=0;i<20;i++)airTurn=ScooterHandling.airSteer(airTurn,28);check(airTurn.speed()>.649&&airTurn.x()<-.2,"air steering changes heading without momentum loss");
 var wall=ScooterHandling.wallSlide(new ScooterHandling.Motion(.3,.6),new ScooterHandling.Motion(0,.6),true);check(Math.abs(wall.speed()/Math.hypot(.3,.6)-.97)<1e-8&&wall.x()==0,"angled wall impact slides with only 3 percent momentum loss");
 var stop=ScooterHandling.wallSlide(new ScooterHandling.Motion(.6,.05),new ScooterHandling.Motion(0,.05),true);check(stop.speed()<.1,"head-on wall impact does not turn into a dash");
 var boost=ScooterHandling.step(0,1.0,0,1,true,true);check(boost.speed()>.98,"lunge overspeed decays smoothly instead of snapping to cruise cap");
 check(ScooterHandling.turnRate(0)>ScooterHandling.turnRate(.36)&&ScooterHandling.turnRate(.36)>ScooterHandling.turnRate(.72),"steering slows progressively with speed");
 }
}
