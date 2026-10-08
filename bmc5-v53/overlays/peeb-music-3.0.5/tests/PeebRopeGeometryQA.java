import java.util.Random;
import net.minecraft.world.phys.Vec3;
import pl.aridlin.psychiatrykroles.peeb.client.PeebRopeGeometry;

public final class PeebRopeGeometryQA {
   private static int checks;
   private static void require(boolean value, String message) {
      checks++;
      if (!value) throw new AssertionError(message);
   }
   public static void main(String[] args) {
      Random random = new Random(880817L);
      for (int k = 0; k < 500; k++) {
         Vec3 tip = new Vec3(random.nextDouble() * 20 - 10, random.nextDouble() * 20 - 10, random.nextDouble() * 20 - 10);
         Vec3 anchor = tip.add(random.nextDouble() * 12 - 6, random.nextDouble() * 12 - 6, random.nextDouble() * 12 - 6);
         double distance = anchor.distanceTo(tip);
         double rest = distance + random.nextDouble() * 12;
         var curve = PeebRopeGeometry.curve(tip, anchor, rest);
         require(curve.points().length == 25, "bounded segments");
         require(curve.points()[0].equals(tip), "exact tusk origin");
         require(curve.points()[24].distanceTo(anchor) < 1E-10, "exact anchor endpoint");
         require(curve.sag() > 0, "slack has sag");
         require(Math.abs(curve.arcLength() - rest) < 0.004, "arc fits synced rest length");
         for (Vec3 point : curve.points()) require(Double.isFinite(point.x + point.y + point.z), "finite curve");
         var tight = PeebRopeGeometry.curve(tip, anchor, distance - 0.5);
         require(tight.sag() == 0, "tension straightens");
         require(Math.abs(tight.arcLength() - distance) < 1E-10, "taut length");
         for (int i = 0; i <= 24; i++) require(tight.points()[i].distanceTo(tip.add(anchor.subtract(tip).scale(i / 24.0))) < 1E-10, "taut follows chord");
      }
      for (double length : new double[]{1.0, 2.0, 8.0, 32.0}) {
         var vertical = PeebRopeGeometry.curve(Vec3.ZERO, new Vec3(0, 0.5, 0), length);
         require(Double.isFinite(vertical.sag()), "vertical bend stable");
         require(Math.abs(vertical.arcLength() - length) < 0.004, "vertical rest length");
         var coincident = PeebRopeGeometry.curve(Vec3.ZERO, Vec3.ZERO, length);
         require(Math.abs(coincident.arcLength() - length) < 0.004, "coincident slack bounded");
      }
      var invalid = PeebRopeGeometry.curve(Vec3.ZERO, new Vec3(1, 0, 0), Double.NaN);
      require(invalid.sag() == 0 && invalid.arcLength() == 1, "invalid rest length safe");
      System.out.println("{\"success\":true,\"checks\":" + checks + "}");
   }
}
