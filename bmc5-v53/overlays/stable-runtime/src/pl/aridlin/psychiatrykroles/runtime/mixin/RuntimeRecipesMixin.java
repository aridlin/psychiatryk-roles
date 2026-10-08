package pl.aridlin.psychiatrykroles.runtime.mixin;
import net.minecraft.world.item.crafting.RecipeManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pl.aridlin.psychiatrykroles.runtime.RuntimeServer;
@Mixin(RecipeManager.class)
public abstract class RuntimeRecipesMixin {
 @Inject(method="apply(Ljava/util/Map;Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)V",at=@At("TAIL"))
 private void runtimeRecipes(java.util.Map<net.minecraft.resources.ResourceLocation,com.google.gson.JsonElement> recipes,net.minecraft.server.packs.resources.ResourceManager resources,net.minecraft.util.profiling.ProfilerFiller profiler,CallbackInfo ci){RuntimeServer.injectRecipes((RecipeManager)(Object)this);}
}
