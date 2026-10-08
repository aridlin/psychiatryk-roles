package pl.aridlin.peebheadqa;
import java.util.*;
import java.nio.file.*;
import java.lang.reflect.*;
import sun.misc.Unsafe;
import com.google.gson.*;
import org.joml.Vector3f;
import org.joml.Quaternionf;
import org.joml.Matrix4f;
import pl.aridlin.psychiatrykroles.peeb.client.*;
public final class PeebHeadTest {
 static int checks;
 static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
 static Field field(Class<?> type,String name)throws Exception{Field f=type.getDeclaredField(name);f.setAccessible(true);return f;}
 static Object get(Object value,String name)throws Exception{return field(value.getClass(),name).get(value);}
 static void put(Object value,String name,Object data)throws Exception{field(value.getClass(),name).set(value,data);}
 static JsonObject read(Path file)throws Exception{return JsonParser.parseString(Files.readString(file)).getAsJsonObject();}
 static float[] floats(JsonArray a){float[] out=new float[a.size()];for(int i=0;i<out.length;i++)out[i]=a.get(i).getAsFloat();return out;}
 static float[][] rows(JsonArray a){float[][] out=new float[a.size()][];for(int i=0;i<out.length;i++)out[i]=floats(a.get(i).getAsJsonArray());return out;}
 static int[] ints(JsonArray a){int[] out=new int[a.size()];for(int i=0;i<out.length;i++)out[i]=a.get(i).getAsInt();return out;}
 static Vector3f[] vectors(JsonArray a){Vector3f[] out=new Vector3f[a.size()];for(int i=0;i<out.length;i++)out[i]=new Vector3f(floats(a.get(i).getAsJsonArray()));return out;}
 static PeebMesh load(Path root)throws Exception{
  Field uf=field(Unsafe.class,"theUnsafe");Unsafe unsafe=(Unsafe)uf.get(null);PeebMesh mesh=(PeebMesh)unsafe.allocateInstance(PeebMesh.class);JsonObject j=read(root.resolve("peeb-model.json"));
  put(mesh,"vertices",vectors(j.getAsJsonArray("vertices")));put(mesh,"normals",vectors(j.getAsJsonArray("normals")));put(mesh,"weights",rows(j.getAsJsonArray("bone_weights")));
  JsonArray b=j.getAsJsonArray("bone_indices");int[][] bones=new int[b.size()][];for(int i=0;i<bones.length;i++)bones[i]=ints(b.get(i).getAsJsonArray());put(mesh,"bones",bones);
  JsonArray n=j.getAsJsonArray("nodes");int[] parents=new int[n.size()],nose=new int[3];float[][] defaults=new float[n.size()][10];int head=-1;
  for(int i=0;i<n.size();i++){JsonObject row=n.get(i).getAsJsonObject();parents[i]=row.get("parent").getAsInt();float[] p=floats(row.getAsJsonArray("position")),q=floats(row.getAsJsonArray("rotation")),s=floats(row.getAsJsonArray("scale"));System.arraycopy(p,0,defaults[i],0,3);System.arraycopy(q,0,defaults[i],3,4);System.arraycopy(s,0,defaults[i],7,3);String name=row.get("name").getAsString();if(name.equals("Head"))head=i;for(int k=0;k<3;k++)if(name.equals("Nose_"+k))nose[k]=i;}
  put(mesh,"parents",parents);put(mesh,"defaults",defaults);put(mesh,"headNode",head);put(mesh,"noseNodes",nose);put(mesh,"boneNodes",ints(j.getAsJsonArray("bone_node_indices")));
  JsonArray a=j.getAsJsonArray("bind_matrices");Matrix4f[] bind=new Matrix4f[a.size()];for(int i=0;i<bind.length;i++){float[] input=floats(a.get(i).getAsJsonArray()),matrix=new float[16];for(int r=0;r<4;r++)for(int c=0;c<4;c++)matrix[c*4+r]=input[r*4+c];bind[i]=new Matrix4f().set(matrix);}put(mesh,"bind",bind);
  JsonArray sub=j.getAsJsonArray("submeshes");int[][] triangles=new int[sub.size()][];for(int i=0;i<sub.size();i++)triangles[i]=ints(sub.get(i).getAsJsonObject().getAsJsonArray("triangles"));put(mesh,"triangles",triangles);put(mesh,"colors",new int[sub.size()]);
  Class<?> clip=Class.forName("pl.aridlin.psychiatrykroles.peeb.client.PeebMesh$Clip");Constructor<?> ctor=clip.getDeclaredConstructor(float.class,float.class,Map.class);ctor.setAccessible(true);Map<String,Object> clips=new HashMap<>();
  for(String name:List.of("idle","run","midair")){JsonObject animation=read(root.resolve(name+"-animation-30fps.json"));Map<Integer,float[][]> tracks=new HashMap<>();for(JsonElement e:animation.getAsJsonArray("tracks")){JsonObject track=e.getAsJsonObject();tracks.put(track.get("node").getAsInt(),rows(track.getAsJsonArray("samples")));}clips.put(name,ctor.newInstance(animation.get("duration").getAsFloat(),animation.get("fps").getAsFloat(),tracks));}put(mesh,"clips",clips);return mesh;
 }
 static PeebClient.Motion motion(String clip,float t){return new PeebClient.Motion(t*20,t/0.32f,clip.equals("run")?0.15f:0,clip.equals("midair"),clip.equals("midair")?-0.3f:0,0,Optional.empty());}
 static PeebMesh.Pose pose(PeebMesh mesh,String clip,float t)throws Exception{PeebMesh.Pose p=mesh.pose();put(p,"clip",clip);put(p,"blend",1f);put(p,"age",t*20-1);return p;}
 static Quaternionf worldQuat(PeebMesh mesh,PeebMesh.Pose pose,int node)throws Exception{float[][] local=(float[][])get(pose,"local");int[] parents=(int[])get(mesh,"parents");Quaternionf q=new Quaternionf();for(int i=node;i>=0;i=parents[i]){float[] row=local[i];q.premul(new Quaternionf(row[3],row[4],row[5],row[6]));}return q.normalize();}
 static float angleError(Quaternionf a,Quaternionf b){return 1-Math.abs(a.dot(b));}
 static JsonArray state(PeebMesh.Pose p)throws Exception{JsonArray a=new JsonArray();for(Vector3f v:(Vector3f[])get(p,"skinned")){a.add(v.x);a.add(v.y);a.add(v.z);}return a;}
 public static void main(String[] args)throws Exception{
  PeebMesh mesh=load(Path.of(args[0]));int head=(Integer)get(mesh,"headNode");check(mesh.vertexCount()==500,"original500vertexmodel");check(head==17,"originalheadnode");
  Map<String,JsonArray> neutral=new LinkedHashMap<>();for(String clip:List.of("idle","run","midair"))for(float t:new float[]{0.03f,0.18f,0.4f}){PeebMesh.Pose p=pose(mesh,clip,t);mesh.animate(p,motion(clip,t),0,0,0);neutral.put(clip+":"+t,state(p));}
  if(args.length>1&&args[1].equals("baseline")){System.out.println(new Gson().toJson(neutral));return;}
  JsonObject baseline=read(Path.of(args[1]));for(var entry:neutral.entrySet()){JsonArray ref=baseline.getAsJsonArray(entry.getKey());for(int i=0;i<ref.size();i++)check(Math.abs(ref.get(i).getAsFloat()-entry.getValue().get(i).getAsFloat())<0.00001f,"neutralbindanimationunchanged");}
  Matrix4f[] bind=(Matrix4f[])get(mesh,"bind");float[][] before=new float[bind.length][16];for(int i=0;i<bind.length;i++)bind[i].get(before[i]);
  for(String clip:List.of("idle","run","midair"))for(float t:new float[]{0.03f,0.18f,0.4f})for(float[] aim:new float[][]{{0,0},{45,0},{-45,0},{0,-40},{0,45},{60,35},{-60,-35},{500,500}}){
   PeebMesh.Pose base=pose(mesh,clip,t),look=pose(mesh,clip,t);PeebClient.Motion m=motion(clip,t);mesh.animate(base,m,0,0,0);mesh.animate(look,m,aim[0],aim[1],0);
   Quaternionf reference=worldQuat(mesh,base,head);Quaternionf delta=new Quaternionf().rotationYXZ((float)Math.toRadians(Math.max(-65,Math.min(65,aim[0]))),(float)Math.toRadians(Math.max(-40,Math.min(45,aim[1]))*0.6),0);Quaternionf desired=new Quaternionf(delta).mul(reference).normalize();Quaternionf actual=worldQuat(mesh,look,head);check(angleError(desired,actual)<0.00001,"headmodelspacelookcorrect:"+clip+Arrays.toString(aim));
   Vector3f forward=new Vector3f(0,0,-1);reference.transform(forward);Vector3f expectedDirection=delta.transform(new Vector3f(forward)),direction=actual.transform(new Vector3f(0,0,-1));check(expectedDirection.distance(direction)<0.0001f,"lookdirectioncorrect");
   for(Vector3f vertex:(Vector3f[])get(look,"skinned"))check(Float.isFinite(vertex.x)&&Float.isFinite(vertex.y)&&Float.isFinite(vertex.z),"finitevertex");
   for(Vector3f normal:(Vector3f[])get(look,"skinnedNormal"))check(Float.isFinite(normal.x)&&Float.isFinite(normal.y)&&Float.isFinite(normal.z),"finitenormal");
   float[][] local=(float[][])get(look,"local");int[] parents=(int[])get(mesh,"parents");float[][] baseLocal=(float[][])get(base,"local");for(int node=parents[head];node>=0;node=parents[node])check(Arrays.equals(local[node],baseLocal[node]),"ancestoranimationunchanged");
   for(int repeat=0;repeat<20;repeat++)mesh.animate(look,m,aim[0],aim[1],0);check(angleError(actual,worldQuat(mesh,look,head))<0.00001,"lookdoesnotaccumulate");
  }
  for(int i=0;i<bind.length;i++)check(Arrays.equals(before[i],bind[i].get(new float[16])),"bindmatriximmutable");
  System.out.println("{\"success\":true,\"assertions\":"+checks+",\"model_vertices\":500,\"native_game_launched\":false}");
 }
}
