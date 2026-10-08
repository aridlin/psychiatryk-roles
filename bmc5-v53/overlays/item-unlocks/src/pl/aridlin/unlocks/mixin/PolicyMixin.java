package pl.aridlin.unlocks.mixin;
import pl.aridlin.unlocks.Rules;
import com.google.gson.JsonElement;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets="pl.aridlin.psychiatrykroles.migration.MigrationPolicy",remap=false)
public abstract class PolicyMixin {
    @Inject(method="station(Lnet/minecraft/resources/ResourceLocation;)Z",at=@At("HEAD"),cancellable=true,remap=false)
    private static void calendar(ResourceLocation id,CallbackInfoReturnable<Boolean> cir){cir.setReturnValue(id!=null&&Rules.locked(id.toString()));}
    @Inject(method="allowRecipe",at=@At("HEAD"),cancellable=true,remap=false)
    private static void recipe(ResourceLocation id,JsonElement json,CallbackInfoReturnable<Boolean> cir){
        if(!json.isJsonObject())return;
        var result=json.getAsJsonObject().get("result");if(result==null)return;
        String output=null;
        if(result.isJsonPrimitive()&&result.getAsJsonPrimitive().isString())output=result.getAsString();
        else if(result.isJsonObject())for(String key:new String[]{"id","item"})if(result.getAsJsonObject().has(key)){output=result.getAsJsonObject().get(key).getAsString();break;}
        if(output!=null&&Rules.all().containsKey(output))cir.setReturnValue(!Rules.locked(output));
    }
}
