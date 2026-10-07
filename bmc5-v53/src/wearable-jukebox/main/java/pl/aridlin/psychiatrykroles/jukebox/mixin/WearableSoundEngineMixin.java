package pl.aridlin.psychiatrykroles.jukebox.mixin;

import java.util.Map;
import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.resources.sounds.SoundInstance;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pl.aridlin.kukirin.ScooterAudioClient;
import pl.aridlin.psychiatrykroles.jukebox.WearableSoundPhysics;

@Mixin(SoundEngine.class)
public abstract class WearableSoundEngineMixin {
    @Shadow @Final private Map<SoundInstance, ChannelAccess.ChannelHandle> instanceToChannel;
    @Shadow private int tickCount;

    @Inject(method = "tickNonPaused", at = @At("TAIL"))
    private void jukebox$movingEnvironment(CallbackInfo callback) {
        int evaluated = 0;
        for (var entry : instanceToChannel.entrySet()) {
            if (!(entry.getKey() instanceof ScooterAudioClient.Moving moving)
                || !moving.physicsActive() || moving.getVolume() <= 0
                || Math.floorMod(tickCount + moving.hashCode(), 10) != 0) continue;
            WearableSoundPhysics.update(moving, entry.getValue());
            // Listener movement also changes acoustics for stationary jukeboxes.
            // The budget covers only our sources, including scooter vanilla discs.
            if (++evaluated == 4) break;
        }
    }
}
