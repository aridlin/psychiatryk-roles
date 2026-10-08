package pl.aridlin.psychiatrykroles.peeb.client.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pl.aridlin.psychiatrykroles.peeb.client.PeebAudio;

@Mixin({Player.class})
public abstract class PeebStepMixin {
   @Inject(
      method = {"playStepSound"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void peeb$step(BlockPos var1, BlockState var2, CallbackInfo var3) {
      Player var4 = (Player)(Object)this;
      if (!var4.isInWater() && PeebAudio.step(var4)) {
         var3.cancel();
      }
   }
}
