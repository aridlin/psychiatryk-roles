package pl.aridlin.pbqa.mixin;
import net.minecraft.client.renderer.GameRenderer;import org.spongepowered.asm.mixin.Mixin;import org.spongepowered.asm.mixin.injection.*;import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(GameRenderer.class) public abstract class GameRendererProbeMixin{
 @Inject(method="renderLevel",at=@At("HEAD")) private void qa$world(CallbackInfo ci){pl.aridlin.pbqa.PointBlankQA.probe("world_head");}
}
