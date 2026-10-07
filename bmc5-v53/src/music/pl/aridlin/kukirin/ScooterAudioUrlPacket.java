package pl.aridlin.kukirin;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
/** Small playback metadata only; the protected WAV is fetched directly and cached by the listener. */
public record ScooterAudioUrlPacket(UUID source,UUID session,boolean block,BlockPos position,String url,String bearer,String sha256,int bytes,boolean loop) implements CustomPacketPayload {
 public static final Type<ScooterAudioUrlPacket> TYPE=new Type<>(ResourceLocation.parse("goplanska_kukirin:audio_url"));
 public static final StreamCodec<RegistryFriendlyByteBuf,ScooterAudioUrlPacket> CODEC=StreamCodec.of((b,p)->{b.writeUUID(p.source);b.writeUUID(p.session);b.writeBoolean(p.block);b.writeBlockPos(p.position);b.writeUtf(p.url,2048);b.writeUtf(p.bearer,512);b.writeUtf(p.sha256,64);b.writeVarInt(p.bytes);b.writeBoolean(p.loop);},b->new ScooterAudioUrlPacket(b.readUUID(),b.readUUID(),b.readBoolean(),b.readBlockPos(),b.readUtf(2048),b.readUtf(512),b.readUtf(64),b.readVarInt(),b.readBoolean()));
 public Type<ScooterAudioUrlPacket> type(){return TYPE;}
 @Override public String toString(){return "ScooterAudioUrlPacket[source="+source+",session="+session+",bytes="+bytes+",block="+block+"]";}
 public static void register(net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent e){e.registrar("1").playToClient(TYPE,CODEC,(p,c)->c.enqueueWork(()->ScooterAudioClient.receive(p)));}
}
