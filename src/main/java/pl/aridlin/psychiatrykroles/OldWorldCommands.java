package pl.aridlin.psychiatrykroles;

import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.io.IOException;
import java.nio.file.Path;

/** Operator-only route to the archived 1.20.1 overworld, with an S23 return bookmark. */
final class OldWorldCommands {
    static final ResourceKey<Level> DIMENSION = ResourceKey.create(Registries.DIMENSION,
        ResourceLocation.fromNamespaceAndPath(PsychiatrykRoles.MOD_ID, "old_overworld"));
    private static final int LANDING_RADIUS = 4;
    private static final int VERTICAL_RADIUS = 64;

    private OldWorldCommands() {}

    static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("oldworld")
            .requires(source -> source.hasPermission(4))
            .executes(context -> toggle(context.getSource().getPlayerOrException()))
            .then(Commands.literal("enter")
                .executes(context -> enter(context.getSource().getPlayerOrException(), null))
                .then(Commands.argument("position", Vec3Argument.vec3())
                    .executes(context -> enter(context.getSource().getPlayerOrException(),
                        Vec3Argument.getVec3(context, "position")))))
            .then(Commands.literal("return")
                .executes(context -> returnHome(context.getSource().getPlayerOrException()))));
    }

    private static int toggle(ServerPlayer player) {
        return player.serverLevel().dimension().equals(DIMENSION) ? returnHome(player) : enter(player, null);
    }

    private static int enter(ServerPlayer player, Vec3 requested) {
        if (player.serverLevel().dimension().equals(DIMENSION)) {
            player.sendSystemMessage(Component.literal("Already in the old world. Use /oldworld return."));
            return 0;
        }
        if (!player.serverLevel().dimension().equals(Level.OVERWORLD)) {
            player.sendSystemMessage(Component.literal("Enter the old world from the new Overworld, so /oldworld return is always available."));
            return 0;
        }
        MinecraftServer server = player.getServer();
        ServerLevel oldWorld = server.getLevel(DIMENSION);
        if (oldWorld == null) {
            player.sendSystemMessage(Component.literal("Old-world dimension is unavailable; install the matching mod on the server."));
            return 0;
        }
        Path directory = server.getWorldPath(LevelResource.ROOT).resolve(OldWorldImport.DIRECTORY);
        OldWorldImport.Manifest manifest;
        try {
            manifest = OldWorldImport.readManifest(directory);
        } catch (IOException error) {
            player.sendSystemMessage(Component.literal("Old world has not finished importing: " + error.getMessage()));
            return 0;
        }
        Vec3 position = requested == null ? Vec3.atBottomCenterOf(manifest.spawn()) : requested;
        if (!Double.isFinite(position.x) || !Double.isFinite(position.y) || !Double.isFinite(position.z)
            || Math.abs(position.x) > 29_999_984.0 || Math.abs(position.z) > 29_999_984.0) {
            player.sendSystemMessage(Component.literal("Invalid old-world coordinates."));
            return 0;
        }
        BlockPos anchor = BlockPos.containing(position);
        if (!OldWorldImport.hasImportedChunk(directory, anchor.getX(), anchor.getZ())) {
            player.sendSystemMessage(Component.literal("That old-world chunk is absent from the imported archive; no terrain was generated."));
            return 0;
        }
        Vec3 landing = safeLanding(oldWorld, anchor, directory);
        if (landing == null) {
            player.sendSystemMessage(Component.literal("No safe landing in that archived chunk. Try /oldworld enter <x> <y> <z> nearby."));
            return 0;
        }
        OldWorldTravelData.ReturnPosition from = new OldWorldTravelData.ReturnPosition(
            player.serverLevel().dimension().location().toString(), player.getX(), player.getY(), player.getZ(),
            player.getYRot(), player.getXRot());
        player.teleportTo(oldWorld, landing.x, landing.y, landing.z, player.getYRot(), player.getXRot());
        if (player.serverLevel() != oldWorld || player.position().distanceToSqr(landing) > 0.25) {
            player.sendSystemMessage(Component.literal("Old-world teleport failed; your return point was not changed."));
            return 0;
        }
        OldWorldTravelData.get(server).remember(player.getUUID(), from);
        player.setDeltaMovement(Vec3.ZERO);
        player.fallDistance = 0.0F;
        player.sendSystemMessage(Component.literal("Old world. Use /oldworld return to go back to your saved location."));
        return 1;
    }

    private static int returnHome(ServerPlayer player) {
        if (!player.serverLevel().dimension().equals(DIMENSION)) {
            player.sendSystemMessage(Component.literal("You are not in the old world. Use /oldworld enter."));
            return 0;
        }
        MinecraftServer server = player.getServer();
        OldWorldTravelData data = OldWorldTravelData.get(server);
        OldWorldTravelData.ReturnPosition bookmark = data.getReturn(player.getUUID());
        // Always return to the new Overworld. The seasonal Nether/End lock does not apply there.
        ServerLevel destination = server.overworld();
        boolean useBookmark = bookmark != null && Level.OVERWORLD.location().toString().equals(bookmark.dimension());
        BlockPos anchor = useBookmark ? BlockPos.containing(bookmark.x(), bookmark.y(), bookmark.z())
            : destination.getSharedSpawnPos();
        Vec3 landing = safeLanding(destination, anchor, null);
        if (landing == null) landing = safeLanding(destination, destination.getSharedSpawnPos(), null);
        if (landing == null) {
            player.sendSystemMessage(Component.literal("No safe return location found. Your old-world bookmark was kept."));
            return 0;
        }
        float yaw = bookmark == null ? player.getYRot() : bookmark.yaw();
        float pitch = bookmark == null ? player.getXRot() : bookmark.pitch();
        player.teleportTo(destination, landing.x, landing.y, landing.z, yaw, pitch);
        if (player.serverLevel() != destination || player.position().distanceToSqr(landing) > 0.25) {
            player.sendSystemMessage(Component.literal("Return teleport failed; your bookmark was kept."));
            return 0;
        }
        data.forget(player.getUUID());
        player.setDeltaMovement(Vec3.ZERO);
        player.fallDistance = 0.0F;
        player.sendSystemMessage(Component.literal("Returned from the old world."));
        return 1;
    }

    /** Searches only the specified archived chunk, so the command never probes an unimported neighbor. */
    private static Vec3 safeLanding(ServerLevel level, BlockPos anchor, Path importedDirectory) {
        int chunkX = Math.floorDiv(anchor.getX(), 16);
        int chunkZ = Math.floorDiv(anchor.getZ(), 16);
        for (int radius = 0; radius <= LANDING_RADIUS; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
                    int x = anchor.getX() + dx;
                    int z = anchor.getZ() + dz;
                    if (importedDirectory != null && (Math.floorDiv(x, 16) != chunkX
                        || Math.floorDiv(z, 16) != chunkZ)) continue;
                    if (importedDirectory != null && !OldWorldImport.hasImportedChunk(importedDirectory, x, z)) continue;
                    int minY = Math.max(level.getMinBuildHeight() + 1, anchor.getY() - VERTICAL_RADIUS);
                    int maxY = Math.min(level.getMaxBuildHeight() - 2, anchor.getY() + VERTICAL_RADIUS);
                    for (int offset = 0; offset <= VERTICAL_RADIUS; offset++) {
                        int up = anchor.getY() + offset;
                        if (up >= minY && up <= maxY && safeAt(level, x, up, z))
                            return new Vec3(x + 0.5, up, z + 0.5);
                        int down = anchor.getY() - offset;
                        if (offset > 0 && down >= minY && down <= maxY && safeAt(level, x, down, z))
                            return new Vec3(x + 0.5, down, z + 0.5);
                    }
                }
            }
        }
        return null;
    }

    private static boolean safeAt(ServerLevel level, int x, int y, int z) {
        BlockPos feetPos = new BlockPos(x, y, z);
        BlockPos headPos = feetPos.above();
        BlockPos floorPos = feetPos.below();
        BlockState floor = level.getBlockState(floorPos);
        if (!floor.isFaceSturdy(level, floorPos, Direction.UP) || !floor.getFluidState().isEmpty()
            || floor.is(Blocks.MAGMA_BLOCK) || floor.is(Blocks.CAMPFIRE)
            || floor.is(Blocks.SOUL_CAMPFIRE) || floor.is(Blocks.CACTUS)) return false;
        BlockState feet = level.getBlockState(feetPos);
        BlockState head = level.getBlockState(headPos);
        return feet.getFluidState().isEmpty() && head.getFluidState().isEmpty()
            && feet.getCollisionShape(level, feetPos).isEmpty()
            && head.getCollisionShape(level, headPos).isEmpty()
            && !feet.is(Blocks.FIRE) && !feet.is(Blocks.SOUL_FIRE)
            && !feet.is(Blocks.POWDER_SNOW) && !feet.is(Blocks.SWEET_BERRY_BUSH)
            && !feet.is(Blocks.WITHER_ROSE);
    }
}
