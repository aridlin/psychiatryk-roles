package pl.aridlin.saveqa.mixin;

import java.io.IOException;
import java.nio.ByteBuffer;
import dev.ryanhcode.sable.sublevel.storage.region.SubLevelStorageFile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pl.aridlin.saveqa.DiskFaults;

@Mixin(value=SubLevelStorageFile.class, remap=false)
public final class SableDiskFaultMixin {
    @Inject(method="write(ILjava/nio/ByteBuffer;)V", at=@At("HEAD"))
    private void delay(int index, ByteBuffer data, CallbackInfo ci) throws IOException { DiskFaults.beforeSable(); }
    @Inject(method="write(ILjava/nio/ByteBuffer;)V", at=@At("RETURN"))
    private void completed(int index, ByteBuffer data, CallbackInfo ci) { DiskFaults.sableEnds.incrementAndGet(); }
    @Inject(method="flush()V", at=@At("HEAD"))
    private void slowForce(CallbackInfo ci) throws IOException { DiskFaults.delay(DiskFaults.sableFlushDelayMs); }
}
