package pl.aridlin.kukirin;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
public record ScooterBoost() implements CustomPacketPayload {
 public static final Type<ScooterBoost> TYPE=new Type<>(ResourceLocation.parse("goplanska_kukirin:boost"));
 public static final StreamCodec<RegistryFriendlyByteBuf,ScooterBoost> CODEC=StreamCodec.unit(new ScooterBoost());
 @Override public Type<ScooterBoost> type(){return TYPE;}
 public static void register(RegisterPayloadHandlersEvent e){e.registrar("1").playToServer(TYPE,CODEC,(data,ctx)->ctx.enqueueWork(()->{if(ctx.player() instanceof ServerPlayer p&&p.getVehicle() instanceof Scooter s)s.windBoost(p);}));}
}
