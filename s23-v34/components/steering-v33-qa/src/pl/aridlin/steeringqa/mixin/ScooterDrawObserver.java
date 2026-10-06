package pl.aridlin.steeringqa.mixin;
import pl.aridlin.kukirin.Scooter;
import pl.aridlin.kukirin.ScooterRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(value=ScooterRenderer.class,remap=false)
public abstract class ScooterDrawObserver {
 @Inject(method="render(Lpl/aridlin/kukirin/Scooter;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",at=@At("HEAD"))
 private void before(Scooter entity,float yaw,float partial,PoseStack pose,MultiBufferSource buffers,int light,CallbackInfo callback){pl.aridlin.steeringqa.SteeringQA.DRAWN.set(entity);}
 @Inject(method="render(Lpl/aridlin/kukirin/Scooter;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",at=@At("RETURN"))
 private void after(Scooter entity,float yaw,float partial,PoseStack pose,MultiBufferSource buffers,int light,CallbackInfo callback){pl.aridlin.steeringqa.SteeringQA.DRAWN.remove();}
}
