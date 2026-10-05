package pl.aridlin.kukirin;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
public record ScooterLunge() implements CustomPacketPayload {
 public static final Type<ScooterLunge> TYPE=new Type<>(ResourceLocation.parse("goplanska_kukirin:lunge"));
 public static final StreamCodec<RegistryFriendlyByteBuf,ScooterLunge> CODEC=StreamCodec.unit(new ScooterLunge());
 public Type<ScooterLunge> type(){return TYPE;}
 public static void register(net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent e){e.registrar("1").playToServer(TYPE,CODEC,(data,ctx)->ctx.enqueueWork(()->{if(ctx.player() instanceof ServerPlayer p&&p.getVehicle() instanceof Scooter s){if(ScooterEnchants.level(s,"lunge")==0)p.displayClientMessage(net.minecraft.network.chat.Component.literal("Lunge requires the Lunge enchantment on this scooter."),true);else s.lunge(p);}}));}
}
