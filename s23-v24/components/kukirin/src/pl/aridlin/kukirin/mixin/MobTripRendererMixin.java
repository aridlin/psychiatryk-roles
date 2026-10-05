package pl.aridlin.kukirin.mixin;
@org.spongepowered.asm.mixin.Mixin(net.minecraft.client.renderer.entity.LivingEntityRenderer.class)
public abstract class MobTripRendererMixin {
 @org.spongepowered.asm.mixin.injection.Inject(method="setupRotations",at=@org.spongepowered.asm.mixin.injection.At("RETURN"))
 private void tumble(net.minecraft.world.entity.LivingEntity entity,com.mojang.blaze3d.vertex.PoseStack pose,float bob,float yaw,float partial,float scale,org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci){if(entity instanceof net.minecraft.world.entity.player.Player||entity.deathTime>0)return;float t=pl.aridlin.kukirin.RentalTripClient.progress(entity,partial);if(t<0)return;float w=pl.aridlin.kukirin.RentalTripClient.weight(t);pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(-88*w));pose.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(15*w));pose.translate(0,-entity.getBbHeight()*.5*w,entity.getBbHeight()*.2*w);}
}
