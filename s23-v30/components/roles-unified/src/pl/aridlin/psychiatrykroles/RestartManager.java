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
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.bus.api.SubscribeEvent;

import java.io.IOException;
import java.nio.file.Path;

final class RestartManager {
    private RestartCountdown countdown;
    private RestartVote vote;
    private final java.util.Map<java.util.UUID,Long> lastProposal=new java.util.HashMap<>();
    private RestartHistory history = new RestartHistory();
    private boolean historyAvailable;

    @SubscribeEvent
    public void register(RegisterCommandsEvent event) {
        RestartVoteUI.manager=this;
        event.getDispatcher().register(Commands.literal("restartin")
            .executes(context -> status(context.getSource()))
            .then(Commands.literal("vote")
                .executes(c->voteStatus(c.getSource()))
                .then(Commands.literal("yes").executes(c->castVote(c.getSource(),true)))
                .then(Commands.literal("no").executes(c->castVote(c.getSource(),false)))
                .then(Commands.literal("status").executes(c->voteStatus(c.getSource())))
                .then(Commands.argument("time",StringArgumentType.word()).executes(c->startVote(c.getSource(),StringArgumentType.getString(c,"time")))))
            .then(Commands.literal("status").executes(context -> status(context.getSource())))
            .then(Commands.literal("cancel").requires(s->s.hasPermission(4)).executes(context -> cancel(context.getSource())))
            .then(Commands.argument("duration", StringArgumentType.word()).requires(s->s.hasPermission(4))
                .suggests((context, builder) -> net.minecraft.commands.SharedSuggestionProvider.suggest(
                    new String[] {"5m", "1m", "30s", "1h30m"}, builder))
                .executes(context -> schedule(context.getSource(), StringArgumentType.getString(context, "duration")))));
    }

    private int startVote(CommandSourceStack source,String duration) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var player=source.getPlayerOrException();if(countdown!=null||vote!=null){source.sendFailure(Component.literal("A restart or vote is already active."));return 0;}
        try{RestartCountdown.parse(duration);}catch(IllegalArgumentException error){source.sendFailure(Component.literal(error.getMessage()));return 0;}
        long now=System.nanoTime();if(now-lastProposal.getOrDefault(player.getUUID(),now-120_000_000_000L)<120_000_000_000L){source.sendFailure(Component.literal("Wait two minutes before proposing another restart vote."));return 0;}
        lastProposal.put(player.getUUID(),now);vote=new RestartVote(source.getServer().getPlayerList().getPlayers().stream().map(ServerPlayer::getUUID).toList(),duration,now);for(var voter:source.getServer().getPlayerList().getPlayers())RestartVoteUI.send(voter,vote,true,true);
        source.getServer().getPlayerList().broadcastSystemMessage(Component.literal("Restart vote: "+player.getGameProfile().getName()+" proposes a restart in "+duration+" after approval. Need "+vote.required()+"/"+vote.eligible.size()+" yes votes. Use the Yes/No ballot. /restartin vote reopens it. Voting lasts 2 minutes."),false);return checkVote(source.getServer());
    }
    void castGUI(ServerPlayer p,RestartVoteUI.Choice choice){if(vote==null||!vote.id.equals(choice.id())||System.nanoTime()>=vote.deadline)return;try{castVote(p.createCommandSourceStack(),choice.yes());}catch(com.mojang.brigadier.exceptions.CommandSyntaxException ignored){}}
    private void closeVote(MinecraftServer server){for(var p:server.getPlayerList().getPlayers())if(vote.eligible.contains(p.getUUID()))RestartVoteUI.send(p,vote,false,false);}
    private int castVote(CommandSourceStack source,boolean yes) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        if(vote==null){source.sendFailure(Component.literal("No active restart vote."));return 0;}if(!vote.cast(source.getPlayerOrException().getUUID(),yes)){source.sendFailure(Component.literal("Only players online when this vote began may vote."));return 0;}return checkVote(source.getServer());
    }
    private int checkVote(MinecraftServer server){if(vote==null)return 0;for(var p:server.getPlayerList().getPlayers())if(vote.eligible.contains(p.getUUID()))RestartVoteUI.send(p,vote,false,true);server.getPlayerList().broadcastSystemMessage(Component.literal("Restart vote: "+vote.yes.size()+"/"+vote.required()+" required yes votes ("+vote.no.size()+" no)."),false);if(vote.passed()){String duration=vote.duration;closeVote(server);vote=null;server.getPlayerList().broadcastSystemMessage(Component.literal("Restart vote approved by at least 50% of the players."),false);return schedule(server.createCommandSourceStack(),duration);}if(vote.finished()){closeVote(server);vote=null;server.getPlayerList().broadcastSystemMessage(Component.literal("Restart vote rejected."),false);}return 1;}
    private int voteStatus(CommandSourceStack source){if(vote!=null&&source.getEntity() instanceof ServerPlayer p&&vote.eligible.contains(p.getUUID()))RestartVoteUI.send(p,vote,true,true);source.sendSuccess(()->Component.literal(vote==null?"No active restart vote.":"Restart vote for "+vote.duration+": "+vote.yes.size()+"/"+vote.required()+" required yes votes; "+vote.no.size()+" no."),false);return 1;}

    private int schedule(CommandSourceStack source, String duration) {
        if(vote!=null){source.sendFailure(Component.literal("A restart vote is active. Wait for its result."));return 0;}
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
    public void tick(ServerTickEvent.Post event) {
        if(vote!=null&&System.nanoTime()>=vote.deadline){closeVote(event.getServer());vote=null;event.getServer().getPlayerList().broadcastSystemMessage(Component.literal("Restart vote expired without 50% approval."),false);}
        if(vote!=null&&event.getServer().getTickCount()%20==0)for(var p:event.getServer().getPlayerList().getPlayers())if(vote.eligible.contains(p.getUUID()))RestartVoteUI.send(p,vote,false,true);
        if (countdown == null) return;
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
    public void stopped(ServerStoppedEvent event) { countdown = null; vote=null;lastProposal.clear();historyAvailable = false; }
}
