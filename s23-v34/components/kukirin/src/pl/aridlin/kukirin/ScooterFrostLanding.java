package pl.aridlin.kukirin;
/** Bounded sweep for Frost Walker II falling landings; never digs into submerged water. */
public final class ScooterFrostLanding {
 public static double lowestSurface(double height,double verticalSpeed){return height-1.25-Math.min(8,Math.max(0,-verticalSpeed)*2);}
}
