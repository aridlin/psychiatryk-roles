package pl.aridlin.psychiatrykroles;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
public record PhasePacket() implements CustomPacketPayload {
 public static final PhasePacket INSTANCE=new PhasePacket();
 public static final Type<PhasePacket> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("psychiatryk_roles","phase_charm"));
 public static final StreamCodec<RegistryFriendlyByteBuf,PhasePacket> CODEC=StreamCodec.unit(INSTANCE);
 public Type<? extends CustomPacketPayload> type(){return TYPE;}

}
