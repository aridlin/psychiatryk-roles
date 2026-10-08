package pl.aridlin.psychiatrykroles.peeb.client.mixin;

import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pl.aridlin.psychiatrykroles.peeb.client.PeebClient;

@Mixin({Camera.class})
public abstract class PeebCameraMixin {
   @Shadow
   protected abstract void setPosition(double var1, double var3, double var5);

   @Shadow
   protected abstract void setRotation(float var1, float var2);

   @Inject(
      method = {"setup"},
      at = {@At("RETURN")}
   )
   private void peeb$camera(BlockGetter var1, Entity var2, boolean var3, boolean var4, float var5, CallbackInfo var6) {
      PeebClient.CameraFrame var7 = PeebClient.cameraFrame((Camera)(Object)this, var5);
      if (var7 != null) {
         this.setRotation(var7.yaw(), var7.pitch());
         this.setPosition(var7.position().x, var7.position().y, var7.position().z);
      }
   }
}
