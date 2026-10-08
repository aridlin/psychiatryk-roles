package pl.aridlin.psychiatrykroles.peeb;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import pl.aridlin.kukirin.Scooter;

/** Frozen client-side grapple calculations for the 3.0.10 protocol boundary. */
public final class PeebSharedPhysics {
   public static final double ATTACH_RANGE_GRACE = 0.35;
   public static final double MIN_ROPE_LENGTH = 0.35;
   public static final double TETHER_RANGE_EPSILON = 0.15;
   public static final int ROPE_SYNC_INTERVAL_TICKS = 2;

   private PeebSharedPhysics() {
   }

   public static boolean isScooterRider(Player player) {
      return player != null && player.isPassenger() && player.getRootVehicle() instanceof Scooter scooter
         && scooter.isAlive() && !scooter.isRemoved() && scooter.getControllingPassenger() == player;
   }

   public static float initiationYaw(Player player) {
      return PeebAttachment.normalizeYaw(player.getYRot());
   }

   public static boolean withinInitiationRange(Vec3 eye, Vec3 pivot, Vec3 point, double range, double grace) {
      if (eye == null || pivot == null || point == null || !finite(eye) || !finite(pivot) || !finite(point)
         || !Double.isFinite(range) || range < MIN_ROPE_LENGTH || !Double.isFinite(grace)
         || grace < 0.0 || grace > ATTACH_RANGE_GRACE) return false;
      double eyeDistance = eye.distanceTo(point), pivotDistance = pivot.distanceTo(point);
      return eyeDistance >= MIN_ROPE_LENGTH && pivotDistance >= MIN_ROPE_LENGTH
         && eyeDistance <= range + grace && pivotDistance <= range + grace;
   }

   private static boolean finite(Vec3 vector) {
      return Double.isFinite(vector.x) && Double.isFinite(vector.y) && Double.isFinite(vector.z);
   }

   private static boolean validPhysics(Vec3 pivot, Vec3 velocity, Vec3 anchor, double restLength, PeebConfig.Values settings) {
      return pivot != null && velocity != null && anchor != null
         && finite(pivot) && finite(velocity) && finite(anchor)
         && Double.isFinite(restLength) && settings != null && settings.valid()
         && restLength >= 0.0 && restLength <= settings.range();
   }

   public static double reelLength(double restLength, PeebConfig.Values settings) {
      if (!Double.isFinite(restLength) || settings == null || !settings.valid()) return 0.0;
      return PeebAdventuresPhysics.reel(restLength, settings.range(), settings.stopDistance(), PeebAdventuresPhysics.TICK_SECONDS);
   }

   public static double predictedRestLength(double serverRestLength, long ageTicks, PeebConfig.Values settings) {
      double predicted = serverRestLength;
      long ticksToPredict = Math.max(0L, Math.min(ROPE_SYNC_INTERVAL_TICKS, ageTicks));
      for (long tick = 0; tick < ticksToPredict; tick++) {
         predicted = reelLength(predicted, settings);
      }
      return predicted;
   }

   public static Vec3 pullVelocity(Vec3 pivot, Vec3 velocity, Vec3 anchor, double restLength, PeebConfig.Values settings, boolean grounded) {
      if (!validPhysics(pivot, velocity, anchor, restLength, settings)) return Vec3.ZERO;
      Vec3 pulled = PeebAdventuresPhysics.pull(pivot, velocity, anchor, restLength,
         settings.strength() / PeebConfig.DEFAULT.strength(), 1.0, PeebAdventuresPhysics.TICK_SECONDS);
      return PeebAdventuresPhysics.addWithinBudget(velocity, pulled, settings.maxHorizontalSpeed() * 0.25, settings.maxSpeed() * 0.25);
   }
}
