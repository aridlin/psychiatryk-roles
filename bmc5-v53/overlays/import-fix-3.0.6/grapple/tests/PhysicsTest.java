import net.minecraft.world.phys.Vec3;
import pl.aridlin.psychiatrykroles.peeb.PeebAdventuresPhysics;
import java.util.Random;

public class PhysicsTest {
 static int checks;
 static void check(boolean ok,String label){checks++;if(!ok)throw new AssertionError(label);}
 static void near(double a,double b,String label){check(Math.abs(a-b)<=1E-9*Math.max(1,Math.max(Math.abs(a),Math.abs(b))),label+": "+a+" != "+b);}
 static void vector(Vec3 a,Vec3 b,String label){near(a.x,b.x,label+" x");near(a.y,b.y,label+" y");near(a.z,b.z,label+" z");}
 // Independent reference in the original game's units: metres and metres/second.
 static Vec3 reference(Vec3 p,Vec3 v,Vec3 desired,Vec3 a,double length,boolean grounded,boolean jump,double dt){
  double t=1-Math.pow(1-(grounded?.1:.1*.2),dt*60);
  Vec3 q=v.add(desired.subtract(v).scale(t));
  q=new Vec3(q.x,Math.max(-100,q.y-35*dt),q.z);
  if(grounded)q=new Vec3(q.x,Math.max(-4,q.y),q.z);
  if(grounded&&jump)q=new Vec3(q.x,12,q.z);
  double d=p.distanceTo(a),stretch=Math.max(d-length,0);
  return q.add(a.subtract(p).scale(stretch/length*1.5*dt*60*1.2));
 }
 public static void main(String[] args){
  Random r=new Random(4623);double s=PeebAdventuresPhysics.SCALE;
  for(int i=0;i<10000;i++){
   Vec3 p=new Vec3(r.nextDouble()*4,r.nextDouble()*3,r.nextDouble()*4);
   Vec3 a=new Vec3(r.nextDouble()*4,r.nextDouble()*6,r.nextDouble()*4);
   Vec3 v=new Vec3(r.nextDouble()*30-15,r.nextDouble()*30-15,r.nextDouble()*30-15);
   Vec3 desired=new Vec3(r.nextDouble()*22-11,0,r.nextDouble()*22-11);
   double length=.5+r.nextDouble()*5,dt=(i%2==0?.05:1./60);
   boolean ground=i%3==0,jump=i%7==0;
   Vec3 expected=reference(p,v,desired,a,length,ground,jump,dt).scale(s/20);
   vector(PeebAdventuresPhysics.step(p.scale(s),v.scale(s/20),desired.scale(s/20),a.scale(s),length*s,ground,jump,1,dt),expected,"original equations");
   near(PeebAdventuresPhysics.reel(length*s,100,dt),(Math.abs(2-length)<=.001?2:length+(2-length)*(1-Math.pow(.98,dt*60)))*s,"RLI rest length");
  }
  Vec3 tangent=new Vec3(1.2,.1,0),pivot=Vec3.ZERO,anchor=new Vec3(0,5,0);
  Vec3 pulled=PeebAdventuresPhysics.pull(pivot,tangent,anchor,3,1,1,.05);
  near(pulled.x,tangent.x,"spring retains tangent");near(pulled.z,tangent.z,"spring retains tangentz");
  check(pulled.y>tangent.y,"stretched hook pulls");
  check(PeebAdventuresPhysics.pull(pivot,tangent,anchor,6,1,1,.05)==tangent,"slack hook no impulse");
  for(int i=0;i<10000;i++){
   Vec3 incoming=new Vec3(r.nextDouble()*4-2,r.nextDouble()*4-2,r.nextDouble()*4-2);
   Vec3 force=new Vec3(r.nextDouble()-.5,r.nextDouble()-.5,r.nextDouble()-.5);
   Vec3 result=PeebAdventuresPhysics.addWithinBudget(incoming,incoming.add(force),.4,.8);
   check(result.subtract(incoming).horizontalDistance()<=.4+1E-9,"horizontal added budget");
   check(result.subtract(incoming).length()<=.8+1E-9,"total added budget");
   check(result.subtract(incoming).cross(force).length()<1E-9,"only spring direction changes; no tangent clamp");
   vector(PeebAdventuresPhysics.addWithinBudget(incoming,incoming,.4,.8),incoming,"preexisting overspeed retained");
  }
  Vec3 turn=PeebAdventuresPhysics.addWithinBudget(new Vec3(1,0,0),new Vec3(1,0,.1),.1,.2);
  near(turn.x,1,"overspeed perpendicular tangent retained");near(turn.z,.1,"overspeed anchor turns the swing");
  Vec3 coast=new Vec3(1.8,.6,.4);
  for(int i=0;i<10;i++){
   Vec3 next=PeebAdventuresPhysics.coastStep(coast,Vec3.ZERO,false,false,.05);
   near(next.x,coast.x,"neutral coastx");near(next.z,coast.z,"neutral coastz");
   near(next.y,coast.y-35*s*.05/20,"one gravity step");coast=next;
  }
  check(PeebAdventuresPhysics.coastStep(new Vec3(0,.2,0),Vec3.ZERO,true,true,.05).y>0,"ground jump");
  System.out.println("{\"success\":true,\"checks\":"+checks+",\"oracle\":\"Peeb Adventures metres/second equations vs block/tick port\",\"airborne_coasting\":true,\"game_launched\":false}");
 }
}
