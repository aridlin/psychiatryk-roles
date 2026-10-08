package pl.aridlin.psychiatrykroles.peeb;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/** Frozen packet delivery for the one-time 3.0.10 grapple protocol. */
final class PeebGrappleWire {
   private PeebGrappleWire() {
   }

   static void trackingAndSelf(ServerPlayer player, PeebStatePayload payload) {
      PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, payload, new CustomPacketPayload[0]);
   }

   static void allPlayers(PeebStatePayload payload) {
      PacketDistributor.sendToAllPlayers(payload, new CustomPacketPayload[0]);
   }

   static void player(ServerPlayer player, PeebStatePayload payload) {
      PacketDistributor.sendToPlayer(player, payload, new CustomPacketPayload[0]);
   }
}
