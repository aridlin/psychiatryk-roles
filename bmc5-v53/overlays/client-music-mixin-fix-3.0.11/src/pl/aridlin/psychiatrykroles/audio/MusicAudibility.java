package pl.aridlin.psychiatrykroles.audio;

/** Same linear attenuation radius used by vanilla SoundEngine; independent of sound category. */
public final class MusicAudibility {
    private MusicAudibility() {}
    public static boolean audible(double distanceSquared, float volume, float category, float master,
                                  int attenuationDistance, boolean relative, boolean attenuated) {
        if (!Float.isFinite(volume) || !Float.isFinite(category) || !Float.isFinite(master)
            || volume <= 0 || category <= 0 || master <= 0) return false;
        if (relative || !attenuated) return true;
        double range = attenuationDistance * Math.max(1.0F, volume);
        return Double.isFinite(distanceSquared) && distanceSquared >= 0 && range > 0
            && distanceSquared < range * range;
    }
}
