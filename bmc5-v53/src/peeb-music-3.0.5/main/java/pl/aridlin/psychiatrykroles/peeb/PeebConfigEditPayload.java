package pl.aridlin.psychiatrykroles.peeb;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.ResourceLocation;

public record PeebConfigEditPayload(int action, PeebConfig.Values values) implements CustomPacketPayload {
   public static final Type<PeebConfigEditPayload> TYPE = new Type(ResourceLocation.fromNamespaceAndPath("psychiatryk_peeb", "config_edit"));
   public static final StreamCodec<RegistryFriendlyByteBuf, PeebConfigEditPayload> STREAM_CODEC = StreamCodec.of(
      (var0, var1) -> {
         var0.writeVarInt(var1.action);
         var0.writeDouble(var1.values.range());
         var0.writeDouble(var1.values.maxHorizontalSpeed());
         var0.writeDouble(var1.values.maxSpeed());
         var0.writeDouble(var1.values.strength());
         var0.writeBoolean(var1.values.fallImmunity());
      },
      var0 -> new PeebConfigEditPayload(
            var0.readVarInt(), new PeebConfig.Values(var0.readDouble(), var0.readDouble(), var0.readDouble(), var0.readDouble(), var0.readBoolean())
         )
   );

   public Type<PeebConfigEditPayload> type() {
      return TYPE;
   }
}
