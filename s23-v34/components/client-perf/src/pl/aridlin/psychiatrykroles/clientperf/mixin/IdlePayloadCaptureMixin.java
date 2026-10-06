package pl.aridlin.psychiatrykroles.clientperf.mixin;

import com.moulberry.flashback.Flashback;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** PLAY packets rejected by the pre-roll cache need no wire copy while idle. */
@Pseudo
@Mixin(targets = "dev.flashbackfix.compat.InboundPayloadCapture", remap = false)
public abstract class IdlePayloadCaptureMixin {
    @Inject(method = "capture", at = @At("HEAD"), cancellable = true, require = 1)
    private static void psychiatryk$skipUnusedIdleCapture(ConnectionProtocol protocol,
            CustomPacketPayload payload, ByteBuf buffer, int start, int end, CallbackInfo callback) {
        // RECORDER is volatile. A paused recorder remains non-null and retains all captures.
        // Configuration payloads and every snapshot-eligible mod packet retain original behavior.
        if (payload != null && protocol == ConnectionProtocol.PLAY && Flashback.RECORDER == null
                && SnapshotCacheAccess.psychiatryk$excludedFromSnapshot(payload.type().id())) {
            callback.cancel();
        }
    }
}
