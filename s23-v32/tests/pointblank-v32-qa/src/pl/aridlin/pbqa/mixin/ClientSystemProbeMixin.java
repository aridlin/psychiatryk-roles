package pl.aridlin.pbqa.mixin;
import com.vicmatskiv.pointblank.client.ClientSystem;
import org.spongepowered.asm.mixin.Mixin;import org.spongepowered.asm.mixin.injection.*;import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(value=ClientSystem.class,remap=false) public abstract class ClientSystemProbeMixin{
 @Inject(method="preRender",at=@At("HEAD")) private void qa$before(CallbackInfo ci){pl.aridlin.pbqa.PointBlankQA.probe("before_pre_render");}
 @Inject(method="preRender",at=@At("TAIL")) private void qa$after(CallbackInfo ci){pl.aridlin.pbqa.PointBlankQA.probe("after_pre_render");}
}
