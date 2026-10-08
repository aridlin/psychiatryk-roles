package net.neoforged.neoforge.network;
import java.util.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
/** Fixture-only network sink; all packet objects and handlers stay native. */
public final class PacketDistributor {
 public record Sent(ServerPlayer player,CustomPacketPayload packet){}
 public static final List<Sent> sent=new ArrayList<>();
 public static void sendToPlayer(ServerPlayer player,CustomPacketPayload packet,CustomPacketPayload... rest){sent.add(new Sent(player,packet));for(var p:rest)sent.add(new Sent(player,p));}
 public static void sendToServer(CustomPacketPayload packet,CustomPacketPayload... rest){sent.add(new Sent(null,packet));}
}
