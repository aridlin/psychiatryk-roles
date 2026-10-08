package pl.aridlin.psychiatrykroles.peeb.client;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent.Post;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;
import pl.aridlin.psychiatrykroles.peeb.PeebMode;

@EventBusSubscriber(
   modid = "psychiatryk_peeb",
   value = {Dist.CLIENT}
)
public final class PeebAudio {
   private static final Map<UUID, PeebAudio.Motion> MOTION = new HashMap<>();

   private PeebAudio() {
   }

   public static boolean step(Player var0) {
      if (PeebClient.active(var0) && var0.level().isClientSide && !var0.isSilent()) {
         PeebAudio.Motion var1 = MOTION.computeIfAbsent(var0.getUUID(), var1x -> new PeebAudio.Motion(var0));
         if (var1.age - var1.lastStep >= 2) {
            positional(var0, (SoundEvent)PeebMode.STEP.get(), 0.5F, 1.0F);
            var1.lastStep = var1.age;
         }

         return true;
      } else {
         return false;
      }
   }

   public static float landingAge(Player var0, float var1) {
      PeebAudio.Motion var2 = MOTION.get(var0.getUUID());
      return var2 == null ? 100.0F : (float)(var2.age - var2.lastLand) + var1;
   }

   @SubscribeEvent
   public static void tick(Post var0) {
      Minecraft var1 = Minecraft.getInstance();
      if (var1.level == null) {
         reset();
      } else if (!var1.isPaused()) {
         MOTION.entrySet().removeIf(var1x -> {
            Player var2 = var1.level.getPlayerByUUID(var1x.getKey());
            if (PeebClient.active(var2)) {
               return false;
            } else {
               if (var1x.getValue().loop != null) {
                  var1x.getValue().loop.finish();
               }

               return true;
            }
         });

         for (AbstractClientPlayer var3 : var1.level.players()) {
            if (PeebClient.active(var3)) {
               PeebAudio.Motion var4 = MOTION.computeIfAbsent(var3.getUUID(), var1x -> new PeebAudio.Motion(var3));
               var4.age++;
               Vec3 var5 = PeebClient.velocity(var3);
               boolean var6 = !PeebClient.airborne(var3, var5);
               if (var4.ground && !var6 && var5.y > 0.1 && !var3.isInWater() && !var3.isPassenger()) {
                  positional(var3, (SoundEvent)PeebMode.JUMP.get(), 0.5F, 1.0F);
               }

               if (!var4.ground && var6 && var4.airTicks >= 3 && var4.vertical < -0.04 && var4.age - var4.lastLand >= 6 && !var3.isInWater()) {
                  positional(var3, (SoundEvent)PeebMode.LAND.get(), 0.5F, 1.0F);
                  var4.lastLand = var4.age;
               }

               var4.airTicks = var6 ? 0 : var4.airTicks + 1;
               var4.ground = var6;
               var4.vertical = var5.y;
               boolean var7 = PeebClient.grapple(var3).isPresent();
               if (var7 && !var4.attached) {
                  positional(var3, (SoundEvent)PeebMode.GRAPPLE_BEGIN.get(), 0.5F, 1.0F);
                  var4.loop = new PeebAudio.Hold(var3);
                  var1.getSoundManager().play(var4.loop);
               } else if (!var7 && var4.attached) {
                  if (var4.loop != null) {
                     var4.loop.finish();
                  }

                  var4.loop = null;
                  positional(var3, (SoundEvent)PeebMode.GRAPPLE_END.get(), 0.5F, 1.0F);
               }

               var4.attached = var7;
            }
         }
      }
   }

   @SubscribeEvent
   public static void suppressVanilla(PlaySoundEvent var0) {
      SoundInstance var1 = var0.getSound();
      Minecraft var2 = Minecraft.getInstance();
      if (var1 != null && var2.level != null && var1.getSource() == SoundSource.PLAYERS && var1.getLocation().getNamespace().equals("minecraft")) {
         String var3 = var1.getLocation().getPath();
         boolean var4 = var3.endsWith(".step");
         boolean var5 = var3.endsWith(".fall") || var3.equals("entity.player.big_fall") || var3.equals("entity.player.small_fall");
         if (var4 || var5) {
            AbstractClientPlayer var6 = null;
            double var7 = Double.MAX_VALUE;

            for (AbstractClientPlayer var10 : var2.level.players()) {
               if (!(Math.abs(var10.getX() - var1.getX()) > 0.65)
                  && !(Math.abs(var10.getY() - var1.getY()) > 1.5)
                  && !(Math.abs(var10.getZ() - var1.getZ()) > 0.65)) {
                  double var11 = var10.distanceToSqr(var1.getX(), var1.getY(), var1.getZ());
                  if (var11 < var7) {
                     var7 = var11;
                     var6 = var10;
                  }
               }
            }

            if (PeebClient.active(var6)) {
               var0.setSound(null);
               if (var4) {
                  step(var6);
               }
            }
         }
      }
   }

   private static void positional(Player var0, SoundEvent var1, float var2, float var3) {
      if (!var0.isSilent()) {
         Minecraft.getInstance()
            .getSoundManager()
            .play(new SimpleSoundInstance(var1, SoundSource.PLAYERS, var2, var3, RandomSource.create(), var0.getX(), var0.getY(), var0.getZ()));
      }
   }

   public static void reset() {
      for (PeebAudio.Motion var1 : MOTION.values()) {
         if (var1.loop != null) {
            var1.loop.finish();
         }
      }

      MOTION.clear();
   }

   public static final class Hold extends AbstractTickableSoundInstance {
      private final Player player;
      private volatile boolean active = true;

      Hold(Player var1) {
         super((SoundEvent)PeebMode.GRAPPLE_HOLD.get(), SoundSource.PLAYERS, RandomSource.create());
         this.player = var1;
         this.looping = true;
         this.volume = 0.5F;
         this.relative = false;
         this.x = var1.getX();
         this.y = var1.getY();
         this.z = var1.getZ();
      }

      public void tick() {
         if (Minecraft.getInstance().level == this.player.level() && PeebClient.active(this.player) && !PeebClient.grapple(this.player).isEmpty()) {
            this.x = this.player.getX();
            this.y = this.player.getY();
            this.z = this.player.getZ();
         } else {
            this.active = false;
            this.stop();
         }
      }

      public boolean physicsActive() {
         return this.active && !this.isStopped();
      }

      void finish() {
         this.active = false;
         this.stop();
         Minecraft.getInstance().getSoundManager().stop(this);
      }
   }

   private static final class Motion {
      boolean ground;
      boolean attached;
      int age;
      int airTicks;
      int lastStep = -100;
      int lastLand = -100;
      double vertical;
      PeebAudio.Hold loop;

      Motion(Player var1) {
         this.ground = !PeebClient.airborne(var1, PeebClient.velocity(var1));
      }
   }
}
