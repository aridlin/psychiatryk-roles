package pl.aridlin.kukirin;
/** The two rockets are rigidly attached to the same body bone/transform as the scooter. */
public final class ScooterRocketVisual {
 public static void draw(Scooter scooter,com.mojang.blaze3d.vertex.PoseStack pose,net.minecraft.client.renderer.MultiBufferSource buffers,int light,com.wf.gemrender.render.PoseCache.Pose evaluated){
  if(!scooter.rocketActive())return;pose.pushPose();pose.mulPose(evaluated.boneMatrix("body",new org.joml.Matrix4f()));
  for(int side:new int[]{-1,1}){pose.pushPose();pose.translate(side*.19,.23,.05);pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(90));pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(side<0?180:0));pose.scale(.5f,.5f,.5f);net.minecraft.client.Minecraft.getInstance().getItemRenderer().renderStatic(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.FIREWORK_ROCKET),net.minecraft.world.item.ItemDisplayContext.FIXED,light,net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY,pose,buffers,scooter.level(),scooter.getId()+side);pose.popPose();}pose.popPose();
 }
}
