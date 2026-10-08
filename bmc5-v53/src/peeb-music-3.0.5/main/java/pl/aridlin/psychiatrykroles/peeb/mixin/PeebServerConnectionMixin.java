package pl.aridlin.psychiatrykroles.peeb.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pl.aridlin.psychiatrykroles.peeb.PeebGrapple;

@Mixin({ServerGamePacketListenerImpl.class})
public abstract class PeebServerConnectionMixin {
   @Shadow
   public ServerPlayer player;
   @Shadow
   private boolean clientIsFloating;
   @Shadow
   private int aboveGroundTickCount;

   @Inject(
      method = {"tick"},
      at = {@At("HEAD")}
   )
   private void peeb$allowValidatedHanging(CallbackInfo var1) {
      if (PeebGrapple.validTether(this.player)) {
         this.clientIsFloating = false;
         this.aboveGroundTickCount = 0;
      }
   }
}
