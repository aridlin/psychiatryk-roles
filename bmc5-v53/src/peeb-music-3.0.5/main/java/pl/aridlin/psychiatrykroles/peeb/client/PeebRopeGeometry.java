package pl.aridlin.psychiatrykroles.peeb.client;

import net.minecraft.world.phys.Vec3;

/** A bounded visual cable; physics remains owned by the server grapple. */
public final class PeebRopeGeometry {
   public static final int SEGMENTS = 24;

   public static Curve curve(Vec3 tip, Vec3 anchor, double restLength) {
      Vec3 chord = anchor.subtract(tip);
      double distance = chord.length();
      double length = Double.isFinite(restLength) ? Math.max(distance, Math.min(32.0, restLength)) : distance;
      // Gravity projected perpendicular to the chord supplies a stable bend plane.
      // A vertical cable has no preferred gravity bend; use a fixed horizontal one.
      Vec3 direction = distance > 1.0E-7 ? chord.scale(1.0 / distance) : new Vec3(0.0, 1.0, 0.0);
      Vec3 gravity = new Vec3(0.0, -1.0, 0.0);
      Vec3 bend = gravity.subtract(direction.scale(gravity.dot(direction)));
      if (bend.lengthSqr() < 1.0E-6) bend = new Vec3(1.0, 0.0, 0.0);
      bend = bend.normalize();
      double low = 0.0;
      double high = Math.min(16.0, length * 0.5);
      if (length - distance > 1.0E-4) {
         for (int i = 0; i < 14; i++) {
            double middle = (low + high) * 0.5;
            if (arcLength(tip, chord, bend, middle) < length) low = middle;
            else high = middle;
         }
      } else high = 0.0;
      double sag = (low + high) * 0.5;
      Vec3[] points = new Vec3[SEGMENTS + 1];
      for (int i = 0; i <= SEGMENTS; i++) points[i] = point(tip, chord, bend, sag, (double)i / SEGMENTS);
      return new Curve(points, sag, arcLength(tip, chord, bend, sag));
   }

   private static Vec3 point(Vec3 tip, Vec3 chord, Vec3 bend, double sag, double t) {
      return tip.add(chord.scale(t)).add(bend.scale(4.0 * sag * t * (1.0 - t)));
   }

   private static double arcLength(Vec3 tip, Vec3 chord, Vec3 bend, double sag) {
      Vec3 previous = tip;
      double length = 0.0;
      for (int i = 1; i <= SEGMENTS; i++) {
         Vec3 next = point(tip, chord, bend, sag, (double)i / SEGMENTS);
         length += next.distanceTo(previous);
         previous = next;
      }
      return length;
   }

   public record Curve(Vec3[] points, double sag, double arcLength) {}

   private PeebRopeGeometry() {}
}
