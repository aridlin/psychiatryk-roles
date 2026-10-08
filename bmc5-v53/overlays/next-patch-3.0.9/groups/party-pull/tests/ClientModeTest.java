import java.util.*;
import java.lang.reflect.*;
import sun.misc.Unsafe;
import net.minecraft.client.*;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import pl.aridlin.psychiatrykroles.peeb.*;
import pl.aridlin.psychiatrykroles.peeb.client.*;

/** Execute the actual transplanted client methods and movement guard with a
 * native LocalPlayer whose movement writes are observable, without a window.
 */
public final class ClientModeTest {
 static int checks;
 static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
 static void field(Class<?> type,String name,Object value)throws Exception{var f=type.getDeclaredField(name);f.setAccessible(true);f.set(null,value);}
 static Object field(Class<?> type,String name)throws Exception{var f=type.getDeclaredField(name);f.setAccessible(true);return f.get(null);}
 public static void main(String[] args)throws Exception{
  var f=Unsafe.class.getDeclaredField("theUnsafe");f.setAccessible(true);Unsafe unsafe=(Unsafe)f.get(null);
  net.neoforged.fml.loading.LoadingModList.of(List.of(),List.of(),List.of(),List.of(),Map.of());
  net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();
  FakeLocal player=(FakeLocal)unsafe.allocateInstance(FakeLocal.class);player.uuid=UUID.randomUUID();player.abilities=new Abilities();player.velocity=new Vec3(.4,.1,.3);
  Minecraft game=Minecraft.getInstance();game.player=player;game.level=(ClientLevel)unsafe.allocateInstance(ClientLevel.class);
  field(PeebClient.class,"owner",player);field(PeebClient.class,"level",game.level);field(PeebClient.class,"owned",true);
  Map<UUID,Object> states=(Map<UUID,Object>)field(PeebClient.class,"STATES");states.clear();
  check(PeebClient.localActive(),"native client owner and active checks pass fixture");
  check(PeebClient.grapple(player).isEmpty(),"request has no locally predicted anchor before server state");
  check(!PeebMovement.travel(player,Vec3.ZERO)&&player.writes==0,"pre-ack walking does not apply a self-pull");
  PeebPackets.requestAttach(new Vec3(0,1,6));
  check(states.isEmpty()&&PeebClient.grapple(player).isEmpty(),"actual request packet does not manufacture local tether state");
  Class<?> received=Class.forName("pl.aridlin.psychiatrykroles.peeb.client.PeebClient$Received");var c=received.getDeclaredConstructor(PeebStatePayload.class,long.class);c.setAccessible(true);
  var packet=new PeebStatePayload(player.uuid,player.getId(),true,Optional.of(new Vec3(0,1,6)),6,0,true);
  states.put(player.uuid,c.newInstance(packet,0L));
  var rope=PeebClient.grapple(player);check(rope.isPresent()&&rope.get().pullingTeammate(),"actual transplanted grapple carries acknowledged mode");
  check(!PeebMovement.travel(player,Vec3.ZERO)&&player.writes==0,"acknowledged teammate rope leaves owner movement to vanilla");
  field(PeebClient.class,"lastPredictionTick",Long.MIN_VALUE);field(PeebClient.class,"previousTugLook",new Vec3(0,0,1));field(PeebClient.class,"previousTugAnchor",new Vec3(0,1,6));
  PeebClient.predict(new ClientTickEvent.Post());
  check(player.writes==0,"actual transplanted prediction applies no owner camera tug");
  check(field(PeebClient.class,"previousTugLook")==null&&field(PeebClient.class,"previousTugAnchor")==null,"teammate prediction clears stale camera-tug state");
  check(player.velocity.equals(new Vec3(.4,.1,.3)),"owner's existing momentum unchanged");
  states.put(player.uuid,c.newInstance(new PeebStatePayload(player.uuid,player.getId(),true,Optional.of(new Vec3(0,1,6)),6,0,false),0L));
  check(PeebClient.grapple(player).isPresent()&&!PeebClient.grapple(player).get().pullingTeammate(),"ordinary world/enemy tether retains its original mode");
  states.clear();
  System.out.println("{\"success\":true,\"checks\":"+checks+",\"actual_transplanted_client_methods\":true,\"pre_ack_self_pull_absent\":true,\"acknowledged_self_pull_absent\":true,\"window_free_singleton_double\":true,\"game_launched\":false}");
 }
 public static final class FakeLocal extends LocalPlayer {
  UUID uuid;Abilities abilities;Vec3 velocity;int writes;
  private FakeLocal(){super(null,null,null,null,null,false,false);}
  @Override public UUID getUUID(){return uuid;}
  @Override public int getId(){return 42;}
  @Override public boolean isAlive(){return true;}
  @Override public boolean isSleeping(){return false;}
  @Override public boolean isSpectator(){return false;}
  @Override public boolean isPassenger(){return false;}
  @Override public boolean isFallFlying(){return false;}
  @Override public boolean isInWater(){return false;}
  @Override public boolean isInLava(){return false;}
  @Override public boolean onClimbable(){return false;}
  @Override public Abilities getAbilities(){return abilities;}
  @Override public Entity getRootVehicle(){return this;}
  @Override public Vec3 getDeltaMovement(){return velocity;}
  @Override public void setDeltaMovement(Vec3 velocity){writes++;this.velocity=velocity;}
 }
}
