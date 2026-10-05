package pl.aridlin.kukirin;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
public record RentalTripPacket(java.util.UUID player,int ticks) implements CustomPacketPayload {
 public static final Type<RentalTripPacket> TYPE=new Type<>(ResourceLocation.parse("goplanska_kukirin:rental_trip"));
 public static final StreamCodec<RegistryFriendlyByteBuf,RentalTripPacket> CODEC=new StreamCodec<>(){public RentalTripPacket decode(RegistryFriendlyByteBuf b){return new RentalTripPacket(b.readUUID(),Math.clamp(b.readVarInt(),0,40));}public void encode(RegistryFriendlyByteBuf b,RentalTripPacket p){b.writeUUID(p.player);b.writeVarInt(p.ticks);}};
 public Type<RentalTripPacket> type(){return TYPE;}
 public static void register(net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent e){e.registrar("1").playToClient(TYPE,CODEC,(data,ctx)->ctx.enqueueWork(()->RentalTripClient.receive(data)));}
}
