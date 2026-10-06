package pl.aridlin.psychiatrykroles.io.mixin;

import dev.ryanhcode.sable.sublevel.storage.holding.GlobalSavedSubLevelPointer;
import dev.ryanhcode.sable.sublevel.storage.holding.SavedSubLevelPointer;
import dev.ryanhcode.sable.sublevel.storage.holding.SubLevelHoldingChunk;
import dev.ryanhcode.sable.sublevel.storage.region.SubLevelRegionFile;
import dev.ryanhcode.sable.sublevel.storage.region.SubLevelStorageFile;
import dev.ryanhcode.sable.sublevel.storage.serialization.SubLevelData;
import dev.ryanhcode.sable.sublevel.storage.serialization.SubLevelStorage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pl.aridlin.psychiatrykroles.io.OrderedIoOwner;
import pl.aridlin.psychiatrykroles.io.SableIoBarrier;

/** Exact Sable 1.21.1 / 2.0.5 storage API. All LRU/allocator/file operations share one owner. */
@Mixin(value = SubLevelStorage.class, remap = false)
public abstract class SableStorageIoMixin implements SableIoBarrier.Store {
    @Unique private static final Logger PSYCHIATRYK_IO_LOG = LoggerFactory.getLogger("PsychiatrykSaveIo");
    @Unique private OrderedIoOwner psychiatryk$owner;
    @Shadow private SubLevelRegionFile getRegionFile(ChunkPos position) throws IOException {
        throw new AssertionError("Mixin shadow");
    }
    @Shadow private SubLevelStorageFile getRegionStorageFile(ChunkPos position, int index) throws IOException {
        throw new AssertionError("Mixin shadow");
    }
    @Unique private SubLevelStorage psychiatryk$self() { return (SubLevelStorage) (Object) this; }

    @Inject(method = "<init>(Ljava/nio/file/Path;)V", at = @At("RETURN"), require = 1)
    private void psychiatryk$init(Path folder, CallbackInfo callback) {
        psychiatryk$owner = new OrderedIoOwner(folder.toString());
        SableIoBarrier.register(this);
    }
    @Override public OrderedIoOwner psychiatryk$ioOwner() { return psychiatryk$owner; }
    @Override public void psychiatryk$durableBarrier() throws IOException {
        psychiatryk$owner.call(() -> {
            psychiatryk$self().flush(); // Owner reentrancy reaches original real force(true).
            return null;
        });
    }

