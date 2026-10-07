package pl.aridlin.psychiatrykroles.jukebox;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.client.sounds.ChannelAccess;
import pl.aridlin.kukirin.ScooterAudioClient;
import pl.aridlin.psychiatrykroles.jukebox.mixin.WearableChannelAccessor;

/** Optional SPR 1.5 adapter; no common/server class has a dependency on its client API. */
public final class WearableSoundPhysics {
    private record Adapter(Method process, Field configuration, Field enabled, Field globalUpdates, Method value) {
        boolean scopedUpdatesEnabled() throws ReflectiveOperationException {
            var config = configuration.get(null);
            return Boolean.TRUE.equals(value.invoke(enabled.get(config)))
                && !Boolean.TRUE.equals(value.invoke(globalUpdates.get(config)));
        }
    }
    private static boolean resolved;
    private static volatile Adapter adapter;
    public static volatile long applications;
    public static volatile int lastSource;
    public static volatile double lastX, lastY, lastZ;

    private static synchronized Adapter resolve() {
        if (resolved) return adapter;
        resolved = true;
        try {
            var physics = Class.forName("com.sonicether.soundphysics.SoundPhysics");
            var mod = Class.forName("com.sonicether.soundphysics.SoundPhysicsMod");
            var config = Class.forName("com.sonicether.soundphysics.config.SoundPhysicsConfig");
            var entry = Class.forName("de.maxhenkel.sound_physics_remastered.configbuilder.entry.ConfigEntry");
            adapter = new Adapter(physics.getMethod("processSound", int.class, double.class, double.class, double.class,
                SoundSource.class, ResourceLocation.class), mod.getField("CONFIG"), config.getField("enabled"),
                config.getField("updateMovingSounds"), entry.getMethod("get"));
        } catch (ClassNotFoundException ignored) {
            // Audio remains positional when optional Sound Physics is not installed.
        } catch (ReflectiveOperationException error) {
            org.slf4j.LoggerFactory.getLogger("WearableJukebox").warn("Moving Sound Physics adapter unavailable ({}).", error.getClass().getSimpleName());
        }
        return adapter;
    }

    /** Called on the client thread, with a live Minecraft handle rather than a retained source ID. */
    public static void update(ScooterAudioClient.Moving sound, ChannelAccess.ChannelHandle handle) {
        var api = resolve();
        if (api == null || handle.isStopped() || !sound.physicsActive()) return;
        try {
            // If the user enabled global reevaluation, SPR already handles this sound.
            if (!api.scopedUpdatesEnabled()) return;
        } catch (ReflectiveOperationException error) {
            disable(error);
            return;
        }
        double x = sound.getX(), y = sound.getY(), z = sound.getZ();
        var category = sound.getSource();
        var name = sound.getLocation();
        handle.execute(channel -> {
            // ChannelHandle rejects released channels. This volatile guard prevents
            // queued work from evaluating an obsolete sound before channel release.
            if (!sound.physicsActive() || channel.stopped() || !channel.playing()) return;
            int source = ((WearableChannelAccessor) channel).jukebox$getSource();
            try {
                api.process.invoke(null, source, x, y, z, category, name);
                lastSource = source; lastX = x; lastY = y; lastZ = z;
                applications++;
            } catch (ReflectiveOperationException error) {
                disable(error);
            }
        });
    }

    private static synchronized void disable(Exception error) {
        if (adapter == null) return;
        adapter = null;
        org.slf4j.LoggerFactory.getLogger("WearableJukebox").warn("Moving Sound Physics adapter disabled ({}).", error.getClass().getSimpleName());
    }
    private WearableSoundPhysics() {}
}
