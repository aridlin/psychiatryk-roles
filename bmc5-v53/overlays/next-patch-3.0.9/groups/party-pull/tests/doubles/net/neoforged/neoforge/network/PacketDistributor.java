package net.neoforged.neoforge.network;
import java.util.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
/** Test-only packet transport sink; runtime classes still generate actual payloads. */
public final class PacketDistributor {
 public static final List<CustomPacketPayload> packets=new ArrayList<>();
 public static void sendToPlayersTrackingEntityAndSelf(Entity entity,CustomPacketPayload first,CustomPacketPayload...rest){packets.add(first);}
 public static void sendToAllPlayers(CustomPacketPayload first,CustomPacketPayload...rest){packets.add(first);}
 public static void sendToPlayer(ServerPlayer player,CustomPacketPayload first,CustomPacketPayload...rest){packets.add(first);}
 public static void sendToServer(CustomPacketPayload first,CustomPacketPayload...rest){packets.add(first);}
}
