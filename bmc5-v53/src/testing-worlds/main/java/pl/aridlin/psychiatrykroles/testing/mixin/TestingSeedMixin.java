package pl.aridlin.psychiatrykroles.testing.mixin;

import java.util.function.Supplier;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.storage.WritableLevelData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mutable;
import net.minecraft.world.RandomSequences;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pl.aridlin.psychiatrykroles.testing.TestingWorlds;

/** Terrain, structure placement, random sequences and biome zoom all use one test-only seed. */
@Mixin(ServerLevel.class)
public abstract class TestingSeedMixin {
 @Shadow @Final @Mutable private RandomSequences randomSequences;
 @Inject(method="<init>",at=@At("RETURN"),require=1)
 private void testing$ownLootSequences(CallbackInfo ci){var level=(ServerLevel)(Object)this;if(TestingWorlds.testing(level.dimension()))this.randomSequences=level.getDataStorage().computeIfAbsent(RandomSequences.factory(level.getSeed()),"random_sequences");}


 @Inject(method="getSeed",at=@At("HEAD"),cancellable=true)
 private void testing$seed(CallbackInfoReturnable<Long> ci){if(((ServerLevel)(Object)this).dimension().equals(TestingWorlds.NORMAL))ci.setReturnValue(TestingWorlds.NORMAL_SEED);}
 @Redirect(method="<init>",at=@At(value="INVOKE",target="Lnet/minecraft/world/level/levelgen/WorldOptions;seed()J"),require=1)
 private long testing$structureSeed(WorldOptions options){return ((ServerLevel)(Object)this).dimension().equals(TestingWorlds.NORMAL)?TestingWorlds.NORMAL_SEED:options.seed();}
 @ModifyArg(method="<init>",at=@At(value="INVOKE",target="Lnet/minecraft/world/level/Level;<init>(Lnet/minecraft/world/level/storage/WritableLevelData;Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/core/RegistryAccess;Lnet/minecraft/core/Holder;Ljava/util/function/Supplier;ZZJI)V"),index=7,require=1)
 private static long testing$biomeSeed(WritableLevelData data,ResourceKey<Level> dimension,RegistryAccess registries,Holder<DimensionType> type,Supplier<ProfilerFiller> profiler,boolean client,boolean debug,long seed,int maxUpdates){return dimension.equals(TestingWorlds.NORMAL)?BiomeManager.obfuscateSeed(TestingWorlds.NORMAL_SEED):seed;}
}
