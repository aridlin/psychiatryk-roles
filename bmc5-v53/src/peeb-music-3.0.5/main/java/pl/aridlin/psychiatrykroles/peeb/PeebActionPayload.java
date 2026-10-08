package pl.aridlin.psychiatrykroles.peeb;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

public record PeebActionPayload(boolean attach, double targetX, double targetY, double targetZ) implements CustomPacketPayload {
   public static final Type<PeebActionPayload> TYPE = new Type(ResourceLocation.fromNamespaceAndPath("psychiatryk_peeb", "action"));
   public static final StreamCodec<RegistryFriendlyByteBuf, PeebActionPayload> STREAM_CODEC = StreamCodec.of((var0, var1) -> {
      var0.writeBoolean(var1.attach);
      var0.writeDouble(var1.targetX);
      var0.writeDouble(var1.targetY);
      var0.writeDouble(var1.targetZ);
   }, var0 -> new PeebActionPayload(var0.readBoolean(), var0.readDouble(), var0.readDouble(), var0.readDouble()));

   public Vec3 target() {
      return new Vec3(this.targetX, this.targetY, this.targetZ);
   }

   public Type<PeebActionPayload> type() {
      return TYPE;
   }
}
