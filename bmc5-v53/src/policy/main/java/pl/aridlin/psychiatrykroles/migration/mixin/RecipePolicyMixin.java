package pl.aridlin.psychiatrykroles.migration.mixin;
import pl.aridlin.psychiatrykroles.migration.MigrationPolicy;
import com.google.gson.JsonElement;
import java.util.Map;
import java.util.HashMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(RecipeManager.class)
public abstract class RecipePolicyMixin {
    @ModifyVariable(method="apply(Ljava/util/Map;Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)V",at=@At("HEAD"),argsOnly=true)
    private Map<ResourceLocation,JsonElement> roles$survivalRecipes(Map<ResourceLocation,JsonElement> original) {
        var allowed=new HashMap<ResourceLocation,JsonElement>();
        original.forEach((id,json)->{if(MigrationPolicy.allowRecipe(id,json))allowed.put(id,json);});
        return allowed;
    }
}
