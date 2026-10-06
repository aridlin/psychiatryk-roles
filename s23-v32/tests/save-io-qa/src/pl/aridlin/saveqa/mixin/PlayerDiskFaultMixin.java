package pl.aridlin.saveqa.mixin;

import java.io.IOException;
import java.nio.file.Path;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pl.aridlin.saveqa.DiskFaults;

@Mixin(NbtIo.class)
public final class PlayerDiskFaultMixin {
    @Inject(method="writeCompressed(Lnet/minecraft/nbt/CompoundTag;Ljava/nio/file/Path;)V", at=@At("HEAD"))
    private static void delay(CompoundTag tag, Path path, CallbackInfo ci) throws IOException {
        if (DiskFaults.isQaPlayer(path)) DiskFaults.beforePlayer();
    }
    @Inject(method="writeCompressed(Lnet/minecraft/nbt/CompoundTag;Ljava/nio/file/Path;)V", at=@At("RETURN"))
    private static void completed(CompoundTag tag, Path path, CallbackInfo ci) {
        if (DiskFaults.isQaPlayer(path)) DiskFaults.playerEnds.incrementAndGet();
    }
}
