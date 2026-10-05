package pl.aridlin.kukirin;
/** Grip targets are transformed by the same evaluated stem animation as the mesh and trim. */
public final class ScooterGripPose {
 public static net.minecraft.world.phys.Vec3 grip(Scooter scooter,boolean left,float partial){
  var gpu=ScooterVisual.MODEL.get();var point=new org.joml.Vector3f(left?-.244f:.244f,1.168f,-.308f);var pivot=G2Mesh.pivot("steeringPivot");
  if(gpu!=null){var blend=new com.wf.gemrender.gltf.blend.AnimationBlend(2);blend.override(gpu.animation("steering"),(scooter.steering(partial)+28)/56,1);blend.override(gpu.animation("wheels"),((scooter.wheelRotation(partial)%360)+360)%360/360f,1);var evaluated=com.wf.gemrender.render.PoseCache.getInstance().pose(gpu.layout(),gpu.bounds(),gpu.morphs(),blend,0);point.sub((float)pivot.x,(float)pivot.y,(float)pivot.z);evaluated.boneMatrix("stem",new org.joml.Matrix4f()).transformPosition(point);}
  else{point.sub((float)pivot.x,(float)pivot.y,(float)pivot.z).rotateY(-scooter.steering(partial)*(float)Math.PI/180).add((float)pivot.x,(float)pivot.y,(float)pivot.z);}
  return new net.minecraft.world.phys.Vec3(point.x*1.25,point.y*1.25,point.z*1.25);
 }
 private ScooterGripPose(){}
}
