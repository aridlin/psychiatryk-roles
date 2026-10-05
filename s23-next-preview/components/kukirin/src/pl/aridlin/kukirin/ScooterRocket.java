package pl.aridlin.kukirin;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
/** Only the held firework is consumed; the server validates rider, ownership and existing boost. */
public record ScooterRocket(boolean offhand) implements CustomPacketPayload {
 public static final Type<ScooterRocket> TYPE=new Type<>(ResourceLocation.parse("goplanska_kukirin:rocket"));
 public static final StreamCodec<RegistryFriendlyByteBuf,ScooterRocket> CODEC=new StreamCodec<>(){public ScooterRocket decode(RegistryFriendlyByteBuf b){return new ScooterRocket(b.readBoolean());}public void encode(RegistryFriendlyByteBuf b,ScooterRocket p){b.writeBoolean(p.offhand);}};
 public Type<ScooterRocket> type(){return TYPE;}
 public static void register(net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent e){e.registrar("1").playToServer(TYPE,CODEC,(data,ctx)->ctx.enqueueWork(()->{if(ctx.player() instanceof net.minecraft.server.level.ServerPlayer p&&p.getVehicle() instanceof Scooter s)s.igniteRocket(p,data.offhand?net.minecraft.world.InteractionHand.OFF_HAND:net.minecraft.world.InteractionHand.MAIN_HAND);}));}
}
