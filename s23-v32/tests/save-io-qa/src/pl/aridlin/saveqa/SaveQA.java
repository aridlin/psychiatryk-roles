package pl.aridlin.saveqa;

import com.google.gson.*;
import com.mojang.authlib.GameProfile;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.storage.holding.GlobalSavedSubLevelPointer;
import dev.ryanhcode.sable.sublevel.storage.serialization.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.item.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.PlayerDataStorage;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

@Mod("goplanska_save_qa")
public final class SaveQA {
    private MinecraftServer server;
    private PlayerDataStorage players;
    private ServerPlayer player, secondPlayer;
    private SubLevelStorage storage;
    private GlobalSavedSubLevelPointer pointer;
    private SubLevelData assemblySnapshot;
    private int ticks, ioStartTick, lastCompleted, heartbeatsDuringWrite;
    private long previousTick, maximumTickGapMs;
    private boolean success = true, done;
    private final JsonArray checks = new JsonArray();
    private final String mode = System.getProperty("saveqa.mode", "basic");

    public SaveQA() {
        NeoForge.EVENT_BUS.addListener(this::start);
        NeoForge.EVENT_BUS.addListener(this::tick);
        NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.LOWEST, false,
            net.neoforged.neoforge.event.server.ServerStoppedEvent.class, this::stopped);
    }
    private void stopped(net.neoforged.neoforge.event.server.ServerStoppedEvent event) {
        try {
            var status = new JsonObject(); status.addProperty("server_stopped_event", true);
            if (!mode.equals("baseline")) {
                status.addProperty("player_pending_jobs", ((Number)playerApi("pendingJobs")).intValue());
                status.addProperty("sable_pending_jobs", ((Number)sableApi("pendingJobs")).intValue());
            }
            Files.writeString(Path.of("save-qa-"+mode+"-stopped.json"), status.toString());
            System.out.println("SAVE_QA SERVER_STOPPED " + mode);
        } catch (Throwable error) { error.printStackTrace(); }
    }
    private void check(boolean condition, String description) {
        success &= condition;
        var entry = new JsonObject();
        entry.addProperty("passed", condition); entry.addProperty("description", description);
        checks.add(entry);
        System.out.println("SAVE_QA " + (condition ? "PASS " : "FAIL ") + description);
    }
    private void start(net.neoforged.neoforge.event.server.ServerStartedEvent event) {
        server = event.getServer();
        server.overworld().setChunkForced(0, 0, true);
        try {
            var field = PlayerList.class.getDeclaredField("playerIo");
            field.setAccessible(true); players = (PlayerDataStorage) field.get(server.getPlayerList());
            player = FakePlayerFactory.get(server.overworld(), new GameProfile(UUID.fromString(DiskFaults.PLAYER_UUID), "SaveIoQA"));
            secondPlayer = FakePlayerFactory.get(server.overworld(), new GameProfile(UUID.fromString(DiskFaults.SECOND_PLAYER_UUID), "SaveIoQASecond"));
            player.setPos(2, 110, 2);
            storage = new SubLevelStorage(Path.of("qa-sable"));
        } catch (Throwable failure) { finish(failure); }
    }
    private void snapshotPlayer(int level, int diamonds) {
        player.experienceLevel = level;
        player.getInventory().setItem(0, new ItemStack(Items.DIAMOND, diamonds));
    }
    private CompoundTag savedPlayer() throws Exception {
        return NbtIo.readCompressed(players.getPlayerDir().toPath().resolve(DiskFaults.PLAYER_UUID + ".dat"), NbtAccounter.unlimitedHeap());
    }
    private Object playerApi(String name) throws Exception {
        try { return Class.forName("pl.aridlin.psychiatrykroles.io.PlayerSaves").getMethod(name).invoke(null); }
        catch (java.lang.reflect.InvocationTargetException wrapper) {
            Throwable cause = wrapper.getCause();
            if (cause instanceof Exception checked) throw checked;
            throw wrapper;
        }
    }
    private Object sableApi(String name) throws Exception {
        try { return Class.forName("pl.aridlin.psychiatrykroles.io.SableIoBarrier").getMethod(name).invoke(null); }
        catch (java.lang.reflect.InvocationTargetException wrapper) {
            Throwable cause = wrapper.getCause();
            if (cause instanceof Exception checked) throw checked;
            throw wrapper;
        }
    }
    private int diamondCount(CompoundTag tag) {
        var inventory = tag.getList("Inventory", Tag.TAG_COMPOUND);
        for (var element : inventory) {
            var stack = (CompoundTag) element;
            if (stack.getString("id").equals("minecraft:diamond")) return stack.getInt("count");
        }
        return 0;
    }
    private void assemble() {
        var level = server.overworld();
        var anchor = new BlockPos(3, 110, 3);
        var other = anchor.east();
        level.setBlock(anchor, Blocks.IRON_BLOCK.defaultBlockState(), 3);
        level.setBlock(other, Blocks.GOLD_BLOCK.defaultBlockState(), 3);
        ServerSubLevel assembly = SubLevelAssemblyHelper.assembleBlocks(level, anchor, List.of(anchor, other),
            new BoundingBox3i(3, 110, 3, 4, 110, 3));
        assembly.setName("SAVE_QA_TWO_BLOCK_ASSEMBLY");
        assemblySnapshot = SubLevelSerializer.toData(assembly, List.of());
        pointer = storage.attemptSaveSubLevel(new ChunkPos(0, 0), assemblySnapshot);
        check(pointer != null, "actual assembled two-block Sable prop obtains storage pointer");
        check(!assemblySnapshot.fullTag().getCompound("plot").isEmpty(), "assembly serialization contains real plot blocks");
    }
    private void tick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) {
        if (server == null || done) return;
        long now = System.nanoTime();
        if (previousTick != 0 && ticks > 70) maximumTickGapMs = Math.max(maximumTickGapMs, (now - previousTick)/1_000_000L);
        previousTick = now;
        ticks++;
        if (DiskFaults.playerBegins.get() > DiskFaults.playerEnds.get() && ticks >= ioStartTick) heartbeatsDuringWrite++;
        try {
            if (mode.equals("verify")) {
                if (ticks == 60) {
                    var saved = savedPlayer();
                    check(saved.getInt("XpLevel") == 31 && diamondCount(saved) == 11, "process restart sees final queued player snapshot drained at shutdown");
                    var restored = storage.attemptLoadSubLevel(new ChunkPos(0, 0), new dev.ryanhcode.sable.sublevel.storage.holding.SavedSubLevelPointer((short)0, (short)0));
                    check(restored != null && restored.fullTag().getInt("SAVE_QA_VERSION") == 31, "process restart sees final Sable snapshot drained at shutdown");
                    finish(null);
                }
                return;
            }
            if (ticks == 80) {
                assemble();
                DiskFaults.playerDelayMs = 700;
                snapshotPlayer(7, 4);
                ioStartTick = ticks;
                long begin = System.nanoTime();
                players.save(player);
                long returnedMs = (System.nanoTime() - begin)/1_000_000L;
                check(returnedMs < 250, "700 ms player disk delay does not block save submission (" + returnedMs + " ms)");
                snapshotPlayer(90, 63);
            }
            if (ticks == 104) {
                var saved = savedPlayer();
                check(DiskFaults.playerEnds.get() > 0, "injected player disk write actually completed");
                check(heartbeatsDuringWrite >= 7, "server continued ticking during delayed player write (" + heartbeatsDuringWrite + " ticks)");
                check(saved.getInt("XpLevel") == 7 && diamondCount(saved) == 4, "player snapshot unaffected by subsequent live inventory mutation");
                DiskFaults.playerDelayMs = 350;
                snapshotPlayer(8, 5); players.save(player);
                secondPlayer.experienceLevel = 16;
                secondPlayer.getInventory().setItem(0, new ItemStack(Items.DIAMOND, 9));
                players.save(secondPlayer);
                secondPlayer.experienceLevel = 93;
                snapshotPlayer(9, 6); players.save(player);
                snapshotPlayer(91, 64);
            }
            if (ticks == 130) {
                var saved = savedPlayer();
                check(saved.getInt("XpLevel") == 9 && diamondCount(saved) == 6, "two queued player saves preserve final overwrite order");
                var previous = NbtIo.readCompressed(players.getPlayerDir().toPath().resolve(DiskFaults.PLAYER_UUID+".dat_old"), NbtAccounter.unlimitedHeap());
                check(previous.getInt("XpLevel") == 8 && diamondCount(previous) == 5, "vanilla .dat_old contains previous snapshot for the same UUID");
                var otherSaved = NbtIo.readCompressed(players.getPlayerDir().toPath().resolve(DiskFaults.SECOND_PLAYER_UUID+".dat"), NbtAccounter.unlimitedHeap());
                check(otherSaved.getInt("XpLevel") == 16 && diamondCount(otherSaved) == 9, "interleaved second UUID save remains independent and immutable");
                DiskFaults.playerDelayMs = 500;
                snapshotPlayer(10, 7); players.save(player);
                snapshotPlayer(92, 64);
                long begin = System.nanoTime();
                var reloaded = players.load(player);
                check(reloaded.isPresent() && player.experienceLevel == 10 && player.getInventory().getItem(0).getCount() == 7,
                    "player reload barrier reads the latest pending snapshot (" + (System.nanoTime()-begin)/1_000_000L + " ms)");
                DiskFaults.playerDelayMs = 0;
                DiskFaults.sableDelayMs = 700;
                var data = assemblySnapshot.fullTag().copy(); data.putInt("SAVE_QA_VERSION", 20);
                var snapshot = SubLevelSerializer.fromData(data);
                long sableBegin = System.nanoTime(); storage.attemptSaveSubLevel(pointer, snapshot);
                check((System.nanoTime()-sableBegin)/1_000_000L < 250, "700 ms Sable write delay does not block existing-pointer save submission");
                data.putInt("SAVE_QA_VERSION", 99);
            }
            if (ticks == 154) {
                var loaded = storage.attemptLoadSubLevel(pointer.chunkPos(), pointer.local());
                check(loaded != null && loaded.fullTag().getInt("SAVE_QA_VERSION") == 20, "Sable snapshot unaffected by caller tag mutation");
                DiskFaults.sableDelayMs = 350;
                for (int version = 21; version <= 22; version++) {
                    var data = assemblySnapshot.fullTag().copy(); data.putInt("SAVE_QA_VERSION", version);
                    storage.attemptSaveSubLevel(pointer, SubLevelSerializer.fromData(data));
                }
                var holding = new dev.ryanhcode.sable.sublevel.storage.holding.SubLevelHoldingChunk(new ChunkPos(0,0));
                holding.getSubLevelPointers().add(pointer.local());
                storage.attemptSaveHoldingChunk(new ChunkPos(0,0), holding);
                holding.getSubLevelPointers().clear();
            }
            if (ticks == 180) {
                var loaded = storage.attemptLoadSubLevel(pointer.chunkPos(), pointer.local());
                check(loaded != null && loaded.fullTag().getInt("SAVE_QA_VERSION") == 22, "Sable existing-pointer saves preserve final overwrite order");
                var holding = storage.attemptLoadHoldingChunk(new ChunkPos(0,0));
                check(holding != null && holding.getSubLevelPointers().equals(List.of(pointer.local())),
                    "Sable holding chunk pointers use detached immutable snapshot");
                storage.attemptRemoveHoldingChunk(new ChunkPos(0,0));
                check(storage.attemptLoadHoldingChunk(new ChunkPos(0,0)) == null,
                    "Sable ordered holding-chunk deletion is visible to subsequent load");
                DiskFaults.sableDelayMs = 500;
                var data = assemblySnapshot.fullTag().copy(); data.putInt("SAVE_QA_VERSION", 23);
                storage.attemptSaveSubLevel(pointer, SubLevelSerializer.fromData(data));
                loaded = storage.attemptLoadSubLevel(pointer.chunkPos(), pointer.local());
                check(loaded != null && loaded.fullTag().getInt("SAVE_QA_VERSION") == 23, "Sable immediate load waits for latest submitted save");
                storage.flush();
                if (!mode.equals("baseline")) sableApi("flushAll");
                check(DiskFaults.sableEnds.get() >= 5, "explicit Sable flush drains submitted disk writes");
                DiskFaults.sableDelayMs = 0;
            }
            if (ticks == 205) {
                DiskFaults.playerDelayMs = 700;
                snapshotPlayer(30, 10); players.save(player);
                long start = System.nanoTime();
                server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "save-all flush");
                check(savedPlayer().getInt("XpLevel") == 30, "save-all flush acknowledgment includes submitted player snapshot");
                System.out.println("SAVE_QA save-all flush wall ms=" + (System.nanoTime()-start)/1_000_000L);
                DiskFaults.playerDelayMs = 0;
            }
            if (ticks == 220 && !mode.equals("baseline")) {
                DiskFaults.playerFailures.set(1);
                snapshotPlayer(40, 12); players.save(player);
                snapshotPlayer(41, 13); players.save(player);
            }
            if (ticks == 240 && !mode.equals("baseline")) {
                playerApi("flushAll");
                var saved = savedPlayer();
                check(saved.getInt("XpLevel") == 41 && diamondCount(saved) == 13, "single player write failure retries retained snapshots in order");
                var previous = NbtIo.readCompressed(players.getPlayerDir().toPath().resolve(DiskFaults.PLAYER_UUID+".dat_old"), NbtAccounter.unlimitedHeap());
                check(previous.getInt("XpLevel") == 40, "retry keeps .dat_old backup in correct overwrite order");
                DiskFaults.persistentPlayerFailure = true;
                snapshotPlayer(50, 14); players.save(player);
                snapshotPlayer(51, 15); players.save(player);
            }
            if (ticks == 260 && !mode.equals("baseline")) {
                boolean rejected = false;
                try { playerApi("flushAll"); } catch (java.io.IOException expected) { rejected = true; }
                check(rejected, "persistent player disk failure makes explicit flush fail visibly");
                check(savedPlayer().getInt("XpLevel") == 41, "persistent failure preserves previous durable player data");
                DiskFaults.persistentPlayerFailure = false;
                playerApi("flushAll");
                check(savedPlayer().getInt("XpLevel") == 51, "recovered player disk drains retained snapshots instead of losing them");
                DiskFaults.sableFailures.set(1);
                var data = assemblySnapshot.fullTag().copy(); data.putInt("SAVE_QA_VERSION", 40);
                storage.attemptSaveSubLevel(pointer, SubLevelSerializer.fromData(data));
                data = assemblySnapshot.fullTag().copy(); data.putInt("SAVE_QA_VERSION", 41);
                storage.attemptSaveSubLevel(pointer, SubLevelSerializer.fromData(data));
            }
            if (ticks == 280 && !mode.equals("baseline")) {
                boolean blocked = false;
                try { sableApi("flushAll"); } catch (java.io.IOException expected) { blocked = true; }
                check(blocked, "Sable async failure blocks explicit completion before automatic retry");
                boolean allocationRejected = false;
                try { storage.attemptSaveSubLevel(new ChunkPos(0,0), assemblySnapshot); }
                catch (java.io.UncheckedIOException expected) { allocationRejected = true; }
                check(allocationRejected, "new pointer allocation rejects failed save prefix without orphaned deferred allocation");
                boolean readRejected = false;
                try { storage.attemptLoadSubLevel(pointer.chunkPos(), pointer.local()); }
                catch (java.io.UncheckedIOException expected) { readRejected = true; }
                check(readRejected, "Sable read reports failed barrier instead of pretending persisted assembly is absent");
            }
            if (ticks == 310 && !mode.equals("baseline")) {
                sableApi("flushAll");
                check(storage.attemptLoadSubLevel(pointer.chunkPos(), pointer.local()).fullTag().getInt("SAVE_QA_VERSION") == 41,
                    "single Sable disk failure retries immutable snapshots in order");
                DiskFaults.persistentSableFailure = true;
                var data = assemblySnapshot.fullTag().copy(); data.putInt("SAVE_QA_VERSION", 50);
                storage.attemptSaveSubLevel(pointer, SubLevelSerializer.fromData(data));
            }
            if (ticks == 330 && !mode.equals("baseline")) {
                boolean rejected = false;
                try { sableApi("flushAll"); } catch (java.io.IOException expected) { rejected = true; }
                check(rejected, "persistent Sable disk failure makes explicit durable barrier fail visibly");
                check(((Number)sableApi("pendingJobs")).intValue() >= 1 && ((Number)sableApi("failedOwners")).intValue() >= 1,
                    "persistent Sable failure retains queued snapshot and reports failed owner");
                DiskFaults.persistentSableFailure = false;
            }
            if (ticks == 380 && !mode.equals("baseline")) {
                sableApi("flushAll");
                check(storage.attemptLoadSubLevel(pointer.chunkPos(), pointer.local()).fullTag().getInt("SAVE_QA_VERSION") == 50,
                    "recovered Sable disk drains retained snapshot instead of losing it");
                DiskFaults.sableFlushDelayMs = 700;
                long begin = System.nanoTime(); storage.flush();
                check((System.nanoTime()-begin)/1_000_000L < 250, "ordinary Sable autosave flush does not block ticks on 700 ms force latency");
            }
            if (ticks == 405 && !mode.equals("baseline")) {
                sableApi("flushAll");
                DiskFaults.sableFlushDelayMs = 0;
                var extra = storage.attemptSaveSubLevel(new ChunkPos(0,0), assemblySnapshot);
                check(extra != null && extra.subLevelIndex() != pointer.subLevelIndex(), "new Sable allocation cannot reuse occupied pending-save pointer");
                check(extra != null && extra.subLevelIndex() == 1, "rejected allocation during disk failure did not later create an orphaned saved pointer");
                storage.attemptSaveSubLevel(extra, null);
                storage.pruneCache();
                sableApi("flushAll");
                check(storage.attemptLoadSubLevel(extra.chunkPos(), extra.local()) == null, "ordered Sable delete plus cache prune removes only selected pointer");
                check(storage.attemptLoadSubLevel(pointer.chunkPos(), pointer.local()).fullTag().getInt("SAVE_QA_VERSION") == 50,
                    "cache prune and another pointer deletion preserve live assembly data");
            }
            if ((ticks == 230 && mode.equals("baseline")) || (ticks == 430 && !mode.equals("baseline"))) {
                DiskFaults.playerDelayMs = DiskFaults.sableDelayMs = 700;
                snapshotPlayer(31, 11); players.save(player);
                var data = assemblySnapshot.fullTag().copy(); data.putInt("SAVE_QA_VERSION", 31);
                storage.attemptSaveSubLevel(pointer, SubLevelSerializer.fromData(data));
                // Test close drains this independent actual Sable storage instance.
                storage.close();
                storage = null;
                finish(null);
            }
        } catch (Throwable failure) { finish(failure); }
    }
    private void finish(Throwable failure) {
        if (done) return;
        done = true;
        if (failure != null) { success = false; failure.printStackTrace(); }
        try {
            if (storage != null) { storage.close(); storage = null; }
            var report = new JsonObject(); report.addProperty("success", success);
            report.addProperty("mode", mode); report.addProperty("ticks", ticks);
            report.addProperty("max_tick_gap_ms", maximumTickGapMs);
            report.addProperty("player_io_begins", DiskFaults.playerBegins.get());
            report.addProperty("player_io_ends", DiskFaults.playerEnds.get());
            report.addProperty("sable_io_begins", DiskFaults.sableBegins.get());
            report.addProperty("sable_io_ends", DiskFaults.sableEnds.get());
            if (failure != null) report.addProperty("error", failure.toString());
            report.add("checks", checks);
            Files.writeString(Path.of("save-qa-" + mode + "-report.json"), new GsonBuilder().setPrettyPrinting().create().toJson(report));
        } catch (Throwable error) { error.printStackTrace(); }
        System.out.println("SAVE_QA COMPLETE " + mode + " success=" + success);
        if (server != null) server.halt(false);
    }
}
