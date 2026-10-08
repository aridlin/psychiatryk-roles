package pl.aridlin.kukirin;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
/** Content-addressed audio and the authoritative shared session position. Never logs authorization. */
public record ScooterAudioUrlPacket(UUID source,UUID session,boolean block,BlockPos position,String url,String bearer,String sha256,int bytes,boolean loop,long elapsedMillis,int durationTicks,int entityId) implements CustomPacketPayload {
 public ScooterAudioUrlPacket(UUID source,UUID session,boolean block,BlockPos position,String url,String bearer,String sha256,int bytes,boolean loop) {
  this(source,session,block,position,url,bearer,sha256,bytes,loop,0,0,-1);
 }
 public ScooterAudioUrlPacket {if(elapsedMillis<0||durationTicks<0||entityId< -1)throw new IllegalArgumentException("Invalid audio timeline");}
 public static final Type<ScooterAudioUrlPacket> TYPE=new Type<>(ResourceLocation.parse("goplanska_kukirin:audio_url"));
 public static final StreamCodec<RegistryFriendlyByteBuf,ScooterAudioUrlPacket> CODEC=StreamCodec.of((b,p)->{b.writeUUID(p.source);b.writeUUID(p.session);b.writeBoolean(p.block);b.writeBlockPos(p.position);b.writeUtf(p.url,2048);b.writeUtf(p.bearer,512);b.writeUtf(p.sha256,64);b.writeVarInt(p.bytes);b.writeBoolean(p.loop);b.writeVarLong(p.elapsedMillis);b.writeVarInt(p.durationTicks);b.writeVarInt(p.entityId);},b->new ScooterAudioUrlPacket(b.readUUID(),b.readUUID(),b.readBoolean(),b.readBlockPos(),b.readUtf(2048),b.readUtf(512),b.readUtf(64),b.readVarInt(),b.readBoolean(),b.readVarLong(),b.readVarInt(),b.readVarInt()));
 public Type<ScooterAudioUrlPacket> type(){return TYPE;}
 @Override public String toString(){return "ScooterAudioUrlPacket[source="+source+",session="+session+",bytes="+bytes+",block="+block+",elapsedMillis="+elapsedMillis+"]";}
 public static void register(net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent e){e.registrar("2").playToClient(TYPE,CODEC,(p,c)->c.enqueueWork(()->ScooterAudioClient.receive(p)));}
}
