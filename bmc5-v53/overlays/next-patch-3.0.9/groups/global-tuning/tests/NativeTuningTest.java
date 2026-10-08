package pl.aridlin.kukirin;
import java.nio.file.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.network.handling.*;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
public final class NativeTuningTest {
 static int checks;static void check(boolean v,String m){checks++;if(!v)throw new AssertionError(m);}
 static void near(double a,double b,String m){check(Math.abs(a-b)<1E-5,m+" "+a+" != "+b);}
 static class Recorder extends PayloadRegistrar {
  IPayloadHandler<ScooterAdmin.Edit> edit;IPayloadHandler<ScooterAdmin.State> state;
  Recorder(){super("fixture");}
  @SuppressWarnings("unchecked") @Override public <T extends CustomPacketPayload> PayloadRegistrar playToServer(CustomPacketPayload.Type<T> t,StreamCodec<? super RegistryFriendlyByteBuf,T> c,IPayloadHandler<T> h){edit=(IPayloadHandler<ScooterAdmin.Edit>)h;return this;}
  @SuppressWarnings("unchecked") @Override public <T extends CustomPacketPayload> PayloadRegistrar playToClient(CustomPacketPayload.Type<T> t,StreamCodec<? super RegistryFriendlyByteBuf,T> c,IPayloadHandler<T> h){state=(IPayloadHandler<ScooterAdmin.State>)h;return this;}
 }
 static class Registration extends RegisterPayloadHandlersEvent {
  Recorder recorder=new Recorder();String version;
  @Override public PayloadRegistrar registrar(String value){version=value;return recorder;}
 }
 static class TestPlayer extends ServerPlayer {
  boolean authorized;int messages;
  TestPlayer(){super(null,null,new GameProfile(UUID.randomUUID(),"fixture"),ClientInformation.createDefault());}
  @Override public boolean hasPermissions(int level){return authorized&&level<=2;}
  @Override public void displayClientMessage(Component message,boolean actionBar){messages++;}
 }
 static TestPlayer player(boolean admin)throws Exception {
  var f=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");f.setAccessible(true);var unsafe=(sun.misc.Unsafe)f.get(null);
  var p=(TestPlayer)unsafe.allocateInstance(TestPlayer.class);p.authorized=admin;return p;
 }
 static IPayloadContext context(ServerPlayer player){
  return (IPayloadContext)Proxy.newProxyInstance(NativeTuningTest.class.getClassLoader(),new Class<?>[]{IPayloadContext.class},(proxy,method,args)->{
   if(method.getName().equals("player"))return player;
   if(method.getName().equals("enqueueWork")){Object action=args[0];Object value=null;if(action instanceof Runnable run)run.run();else value=((Supplier<?>)action).get();return CompletableFuture.completedFuture(value);}
   throw new UnsupportedOperationException(method.getName());
  });
 }
 public static void main(String[] args)throws Exception {
  net.neoforged.fml.loading.LoadingModList.of(List.of(),List.of(),List.of(),List.of(),Map.of());
  net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();
  check(ScooterTuning.DEFAULT.minimumTurnRate()==2.2,"default improves useful high-speed turning");
  check(new ScooterTuning.Values(1,.85,.045,.42,.35).minimumTurnRate()==2.2,"five-value binary constructor");
  for(double floor:new double[]{.5,1.344,2.2,4,6})for(boolean open:new boolean[]{false,true}){
   var v=new ScooterTuning.Values(1,.85,.045,.42,.35,floor);check(v.valid(),"valid min");
   var b=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);var s=new ScooterAdmin.State(open,v);ScooterAdmin.State.CODEC.encode(b,s);
   check(b.readableBytes()==49,"state wire size");check(ScooterAdmin.State.CODEC.decode(b).equals(s),"native state roundtrip");check(b.readableBytes()==0,"native state full consumption");b.release();
   b=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);var e=new ScooterAdmin.Edit(open,v);ScooterAdmin.Edit.CODEC.encode(b,e);check(ScooterAdmin.Edit.CODEC.decode(b).equals(e),"native edit roundtrip");b.release();
  }
  for(double bad:new double[]{-.1,0,.49,6.01,Double.NaN,Double.NEGATIVE_INFINITY,Double.POSITIVE_INFINITY})check(!new ScooterTuning.Values(1,.85,.045,.42,.35,bad).valid(),"invalid minimum rejected");
  Files.createDirectories(ScooterTuning.FILE.getParent());
  Files.writeString(ScooterTuning.FILE,"steeringMultiplier=1.5\nnormalGrip=0.9\ndriftGrip=0.1\ndriftRecovery=0.5\ntyreVolume=0.2\n");
  var old=ScooterTuning.reload();check(old.steering()==1.5&&old.minimumTurnRate()==2.2,"existing file migration preserves tuning");
  var shared=new ScooterTuning.Values(1.25,.9,.1,.55,.8,3.1);ScooterTuning.save(shared);check(ScooterTuning.reload().equals(shared),"native shared save/reload");
  check(Files.readString(ScooterTuning.FILE).contains("minimumTurnRate=3.1"),"persisted floor");
  byte[] before=Files.readAllBytes(ScooterTuning.FILE);int[] publishes={0};
  check(!ScooterAdmin.applyEdit(false,ScooterTuning.DEFAULT,()->publishes[0]++),"unauthorized rejected");
  check(Arrays.equals(before,Files.readAllBytes(ScooterTuning.FILE))&&publishes[0]==0,"unauthorized cannot save/broadcast");
  check(!ScooterAdmin.applyEdit(true,new ScooterTuning.Values(1,.85,.045,.42,.35,Double.NaN),()->publishes[0]++),"invalid admin edit rejected");
  check(Arrays.equals(before,Files.readAllBytes(ScooterTuning.FILE))&&publishes[0]==0,"invalid cannot save/broadcast");
  var p1=player(false);var p2=player(false);var late=player(false);var admin=player(true);
  check(ScooterAdmin.applyEdit(true,shared,()->{ScooterAdmin.sendToPlayers(List.of(p1,p2),ScooterTuning.server());publishes[0]++;}),"authorized applies");
  check(PacketDistributor.sent.size()==2&&PacketDistributor.sent.get(0).player()==p1&&PacketDistributor.sent.get(1).player()==p2,"all current recipients receive snapshot");
  for(var record:PacketDistributor.sent)check(record.packet().equals(new ScooterAdmin.State(false,shared)),"same server snapshot for everyone");
  Registration registration=new Registration();ScooterAdmin.register(registration);check(registration.version.equals("2"),"required protocol 2");
  registration.recorder.edit.handle(new ScooterAdmin.Edit(true,ScooterTuning.DEFAULT),context(p1));check(PacketDistributor.sent.size()==2,"real handler rejects non-admin");
  registration.recorder.edit.handle(new ScooterAdmin.Edit(true,new ScooterTuning.Values(1,.85,.045,.42,.35,99)),context(admin));check(admin.messages==1&&PacketDistributor.sent.size()==2,"real handler rejects invalid admin values");
  registration.recorder.state.handle(new ScooterAdmin.State(false,shared),context(null));check(ScooterTuning.get(true).equals(shared),"real client callback applies server prediction snapshot");
  registration.recorder.state.handle(new ScooterAdmin.State(false,new ScooterTuning.Values(1,.85,.045,.42,.35,99)),context(null));check(ScooterTuning.get(true).equals(shared),"invalid received snapshot ignored");
  ScooterAdmin.login(new PlayerEvent.PlayerLoggedInEvent(late));check(PacketDistributor.sent.getLast().player()==late&&PacketDistributor.sent.getLast().packet().equals(new ScooterAdmin.State(false,shared)),"later joiner receives server snapshot");
  ScooterAdmin.respawn(new PlayerEvent.PlayerRespawnEvent(late,false));check(PacketDistributor.sent.getLast().packet().equals(new ScooterAdmin.State(false,shared)),"respawn resynchronizes");
  ScooterAdmin.dimension(new PlayerEvent.PlayerChangedDimensionEvent(late,Level.OVERWORLD,Level.NETHER));check(PacketDistributor.sent.getLast().packet().equals(new ScooterAdmin.State(false,shared)),"dimension resynchronizes");
  ScooterTuningClient.logout(null);check(ScooterTuning.get(true).equals(ScooterTuning.DEFAULT),"logout clears previous server prediction");
  ScooterTuning.receive(shared);check(ScooterTuning.get(true).equals(shared),"next connection accepts snapshot");
  // Near and far speed retain a monotonic envelope; configured floor never permits turning at rest.
  for(double floor:new double[]{.5,2.2,4,6}){
   double previous=Double.POSITIVE_INFINITY;
   for(double speed:new double[]{0,.007,.1,.4,.72,1,2,3,5,20}){
    double rate=ScooterHandling.turnRate(speed,floor);check(rate<=previous+1E-5&&rate>=floor-1E-5,"monotonic bounded speed rate");previous=rate;
    for(double sign:new double[]{-1,1}){
     var solution=ScooterHandling.steering(sign*speed,speed,28,0,true,false,1,false,floor);
     check(Math.abs(solution.wheelDegrees())<=28.0001,"geometry maximum wheel");
     if(speed<.007)near(solution.yawDegrees(),0,"stationary yaw stays zero");
     else{check(Math.signum(solution.yawDegrees())==sign,"reverse geometry direction");double implied=Math.toDegrees(2*Math.asin(speed*Math.tan(Math.toRadians(solution.wheelDegrees()))/(2*ScooterHandling.WHEELBASE)));near(implied*sign,solution.yawDegrees(),"visible wheel and turn radius match");}
    }
   }
  }
  near(ScooterHandling.turnRate(2.78,2.2),2.2,"200kmh retains floor");
  check(ScooterHandling.turnRate(.1,2.2)>ScooterHandling.turnRate(2.78,2.2),"low speed remains faster");
  check(ScooterHandling.steering(2.78,2.78,28,0,true,false,1,false,2.2).yawDegrees()>ScooterHandling.steering(2.78,2.78,28,0,true,false,1,false,1.344).yawDegrees(),"high speed floor applied in actual solver");
  var both=ScooterHandling.steering(.5,.5,28,0,true,false,1,true,2.2);var ordinary=ScooterHandling.steering(.5,.5,28,0,true,false,1,false,2.2);check(both.yawDegrees()>ordinary.yawDegrees(),"W+S tighter turns retained");
  near(ScooterHandling.steering(1,1,0,0,true,false,1,false,2.2).yawDegrees(),0,"no spontaneous turning");
  check(Float.isFinite(ScooterHandling.turnRate(Double.NaN,2.2)),"nonfinite speed safe");
  System.out.println("{\"success\":true,\"checks\":"+checks+",\"native_config_codec_verified\":true,\"handling_protocol2_verified\":true,\"global_broadcast_join_sync_verified\":true,\"native_handler_permission_verified\":true,\"prediction_server_values_verified\":true,\"high_speed_turn_floor_verified\":true,\"geometry_reverse_stationary_verified\":true,\"game_launched\":false}");
  net.minecraft.Util.shutdownExecutors();
 }
}
