import net.minecraft.world.phys.Vec3;
import pl.aridlin.psychiatrykroles.peeb.PeebAdventuresPhysics;
public final class CameraTugTest {
 static int checks; static void ok(boolean v,String s){checks++;if(!v)throw new AssertionError(s);}
 static void near(double a,double b,String s){ok(Math.abs(a-b)<1e-9,s);}
 public static void main(String[] args){
  Vec3 p=Vec3.ZERO,anchor=new Vec3(0,6,0),up=new Vec3(0,1,0),down=new Vec3(0,-1,0),forward=new Vec3(0,0,1);
  Vec3 tug=PeebAdventuresPhysics.cameraTug(p,up,down,anchor,6);
  ok(tug.y>0,"up to down loads taut overhead rope upward");near(tug.x,0,"radial only x");near(tug.z,0,"radial only z");
  near(tug.length(),.14,"endpoint force budget");
  ok(PeebAdventuresPhysics.cameraTug(p,up,down,anchor,7).equals(Vec3.ZERO),"slack consumes all endpoint displacement");
  ok(PeebAdventuresPhysics.cameraTug(p,down,up,anchor,6).equals(Vec3.ZERO),"inward endpoint motion does not push away");
  ok(PeebAdventuresPhysics.cameraTug(p,up,up,anchor,4).equals(Vec3.ZERO),"stationary view on already stretched rope adds nothing");
  ok(PeebAdventuresPhysics.cameraTug(p,null,down,anchor,4).equals(Vec3.ZERO),"initial attach no stale yank");
  Vec3 partial=PeebAdventuresPhysics.cameraTug(p,up,forward,anchor,6.02);
  near(partial.length(),(Math.sqrt(36+.55*.55)-6.02)*.7,"slack crossing transfers only extension beyond slack");
  Vec3 tiny=PeebAdventuresPhysics.cameraTug(p,forward,new Vec3(0,-.0001,1),anchor,5);
  ok(tiny.length()>0&&tiny.length()<.0001,"arbitrarily small movement continuous no flick threshold");
  Vec3 incoming=new Vec3(2,.4,1);Vec3 after=incoming.add(tug);near(after.x,incoming.x,"existing tangent x retained");near(after.z,incoming.z,"existing tangent z retained");near(after.y-incoming.y,tug.y,"additive impulse");
  Vec3 translate=new Vec3(100,250,-430);Vec3 same=PeebAdventuresPhysics.cameraTug(p.add(translate),up,down,anchor.add(translate),6);near(same.distanceTo(tug),0,"coordinate independent");
  ok(PeebAdventuresPhysics.cameraTug(p,Vec3.ZERO,down,anchor,4).equals(Vec3.ZERO),"zero look rejected");
  ok(PeebAdventuresPhysics.cameraTug(p,up,down,anchor,Double.NaN).equals(Vec3.ZERO),"invalid rest rejected");
  ok(PeebAdventuresPhysics.cameraTug(p,up,new Vec3(Double.NaN,1,0),anchor,4).equals(Vec3.ZERO),"invalid look rejected");
  System.out.println("{\"success\":true,\"checks\":"+checks+",\"camera_tug_verified\":true,\"geometric_endpoint_only\":true,\"game_launched\":false}");
 }
}
