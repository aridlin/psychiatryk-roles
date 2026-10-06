import pl.aridlin.kukirin.*;
/** Actual final candidate handling/taper methods; no game or world launch. */
public final class SoulSpeedBoundQA {
 static int checks;
 static void check(boolean yes,String name){if(!yes)throw new AssertionError(name);checks++;System.out.println("PASS "+name);}
 public static void main(String[] args){
  for(double limit:new double[]{.72*1.045,.72*1.4*1.045})for(double input:new double[]{0,1})for(double start:new double[]{limit,200/72d}){
   var taper=new ScooterOverspeed();var motion=new ScooterHandling.Motion(0,start);double previous=motion.speed();boolean bounded=true;
   for(int tick=0;tick<1000;tick++){
    motion=ScooterHandling.step(motion.x(),motion.z(),0,input,true,true,0,limit,.045,1);
    motion=ScooterFlight.accelerate(motion,0,input,previous,limit,false,false,true);
    motion=taper.apply(motion,limit,false,tick);
    bounded&=motion.speed()<=Math.max(start,limit)+1e-6;
    if(tick==99)check(motion.speed()<=limit+1e-6,"terrain cap reached by100ticks input="+input+" start="+start+" cap="+limit);
    previous=motion.speed();
   }
   check(bounded&&motion.speed()<=limit+1e-6,"no SoulSpeed runaway for1000ticks input="+input+" start="+start+" cap="+limit);
  }
  System.out.println("TOTAL "+checks);
 }
}
