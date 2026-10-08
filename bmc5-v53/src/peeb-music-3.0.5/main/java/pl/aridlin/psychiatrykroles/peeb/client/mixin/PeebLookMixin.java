package pl.aridlin.psychiatrykroles.peeb.client.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pl.aridlin.psychiatrykroles.peeb.client.PeebClient;

@Mixin({Entity.class})
public abstract class PeebLookMixin {
   @Inject(
      method = {"turn"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void peeb$freeLook(double var1, double var3, CallbackInfo var5) {
      if ((Object)this == Minecraft.getInstance().player && PeebClient.freeLookTurn(var1, var3)) {
         var5.cancel();
      }
   }
}
