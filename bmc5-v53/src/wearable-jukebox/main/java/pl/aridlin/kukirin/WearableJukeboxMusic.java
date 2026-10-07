package pl.aridlin.kukirin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import pl.aridlin.psychiatrykroles.jukebox.WearableJukebox;

/** A picker can control only the requesting player's current back-slot equip epoch. */
@EventBusSubscriber(modid = "goplanska_kukirin")
public final class WearableJukeboxMusic {
    private record Open(ScooterMusic.Source source, long expires) {}
    private static final Map<UUID, Open> opened = new HashMap<>();

    public static int open(ServerPlayer player) {
        ScooterJukeboxMusic.forget(player);
        forget(player);
        if (!player.isAlive() || WearableJukebox.equipped(player).isEmpty()) {
            player.displayClientMessage(Component.literal("Equip a jukebox in your back slot to open its music."), false);
            return 0;
        }
        var source = new ScooterMusic.Source(player);
        if (!source.valid()) return 0;
        opened.put(player.getUUID(), new Open(source, source.level.getGameTime() + 20 * 60 * 5));
        PacketDistributor.sendToPlayer(player, new ScooterMusicMenu.Catalog(ScooterMusic.songs(), true, false, source.looping()));
        return 1;
    }

    static boolean handle(ServerPlayer player, String action, String song) {
        var open = opened.get(player.getUUID());
        if (open == null) return false;
        var source = open.source;
        // Retain the selected context until another picker is opened. Stale repeated
        // packets must not fall through and accidentally control a nearby scooter.
        if (!source.valid() || source.level.getGameTime() > open.expires) return true;
        switch (action) {
            case "play" -> ScooterMusic.play(player, source, song);
            case "loop" -> ScooterMusic.wearableLoop(source, song.equals("true"));
            case "stop" -> ScooterMusic.stop(source.id);
            default -> { }
        }
        return true;
    }

    public static void forget(ServerPlayer player) { opened.remove(player.getUUID()); }
    static void clear() { opened.clear(); }

    private static int loop(ServerPlayer player, boolean enabled) {
        if (!player.isAlive() || WearableJukebox.equipped(player).isEmpty()) return 0;
        return ScooterMusic.wearableLoop(new ScooterMusic.Source(player), enabled) ? 1 : 0;
    }

    @SubscribeEvent
    public static void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("jukebox")
            .executes(context -> open(context.getSource().getPlayerOrException()))
            .then(Commands.literal("stop").executes(context -> {
                ScooterMusic.stop(context.getSource().getPlayerOrException().getUUID());
                return 1;
            }))
            .then(Commands.literal("loop")
                .then(Commands.argument("enabled", com.mojang.brigadier.arguments.BoolArgumentType.bool())
                    .executes(context -> loop(context.getSource().getPlayerOrException(),
                        com.mojang.brigadier.arguments.BoolArgumentType.getBool(context, "enabled"))))));
    }

    @SubscribeEvent
    public static void death(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) ScooterMusic.stop(player.getUUID());
    }

    @SubscribeEvent
    public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) ScooterMusic.stop(player.getUUID());
    }

    @SubscribeEvent
    public static void respawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) ScooterMusic.stop(player.getUUID());
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ScooterMusic.stop(player.getUUID());
            forget(player);
        }
    }

    private WearableJukeboxMusic() {}
}
