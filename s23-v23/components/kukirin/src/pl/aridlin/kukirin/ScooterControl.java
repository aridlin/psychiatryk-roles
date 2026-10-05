package pl.aridlin.kukirin;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.*;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
public record ScooterControl(boolean mouseSteering,float steer,boolean upward,boolean drifting) implements CustomPacketPayload {
 public static final Type<ScooterControl> TYPE=new Type<>(ResourceLocation.parse("goplanska_kukirin:control"));
 public static final StreamCodec<RegistryFriendlyByteBuf,ScooterControl> CODEC=StreamCodec.composite(ByteBufCodecs.BOOL,ScooterControl::mouseSteering,ByteBufCodecs.FLOAT,ScooterControl::steer,ByteBufCodecs.BOOL,ScooterControl::upward,ByteBufCodecs.BOOL,ScooterControl::drifting,ScooterControl::new);
 public Type<ScooterControl> type(){return TYPE;}
 public static void register(net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent e){e.registrar("1").playToServer(TYPE,CODEC,(data,ctx)->ctx.enqueueWork(()->{if(ctx.player() instanceof ServerPlayer p&&p.getVehicle() instanceof Scooter s&&s.getControllingPassenger()==p){s.easyDrift(data.drifting());s.mouseSteering(data.mouseSteering());s.thrustUp(data.upward());if(Float.isFinite(data.steer()))s.remoteSteering(data.steer());}}));}
}
