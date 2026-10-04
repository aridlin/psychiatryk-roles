package pl.aridlin.starter;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Rotation;
public record PreviewPacket(BlockPos origin,Rotation rotation,boolean visible,boolean valid) implements CustomPacketPayload {
 public static final Type<PreviewPacket> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("goplanska_starter","preview"));
 public static final StreamCodec<RegistryFriendlyByteBuf,PreviewPacket> CODEC=new StreamCodec<>(){
  public PreviewPacket decode(RegistryFriendlyByteBuf b){return new PreviewPacket(b.readBlockPos(),b.readEnum(Rotation.class),b.readBoolean(),b.readBoolean());}
  public void encode(RegistryFriendlyByteBuf b,PreviewPacket p){b.writeBlockPos(p.origin);b.writeEnum(p.rotation);b.writeBoolean(p.visible);b.writeBoolean(p.valid);}
 };
 @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
