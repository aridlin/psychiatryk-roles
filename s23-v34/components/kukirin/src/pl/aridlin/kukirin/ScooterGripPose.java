package pl.aridlin.kukirin;
/** Grip targets are transformed by the same evaluated stem animation as the mesh and trim. */
public final class ScooterGripPose {
 public static net.minecraft.world.phys.Vec3 grip(Scooter scooter,boolean left,float partial){
  var gpu=ScooterVisual.MODEL.get();var point=new org.joml.Vector3f(left?-.244f:.244f,1.168f,-.308f);var pivot=G2Mesh.pivot("steeringPivot");
  if(gpu!=null){var evaluated=pose(gpu,scooter.steering(partial),scooter.wheelRotation(partial));point.sub((float)pivot.x,(float)pivot.y,(float)pivot.z);evaluated.boneMatrix("stem",new org.joml.Matrix4f()).transformPosition(point);}
  else{point.sub((float)pivot.x,(float)pivot.y,(float)pivot.z).rotate(ScooterSteeringRig.rotation(scooter.steering(partial))).add((float)pivot.x,(float)pivot.y,(float)pivot.z);}
  return new net.minecraft.world.phys.Vec3(point.x*1.25,point.y*1.25,point.z*1.25);
 }
 /** Layer disjoint rig channels: crossfading complete poses would halve steering. */
 public static com.wf.gemrender.render.PoseCache.Pose pose(com.wf.gemrender.gltf.GemRenderGltfModel model,float steering,float wheel){
  return com.wf.gemrender.render.PoseCache.getInstance().pose(model.layout(),model.bounds(),model.morphs(),
   new com.wf.gemrender.gltf.GltfAnimation[]{model.animation("steering"),model.animation("wheels")},
   new float[]{ScooterSteeringRig.animationTime(steering),((wheel%360)+360)%360/360f},0);
 }
 private ScooterGripPose(){}
}
