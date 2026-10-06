package pl.aridlin.psychiatrykroles.clientperf.mixin;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pl.aridlin.psychiatrykroles.clientperf.SnapshotReflectionCache;

@Pseudo
@Mixin(targets = "dev.flashbackfix.compat.ModdedPayloadSnapshotCache", remap = false)
public abstract class SnapshotReflectionMixin {
    @Inject(method = "readField", at = @At("HEAD"), cancellable = true, require = 1)
    private static void psychiatryk$cachedField(Object target, String name, CallbackInfoReturnable<Object> callback) {
        callback.setReturnValue(SnapshotReflectionCache.readField(target, name));
    }

    @Inject(method = "findEntityId", at = @At("HEAD"), cancellable = true, require = 1)
    private static void psychiatryk$cachedEntityId(CustomPacketPayload payload,
            CallbackInfoReturnable<Integer> callback) {
        callback.setReturnValue(SnapshotReflectionCache.findEntityId(payload));
    }
}
