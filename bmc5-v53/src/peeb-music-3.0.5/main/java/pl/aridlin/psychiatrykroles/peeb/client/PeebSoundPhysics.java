package pl.aridlin.psychiatrykroles.peeb.client;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.minecraft.client.sounds.ChannelAccess.ChannelHandle;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import org.slf4j.LoggerFactory;
import pl.aridlin.psychiatrykroles.peeb.client.mixin.PeebChannelAccessor;

public final class PeebSoundPhysics {
   private static boolean resolved;
   private static volatile PeebSoundPhysics.Adapter adapter;
   public static volatile long applications;

   private PeebSoundPhysics() {
   }

   private static synchronized PeebSoundPhysics.Adapter resolve() {
      if (resolved) {
         return adapter;
      } else {
         resolved = true;

         try {
            Class var0 = Class.forName("com.sonicether.soundphysics.SoundPhysics");
            Class var1 = Class.forName("com.sonicether.soundphysics.SoundPhysicsMod");
            Class var2 = Class.forName("com.sonicether.soundphysics.config.SoundPhysicsConfig");
            Class var3 = Class.forName("de.maxhenkel.sound_physics_remastered.configbuilder.entry.ConfigEntry");
            adapter = new PeebSoundPhysics.Adapter(
               var0.getMethod("processSound", int.class, double.class, double.class, double.class, SoundSource.class, ResourceLocation.class),
               var1.getField("CONFIG"),
               var2.getField("enabled"),
               var2.getField("updateMovingSounds"),
               var3.getMethod("get")
            );
         } catch (ClassNotFoundException var4) {
         } catch (ReflectiveOperationException var5) {
            disable(var5);
         }

         return adapter;
      }
   }

   public static void update(PeebAudio.Hold var0, ChannelHandle var1) {
      PeebSoundPhysics.Adapter var2 = resolve();
      if (var2 != null && !var1.isStopped() && var0.physicsActive()) {
         try {
            if (!var2.scopedUpdatesEnabled()) {
               return;
            }
         } catch (ReflectiveOperationException var11) {
            disable(var11);
            return;
         }

         double var3 = var0.getX();
         double var5 = var0.getY();
         double var7 = var0.getZ();
         SoundSource var9 = var0.getSource();
         ResourceLocation var10 = var0.getLocation();
         var1.execute(var10x -> {
            if (var0.physicsActive() && !var10x.stopped() && var10x.playing()) {
               try {
                  var2.process.invoke(null, ((PeebChannelAccessor)var10x).peeb$source(), var3, var5, var7, var9, var10);
                  applications++;
               } catch (ReflectiveOperationException var12) {
                  disable(var12);
               }
            }
         });
      }
   }

   private static synchronized void disable(Exception var0) {
      adapter = null;
      LoggerFactory.getLogger("Peeb").warn("Peeb Sound Physics adapter unavailable ({}).", var0.getClass().getSimpleName());
   }

   private static record Adapter(Method process, Field configuration, Field enabled, Field globalUpdates, Method value) {
      boolean scopedUpdatesEnabled() throws ReflectiveOperationException {
         Object var1 = this.configuration.get(null);
         return Boolean.TRUE.equals(this.value.invoke(this.enabled.get(var1))) && !Boolean.TRUE.equals(this.value.invoke(this.globalUpdates.get(var1)));
      }
   }
}
