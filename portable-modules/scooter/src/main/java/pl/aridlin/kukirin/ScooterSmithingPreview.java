package pl.aridlin.kukirin;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/** A detached result-slot model. Never adds an entity to the world or queues a level GPU pass. */
public final class ScooterSmithingPreview {
 private static Scooter preview;
 public static ItemStack displayedStack=ItemStack.EMPTY;

 public static void render(GuiGraphics graphics,float x,float y,ItemStack result){
  var mc=Minecraft.getInstance();
  if(mc.level==null)return;
  if(preview==null||preview.level()!=mc.level)preview=new Scooter(Kukirin.SCOOTER.get(),mc.level);
  displayedStack=result.copy();
  preview.setItemSlot(EquipmentSlot.FEET,displayedStack);
  graphics.flush();
  var pose=graphics.pose();
  pose.pushPose();
  try{
   pose.translate(x,y,100);
   pose.scale(35,35,-35);
   pose.mulPose(Axis.ZP.rotationDegrees(180));
   pose.mulPose(Axis.XP.rotationDegrees(38));
   pose.mulPose(Axis.YP.rotationDegrees(45));
   Lighting.setupForEntityInInventory();
   pose.pushPose();pose.scale(1,-1,-1);var vertex=graphics.bufferSource().getBuffer(RenderType.entityCutout(ScooterRenderer.TEX));
   for(int part=0;part<4;part++)G2Mesh.draw(part,pose,vertex,15728880,ScooterDyeRecipe.variant(result));pose.popPose();
   ScooterTrim.draw(preview,pose,graphics.bufferSource(),15728880,1);
   graphics.flush();
  }finally{
   pose.popPose();
   Lighting.setupFor3DItems();
  }
 }
}
