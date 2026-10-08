package pl.aridlin.psychiatrykroles.peeb;

import java.util.Optional;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

public record PeebStatePayload(UUID playerUuid, int entityId, boolean active, Optional<Vec3> anchor, double ropeLength, float attachmentYaw, boolean pullingTeammate)
   implements CustomPacketPayload {
   public static final Type<PeebStatePayload> TYPE = new Type(ResourceLocation.fromNamespaceAndPath("psychiatryk_peeb", "state"));
   public static final StreamCodec<RegistryFriendlyByteBuf, PeebStatePayload> STREAM_CODEC = StreamCodec.of((var0, var1) -> {
      var0.writeUUID(var1.playerUuid);
      var0.writeVarInt(var1.entityId);
      var0.writeBoolean(var1.active);
      var0.writeBoolean(var1.anchor.isPresent());
      var1.anchor.ifPresent(var1x -> {
         var0.writeDouble(var1x.x);
         var0.writeDouble(var1x.y);
         var0.writeDouble(var1x.z);
      });
      var0.writeDouble(var1.ropeLength);
      var0.writeFloat(var1.attachmentYaw);
      var0.writeBoolean(var1.pullingTeammate);
   }, var0 -> {
      UUID var1 = var0.readUUID();
      int var2 = var0.readVarInt();
      boolean var3 = var0.readBoolean();
      Optional var4 = var0.readBoolean() ? Optional.of(new Vec3(var0.readDouble(), var0.readDouble(), var0.readDouble())) : Optional.empty();
      return new PeebStatePayload(var1, var2, var3, var4, var0.readDouble(), var0.readFloat(), var0.readBoolean());
   });

   public PeebStatePayload(UUID uuid, int entityId, boolean active, Optional<Vec3> anchor, double ropeLength, float yaw) {
      this(uuid, entityId, active, anchor, ropeLength, yaw, false);
   }

   public PeebStatePayload(UUID var1, int var2, boolean var3, Optional<Vec3> var4, double var5) {
      this(var1, var2, var3, var4, var5, 0.0F, false);
   }

   public Type<PeebStatePayload> type() {
      return TYPE;
   }
}
