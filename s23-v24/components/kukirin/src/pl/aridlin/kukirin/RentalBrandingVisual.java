package pl.aridlin.kukirin;
/** Large, crisp side-deck lettering, rigidly attached to the rental's body bone. */
public final class RentalBrandingVisual {
 public static void draw(Scooter scooter,com.mojang.blaze3d.vertex.PoseStack pose,net.minecraft.client.renderer.MultiBufferSource buffers,int light,com.wf.gemrender.render.PoseCache.Pose evaluated){
  if(!ScooterRental.isRental(scooter))return;var data=scooter.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.FEET).getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA,net.minecraft.world.item.component.CustomData.EMPTY).copyTag();String brand=switch(data.getInt("RentalBrand")){case 0->"LIME";case 1->"BOLT";default->"CITY";};var label=net.minecraft.network.chat.Component.literal(brand).withStyle(net.minecraft.ChatFormatting.BOLD);var font=net.minecraft.client.Minecraft.getInstance().font;
  pose.pushPose();pose.mulPose(evaluated.boneMatrix("body",new org.joml.Matrix4f()));
  for(int side:new int[]{-1,1}){pose.pushPose();pose.translate(side>0?.108:-.099,.22,.19);pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(side>0?90:-90));pose.scale(.013f,-.013f,.013f);font.drawInBatch(label,-font.width(label)/2f,0,0xffffffff,false,pose.last().pose(),buffers,net.minecraft.client.gui.Font.DisplayMode.NORMAL,0xa017211b,light);pose.popPose();}pose.popPose();
 }
 private RentalBrandingVisual(){}
}
