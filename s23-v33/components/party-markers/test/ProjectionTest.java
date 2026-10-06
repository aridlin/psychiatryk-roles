package pl.aridlin.partymarkers;
import org.joml.Matrix4f;
import org.joml.Vector4f;
public class ProjectionTest {
 public static void main(String[] args) {
  var p=new Matrix4f().perspective((float)Math.toRadians(70),16f/9f,.05f,256f);
  float[] near=null;
  for(float distance:new float[]{2,10,1000,100000,999999}) {
   var v=p.transform(new Vector4f(distance*.1f,distance*.05f,-distance,1));
   var point=Projection.screen(v.x,v.y,v.w);
   if(point==null) throw new AssertionError("Far-plane incorrectly culled "+distance);
   if(near==null) near=point;
   if(Math.abs(point[0]-near[0])>1e-6 || Math.abs(point[1]-near[1])>1e-6) throw new AssertionError("Distance changed direction");
  }
  if(Projection.screen(0,0,-10)!=null) throw new AssertionError("Behind camera");
  if(Projection.screen(100,0,1)!=null) throw new AssertionError("Off screen");
  if(Projection.screen(Float.NaN,0,1)!=null) throw new AssertionError("Invalid position");
  var center=Projection.screen(0,0,10);
  if(center[0]!=.5f || center[1]!=.5f) throw new AssertionError("Center");
  System.out.println("PASS: stable projection at 2 to 999999 blocks, no far-plane culling, behind/offscreen/invalid rejection");
 }
}
