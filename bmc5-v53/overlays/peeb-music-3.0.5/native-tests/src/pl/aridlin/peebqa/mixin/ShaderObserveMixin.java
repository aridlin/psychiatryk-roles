package pl.aridlin.peebqa.mixin;
import net.minecraft.client.renderer.ShaderInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(ShaderInstance.class)
public abstract class ShaderObserveMixin {
 @Inject(method="apply",at=@At("TAIL")) private void peebqa$apply(CallbackInfo ci){pl.aridlin.peebqa.NativeDraws.shader((ShaderInstance)(Object)this);}
}
