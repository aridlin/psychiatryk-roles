package pl.aridlin.psychiatrykroles.music;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/** Ordinary record discs coexist with the server-library jukebox controller. */
public final class VanillaJukeboxAudio {
    private VanillaJukeboxAudio() {}
    public static boolean audible(Minecraft minecraft, SoundEngine engine) {
        if (minecraft.level == null) return false;
        SoundEngineMusicAccessor access = (SoundEngineMusicAccessor) engine;
        SoundManager manager = minecraft.getSoundManager();
        Vec3 listener = manager.getListenerTransform().position();
        for (SoundInstance sound : access.psychiatryk$musicBySource().get(SoundSource.RECORDS)) {
            if (!manager.isActive(sound) || !access.psychiatryk$musicChannels().containsKey(sound)) continue;
            Sound resolved = sound.getSound();
            if (resolved == null || resolved == SoundManager.EMPTY_SOUND) continue;
            if (MusicAudibility.audible(listener.distanceToSqr(new Vec3(sound.getX(), sound.getY(), sound.getZ())),
                sound.getVolume(), minecraft.options.getSoundSourceVolume(SoundSource.RECORDS),
                minecraft.options.getSoundSourceVolume(SoundSource.MASTER), resolved.getAttenuationDistance(),
                sound.isRelative(), sound.getAttenuation() == SoundInstance.Attenuation.LINEAR)) return true;
        }
        return false;
    }
}
