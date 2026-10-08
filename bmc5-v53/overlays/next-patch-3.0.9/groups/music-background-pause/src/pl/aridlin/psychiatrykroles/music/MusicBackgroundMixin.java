package pl.aridlin.psychiatrykroles.music;

import com.mojang.blaze3d.audio.Channel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.client.sounds.MusicManager;
import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pl.aridlin.kukirin.ScooterAudioClient;

/** Pauses the one vanilla background channel, retaining its playback position. */
@Mixin(MusicManager.class)
public abstract class MusicBackgroundMixin {
    @Shadow @Final private Minecraft minecraft;
    @Shadow private SoundInstance currentMusic;
    @Unique private final BackgroundMusicPauseState<SoundInstance, ChannelAccess.ChannelHandle>
        psychiatryk$backgroundPause = new BackgroundMusicPauseState<>();

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void psychiatryk$pauseBehindJukebox(CallbackInfo callback) {
        SoundEngine engine = ((SoundManagerMusicAccessor) minecraft.getSoundManager()).psychiatryk$musicEngine();
        ChannelAccess.ChannelHandle handle = currentMusic == null ? null
            : ((SoundEngineMusicAccessor) engine).psychiatryk$musicChannels().get(currentMusic);
        if (psychiatryk$backgroundPause.update(ScooterAudioClient.hasAudibleMusic() || VanillaJukeboxAudio.audible(minecraft, engine),
            ScooterAudioClient.hasPendingAudibleMusic(), minecraft.isPaused(), currentMusic, handle,
            channel -> channel.execute(Channel::pause), channel -> channel.execute(Channel::unpause))) callback.cancel();
    }
}
