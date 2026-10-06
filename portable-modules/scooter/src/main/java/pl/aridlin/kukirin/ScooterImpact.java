package pl.aridlin.kukirin;
/** Impact damage scales linearly with speed, with a defensive cap for teleports or malformed movement. */
public final class ScooterImpact {
 public static float piercingDamage(int level,double blocksPerTick){if(level<=0||!Double.isFinite(blocksPerTick))return 0;return (float)Math.clamp(2*level*blocksPerTick/ScooterHandling.TOP_SPEED,0,40);}
 private ScooterImpact(){}
}
