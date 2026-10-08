package pl.aridlin.peebqa;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import java.util.ArrayList;
public final class NativeDraws {
 public static long local,remote,localTarget,depthCorrect,ropes; public static boolean forceFadeOff; public static float[] lastFade;
 public static double endpointError,sag; public static int vertices; private static Vec3 tip,anchor;private static final ArrayList<Vec3> points=new ArrayList<>();
 public static void shader(ShaderInstance shader){
  if(!shader.getName().contains("peeb_ps1"))return;
  var f=shader.getUniform("PeebLocalFade");if(f==null)return;
  var b=f.getFloatBuffer();lastFade=new float[]{b.get(0),b.get(1),b.get(2),b.get(3)};
  if(b.get(0)>.5){local++;if(b.get(1)>.5)localTarget++;}else remote++;
  if(GL11.glIsEnabled(GL11.GL_DEPTH_TEST)&&GL11.glGetInteger(GL11.GL_DEPTH_FUNC)==GL11.GL_LEQUAL)depthCorrect++;
  if(forceFadeOff)GL20.glUniform4f(f.getLocation(),0,b.get(1),b.get(2),b.get(3));
 }
 public static void begin(Vec3 a,Vec3 b,double length){tip=a;anchor=b;points.clear();sag=pl.aridlin.psychiatrykroles.peeb.client.PeebRopeGeometry.curve(a,b,length).sag();}
 public static void vertex(Vec3 p){points.add(p);}
 public static void end(){ropes++;vertices=points.size();if(vertices<4)return;
  var first=points.get(0).add(points.get(1)).scale(.5);var last=points.get(vertices-2).add(points.get(vertices-1)).scale(.5);
  endpointError=Math.max(first.distanceTo(tip),last.distanceTo(anchor));
 }
}
