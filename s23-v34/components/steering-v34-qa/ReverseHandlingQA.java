import pl.aridlin.kukirin.ScooterHandling;
import java.util.Locale;

/** Driving outcomes, independent of keyboard sign or implementation formulas. */
public final class ReverseHandlingQA {
 static int checks;
 static void check(boolean ok,String name){if(!ok)throw new AssertionError(name);checks++;System.out.println("CHECK\t"+name);}
 static ScooterHandling.Motion tick(ScooterHandling.Motion m,double input,boolean brakeOnly){
  return ScooterHandling.step(m.x(),m.z(),0,input,true,true,0,.72,.045,1,.85,.045,brakeOnly);
 }
 public static void main(String[] args){
  var m=new ScooterHandling.Motion(0,0);
  for(int i=0;i<50;i++){m=tick(m,-1,false);check(m.z()<0&&Math.abs(m.x())<1e-8&&m.speed()<=.1600001,"S backs north at bounded speed "+i);}
  check(m.speed()>.159,"holding S reaches reverse cruise");
  m=new ScooterHandling.Motion(0,.72);boolean stopped=false,backed=false;
  for(int i=0;i<60;i++){
   double before=m.z();m=tick(m,-1,false);
   if(!stopped){check(m.z()>=-1e-9,"S brakes before reversing "+i);if(m.speed()<1e-8)stopped=true;else check(m.z()<before,"S reduces forward speed "+i);}
   else if(m.z()<-.01)backed=true;
  }
  check(stopped&&backed,"S transitions through stop into reverse");
  m=new ScooterHandling.Motion(0,-.16);stopped=false;boolean forward=false;
  for(int i=0;i<30;i++){
   m=tick(m,1,false);
   if(!stopped){check(m.z()<=1e-9,"W brakes reverse before forward "+i);stopped=m.speed()<1e-8;}
   else if(m.z()>.01)forward=true;
  }
  check(stopped&&forward,"W transitions through stop into forward");
  for(double input:new double[]{-1,0,1}){
   m=new ScooterHandling.Motion(0,.3);
   for(int i=0;i<100;i++)m=tick(m,input,true);
   check(m.speed()<1e-8,"brake-only never propels stopped scooter "+input);
  }
  for(double chord:new double[]{0,.001,.0069}){
   var t=ScooterHandling.steering(chord,2,28,0,true,false,2,true);
   check(t.yawDegrees()==0&&t.wheelDegrees()==28,"HUD-zero progress keeps chassis fixed "+chord);
  }
  // Steering-wheel direction remains the same in reverse; chassis rotation reverses.
  for(double input:new double[]{-28,28}){
   var f=ScooterHandling.steering(.15,.15,input,0,true,false,1,false);
   var b=ScooterHandling.steering(-.15,.15,input,0,true,false,1,false);
   check(Math.abs(f.yawDegrees()+b.yawDegrees())<1e-6,"reverse flips chassis curvature "+input);
   check(Math.abs(f.wheelDegrees()-b.wheelDegrees())<1e-6,"reverse preserves wheel direction "+input);
  }
  System.out.printf(Locale.ROOT,"TOTAL\t%d%n",checks);
 }
}
