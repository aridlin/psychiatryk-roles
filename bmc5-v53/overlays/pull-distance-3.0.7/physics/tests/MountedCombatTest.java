import java.util.*;
import java.lang.reflect.*;
import net.minecraft.world.phys.*;
import pl.aridlin.psychiatrykroles.peeb.*;
import pl.aridlin.kukirin.ScooterHandling;
public final class MountedCombatTest {
 static int checks;static void ok(boolean v,String n){checks++;if(!v)throw new AssertionError(n);}
 public static void main(String[] args)throws Exception{
  Vec3 position=Vec3.ZERO,anchor=new Vec3(0,1,8),observed=Vec3.ZERO;double rope=8;
  double maxSpeed=0;
  for(int tick=0;tick<24;tick++){
   rope=PeebGrapple.reelLength(rope,PeebConfig.DEFAULT);
   // Runtime ridden travel adds ordinary coast/engine grip; the server field
   // deliberately stays stale, as on client-owned Minecraft vehicles.
   Vec3 corrected=PeebGrapple.scooterPullVelocity(observed,position,anchor,rope,.72,PeebConfig.DEFAULT);
   ok(corrected.subtract(observed).horizontalDistance()<=.18+1e-9,"impulse bounded, not total velocity");
   var coast=ScooterHandling.step(corrected.x,corrected.z,0,0,true,true,0,.72,.045,1,.85,.045,false);
   observed=new Vec3(coast.x(),0,coast.z());position=position.add(observed);maxSpeed=Math.max(maxSpeed,observed.horizontalDistance());
  }
  ok(maxSpeed>14/72d,"mounted speed grows beyond flat seven km/h correction");
  Vec3 tangent=new Vec3(2,.2,0);Vec3 pulled=PeebGrapple.scooterPullVelocity(tangent,Vec3.ZERO,new Vec3(0,0,6),0,.72,PeebConfig.DEFAULT);
  ok(Math.abs(pulled.x-tangent.x)<1e-9,"mounted tangent preserved");ok(pulled.z>0,"mounted force actual addition");
  AABB player=new AABB(0,0,0,.6,1.8,.6);
  ok(PeebGrapple.bodyContact(player,new AABB(.65,0,0,1.25,1.8,.6)),"forgiving actual body collision");
  ok(!PeebGrapple.bodyContact(player,new AABB(1.2,0,0,1.8,1.8,.6)),"old 1.8 proximity does not hit");
  ok(!PeebGrapple.bodyContact(player,new AABB(0,2.1,0,.6,3.9,.6)),"height separated body does not hit");
  ok(!PeebGrapple.bodyContact(player,null),"missing target safe");
  ok(PeebGrapple.HOOK_DAMAGE==2&&PeebGrapple.CONTACT_DAMAGE==6,"hook one heart and iron sword impact");
  Method claim=PeebGrapple.class.getDeclaredMethod("claimAttack",Map.class,UUID.class,long.class);claim.setAccessible(true);
  UUID id=UUID.randomUUID();Map<UUID,Long> hook=new HashMap<>(),body=new HashMap<>();
  ok((boolean)claim.invoke(null,hook,id,100L),"initial hook claimed");ok(!(boolean)claim.invoke(null,hook,id,101L),"reattach hook spam blocked");
  ok((boolean)claim.invoke(null,body,id,101L),"hook does not suppress same tether body strike");ok(!(boolean)claim.invoke(null,body,id,119L),"body cooldown independent");
  ok((boolean)claim.invoke(null,hook,id,120L),"hook cooldown expires at twenty ticks");ok((boolean)claim.invoke(null,body,UUID.randomUUID(),102L),"players have own cooldown");
  System.out.println("{\"success\":true,\"checks\":"+checks+",\"mounted_additive_momentum_verified\":true,\"combat_bounds_cooldowns_verified\":true,\"maximum_simulated_kmh\":"+maxSpeed*72+",\"game_launched\":false}");
 }
}