    @Inject(method = "attemptLoadHoldingChunk(Lnet/minecraft/world/level/ChunkPos;)Ldev/ryanhcode/sable/sublevel/storage/holding/SubLevelHoldingChunk;",
            at = @At("HEAD"), cancellable = true, require = 1)
    private void psychiatryk$readHolding(ChunkPos position, CallbackInfoReturnable<SubLevelHoldingChunk> callback) {
        if (psychiatryk$owner.isWorker()) return;
        try { callback.setReturnValue(psychiatryk$owner.call(() -> psychiatryk$self().attemptLoadHoldingChunk(position))); }
        catch (IOException error) {
            PSYCHIATRYK_IO_LOG.error("Cannot read Sable holding chunk {}; pending IO retained", position, error);
            throw new UncheckedIOException("Sable read blocked by failed ordered IO", error);
        }
    }
    @Inject(method = "attemptLoadSubLevel(Lnet/minecraft/world/level/ChunkPos;Ldev/ryanhcode/sable/sublevel/storage/holding/SavedSubLevelPointer;)Ldev/ryanhcode/sable/sublevel/storage/serialization/SubLevelData;",
            at = @At("HEAD"), cancellable = true, require = 1)
    private void psychiatryk$readSublevel(ChunkPos position, SavedSubLevelPointer pointer, CallbackInfoReturnable<SubLevelData> callback) {
        if (psychiatryk$owner.isWorker()) return;
        try { callback.setReturnValue(psychiatryk$owner.call(() -> psychiatryk$self().attemptLoadSubLevel(position, pointer))); }
        catch (IOException error) {
            PSYCHIATRYK_IO_LOG.error("Cannot read Sable sublevel {}; pending IO retained", pointer, error);
            throw new UncheckedIOException("Sable read blocked by failed ordered IO", error);
        }
    }
    @Inject(method = "attemptSaveHoldingChunk(Lnet/minecraft/world/level/ChunkPos;Ldev/ryanhcode/sable/sublevel/storage/holding/SubLevelHoldingChunk;)V",
            at = @At("HEAD"), cancellable = true, require = 1)
    private void psychiatryk$writeHolding(ChunkPos position, SubLevelHoldingChunk holding, CallbackInfo callback) throws IOException {
        if (psychiatryk$owner.isWorker()) return;
        CompoundTag snapshot = new CompoundTag();
        holding.writeTo(snapshot); // Snapshot world-owned collections on the tick thread.
        CompoundTag detached = snapshot.copy();
        psychiatryk$owner.submit(() -> {
            SubLevelRegionFile file = getRegionFile(position);
            file.write(file.getIndex(position.getRegionLocalX(), position.getRegionLocalZ()), detached);
            return null;
        });
        callback.cancel();
    }
    @Inject(method = "attemptRemoveHoldingChunk(Lnet/minecraft/world/level/ChunkPos;)V",
            at = @At("HEAD"), cancellable = true, require = 1)
    private void psychiatryk$deleteHolding(ChunkPos position, CallbackInfo callback) throws IOException {
        if (psychiatryk$owner.isWorker()) return;
        psychiatryk$owner.submit(() -> {
            SubLevelRegionFile file = getRegionFile(position);
            file.write(file.getIndex(position.getRegionLocalX(), position.getRegionLocalZ()), (CompoundTag) null);
            return null;
        });
        callback.cancel();
    }
    @Inject(method = "attemptSaveSubLevel(Ldev/ryanhcode/sable/sublevel/storage/holding/GlobalSavedSubLevelPointer;Ldev/ryanhcode/sable/sublevel/storage/serialization/SubLevelData;)V",
            at = @At("HEAD"), cancellable = true, require = 1)
    private void psychiatryk$writeSublevel(GlobalSavedSubLevelPointer pointer, SubLevelData data, CallbackInfo callback) throws IOException {
        if (psychiatryk$owner.isWorker()) return;
        CompoundTag snapshot = data == null ? null : data.fullTag().copy();
        psychiatryk$owner.submit(() -> {
            getRegionStorageFile(pointer.chunkPos(), pointer.storageIndex()).write(pointer.subLevelIndex(), snapshot);
            return null;
        });
        callback.cancel();
    }
    @Inject(method = "attemptSaveSubLevel(Lnet/minecraft/world/level/ChunkPos;Ldev/ryanhcode/sable/sublevel/storage/serialization/SubLevelData;)Ldev/ryanhcode/sable/sublevel/storage/holding/GlobalSavedSubLevelPointer;",
            at = @At("HEAD"), cancellable = true, require = 1)
    private void psychiatryk$allocateSublevel(ChunkPos position, SubLevelData data, CallbackInfoReturnable<GlobalSavedSubLevelPointer> callback) {
        if (psychiatryk$owner.isWorker()) return;
        CompoundTag snapshot = data.fullTag().copy();
        // Reserve the same slot across a failed-write retry; never allocate a different slot on retry.
        final GlobalSavedSubLevelPointer[] reserved = {null};
        try {
            callback.setReturnValue(psychiatryk$owner.call(() -> {
                if (reserved[0] == null) {
                    for (int index = 0; ; index++) {
                        if (index > Short.MAX_VALUE) throw new IOException("Sable storage index capacity exceeded");
                        SubLevelStorageFile file = getRegionStorageFile(position, index);
                        int slot = file.findFreeIndex();
                        if (slot != -1 && slot < file.getTotalIndexCapacity()) {
                            reserved[0] = new GlobalSavedSubLevelPointer(position, (short) index, (short) slot);
                            break;
                        }
                    }
                }
                GlobalSavedSubLevelPointer pointer = reserved[0];
                getRegionStorageFile(position, pointer.storageIndex()).write(pointer.subLevelIndex(), snapshot);
                return pointer;
            }));
        } catch (IOException error) {
            PSYCHIATRYK_IO_LOG.error("Cannot allocate Sable sublevel in {}; pending IO retained", position, error);
            throw new UncheckedIOException("Sable allocation failed on ordered IO", error);
        }
    }
    @Inject(method = "flush()V", at = @At("HEAD"), cancellable = true, require = 1)
    private void psychiatryk$flush(CallbackInfo callback) throws IOException {
        if (psychiatryk$owner.isWorker()) return;
        psychiatryk$owner.submit(() -> { psychiatryk$self().flush(); return null; });
        callback.cancel();
    }
    @Inject(method = "pruneCache()V", at = @At("HEAD"), cancellable = true, require = 1)
    private void psychiatryk$prune(CallbackInfo callback) throws IOException {
        if (psychiatryk$owner.isWorker()) return;
        psychiatryk$owner.submit(() -> { psychiatryk$self().pruneCache(); return null; });
        callback.cancel();
    }
    @Inject(method = "close()V", at = @At("HEAD"), cancellable = true, require = 1)
    private void psychiatryk$close(CallbackInfo callback) throws IOException {
        if (psychiatryk$owner.isWorker()) return;
        psychiatryk$owner.call(() -> { psychiatryk$self().close(); return null; });
        psychiatryk$owner.shutdown();
        SableIoBarrier.unregister(this);
        callback.cancel();
    }
}
