package pl.aridlin.kukirin;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
/** Bounded, paced server audio transfer. Empty content with total=0 stops playback. */
public record ScooterAudioPacket(java.util.UUID scooter,java.util.UUID session,int total,int offset,byte[] bytes) implements CustomPacketPayload {
 public static final Type<ScooterAudioPacket> TYPE=new Type<>(ResourceLocation.parse("goplanska_kukirin:audio"));
 public static final StreamCodec<RegistryFriendlyByteBuf,ScooterAudioPacket> CODEC=StreamCodec.of((b,p)->{b.writeUUID(p.scooter);b.writeUUID(p.session);b.writeVarInt(p.total);b.writeVarInt(p.offset);b.writeByteArray(p.bytes);},b->new ScooterAudioPacket(b.readUUID(),b.readUUID(),b.readVarInt(),b.readVarInt(),b.readByteArray(32768)));
 public Type<ScooterAudioPacket> type(){return TYPE;}
 public static void register(RegisterPayloadHandlersEvent e){e.registrar("1").playToClient(TYPE,CODEC,(p,c)->c.enqueueWork(()->ScooterAudioClient.receive(p)));}
}
