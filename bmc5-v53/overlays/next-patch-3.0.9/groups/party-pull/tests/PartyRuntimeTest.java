import java.util.*;
import java.lang.reflect.*;
import sun.misc.Unsafe;
import com.mojang.authlib.GameProfile;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerScoreboard;
import net.minecraft.server.dedicated.*;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.level.Level;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.phys.*;
import net.minecraft.world.scores.*;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.*;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import pl.aridlin.parties.GoplanskaParties;
import pl.aridlin.psychiatrykroles.peeb.*;

/** Run production validators/event handlers against native Scoreboard/Event/
 * Saved session/payload types. Only unstarted server facts and transport are
 * substituted, so no game, socket, world generation or server process starts.
 */
public final class PartyRuntimeTest {
 static int checks;static Unsafe unsafe;static Class<?> sessionClass;static Map<UUID,Object> sessions;
 static void check(boolean ok,String reason){checks++;if(!ok)throw new AssertionError(reason);}
 static Object call(String method,Class<?>[] types,Object...args)throws Exception{var m=PeebGrapple.class.getDeclaredMethod(method,types);m.setAccessible(true);return m.invoke(null,args);}
 static void set(Object object,String name,Object value)throws Exception{var f=sessionClass.getDeclaredField(name);f.setAccessible(true);f.set(object,value);}
 static Object get(Object object,String name)throws Exception{var f=sessionClass.getDeclaredField(name);f.setAccessible(true);return f.get(object);}
 static boolean allowed(FakePlayer a,FakePlayer b)throws Exception{return (boolean)call("allowedTarget",new Class<?>[]{ServerPlayer.class,LivingEntity.class},a,b);}
 static boolean friend(FakePlayer a,FakePlayer b)throws Exception{return (boolean)call("teammateTarget",new Class<?>[]{ServerPlayer.class,ServerPlayer.class},a,b);}
 static Object hook(FakePlayer a,FakePlayer b)throws Exception{
  var c=sessionClass.getDeclaredConstructor(ServerPlayer.class);c.setAccessible(true);Object s=c.newInstance(a);
  set(s,"anchor",b.position().add(0,1,0));set(s,"targetUuid",b.getUUID());set(s,"targetOffset",new Vec3(0,1,0));
  set(s,"teammatePull",true);set(s,"ropeLength",6.0);sessions.put(a.getUUID(),s);return s;
 }
 static LivingEntity target(FakePlayer a,Object s)throws Exception{return (LivingEntity)call("target",new Class<?>[]{ServerPlayer.class,sessionClass},a,s);}
 public static void main(String[] args)throws Exception{
  var uf=Unsafe.class.getDeclaredField("theUnsafe");uf.setAccessible(true);unsafe=(Unsafe)uf.get(null);
  net.neoforged.fml.loading.LoadingModList.of(List.of(),List.of(),List.of(),List.of(),Map.of());
  SharedConstants.tryDetectVersion();Bootstrap.bootStrap();
  var f=PeebGrapple.class.getDeclaredField("SESSIONS");f.setAccessible(true);sessions=(Map<UUID,Object>)f.get(null);
  sessionClass=Class.forName("pl.aridlin.psychiatrykroles.peeb.PeebGrapple$Session");
  FakeServer server=(FakeServer)unsafe.allocateInstance(FakeServer.class);
  server.list=(FakeList)unsafe.allocateInstance(FakeList.class);server.list.online=new HashMap<>();
  server.board=new ServerScoreboard(server);
  server.board.addObjective("goplanska_party",ObjectiveCriteria.DUMMY,Component.literal("Party"),ObjectiveCriteria.RenderType.INTEGER,false,null);
  FakeLevel level=(FakeLevel)unsafe.allocateInstance(FakeLevel.class);level.key=Level.OVERWORLD;level.entities=new HashMap<>();
  FakePlayer a=player(server,level,"HookOwner",1),b=player(server,level,"HookFriend",2);
  assign(server,a,1);assign(server,b,1);
  check(GoplanskaParties.party(a)==1&&GoplanskaParties.party(b)==1,"actual native scoreboard membership read");
  b.xo=b.pos.x-.9;b.yo=b.pos.y+.1;b.zo=b.pos.z-.2;b.delta=new Vec3(.01,0,0);
  Vec3 sampled=(Vec3)call("initialTargetMomentum",new Class<?>[]{Entity.class},b);
  check(sampled.distanceToSqr(new Vec3(.9,-.1,.2))<1E-18,"first hook preserves native previous-position travel instead of stale server field");
  b.xo=b.pos.x;b.yo=b.pos.y;b.zo=b.pos.z;b.delta=new Vec3(.8,.3,.2);
  check(call("initialTargetMomentum",new Class<?>[]{Entity.class},b).equals(b.delta),"first hook preserves a just-queued native launch before it travels");
  b.delta=Vec3.ZERO;check(call("initialTargetMomentum",new Class<?>[]{Entity.class},b).equals(Vec3.ZERO),"actually stationary first target remains stationary before hook force");
  server.pvp=false;b.allied=true;b.abilities.invulnerable=true;
  check(friend(a,b)&&allowed(a,b),"teammate attach works with PvP disabled, allied and damage immunity");
  call("hookAttack",new Class<?>[]{ServerPlayer.class,LivingEntity.class},a,b);
  Object s=hook(a,b);call("contactAttack",new Class<?>[]{ServerPlayer.class,sessionClass},a,s);
  check(b.damageCalls==0,"actual hook/body attack handlers never damage teammate");
  check(target(a,s)==b,"actual native UUID target refresh retains same-party target");
  assign(server,b,2);check(!friend(a,b)&&!allowed(a,b)&&target(a,s)==null,"party switch breaks existing teammate mode and does not bypass PvP");
  b.allied=false;b.abilities.invulnerable=false;
  check(!allowed(a,b),"ordinary nonparty player cannot bypass disabled PvP");
  server.pvp=true;a.harm=false;check(!allowed(a,b),"ordinary nonparty player cannot bypass player damage protection");
  a.harm=true;b.abilities.invulnerable=true;check(!allowed(a,b),"ordinary nonparty damage-immune player cannot be hooked");
  b.abilities.invulnerable=false;check(allowed(a,b),"existing permitted hostile PvP target still works");
  server.pvp=false;b.allied=true;
  assign(server,b,0);check(!friend(a,b)&&target(a,s)==null,"leaving party breaks rope");
  assign(server,b,1);assign(server,a,0);check(!friend(a,b)&&target(a,s)==null,"owner leaving party breaks rope");
  assign(server,a,1);
  server.list.online.remove(b.uuid);check(!friend(a,b)&&target(a,s)==null,"logged-out native player-list target refused");server.list.online.put(b.uuid,b);
  FakePlayer replacement=player(server,level,"HookReplacement",3);server.list.online.put(b.uuid,replacement);
  check(!friend(a,b)&&target(a,s)==null,"stale target object after reconnect refused");server.list.online.put(b.uuid,b);
  b.alive=false;check(!friend(a,b)&&target(a,s)==null,"dead target refused");b.alive=true;
  b.disconnected=true;check(!friend(a,b)&&target(a,s)==null,"disconnected target refused");b.disconnected=false;
  b.changing=true;check(!friend(a,b)&&target(a,s)==null,"dimension transition refused");b.changing=false;
  b.spectator=true;check(!friend(a,b)&&target(a,s)==null,"spectator target refused");b.spectator=false;
  b.sleeping=true;check(!friend(a,b)&&target(a,s)==null,"sleeping target refused");b.sleeping=false;
  b.passenger=true;check(!friend(a,b)&&!allowed(a,b)&&target(a,s)==null,"non-controlling passenger cannot yank a vehicle");b.passenger=false;
  FakeLevel other=(FakeLevel)unsafe.allocateInstance(FakeLevel.class);other.key=Level.NETHER;other.entities=new HashMap<>();
  b.world=other;check(!friend(a,b)&&target(a,s)==null,"other dimension refused");b.world=level;
  var rf=Entity.class.getDeclaredField("removalReason");rf.setAccessible(true);rf.set(b,Entity.RemovalReason.DISCARDED);
  check(!friend(a,b)&&target(a,s)==null,"native entity removal state refused");rf.set(b,null);
  set(s,"teammatePull",false);check(target(a,s)==null,"hostile mode cannot become friendly attack after party change");set(s,"teammatePull",true);
  // Native lifecycle Event instances execute the real package handlers and
  // must emit cleared rope payloads for the owner immediately.
  for(String event:new String[]{"logout","dimension","respawn","death"}){
   s=hook(a,b);PacketDistributor.packets.clear();
   if(event.equals("logout"))call(event,new Class<?>[]{PlayerLoggedOutEvent.class},new PlayerLoggedOutEvent(b));
   if(event.equals("dimension"))call(event,new Class<?>[]{PlayerChangedDimensionEvent.class},new PlayerChangedDimensionEvent(b,Level.OVERWORLD,Level.NETHER));
   if(event.equals("respawn"))call(event,new Class<?>[]{PlayerRespawnEvent.class},new PlayerRespawnEvent(b,false));
   if(event.equals("death"))call(event,new Class<?>[]{LivingDeathEvent.class},new LivingDeathEvent(b,null));
   check(get(s,"anchor")==null&&!((boolean)get(s,"teammatePull")),"native "+event+" handler releases incoming rope immediately");
   check(PacketDistributor.packets.stream().anyMatch(p->p instanceof PeebStatePayload state&&state.playerUuid().equals(a.uuid)&&state.anchor().isEmpty()&&!state.pullingTeammate()),"native "+event+" emits owner release payload");
  }
  s=hook(a,b);PeebGrapple.release(a);check(get(s,"anchor")==null&&!((boolean)get(s,"teammatePull")),"manual release clears friendly mode");
  call("stopped",new Class<?>[]{ServerStoppedEvent.class},new ServerStoppedEvent(server));check(sessions.isEmpty(),"native stop clears all sessions");
  System.out.println("{\"success\":true,\"checks\":"+checks+",\"native_scoreboard_membership\":true,\"production_lifecycle_handlers\":true,\"no_damage_handlers\":true,\"pvp_bypass_rejected\":true,\"server_facts_and_transport_doubles\":true,\"game_launched\":false}");
 }
 static FakePlayer player(FakeServer server,FakeLevel level,String name,int id)throws Exception{
  FakePlayer p=(FakePlayer)unsafe.allocateInstance(FakePlayer.class);p.server=server;p.world=level;p.name=name;p.uuid=UUID.nameUUIDFromBytes(name.getBytes());p.id=id;p.alive=true;p.abilities=new Abilities();p.pos=new Vec3(0,0,id);
  server.list.online.put(p.uuid,p);level.entities.put(p.uuid,p);return p;
 }
 static void assign(FakeServer server,FakePlayer player,int party){server.board.getOrCreatePlayerScore(player,server.board.getObjective("goplanska_party")).set(party);}
 public static final class FakeServer extends DedicatedServer {
  FakeList list;ServerScoreboard board;boolean pvp;
  private FakeServer(){super(null,null,null,null,null,null,null,null);}
  @Override public DedicatedPlayerList getPlayerList(){return list;}
  @Override public ServerScoreboard getScoreboard(){return board;}
  @Override public boolean isPvpAllowed(){return pvp;}
 }
 public static final class FakeList extends DedicatedPlayerList {
  Map<UUID,ServerPlayer> online;
  private FakeList(){super(null,null,null);}
  @Override public ServerPlayer getPlayer(UUID uuid){return online.get(uuid);}
  @Override public List<ServerPlayer> getPlayers(){return List.copyOf(online.values());}
 }
 public static final class FakeLevel extends ServerLevel {
  ResourceKey<Level> key;Map<UUID,Entity> entities;
  private FakeLevel(){super(null,null,null,null,null,null,null,false,0,List.of(),false,null);}
  @Override public ResourceKey<Level> dimension(){return key;}
  @Override public Entity getEntity(UUID uuid){return entities.get(uuid);}
 }
 public static final class FakePlayer extends ServerPlayer {
  FakeServer server;FakeLevel world;UUID uuid;String name;int id;Abilities abilities;Vec3 pos;Vec3 delta;
  boolean alive,disconnected,changing,spectator,sleeping,passenger,allied,harm;int damageCalls;
  private FakePlayer(){super(null,null,new GameProfile(UUID.randomUUID(),"unused"),ClientInformation.createDefault());}
  @Override public MinecraftServer getServer(){return server;}
  @Override public Level level(){return world;}
  @Override public UUID getUUID(){return uuid;}
  @Override public String getScoreboardName(){return name;}
  @Override public int getId(){return id;}
  @Override public Vec3 position(){return pos;}
  @Override public Vec3 getDeltaMovement(){return delta;}
  @Override public boolean isAlive(){return alive;}
  @Override public boolean hasDisconnected(){return disconnected;}
  @Override public boolean isChangingDimension(){return changing;}
  @Override public boolean isSpectator(){return spectator;}
  @Override public boolean isSleeping(){return sleeping;}
  @Override public boolean isPickable(){return true;}
  @Override public boolean isPassenger(){return passenger;}
  @Override public Entity getRootVehicle(){return this;}
  @Override public boolean isAlliedTo(Entity entity){return allied;}
  @Override public boolean canHarmPlayer(net.minecraft.world.entity.player.Player player){return harm;}
  @Override public Abilities getAbilities(){return abilities;}
  @Override public boolean hurt(DamageSource source,float amount){damageCalls++;return true;}
 }
}
