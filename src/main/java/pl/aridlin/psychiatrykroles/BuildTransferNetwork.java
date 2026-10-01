package pl.aridlin.psychiatrykroles;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Versioned client-to-server blueprint upload; the server alone changes the world. */
@EventBusSubscriber(modid = PsychiatrykRoles.MOD_ID)
final class BuildTransferNetwork {
    private static final int CHUNK_BYTES = 24 * 1024;
    private static final int MAX_BYTES = 2 * 1024 * 1024;
    private static final int MAX_BLOCKS = 65536;
    private static final int MAX_CHANGES_PER_TICK = 256;
    private static final long MAX_NANOS_PER_TICK = 4_000_000L;
    private static final Map<UUID, Upload> UPLOADS = new HashMap<>();
    private static PasteJob activeJob;

    private record Upload(String id, int declaredSize, String sha256, BlockPos anchor,
                          boolean carve, ByteArrayOutputStream bytes, int nextChunk, long startedMillis) {
        Upload next(int chunk) { return new Upload(id, declaredSize, sha256, anchor, carve, bytes, chunk, startedMillis); }
    }

    private record Placement(BlockPos pos, BlockState state) {}
    private static final class PasteJob {
        final MinecraftServer server;
        final ServerLevel level;
        final UUID playerId;
        final List<Placement> changes;
        int next;
        int applied;
        int failed;

        PasteJob(MinecraftServer server, ServerLevel level, UUID playerId, List<Placement> changes) {
            this.server = server;
            this.level = level;
            this.playerId = playerId;
            this.changes = changes;
        }
    }

