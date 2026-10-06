package pl.aridlin.kukirin;
import java.lang.reflect.*;
import org.joml.Vector3f;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ReloadableResourceManager;
import net.minecraft.world.phys.Vec3;
/** Exact candidate helper/G2Mesh classes; isolated cached resource fixture, no client launch. */
public final class SteeringRigQA {
 static int checks;
 static void check(boolean value,String name){if(!value)throw new AssertionError(name);checks++;}
 static void set(Class<?> type,Object object,String name,Object value)throws Exception{var field=type.getDeclaredField(name);field.setAccessible(true);field.set(object,value);}
 public static void main(String[] args)throws Exception{
  var field=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");field.setAccessible(true);var unsafe=(sun.misc.Unsafe)field.get(null);
  var mc=(Minecraft)unsafe.allocateInstance(Minecraft.class);var resources=new ReloadableResourceManager(PackType.CLIENT_RESOURCES);
  set(Minecraft.class,null,"instance",mc);set(Minecraft.class,mc,"resourceManager",resources);
  set(G2Mesh.class,null,"manager",resources);set(G2Mesh.class,null,"parts",new float[4][]);
  var axis=new Vec3(0,.9611505739343013,.27602458989329776);set(G2Mesh.class,null,"axis",axis);
  float maximum=(float)Math.toDegrees(Math.atan(Math.tan(Math.toRadians(28))/axis.y));
  var pivot=new Vector3f(.004729188f,.365504978f,-.368380453f);var vector=new Vector3f(0,(float)axis.y,(float)axis.z);
  for(int degrees=-28;degrees<=28;degrees++){
   float angle=ScooterSteeringRig.axisAngleDegrees(degrees);var rotation=ScooterSteeringRig.rotation(degrees);
   var axle=rotation.transform(new Vector3f(1,0,0));double projected=Math.toDegrees(Math.atan2(axle.z,axle.x));
   check(Math.abs(projected-degrees)<.00002,"projected wheel angle "+degrees);
   float time=ScooterSteeringRig.animationTime(degrees);
   check(Math.abs((-maximum+2*maximum*time)-angle)<.00001,"authored animation time "+degrees);
   check(rotation.transform(new Vector3f(vector)).distance(vector)<.000001,"headset axis does not move "+degrees);
   var actual=new Vector3f(.11f,.61f,-.32f);var flipped=new Vector3f(actual.x,-actual.y,-actual.z);
   ScooterSteeringRig.fallbackRotation(degrees).transform(flipped);rotation.transform(actual);
   check(new Vector3f(actual.x,-actual.y,-actual.z).distance(flipped)<.000001,"fallback and GLB transform agree "+degrees);
  }
  check(ScooterSteeringRig.animationTime(0)==.5f,"neutral clip midpoint");
  check(ScooterSteeringRig.animationTime(-28)==1&&ScooterSteeringRig.animationTime(28)==0,"full range endpoints preserved");
  check(ScooterSteeringRig.axisAngleDegrees(280)==ScooterSteeringRig.axisAngleDegrees(28),"large request clamps physically");
  System.out.println("PASS "+checks);
 }
}
