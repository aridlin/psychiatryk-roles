package pl.aridlin.kukirin;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
public record ScooterStorageOpen() implements CustomPacketPayload {
 public static final Type<ScooterStorageOpen> TYPE=new Type<>(ResourceLocation.parse("goplanska_kukirin:storage_open"));
 public static final StreamCodec<RegistryFriendlyByteBuf,ScooterStorageOpen> CODEC=StreamCodec.unit(new ScooterStorageOpen());
 public Type<ScooterStorageOpen> type(){return TYPE;}
 public static void register(net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent event){event.registrar("1").playToServer(TYPE,CODEC,(data,context)->context.enqueueWork(()->{if(context.player() instanceof net.minecraft.server.level.ServerPlayer p&&p.getVehicle() instanceof Scooter s)ScooterStorage.open(s,p);}));}
}
