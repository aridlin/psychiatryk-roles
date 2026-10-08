import java.util.*;
import io.netty.buffer.Unpooled;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import pl.aridlin.kukirin.*;
import pl.aridlin.psychiatrykroles.peeb.*;
import pl.aridlin.psychiatrykroles.peeb.client.PeebClient;
import pl.aridlin.psychiatrykroles.music.*;
public final class Native309Test {
 static int checks;static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
 static void near(double a,double b,String why){check(Math.abs(a-b)<=.0001251,why);}
 public static void main(String[] args)throws Exception{
  UUID one=UUID.fromString("00000000-0000-0000-0000-000000000001"),two=UUID.fromString("00000000-0000-0000-0000-000000000002");
  check(new ScooterTuning.Values(1,.85,.045,.42,.35).minimumTurnRate()==2.2,"legacy tuning constructor");
  for(boolean friend:new boolean[]{false,true}){
   var state=new PeebStatePayload(one,21,true,Optional.of(new Vec3(2,3,4)),8,90,friend);
   var b=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);PeebStatePayload.STREAM_CODEC.encode(b,state);
   check(PeebStatePayload.STREAM_CODEC.decode(b).equals(state)&&b.readableBytes()==0,"packed Peeb protocol4 roundtrip");b.release();
  }
  check(!new PeebStatePayload(one,1,true,Optional.empty(),0).pullingTeammate(),"legacy state5");
  check(!new PeebStatePayload(one,1,true,Optional.empty(),0,0).pullingTeammate(),"legacy state6");
  check(!new PeebClient.Grapple(Vec3.ZERO,1,0).pullingTeammate(),"legacy client rope");
  for(boolean paused:new boolean[]{false,true})for(int volume:new int[]{0,17,100}){
   var packet=new ScooterAudioUrlPacket(one,two,false,BlockPos.ZERO,"https://example.invalid/immutable.wav","fixture","0".repeat(64),256,true,975,2000,21,paused,volume);
   var b=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);ScooterAudioUrlPacket.CODEC.encode(b,packet);
   check(ScooterAudioUrlPacket.CODEC.decode(b).equals(packet)&&b.readableBytes()==0,"unchanged packed music protocol3");b.release();
  }
  // This is the same native entity-motion transport used by authoritative party pulling.
  // Walking and 200km/h scooter velocities are well within vanilla's 3.9-block/tick wire bound.
  for(int targetId:new int[]{2,21,127,4096})for(Vec3 velocity:new Vec3[]{new Vec3(.1234,.25,-.4),new Vec3(2.78,.8,-.1),new Vec3(-3.2,.27123,1.9)}){
   var packet=new ClientboundSetEntityMotionPacket(targetId,velocity);var b=new FriendlyByteBuf(Unpooled.buffer());
   ClientboundSetEntityMotionPacket.STREAM_CODEC.encode(b,packet);var decoded=ClientboundSetEntityMotionPacket.STREAM_CODEC.decode(b);
   check(decoded.getId()==targetId,"target entity id retained");near(decoded.getXa(),velocity.x,"target X velocity");near(decoded.getYa(),velocity.y,"target Y velocity");near(decoded.getZa(),velocity.z,"target Z velocity");check(b.readableBytes()==0,"native motion codec consumes packet");b.release();
  }
  var inherited=new Vec3(2.7,.1,-.25);var next=PeebPartyPull.addImpulse(inherited,inherited,new Vec3(.9,0,0),.1,.2);
  check(next.subtract(inherited).horizontalDistance()<=.10001&&next.z==inherited.z,"combined bound preserves existing travel");
  check(PeebPartyPull.sameParty(3,3)&&!PeebPartyPull.sameParty(0,0)&&!PeebPartyPull.sameParty(3,4),"same native party contract");
  check(ScooterHandling.turnRate(2.78,2.2)>=2.19999&&ScooterHandling.turnRate(.1,2.2)>ScooterHandling.turnRate(2.78,2.2),"combined high-speed useful floor");
  check(ScooterHandling.steering(0,2.78,28,0,true,false,1,false,2.2).yawDegrees()==0,"no stationary spin");
  int[] paused={0},resumed={0};var state=new BackgroundMusicPauseState<Object,Object>();Object music=new Object(),handle=new Object();
  check(state.update(true,false,false,music,handle,h->paused[0]++,h->resumed[0]++),"custom audible pauses scheduler");
  check(paused[0]==1&&resumed[0]==0,"only existing channel paused");
  check(!state.update(false,false,false,music,handle,h->paused[0]++,h->resumed[0]++),"inaudible resumes scheduler");
  check(resumed[0]==1,"same background handle resumed");
  check(MusicAudibility.audible(4,1,1,1,16,false,true)&&!MusicAudibility.audible(400,1,1,1,16,false,true),"audibility boundary");
  for(String type:List.of("pl.aridlin.kukirin.ScooterTuningClient","pl.aridlin.psychiatrykroles.music.MusicBackgroundMixin","pl.aridlin.psychiatrykroles.music.SoundManagerMusicAccessor","pl.aridlin.psychiatrykroles.music.SoundEngineMusicAccessor","pl.aridlin.psychiatrykroles.compat.EmfOptionsCompatPlugin","pl.aridlin.psychiatrykroles.compat.EmfRenderModeLabelsMixin"))check(Class.forName(type,false,Native309Test.class.getClassLoader())!=null,"native signatures resolve "+type);
  System.out.println("{\"success\":true,\"checks\":"+checks+",\"native_target_motion_packet_verified\":true,\"native_peeb4_music3_codecs_verified\":true,\"legacy_constructor_compatibility_verified\":true,\"combined_tuning_party_music_behavior_verified\":true,\"game_launched\":false}");
 }
}