    record Begin(String id, int byteCount, String sha256, BlockPos anchor, boolean carve) implements CustomPacketPayload {
        static final Type<Begin> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(PsychiatrykRoles.MOD_ID, "build_begin"));
        static final StreamCodec<RegistryFriendlyByteBuf, Begin> CODEC = StreamCodec.of(
            (buf, p) -> { buf.writeUtf(p.id, 40); buf.writeVarInt(p.byteCount); buf.writeUtf(p.sha256, 64); buf.writeBlockPos(p.anchor); buf.writeBoolean(p.carve); },
            buf -> new Begin(buf.readUtf(40), buf.readVarInt(), buf.readUtf(64), buf.readBlockPos(), buf.readBoolean()));
        @Override public Type<Begin> type() { return TYPE; }
    }
    record Part(String id, int index, byte[] bytes) implements CustomPacketPayload {
        static final Type<Part> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(PsychiatrykRoles.MOD_ID, "build_part"));
        static final StreamCodec<RegistryFriendlyByteBuf, Part> CODEC = StreamCodec.of(
            (buf, p) -> { buf.writeUtf(p.id, 40); buf.writeVarInt(p.index); buf.writeByteArray(p.bytes); },
            buf -> new Part(buf.readUtf(40), buf.readVarInt(), buf.readByteArray(CHUNK_BYTES)));
        @Override public Type<Part> type() { return TYPE; }
    }
    record Finish(String id) implements CustomPacketPayload {
        static final Type<Finish> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(PsychiatrykRoles.MOD_ID, "build_finish"));
        static final StreamCodec<RegistryFriendlyByteBuf, Finish> CODEC = StreamCodec.of(
            (buf, p) -> buf.writeUtf(p.id, 40), buf -> new Finish(buf.readUtf(40)));
        @Override public Type<Finish> type() { return TYPE; }
    }

    static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("2");
        registrar.playToServer(Begin.TYPE, Begin.CODEC, BuildTransferNetwork::begin);
        registrar.playToServer(Part.TYPE, Part.CODEC, BuildTransferNetwork::part);
        registrar.playToServer(Finish.TYPE, Finish.CODEC, BuildTransferNetwork::finish);
    }

    static void send(CompoundTag blueprint, BlockPos anchor, boolean carve) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        NbtIo.writeCompressed(blueprint, output);
        byte[] bytes = output.toByteArray();
        if (bytes.length > MAX_BYTES) throw new IOException("Blueprint exceeds 2 MiB compressed");
        String id = UUID.randomUUID().toString();
        PacketDistributor.sendToServer(new Begin(id, bytes.length, digest(bytes), anchor, carve));
        for (int offset = 0, index = 0; offset < bytes.length; offset += CHUNK_BYTES, index++) {
            PacketDistributor.sendToServer(new Part(id, index,
                Arrays.copyOfRange(bytes, offset, Math.min(bytes.length, offset + CHUNK_BYTES))));
        }
        PacketDistributor.sendToServer(new Finish(id));
    }

    private static void begin(Begin packet, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        UPLOADS.entrySet().removeIf(entry -> expired(entry.getValue()));
        if (activeJob != null) {
            player.sendSystemMessage(Component.literal("Another build transfer is still running; try again when it finishes."));
            return;
        }
        if (!authorized(player, packet.anchor) || packet.byteCount < 1 || packet.byteCount > MAX_BYTES ||
            !packet.id.matches("[0-9a-f-]{36}") || !packet.sha256.matches("[0-9a-f]{64}")) return;
        UPLOADS.put(player.getUUID(), new Upload(packet.id, packet.byteCount, packet.sha256, packet.anchor, packet.carve,
            new ByteArrayOutputStream(packet.byteCount), 0, System.currentTimeMillis()));
    }

    private static void part(Part packet, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        Upload upload = UPLOADS.get(player.getUUID());
        if (upload == null || !upload.id.equals(packet.id) || packet.index != upload.nextChunk ||
            packet.bytes.length < 1 || packet.bytes.length > CHUNK_BYTES ||
            upload.bytes.size() + packet.bytes.length > upload.declaredSize || expired(upload)) {
            UPLOADS.remove(player.getUUID());
            return;
        }
        upload.bytes.writeBytes(packet.bytes);
        UPLOADS.put(player.getUUID(), upload.next(upload.nextChunk + 1));
    }

    private static void finish(Finish packet, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        Upload upload = UPLOADS.remove(player.getUUID());
        if (upload == null || !upload.id.equals(packet.id) || expired(upload) ||
            upload.bytes.size() != upload.declaredSize || !authorized(player, upload.anchor)) return;
        byte[] bytes = upload.bytes.toByteArray();
        if (!digest(bytes).equals(upload.sha256)) return;
        try {
            if (activeJob != null) throw new IllegalStateException("another build transfer is still running");
            CompoundTag blueprint = NbtIo.readCompressed(new ByteArrayInputStream(bytes), NbtAccounter.create(32L * 1024 * 1024));
            List<Placement> changes = plan(player, upload.anchor, blueprint, upload.carve);
            activeJob = new PasteJob(player.getServer(), player.serverLevel(), player.getUUID(), changes);
            player.sendSystemMessage(Component.literal("Build transfer queued " + changes.size() + " changes"
                + (upload.carve ? " including previewed excavation" : "")
                + "; placing in small batches. Natural/uncertain terrain is skipped."));
        } catch (Exception error) {
            player.sendSystemMessage(Component.literal("Build transfer rejected: " + error.getMessage()));
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        PasteJob job = activeJob;
        if (job == null || job.server != event.getServer()) return;
        long deadline = System.nanoTime() + MAX_NANOS_PER_TICK;
        int batch = 0;
        while (job.next < job.changes.size() && batch < MAX_CHANGES_PER_TICK) {
            Placement change = job.changes.get(job.next++);
            try {
                if (job.level.setBlock(change.pos(), change.state(), 2)) job.applied++;
                else job.failed++;
            } catch (RuntimeException error) {
                activeJob = null;
                String message = "Build transfer stopped after " + job.applied + " changes at "
                    + change.pos() + ": " + error.getMessage();
                ServerPlayer player = event.getServer().getPlayerList().getPlayer(job.playerId);
                if (player != null) player.sendSystemMessage(Component.literal(message));
                com.mojang.logging.LogUtils.getLogger().error(message, error);
                return;
            }
            batch++;
            if (System.nanoTime() >= deadline) break;
        }
        ServerPlayer player = event.getServer().getPlayerList().getPlayer(job.playerId);
        if (job.next == job.changes.size()) {
            activeJob = null;
            String message = "Build transfer finished: " + job.applied + " changed, " + job.failed
                + " failed out of " + job.changes.size() + ".";
            if (player != null) player.sendSystemMessage(Component.literal(message));
            com.mojang.logging.LogUtils.getLogger().info(message);
        } else if (player != null && event.getServer().getTickCount() % 100 == 0) {
            player.sendSystemMessage(Component.literal("Build transfer: " + job.next + "/" + job.changes.size()
                + " changes processed."));
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        UPLOADS.clear();
        activeJob = null;
    }

    private static boolean authorized(ServerPlayer player, BlockPos anchor) {
        return player.isCreative() && player.hasPermissions(2) &&
            player.blockPosition().distSqr(anchor) <= 96 * 96 &&
            player.serverLevel().isLoaded(anchor) &&
            player.serverLevel().getBlockState(anchor).is(BuildTransferMarkers.FIRST);
    }

    private static List<Placement> plan(ServerPlayer player, BlockPos anchor, CompoundTag root, boolean carve) {
        ServerLevel level = player.serverLevel();
        if (root.getInt("version") != 1) throw new IllegalArgumentException("unsupported file version");
        requireCarveEvidence(carve, root.getString("provenance"));
        int sx = root.getInt("sizeX"), sy = root.getInt("sizeY"), sz = root.getInt("sizeZ");
        if (sx < 1 || sy < 1 || sz < 1 || sx > 96 || sy > 96 || sz > 96 ||
            (long)sx * sy * sz > MAX_BLOCKS) throw new IllegalArgumentException("invalid selection size");
        ListTag blocks = root.getList("blocks", Tag.TAG_COMPOUND);
        if (blocks.size() > MAX_BLOCKS) throw new IllegalArgumentException("too many blocks");

        List<Integer> allGround = new ArrayList<>(sx * sz);
        for (int x = 0; x < sx; x++) for (int z = 0; z < sz; z++) {
            int worldX = anchor.getX() + x, worldZ = anchor.getZ() + z;
            if (!level.isLoaded(new BlockPos(worldX, anchor.getY(), worldZ)))
                throw new IllegalArgumentException("destination is not fully loaded");
            allGround.add(level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, worldX, worldZ) - 1);
        }

        record Entry(int x, int y, int z, BlockState state) {}
        List<Entry> entries = new ArrayList<>();
        Map<Long, Integer> ground = new HashMap<>();
        for (int i = 0; i < blocks.size(); i++) {
            CompoundTag block = blocks.getCompound(i);
            byte origin = block.getByte("origin");
            if (!shouldTransfer(origin, carve)) continue;
            int x = block.getInt("x"), y = block.getInt("y"), z = block.getInt("z");
            if (x < 0 || y < 0 || z < 0 || x >= sx || y >= sy || z >= sz) throw new IllegalArgumentException("invalid block offset");
            BlockState state = NbtUtils.readBlockState(level.registryAccess().lookupOrThrow(Registries.BLOCK), block.getCompound("state"));
            if (origin == 2 ? !state.isAir() : state.isAir() || forbidden(state))
                throw new IllegalArgumentException("unsafe block in blueprint");
            entries.add(new Entry(x, y, z, state));
            if (origin == 1) {
                long key = BlockPos.asLong(anchor.getX()+x, 0, anchor.getZ()+z);
                ground.computeIfAbsent(key, k -> level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    anchor.getX()+x, anchor.getZ()+z)-1);
            }
        }
        if (entries.isEmpty()) throw new IllegalArgumentException("no player edits to transfer");
        allGround.sort(Integer::compareTo);
        int dy = allGround.get(allGround.size()/2) - root.getInt("sourceGroundMedian");
        Map<BlockPos, BlockState> planned = new HashMap<>();
        Map<Long, Integer> lowest = new HashMap<>();
        for (Entry entry : entries) {
            BlockPos pos = new BlockPos(anchor.getX() + entry.x,
                root.getInt("sourceMinY") + entry.y + dy, anchor.getZ() + entry.z);
            if (!level.isLoaded(pos) || !level.getWorldBorder().isWithinBounds(pos) ||
                pos.getY() < level.getMinBuildHeight() || pos.getY() >= level.getMaxBuildHeight() ||
                !level.mayInteract(player, pos)) throw new IllegalArgumentException("target area is unloaded or protected");
            if (level.getBlockEntity(pos) != null)
                throw new IllegalArgumentException("target contains a block entity at " + pos);
            if (planned.putIfAbsent(pos, entry.state) != null)
                throw new IllegalArgumentException("duplicate block offset in blueprint");
            if (!entry.state.isAir()) lowest.merge(BlockPos.asLong(pos.getX(), 0, pos.getZ()), pos.getY(), Math::min);
        }
        // Only the lowest built block in a column receives a short support.
        for (var entry : new ArrayList<>(planned.entrySet())) {
            if (entry.getValue().isAir()) continue;
            BlockPos pos = entry.getKey();
            long key = BlockPos.asLong(pos.getX(), 0, pos.getZ());
            if (pos.getY() != lowest.get(key)) continue;
            int top = ground.get(key);
            int gap = pos.getY() - top - 1;
            if (gap < 1 || gap > 4) continue;
            BlockState support = level.getBlockState(new BlockPos(pos.getX(), top, pos.getZ()));
            if (support.is(Blocks.GRASS_BLOCK) || support.is(Blocks.SNOW_BLOCK)) support = Blocks.DIRT.defaultBlockState();
            if (support.isAir() || support.getFluidState().isSource()) support = Blocks.STONE.defaultBlockState();
            for (int y = top + 1; y < pos.getY(); y++) {
                BlockPos under = new BlockPos(pos.getX(), y, pos.getZ());
                if (level.getBlockState(under).isAir()) planned.putIfAbsent(under, support);
            }
        }
        return planned.entrySet().stream()
            .sorted(Comparator.<Map.Entry<BlockPos, BlockState>>comparingInt(e -> e.getValue().isAir() ? 0 : 1)
                .thenComparingInt(e -> e.getValue().isAir() ? -e.getKey().getY() : e.getKey().getY()))
            .map(e -> new Placement(e.getKey(), e.getValue())).toList();
    }

    private static boolean forbidden(BlockState state) {
        return state.is(BuildTransferMarkers.FIRST) || state.is(BuildTransferMarkers.SECOND) ||
            state.is(Blocks.BEDROCK) || state.is(Blocks.BARRIER) ||
            state.is(Blocks.COMMAND_BLOCK) || state.is(Blocks.CHAIN_COMMAND_BLOCK) ||
            state.is(Blocks.REPEATING_COMMAND_BLOCK) || state.is(Blocks.STRUCTURE_BLOCK) ||
            state.is(Blocks.JIGSAW) || state.is(Blocks.SPAWNER);
    }

    static void requireCarveEvidence(boolean carve, String provenance) {
        if (carve && !"pristine-diff".equals(provenance))
            throw new IllegalArgumentException("excavation requires a pristine-world comparison");
    }

    static boolean shouldTransfer(byte origin, boolean carve) {
        return origin == 1 || carve && origin == 2;
    }

    private static boolean expired(Upload upload) { return System.currentTimeMillis() - upload.startedMillis > 120000; }
    private static String digest(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
