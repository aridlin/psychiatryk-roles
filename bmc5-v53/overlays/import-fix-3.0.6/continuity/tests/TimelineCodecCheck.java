package pl.aridlin.kukirin;
import java.util.*;import java.nio.*;import java.io.*;import java.util.concurrent.atomic.*;
import io.netty.buffer.Unpooled;import net.minecraft.network.RegistryFriendlyByteBuf;import net.minecraft.core.RegistryAccess;import net.minecraft.core.BlockPos;import net.minecraft.client.sounds.AudioStream;import javax.sound.sampled.AudioFormat;
public class TimelineCodecCheck {
static int checks;static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
public static void main(String[]args)throws Exception{
 for(boolean loop:new boolean[]{false,true})for(long ms:new long[]{0,1,88_888,Long.MAX_VALUE})for(int entity:new int[]{-1,0,54321}){
  var packet=new ScooterAudioUrlPacket(UUID.randomUUID(),UUID.randomUUID(),false,new BlockPos(1,-20,4),"https://prol.aridlin.pl/scooter-audio/"+"a".repeat(64)+".wav","not-a-real-secret","a".repeat(64),1234567,loop,ms,1754,entity);
  var buf=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);ScooterAudioUrlPacket.CODEC.encode(buf,packet);check(ScooterAudioUrlPacket.CODEC.decode(buf).equals(packet),"native codec fields roundtrip");check(buf.readableBytes()==0,"all protocol2 payload consumed");check(!packet.toString().contains("not-a-real-secret"),"bearer redacted");buf.release();
 }
 for(int scenario=0;scenario<3;scenario++){boolean rejected=false;try{new ScooterAudioUrlPacket(UUID.randomUUID(),UUID.randomUUID(),false,BlockPos.ZERO,"","","",0,false,scenario==0?-1:0,scenario==1?-1:0,scenario==2?-2:0);}catch(IllegalArgumentException expected){rejected=true;}check(rejected,"invalid timeline rejected");}
 var oldCtor=new ScooterAudioUrlPacket(UUID.randomUUID(),UUID.randomUUID(),true,BlockPos.ZERO,"","","",0,true);check(oldCtor.elapsedMillis()==0&&oldCtor.durationTicks()==0&&oldCtor.entityId()==-1,"source compatibility constructor defaults");
 AtomicInteger calls=new AtomicInteger(),readBytes=new AtomicInteger(),largestRead=new AtomicInteger();AudioStream decoded=new AudioStream(){public AudioFormat getFormat(){return new AudioFormat(8000,16,1,true,false);}public ByteBuffer read(int bytes){largestRead.accumulateAndGet(bytes,Math::max);readBytes.addAndGet(bytes);return ByteBuffer.allocate(bytes);}public void close(){}};
 var sought=ScooterAudioSeekStream.at(decoded,()->calls.incrementAndGet()==1?2:2.2);check(Math.abs(sought.positionSeconds()-2.2)<.001,"native decoder catches preparation latency");check(readBytes.get()==35200,"exact PCM frames discarded");check(largestRead.get()<=32768,"native seek bounded decoded buffer");
 System.out.println("{\"success\":true,\"assertions\":"+checks+",\"actual_minecraft_codec\":true,\"native_game_launched\":false}");
}}
