import java.util.*;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.*;
import net.minecraft.core.Direction;
import pl.aridlin.psychiatrykroles.peeb.*;

public final class PartyPullTest {
 static int checks;
 static void check(boolean ok,String reason){checks++;if(!ok)throw new AssertionError(reason);}
 static void near(Vec3 a,Vec3 b,String reason){check(a.distanceToSqr(b)<1E-18,reason);}
 public static void main(String[] args){
  for(int a:new int[]{-1,0,1,2,19})for(int b:new int[]{-1,0,1,2,19})
   check(PeebPartyPull.sameParty(a,b)==(a>0&&a==b),"positive native party identity only");
  Vec3 target=new Vec3(0,1,6),peeb=new Vec3(0,1,0),incoming=new Vec3(.71,-.04,.03);
  for(double strength:new double[]{.05,.3,1})for(double h:new double[]{.1,.4,.7}){
   var settings=new PeebConfig.Values(8,h,Math.max(h,.8),strength,true,0,false);
   Vec3 velocity=PeebGrapple.pullVelocity(target,incoming,peeb,3,settings,false);
   Vec3 force=velocity.subtract(incoming);
   check(force.z<0,"teammate pulls toward Peeb, not toward teammate");
   check(Math.abs(velocity.x-incoming.x)<1E-12,"sideways momentum preserved");
   check(force.horizontalDistance()<=h*.25+1E-12,"existing horizontal force budget");
   check(force.length()<=settings.maxSpeed()*.25+1E-12,"existing total force budget");
   Vec3 slack=PeebGrapple.pullVelocity(target,incoming,peeb,8,settings,false);
   near(slack,incoming,"slack cannot push or wipe velocity");
   Vec3 once=PeebPartyPull.addImpulse(incoming,incoming,force,h*.25,settings.maxSpeed()*.25);
   Vec3 twice=PeebPartyPull.addImpulse(incoming,once,force,h*.25,settings.maxSpeed()*.25);
   check(twice.subtract(incoming).horizontalDistance()<=h*.25+1E-12,"combined two-hook horizontal budget");
   check(twice.subtract(incoming).length()<=settings.maxSpeed()*.25+1E-12,"combined two-hook total budget");
  }
  for(double cruise:new double[]{.1,1,2.8}){
   Vec3 velocity=PeebGrapple.scooterPullVelocity(incoming,target,peeb,3,cruise,PeebConfig.DEFAULT);
   Vec3 force=velocity.subtract(incoming);double cap=Math.max(1.2,Math.min(3,cruise*1.2));
   check(force.z<0,"controlling rider scooter pulled toward Peeb");
   check(force.horizontalDistance()<=cap*.15+1E-12,"unchanged mounted horizontal budget");
   check(force.length()<=Math.max(1.8,cap+.8)*.15+1E-12,"unchanged mounted total budget");
   check(Math.abs(velocity.x-incoming.x)<1E-12,"mounted sideways momentum retained");
  }
  for(double range:new double[]{1,2,8,16,32}){
   check(PeebPartyPull.withinRange(Vec3.ZERO,new Vec3(0,0,range),range),"configured boundary retained");
   check(!PeebPartyPull.withinRange(Vec3.ZERO,new Vec3(0,0,range+.151),range),"no sustained rescue beyond configured reach grace");
   for(long age:new long[]{0,1,2})check(PeebPartyPull.withinRange(Vec3.ZERO,new Vec3(0,0,range+.2),range,PeebPartyPull.rangeGrace(age)),"initial near-limit transport grace survives state sync interval");
   for(long age:new long[]{-1,3,4,30})check(!PeebPartyPull.withinRange(Vec3.ZERO,new Vec3(0,0,range+.2),range,PeebPartyPull.rangeGrace(age)),"initial grace cannot become permanent expanded reach");
  }
  check(!PeebPartyPull.withinRange(Vec3.ZERO,target,8,.351),"caller cannot expand initial grace");
  check(!PeebPartyPull.withinRange(Vec3.ZERO,new Vec3(Double.NaN,0,0),8),"NaN cannot hold rope");
  check(!PeebPartyPull.withinRange(null,target,8),"absent origin cannot hold rope");
  near(PeebPartyPull.addImpulse(incoming,incoming,new Vec3(Double.NaN,0,0),.1,.2),incoming,"invalid force preserves momentum");
  // Native voxel sweeps: a full wall blocks the added force, a free path does
  // not. We do not manufacture a teleport or a custom collision approximation.
  AABB body=new AABB(0,0,0,.6,1.8,.6);Vec3 towardWall=new Vec3(.1,0,0);
  var wall=Shapes.create(new AABB(.6,-1,-2,2,3,2));
  check(Shapes.collide(Direction.Axis.X,body,List.of(wall),towardWall.x)==0,"actual native wall rejects added impulse");
  check(Shapes.collide(Direction.Axis.X,body,List.of(),towardWall.x)==towardWall.x,"native free sweep preserves added impulse");
  System.out.println("{\"success\":true,\"checks\":"+checks+",\"native_vec3_force_budget\":true,\"native_voxel_collision\":true,\"game_launched\":false}");
 }
}
