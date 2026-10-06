package pl.aridlin.partymarkers.mixin;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Pseudo
@Mixin(targets="com.storagefinder.storagefindermod.render.ContainerHighlightRenderer",remap=false)
public class StorageOutlineMixin {
 @Inject(method="onRenderLevel",at=@At("HEAD"),cancellable=true,require=0)
 private static void sharedOutline(net.neoforged.neoforge.client.event.RenderLevelStageEvent event,CallbackInfo ci){if(pl.aridlin.partymarkers.ChamsEffect.ready()&&pl.aridlin.partymarkers.ChamsCategories.enabled(pl.aridlin.partymarkers.ChamsCategories.Kind.CHESTS))ci.cancel();}
}
