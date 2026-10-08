package pl.aridlin.psychiatrykroles.peeb.client.mixin;

import java.util.Map;
import java.util.Map.Entry;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.ChannelAccess.ChannelHandle;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pl.aridlin.psychiatrykroles.peeb.client.PeebAudio;
import pl.aridlin.psychiatrykroles.peeb.client.PeebSoundPhysics;

@Mixin({SoundEngine.class})
public abstract class PeebSoundEngineMixin {
   @Shadow
   @Final
   private Map<SoundInstance, ChannelHandle> instanceToChannel;
   @Shadow
   private int tickCount;

   @Inject(
      method = {"tickNonPaused"},
      at = {@At("TAIL")}
   )
   private void peeb$movingLoop(CallbackInfo var1) {
      int var2 = 0;

      for (Entry var4 : this.instanceToChannel.entrySet()) {
         Object var6 = var4.getKey();
         if (var6 instanceof PeebAudio.Hold) {
            PeebAudio.Hold var5 = (PeebAudio.Hold)var6;
            if (var5.physicsActive() && !(var5.getVolume() <= 0.0F) && Math.floorMod(this.tickCount + var5.hashCode(), 10) == 0) {
               PeebSoundPhysics.update(var5, (ChannelHandle)var4.getValue());
               if (++var2 == 4) {
                  break;
               }
            }
         }
      }
   }
}
