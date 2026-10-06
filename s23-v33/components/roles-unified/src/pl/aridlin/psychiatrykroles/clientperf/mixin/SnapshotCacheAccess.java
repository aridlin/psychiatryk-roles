package pl.aridlin.psychiatrykroles.clientperf.mixin;

import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Invoker;

@Pseudo
@Mixin(targets = "dev.flashbackfix.compat.ModdedPayloadSnapshotCache", remap = false)
public interface SnapshotCacheAccess {
    @Invoker("shouldExclude")
    static boolean psychiatryk$excludedFromSnapshot(ResourceLocation id) {
        throw new AssertionError("Mixin invoker was not applied");
    }
}
