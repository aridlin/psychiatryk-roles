package pl.aridlin.psychiatrykroles;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.io.IOException;
import java.nio.file.Path;

final class RestartManager {
    private RestartCountdown countdown;
    private RestartHistory history = new RestartHistory();
    private boolean historyAvailable;

    @SubscribeEvent
    public void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("restartin").requires(source -> source.hasPermission(4))
            .executes(context -> status(context.getSource()))
            .then(Commands.literal("status").executes(context -> status(context.getSource())))
            .then(Commands.literal("cancel").executes(context -> cancel(context.getSource())))
            .then(Commands.argument("duration", StringArgumentType.word())
                .suggests((context, builder) -> net.minecraft.commands.SharedSuggestionProvider.suggest(
                    new String[] {"5m", "1m", "30s", "1h30m"}, builder))
                .executes(context -> schedule(context.getSource(), StringArgumentType.getString(context, "duration")))));
    }

    private int schedule(CommandSourceStack source, String duration) {
        if (!source.getServer().isDedicatedServer()) {
            source.sendFailure(Component.literal("/restartin requires a dedicated server with automatic restart after stop."));
            return 0;
        }
        if (!historyAvailable) {
            source.sendFailure(Component.literal("Restart timing storage is unavailable; check the server log."));
            return 0;
        }
        if (countdown != null) {
            source.sendFailure(Component.literal("A restart is already scheduled. Use /restartin cancel first."));
            return 0;
        }
        try {
            long seconds = RestartCountdown.parse(duration);
            countdown = new RestartCountdown(seconds, System.nanoTime());
            announce(source.getServer(), seconds);
            source.sendSuccess(() -> Component.literal("Restart scheduled in " + RestartCountdown.format(seconds)
                + ". " + eta(true)), true);
            return 1;
        } catch (IllegalArgumentException invalid) {
            source.sendFailure(Component.literal(invalid.getMessage()));
            return 0;
        }
    }

    private int status(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal((countdown == null ? "No restart scheduled. "
            : "Restart in " + RestartCountdown.format(countdown.remainingSeconds(System.nanoTime())) + ". ") + eta(true)), false);
        return 1;
    }

    private int cancel(CommandSourceStack source) {
        if (countdown == null) {
            source.sendFailure(Component.literal("No restart is scheduled."));
            return 0;
        }
        countdown = null;
        for (ServerPlayer player : source.getServer().getPlayerList().getPlayers()) {
            boolean en = PsychiatrykRoles.isEnglish(player);
            title(player, en ? "Restart cancelled" : "Restart anulowany", "");
        }
        source.sendSuccess(() -> Component.literal("Scheduled restart cancelled."), true);
        return 1;
    }

    @SubscribeEvent
    public void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || countdown == null) return;
        long remaining = countdown.remainingSeconds(System.nanoTime());
        if (remaining > 0) {
            if (countdown.announcementDue(remaining)) announce(event.getServer(), remaining);
            return;
        }
        countdown = null;
        try {
            history.shutdownStartedAt = System.currentTimeMillis();
            history.write(historyPath(event.getServer()));
        } catch (IOException failure) {
            history.shutdownStartedAt = 0;
            LogUtils.getLogger().error("Restart aborted: cannot persist restart timing", failure);
            for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
                boolean en = PsychiatrykRoles.isEnglish(player);
                title(player, en ? "Restart aborted" : "Restart przerwany", en ? "Timing could not be saved." : "Nie można zapisać czasu restartu.");
            }
            return;
        }
        announce(event.getServer(), 0);
        // The host's existing automatic restart supervisor relaunches the process after a clean stop.
        // Never spawn a second JVM while this server is still saving its world.
        event.getServer().halt(false);
    }

    private void announce(MinecraftServer server, long seconds) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) announce(player, seconds);
    }

    private void announce(ServerPlayer player, long seconds) {
        boolean en = PsychiatrykRoles.isEnglish(player);
        String message = seconds == 0 ? (en ? "Restarting now" : "Restart serwera")
            : (en ? "Restart in " : "Restart za ") + RestartCountdown.format(seconds);
        title(player, message, eta(en));
    }

    private String eta(boolean english) {
        long estimate = history.estimateSeconds();
        if (estimate == 0) return english ? "Downtime ETA will be learned after this restart." : "Czas przerwy poznamy po pierwszym restarcie.";
        return (english ? "Estimated downtime: ~" : "Szacowana przerwa: ~") + RestartCountdown.format(estimate);
    }

    private static void title(ServerPlayer player, String message, String subtitle) {
        player.connection.send(new ClientboundSetTitlesAnimationPacket(5, 70, 10));
        player.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal(subtitle).withStyle(ChatFormatting.GRAY)));
        player.connection.send(new ClientboundSetTitleTextPacket(Component.literal(message).withStyle(ChatFormatting.GOLD)));
        player.sendSystemMessage(Component.literal(message + (subtitle.isEmpty() ? "" : " — " + subtitle)));
    }

    @SubscribeEvent
    public void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (countdown != null && event.getEntity() instanceof ServerPlayer player) {
            announce(player, countdown.remainingSeconds(System.nanoTime()));
        }
    }

    @SubscribeEvent
    public void started(ServerStartedEvent event) {
        try {
            history = RestartHistory.read(historyPath(event.getServer()));
            long duration = history.recordStartup(System.currentTimeMillis());
            history.write(historyPath(event.getServer()));
            historyAvailable = true;
            if (duration > 0) LogUtils.getLogger().info("Server restart completed in {} ms; estimated next downtime: {} s", duration, history.estimateSeconds());
        } catch (IOException failure) {
            historyAvailable = false;
            LogUtils.getLogger().error("Cannot load/save restart history; /restartin is unavailable", failure);
        }
    }

    private static Path historyPath(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve("data/psychiatryk_restart_history.json");
    }

    @SubscribeEvent
    public void stopped(ServerStoppedEvent event) { countdown = null; historyAvailable = false; }
}
