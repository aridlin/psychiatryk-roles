package pl.aridlin.kukirin.mixin;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.inventory.SmithingScreen;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import pl.aridlin.kukirin.Kukirin;
import pl.aridlin.kukirin.ScooterSmithingPreview;

@Mixin(SmithingScreen.class)
public abstract class ScooterSmithingPreviewMixin {
 @Redirect(method="renderBg",at=@At(value="INVOKE",target="Lnet/minecraft/client/gui/screens/inventory/InventoryScreen;renderEntityInInventory(Lnet/minecraft/client/gui/GuiGraphics;FFFLorg/joml/Vector3f;Lorg/joml/Quaternionf;Lorg/joml/Quaternionf;Lnet/minecraft/world/entity/LivingEntity;)V"))
 private void scooterResult(GuiGraphics graphics,float x,float y,float scale,Vector3f translation,Quaternionf pose,Quaternionf camera,LivingEntity armorStand){
  var result=((SmithingScreen)(Object)this).getMenu().getSlot(3).getItem();
  if(result.is(Kukirin.ITEM.get()))ScooterSmithingPreview.render(graphics,x,y,result);
  else InventoryScreen.renderEntityInInventory(graphics,x,y,scale,translation,pose,camera,armorStand);
 }
}
