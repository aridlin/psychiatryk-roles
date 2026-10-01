package pl.aridlin.psychiatrykroles;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Season rules are activated only by an explicit file in the new world. */
final class SeasonProgression {
    private static final long DAY_MS = 86_400_000L;
    private MinecraftServer cachedServer;
    private Settings settings;
    private boolean borderInitialized;

    private record Settings(long startedAt, int centerX, int centerZ, double initialDiameter,
                            double finalDiameter, int durationDays, int netherUnlockDays,
                            int endUnlockDays) {}

    @SubscribeEvent
    public void onTravel(EntityTravelToDimensionEvent event) {
        MinecraftServer server = event.getEntity().getServer();
        if (server == null) return;
        Settings season = get(server);
        if (season == null) return;
        long elapsed = Math.max(0L, System.currentTimeMillis() - season.startedAt);
        int remaining = 0;
        if (event.getDimension() == Level.NETHER && elapsed < season.netherUnlockDays * DAY_MS)
            remaining = season.netherUnlockDays - (int)(elapsed / DAY_MS);
        if (event.getDimension() == Level.END && elapsed < season.endUnlockDays * DAY_MS)
            remaining = season.endUnlockDays - (int)(elapsed / DAY_MS);
        if (remaining == 0) return;
        event.setCanceled(true);
        if (event.getEntity() instanceof Player player)
            player.displayClientMessage(Component.literal("Ten wymiar otworzy się za około " + remaining + " dni.")
                .withStyle(ChatFormatting.RED), true);
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        Settings season = get(server);
        if (season == null || !borderUpdateDue(borderInitialized, server.getTickCount())) return;
        long elapsedDays = Math.min(season.durationDays,
            Math.max(0L, (System.currentTimeMillis() - season.startedAt) / DAY_MS));
        double left = 1.0 - (double) elapsedDays / season.durationDays;
        // Slow opening, faster closing, then a clearly visible final arena.
        double diameter = season.finalDiameter
            + (season.initialDiameter - season.finalDiameter) * left * left;
        for (ServerLevel level : server.getAllLevels()) {
            double scale = level.dimension() == Level.NETHER ? 0.125 : 1.0;
            var border = level.getWorldBorder();
            double target = Math.max(1.0, diameter * scale);
            if (Math.abs(border.getSize() - target) > 0.5) border.setSize(target);
            if (Math.abs(border.getCenterX() - season.centerX * scale) > 0.5 ||
                Math.abs(border.getCenterZ() - season.centerZ * scale) > 0.5)
                border.setCenter(season.centerX * scale, season.centerZ * scale);
        }
        borderInitialized = true;
    }

    static boolean borderUpdateDue(boolean alreadyInitialized, int tickCount) {
        return !alreadyInitialized || tickCount % 1200 == 0;
    }

    private Settings get(MinecraftServer server) {
        if (cachedServer == server) return settings;
        cachedServer = server;
        borderInitialized = false;
        settings = read(server);
        return settings;
    }

    private static Settings read(MinecraftServer server) {
        Path path = server.getWorldPath(LevelResource.ROOT).resolve("psychiatryk-season.json");
        if (!Files.isRegularFile(path)) return null;
        try {
            JsonObject config = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
            if (!config.has("enabled") || !config.get("enabled").getAsBoolean()) return null;
            long started = config.has("start_epoch_ms") ? config.get("start_epoch_ms").getAsLong() : 0L;
            int centerX = config.has("center_x") ? config.get("center_x").getAsInt() : server.overworld().getSharedSpawnPos().getX();
            int centerZ = config.has("center_z") ? config.get("center_z").getAsInt() : server.overworld().getSharedSpawnPos().getZ();
            double initial = config.has("border_initial_diameter") ? config.get("border_initial_diameter").getAsDouble() : 200000.0;
            double finalSize = config.has("border_final_diameter") ? config.get("border_final_diameter").getAsDouble() : 96.0;
            int days = config.has("duration_days") ? config.get("duration_days").getAsInt() : 120;
            int nether = config.has("nether_unlock_days") ? config.get("nether_unlock_days").getAsInt() : 3;
            int end = config.has("end_unlock_days") ? config.get("end_unlock_days").getAsInt() : 30;
            if (initial < 1000 || initial > 59_999_968 || finalSize < 16 || finalSize >= initial ||
                days < 2 || days > 365 || nether < 0 || end < nether || end > days)
                throw new IllegalArgumentException("Invalid season dimensions or day counts");
            if (started <= 0) {
                started = System.currentTimeMillis();
                config.addProperty("start_epoch_ms", started);
                config.addProperty("center_x", centerX);
                config.addProperty("center_z", centerZ);
                Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
                Files.writeString(temporary, config.toString() + "\n");
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
            }
            return new Settings(started, centerX, centerZ, initial, finalSize, days, nether, end);
        } catch (IOException | RuntimeException error) {
            com.mojang.logging.LogUtils.getLogger().error("Invalid psychiatryk-season.json; season rules disabled", error);
            return null;
        }
    }
}
