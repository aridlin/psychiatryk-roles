package pl.aridlin.psychiatrykroles.peeb;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.ResourceLocation;

public record PeebConfigPayload(boolean open, double range, double maxHorizontalSpeed, double maxSpeed, double strength, boolean fallImmunity, double stopDistance, boolean grappleStep)
   implements CustomPacketPayload {
   public static final Type<PeebConfigPayload> TYPE = new Type(ResourceLocation.fromNamespaceAndPath("psychiatryk_peeb", "config"));
   public static final StreamCodec<RegistryFriendlyByteBuf, PeebConfigPayload> STREAM_CODEC = StreamCodec.of((var0, var1) -> {
      var0.writeBoolean(var1.open);
      var0.writeDouble(var1.range);
      var0.writeDouble(var1.maxHorizontalSpeed);
      var0.writeDouble(var1.maxSpeed);
      var0.writeDouble(var1.strength);
      var0.writeBoolean(var1.fallImmunity);
      var0.writeDouble(var1.stopDistance);
      var0.writeBoolean(var1.grappleStep);
   }, var0 -> new PeebConfigPayload(var0.readBoolean(), var0.readDouble(), var0.readDouble(), var0.readDouble(), var0.readDouble(), var0.readBoolean(), var0.readDouble(), var0.readBoolean()));

   public PeebConfigPayload(boolean open, double range, double maxHorizontalSpeed, double maxSpeed, double strength, boolean fallImmunity, double stopDistance) {
      this(open, range, maxHorizontalSpeed, maxSpeed, strength, fallImmunity, stopDistance, false);
   }

   public PeebConfig.Values values() {
      return new PeebConfig.Values(this.range, this.maxHorizontalSpeed, this.maxSpeed, this.strength, this.fallImmunity, this.stopDistance, this.grappleStep);
   }

   public static PeebConfigPayload of(boolean var0, PeebConfig.Values var1) {
      return new PeebConfigPayload(var0, var1.range(), var1.maxHorizontalSpeed(), var1.maxSpeed(), var1.strength(), var1.fallImmunity(), var1.stopDistance(), var1.grappleStep());
   }

   public Type<PeebConfigPayload> type() {
      return TYPE;
   }
}
