package pl.aridlin.pbqa.mixin;
import org.spongepowered.asm.mixin.Mixin;import org.spongepowered.asm.mixin.injection.*;import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(targets="qouteall.imm_ptl.core.render.RendererUsingStencil",remap=false) public abstract class PortalPrepareProbeMixin{
 @Inject(method="prepareRendering",at=@At("TAIL")) private void qa$prepared(CallbackInfo ci){pl.aridlin.pbqa.PointBlankQA.probe("portal_prepared");}
}
