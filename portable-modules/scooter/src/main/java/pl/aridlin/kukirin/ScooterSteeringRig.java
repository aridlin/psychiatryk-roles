package pl.aridlin.kukirin;
/** One raked steering transform for the retained mesh, direct mesh, trim and grips. */
public final class ScooterSteeringRig {
 /** Convert projected wheel yaw to rotation around the actual raked headset axis. */
 public static float axisAngleDegrees(double steering){
  double limited=Math.clamp(steering,-28,28);
  return (float)-Math.toDegrees(Math.atan(Math.tan(Math.toRadians(limited))/G2Mesh.axis().y));
 }
 /** The authored clip runs from negative to positive axis rotation in one second. */
 public static float animationTime(double steering){
  float maximum=-axisAngleDegrees(28);
  return (axisAngleDegrees(steering)+maximum)/(2*maximum);
 }
 public static org.joml.Quaternionf rotation(double steering){
  var axis=G2Mesh.axis();
  return new org.joml.Quaternionf().rotationAxis(axisAngleDegrees(steering)*(float)Math.PI/180,(float)axis.x,(float)axis.y,(float)axis.z);
 }
 /** Fallback vertices are stored with Y and Z flipped relative to the GLB. */
 public static org.joml.Quaternionf fallbackRotation(double steering){
  var axis=G2Mesh.axis();
  return new org.joml.Quaternionf().rotationAxis(axisAngleDegrees(steering)*(float)Math.PI/180,(float)axis.x,(float)-axis.y,(float)-axis.z);
 }
 private ScooterSteeringRig(){}
}
