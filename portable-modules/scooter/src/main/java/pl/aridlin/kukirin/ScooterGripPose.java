package pl.aridlin.kukirin;
/** Grip targets share the native renderer's raked steering transform. */
public final class ScooterGripPose {
 public static net.minecraft.world.phys.Vec3 grip(Scooter scooter,boolean left,float partial){
  var point=new org.joml.Vector3f(left?-.244f:.244f,1.168f,-.308f);var pivot=G2Mesh.pivot("steeringPivot");
  point.sub((float)pivot.x,(float)pivot.y,(float)pivot.z).rotate(ScooterSteeringRig.rotation(scooter.steering(partial))).add((float)pivot.x,(float)pivot.y,(float)pivot.z);
  return new net.minecraft.world.phys.Vec3(point.x*1.25,point.y*1.25,point.z*1.25);
 }
 private ScooterGripPose(){}
}
