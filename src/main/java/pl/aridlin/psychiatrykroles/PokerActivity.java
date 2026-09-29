package pl.aridlin.psychiatrykroles;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class PokerActivity {
    private static final int LIMIT = 12;
    private static final Map<UUID, ArrayDeque<Component>> LINES = new HashMap<>();

    static void send(ServerPlayer viewer, Component message) {
        record(viewer.getUUID(), message);
        viewer.sendSystemMessage(message);
    }

    static void record(UUID viewer, Component message) {
        var lines = LINES.computeIfAbsent(viewer, ignored -> new ArrayDeque<>());
        lines.addLast(message.copy());
        while (lines.size() > LIMIT) lines.removeFirst();
    }

    static List<Component> recent(UUID viewer) {
        var lines = LINES.get(viewer);
        return lines == null ? List.of() : List.copyOf(lines);
    }

    @SubscribeEvent public void logout(PlayerEvent.PlayerLoggedOutEvent event) { LINES.remove(event.getEntity().getUUID()); }
    @SubscribeEvent public void stopped(ServerStoppedEvent event) { LINES.clear(); }
}
