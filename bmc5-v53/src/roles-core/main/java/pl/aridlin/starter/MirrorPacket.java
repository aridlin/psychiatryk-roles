package pl.aridlin.starter;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
public record MirrorPacket() implements CustomPacketPayload {
 public static final MirrorPacket INSTANCE=new MirrorPacket();
 public static final Type<MirrorPacket> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("goplanska_starter","return_mirror"));
 public static final StreamCodec<RegistryFriendlyByteBuf,MirrorPacket> CODEC=StreamCodec.unit(INSTANCE);
 public Type<? extends CustomPacketPayload> type(){return TYPE;}
 public static boolean hasMirror(ServerPlayer p){for(int i=0;i<p.getInventory().getContainerSize();i++){var stack=p.getInventory().getItem(i);var data=stack.get(DataComponents.CUSTOM_DATA);if(stack.is(Items.ECHO_SHARD)&&data!=null&&data.copyTag().getBoolean("psychiatrykReturnMirror"))return true;}return false;}
 public static void activate(ServerPlayer p){
  if(!p.isAlive()||p.isSpectator()||!hasMirror(p)){p.displayClientMessage(Component.literal("Carry a Mirror of Returning in your inventory to use this key."),true);return;}
  try {var method=Class.forName("pl.aridlin.psychiatrykroles.PsychiatrykRoles").getDeclaredMethod("useReturnMirror",ServerPlayer.class);method.setAccessible(true);method.invoke(null,p);}
  catch(ReflectiveOperationException ex){throw new IllegalStateException("Mirror of Returning handler unavailable",ex);}
 }
}
