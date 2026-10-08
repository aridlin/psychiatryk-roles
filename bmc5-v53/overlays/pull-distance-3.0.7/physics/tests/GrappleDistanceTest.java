import net.minecraft.world.phys.Vec3;
import pl.aridlin.psychiatrykroles.peeb.*;
public final class GrappleDistanceTest {
 static int checks; static void ok(boolean v,String s){checks++;if(!v)throw new AssertionError(s);}
 public static void main(String[] args){
  Vec3 incoming=new Vec3(2,.5,.1),anchor=new Vec3(0,.2,0);
  ok(PeebConfig.DEFAULT.stopDistance()==0,"default full pull");
  ok(PeebGrapple.constrainVelocity(Vec3.ZERO,incoming,anchor,0,PeebConfig.DEFAULT,false).equals(incoming),"zero rest validates and preserves movement");
  ok(PeebGrapple.pullVelocity(Vec3.ZERO,incoming,anchor,0,PeebConfig.DEFAULT,false).y>incoming.y,"actual gameplay method pulls when less than old gap");
  ok(PeebGrapple.pullVelocity(Vec3.ZERO,incoming,anchor,0,PeebConfig.DEFAULT,false).x==incoming.x,"actual gameplay method retains tangent");
  for(double stop:new double[]{0,.25,1,3,32}){
   var v=new PeebConfig.Values(8,.4,.8,.3,true,stop);double length=8;
   for(int i=0;i<240;i++)length=PeebGrapple.reelLength(length,v);
   ok(length==Math.min(stop,8),"gameplay reel reads actual configured target");
   ok(PeebGrapple.predictedRestLength(length,2,v)==length,"client prediction same target");
  }
  ok(new PeebConfig.Values(8,.4,.8,.3,true).stopDistance()==0,"legacy constructor compatibility");
  System.out.println("{\"success\":true,\"checks\":"+checks+",\"actual_gameplay_helpers\":true,\"zero_rest_accepted\":true,\"game_launched\":false}");
 }
}
