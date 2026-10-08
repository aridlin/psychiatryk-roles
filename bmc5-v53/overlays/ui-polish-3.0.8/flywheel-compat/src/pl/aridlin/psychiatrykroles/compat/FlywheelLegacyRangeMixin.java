package pl.aridlin.psychiatrykroles.compat;

import java.util.concurrent.atomic.AtomicLongArray;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Repairs only the known Flywheel 1.0.4 exclusive-end range clearing defect. */
@Mixin(targets = "dev.engine_room.flywheel.backend.util.AtomicBitSet", remap = false)
public abstract class FlywheelLegacyRangeMixin {
    @Shadow public abstract int currentCapacity();
    @Shadow private AtomicLongArray getSegmentForPosition(int position) { throw new AssertionError(); }
    @Shadow private int longIndexInSegmentForPosition(int position) { throw new AssertionError(); }
    @Shadow private void setAnd(AtomicLongArray segment, int word, long mask) { throw new AssertionError(); }

    @Inject(method = "clear(II)V", at = @At("HEAD"), cancellable = true, remap = false)
    private void psychiatryk$clearExclusiveRange(int fromIndex, int toIndex, CallbackInfo callback) {
        int from = Math.max(0, fromIndex);
        int end = Math.min(toIndex, currentCapacity());
        if (end > from) {
            int firstWord = from >>> 6;
            int lastWord = (end - 1) >>> 6;
            for (int word = firstWord; word <= lastWord; word++) {
                long clearMask = -1L;
                if (word == firstWord) clearMask &= -1L << from;
                if (word == lastWord) clearMask &= -1L >>> -end;
                int position = word << 6;
                // Use the original CAS loop. Clearing never creates a new segment:
                // the last word is strictly below the capacity snapshot above.
                setAnd(getSegmentForPosition(position), longIndexInSegmentForPosition(position), ~clearMask);
            }
        }
        callback.cancel();
    }
}
