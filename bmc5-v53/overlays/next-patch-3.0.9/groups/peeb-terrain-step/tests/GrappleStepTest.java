import java.util.*;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.*;
import pl.aridlin.psychiatrykroles.peeb.PeebAdventuresPhysics;

public final class GrappleStepTest {
 static int checks;
 static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
 static Vec3 collide(AABB box,Vec3 delta,List<VoxelShape> obstacles){
  double y=Shapes.collide(Direction.Axis.Y,box,obstacles,delta.y);box=box.move(0,y,0);
  double x=Shapes.collide(Direction.Axis.X,box,obstacles,delta.x);box=box.move(x,0,0);
  double z=Shapes.collide(Direction.Axis.Z,box,obstacles,delta.z);return new Vec3(x,y,z);
 }
 static double rise(AABB box,Vec3 motion,List<VoxelShape> walls){return PeebAdventuresPhysics.grappleStepRise(motion,box,(b,v)->collide(b,v,walls));}
 static Vec3 route(AABB box,Vec3 motion,double rise,List<VoxelShape> walls){
  Vec3 up=collide(box,new Vec3(0,rise,0),walls);box=box.move(up);
  Vec3 across=collide(box,new Vec3(motion.x,0,motion.z),walls);box=box.move(across);
  Vec3 down=collide(box,new Vec3(0,motion.y-up.y,0),walls);return up.add(across).add(down);
 }
 public static void main(String[] args){
  AABB body=new AABB(-.3,0,-.3,.3,1.8,.3);Vec3 motion=new Vec3(.8,-.12,0);
  var floor=Shapes.create(new AABB(-10,-1,-10,10,0,10));
  for(double height:new double[]{.25,.5,1,1.25,1.3}){
   var wall=Shapes.create(new AABB(.5,0,-1,2,height,1));var obstacles=List.of(floor,wall);
   double step=rise(body,motion,obstacles);check(step>0&&step<=1.3,"ground step height admitted");
   Vec3 result=route(body,motion,step,obstacles);check(Math.abs(result.x-motion.x)<1E-9,"ground forward momentum path clear");check(Math.abs(result.y-height)<1E-9,"ground settles on obstacle top");check(!Shapes.joinIsNotEmpty(Shapes.create(body.move(result)),wall,BooleanOp.AND),"body does not intersect wall");
   AABB airborne=body.move(0,.35,0);double airStep=rise(airborne,motion,obstacles);
   if(height>.35){check(airStep>0,"midair step admitted");Vec3 airResult=route(airborne,motion,airStep,obstacles);check(Math.abs(airResult.x-motion.x)<1E-9,"midair forward path clear");check(airborne.move(airResult).minY>=height-1E-9,"midair settles atop lip");}
  }
  for(double height:new double[]{1.31,1.5,2,4})check(rise(body,motion,List.of(floor,Shapes.create(new AABB(.5,0,-1,2,height,1))))==0,"tall wall cannot be stepped through");
  var wall=Shapes.create(new AABB(.5,0,-1,2,1,1));var lowCeiling=Shapes.create(new AABB(-1,2,-1,2,3,1));
  check(rise(body,motion,List.of(floor,wall,lowCeiling))==0,"ceiling blocks lifting entire body");
  check(rise(body,motion,List.of(floor))==0,"unobstructed ground does not add artificial lift");
  check(rise(body,Vec3.ZERO,List.of(wall))==0,"stationary grapple cannot climb");
  check(rise(body,new Vec3(0,-.2,0),List.of(wall))==0,"no horizontal input cannot climb");
  check(rise(body,new Vec3(.8,2,0),List.of(wall))==0,"upward flight already beyond step cap unchanged");
  check(rise(body,new Vec3(Double.NaN,0,0),List.of(wall))==0,"invalid movement rejected");
  var corner=Shapes.create(new AABB(-1,0,.5,2,3,2));var diagonal=new Vec3(.8,-.1,.8);var corners=List.of(floor,wall,corner);
  double cornerStep=rise(body,diagonal,corners);check(cornerStep>0,"beneficial partial corner step admitted");
  Vec3 cornerRoute=route(body,diagonal,cornerStep,corners);Vec3 cornerOrdinary=collide(body,diagonal,corners);
  check(cornerRoute.horizontalDistanceSqr()>cornerOrdinary.horizontalDistanceSqr(),"partial corner step makes actual progress");
  check(Math.abs(cornerRoute.x-.8)<1E-9,"cleared step preserves forward axis movement");
  check(cornerRoute.z<.8&&cornerRoute.z<=.2+1E-9,"uncleared tall wall still blocks other axis");
  check(!Shapes.joinIsNotEmpty(Shapes.create(body.move(cornerRoute)),corner,BooleanOp.AND),"partial step never intersects adjacent tall wall");
  check(!Shapes.joinIsNotEmpty(Shapes.create(body.move(cornerRoute)),wall,BooleanOp.AND),"partial step lands safely above low lip");
  for(double air:new double[]{0,.25,.5}){
   AABB airborne=body.move(0,air,0);double step=rise(airborne,diagonal,corners);check(step>0,"partial step works in ground/midair");
   Vec3 path=route(airborne,diagonal,step,corners);check(path.x>.7&&path.z<=.2+1E-9,"partial path preserves clear axis only");
   check(!Shapes.joinIsNotEmpty(Shapes.create(airborne.move(path)),corner,BooleanOp.AND),"midair corner collision remains authoritative");
  }
  for(double dx:new double[]{.15,.4,.8,1.2})for(double dz:new double[]{.15,.4,.8,1.2})for(double height:new double[]{.5,1,1.25}){
   var lip=Shapes.create(new AABB(.5,0,-1,2,height,1));var obstacles=List.of(floor,lip,corner);var delta=new Vec3(dx,-.1,dz);double step=rise(body,delta,obstacles);
   if(step>0){Vec3 ordinary=collide(body,delta,obstacles),path=route(body,delta,step,obstacles);check(path.horizontalDistanceSqr()>ordinary.horizontalDistanceSqr()+1E-10,"all accepted corner sweeps improve progress");
    for(var obstacle:obstacles)check(!Shapes.joinIsNotEmpty(Shapes.create(body.move(path)),obstacle,BooleanOp.AND),"accepted sweep ends outside all obstacles");}
  }
  var fullWall=Shapes.create(new AABB(.5,0,-1,2,3,1));check(rise(body,diagonal,List.of(floor,fullWall,corner))==0,"no horizontal improvement means no lift");
  System.out.println("{\"success\":true,\"checks\":"+checks+",\"native_voxel_collision_primitives\":true,\"ground_and_midair_step_verified\":true,\"walls_and_ceilings_rejected\":true,\"game_launched\":false}");
 }
}
