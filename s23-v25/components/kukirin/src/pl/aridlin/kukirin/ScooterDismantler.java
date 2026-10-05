package pl.aridlin.kukirin;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
public record ScooterDismantler(BlockPos pos,InteractionHand hand,boolean confirm) implements CustomPacketPayload {
 public static final Type<ScooterDismantler> TYPE=new Type<>(ResourceLocation.parse("goplanska_kukirin:dismantle"));
 public static final StreamCodec<RegistryFriendlyByteBuf,ScooterDismantler> CODEC=new StreamCodec<>(){public ScooterDismantler decode(RegistryFriendlyByteBuf b){return new ScooterDismantler(b.readBlockPos(),b.readBoolean()?InteractionHand.OFF_HAND:InteractionHand.MAIN_HAND,b.readBoolean());}public void encode(RegistryFriendlyByteBuf b,ScooterDismantler p){b.writeBlockPos(p.pos());b.writeBoolean(p.hand()==InteractionHand.OFF_HAND);b.writeBoolean(p.confirm());}};
 public Type<ScooterDismantler> type(){return TYPE;}
 public static void register(net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent e){var r=e.registrar("1");r.playBidirectional(TYPE,CODEC,(data,ctx)->ctx.enqueueWork(()->{if(ctx.flow()==net.minecraft.network.protocol.PacketFlow.CLIENTBOUND){ScooterDismantlerClient.open(data);return;}var p=ctx.player();if(!data.confirm()||p.distanceToSqr(data.pos().getCenter())>36||!p.level().getBlockState(data.pos()).is(Kukirin.DISMANTLER.get()))return;var stack=p.getItemInHand(data.hand());if(stack.is(Kukirin.ITEM.get())&&ScooterEnchants.bound(stack)&&p.getUUID().equals(ScooterEnchants.owner(stack))){stack.shrink(1);p.displayClientMessage(net.minecraft.network.chat.Component.literal("Bound scooter dismantled."),false);}}));}
}
