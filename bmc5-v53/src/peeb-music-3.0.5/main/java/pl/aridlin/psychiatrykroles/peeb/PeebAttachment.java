package pl.aridlin.psychiatrykroles.peeb;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

public final class PeebAttachment {
   public static final double PIVOT_HEIGHT = 1.2;
   public static final double PIVOT_FORWARD = 0.35;
   public static final double MAX_BODY_TILT_DEGREES = 45.0;
   public static final double TETHER_TILT_WEIGHT = 0.8;
   public static final double TANGENTIAL_TILT_WEIGHT = 0.65;
   public static final Vec3 ORIGINAL_NEUTRAL_TIP = new Vec3(0.0, 0.3787, 0.4056);

   private PeebAttachment() {
   }

   public static float yaw(Player var0) {
      float var1 = var0.yBodyRot;
      if (!Float.isFinite(var1)) {
         var1 = var0.getYRot();
      }

      return Float.isFinite(var1) ? normalizeYaw(var1) : 0.0F;
   }

   public static float normalizeYaw(float var0) {
      if (!Float.isFinite(var0)) {
         return 0.0F;
      } else {
         float var1 = var0 % 360.0F;
         return var1 >= 180.0F ? var1 - 360.0F : (var1 < -180.0F ? var1 + 360.0F : var1);
      }
   }

   public static Vec3 pivot(Player var0) {
      return pivot(var0.position(), yaw(var0));
   }

   public static Vec3 pivot(Player var0, float var1) {
      return pivot(var0.position(), var1);
   }

   public static Vec3 pivot(Vec3 var0, float var1) {
      double var2 = Math.toRadians((double)normalizeYaw(var1));
      return var0.add(-Math.sin(var2) * 0.35, 1.2, Math.cos(var2) * 0.35);
   }

   private static boolean finite(Vec3 var0) {
      return Double.isFinite(var0.x) && Double.isFinite(var0.y) && Double.isFinite(var0.z);
   }

   public static PeebAttachment.Tilt bodyTilt(Vec3 var0, Vec3 var1, Vec3 var2, float var3) {
      if (finite(var0) && finite(var1) && finite(var2) && Float.isFinite(var3)) {
         Vec3 var4 = var1.subtract(var0).normalize();
         if (finite(var4) && !(var4.lengthSqr() < 1.0E-8)) {
            Vec3 var5 = var2.subtract(var4.scale(var2.dot(var4)));
            Vec3 var6 = var4.scale(0.8).add(var5.scale(0.65));
            if (!finite(var6)) {
               return PeebAttachment.Tilt.ZERO;
            } else {
               double var7 = Math.toRadians((double)normalizeYaw(var3));
               Vec3 var9 = new Vec3(-Math.sin(var7), 0.0, Math.cos(var7));
               Vec3 var10 = new Vec3(Math.cos(var7), 0.0, Math.sin(var7));
               double var11 = Math.max(0.2, Math.abs(var4.y));
               double var13 = Math.toDegrees(Math.atan2(var6.dot(var9), var11));
               double var15 = Math.toDegrees(Math.atan2(-var6.dot(var10), var11));
               double var17 = Math.sqrt(var13 * var13 + var15 * var15);
               if (var17 > 45.0) {
                  var13 *= 45.0 / var17;
                  var15 *= 45.0 / var17;
               }

               return new PeebAttachment.Tilt((float)var13, (float)var15);
            }
         } else {
            return PeebAttachment.Tilt.ZERO;
         }
      } else {
         return PeebAttachment.Tilt.ZERO;
      }
   }

   public static PeebAttachment.Tilt smooth(PeebAttachment.Tilt var0, PeebAttachment.Tilt var1, double var2) {
      if (var0 == null || !Float.isFinite(var0.pitchDegrees) || !Float.isFinite(var0.rollDegrees)) {
         var0 = PeebAttachment.Tilt.ZERO;
      }

      if (var1 == null || !Float.isFinite(var1.pitchDegrees) || !Float.isFinite(var1.rollDegrees)) {
         var1 = PeebAttachment.Tilt.ZERO;
      }

      double var4 = Double.isFinite(var2) ? Math.max(0.0, Math.min(1.0, var2)) : 0.0;
      if (var4 == 0.0) {
         return var0;
      } else {
         return var4 == 1.0
            ? var1
            : new PeebAttachment.Tilt(
               (float)((double)var0.pitchDegrees + (double)(var1.pitchDegrees - var0.pitchDegrees) * var4),
               (float)((double)var0.rollDegrees + (double)(var1.rollDegrees - var0.rollDegrees) * var4)
            );
      }
   }

   public static record Tilt(float pitchDegrees, float rollDegrees) {
      public static final PeebAttachment.Tilt ZERO = new PeebAttachment.Tilt(0.0F, 0.0F);
   }
}
