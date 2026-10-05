package pl.aridlin.kukirin.mixin;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.player.AbstractClientPlayer;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(PlayerRenderer.class)
public abstract class RentalTripRendererMixin {
 @Inject(method="setupRotations(Lnet/minecraft/client/player/AbstractClientPlayer;Lcom/mojang/blaze3d/vertex/PoseStack;FFFF)V",at=@At("RETURN"))
 private void tumble(AbstractClientPlayer p,PoseStack pose,float bob,float yaw,float partial,float scale,CallbackInfo ci){
  float t=pl.aridlin.kukirin.RentalTripClient.progress(p,partial);if(t<0||p.isFallFlying()||p.deathTime>0)return;
  float swim=p.getSwimAmount(partial);if(swim>0){if(p.isVisuallySwimming())pose.translate(0,1,-.3);float angle=p.isInWater()||p.isInFluidType((type,height)->p.canSwimInFluidType(type))?-90-p.getXRot():-90;pose.mulPose(Axis.XP.rotationDegrees(-swim*angle));}
  float w=pl.aridlin.kukirin.RentalTripClient.weight(t);pose.mulPose(Axis.XP.rotationDegrees(-88*w));pose.mulPose(Axis.ZP.rotationDegrees(15*w));pose.translate(0,-w,.3*w);
 }
}
