package pl.aridlin.kukirin;
import java.io.*;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
/** Original clean G2-style game geometry, cached for the active resource manager. */
final class G2Mesh {
 private static net.minecraft.world.phys.Vec3 front,rear,steer;
 private static float[][] parts;private static int[][] colors;private static Object manager;
 static net.minecraft.world.phys.Vec3 pivot(String name){load();return name.equals("rearPivot")?rear:name.equals("steeringPivot")?steer:front;}
 static void invalidate(){parts=null;colors=null;manager=null;front=null;rear=null;steer=null;}
 static void draw(int part,PoseStack pose,VertexConsumer out,int light){load();var data=parts[part];var color=colors[part];for(int i=0;i<color.length;i++){int base=i*12;for(int vertex=0;vertex<4;vertex++){int p=base+3+Math.min(vertex,2)*3;out.addVertex(pose.last().pose(),data[p],data[p+1],data[p+2]).setColor(color[i]).setUv(.5f,.5f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose.last(),data[base],data[base+1],data[base+2]);}}}
 private static void load(){var resources=Minecraft.getInstance().getResourceManager();if(manager==resources&&parts!=null)return;try(var in=new DataInputStream(resources.getResourceOrThrow(ResourceLocation.parse("goplanska_kukirin:models/kukirin_g2.mesh")).open())){int[] counts=new int[4];for(int i=0;i<4;i++){counts[i]=in.readInt();if(counts[i]<0||counts[i]>100000)throw new IOException("Invalid mesh size");}parts=new float[4][];colors=new int[4][];for(int i=0;i<4;i++){parts[i]=new float[counts[i]*12];colors[i]=new int[counts[i]];for(int t=0;t<counts[i];t++){colors[i][t]=in.readInt();for(int v=0;v<12;v++)parts[i][t*12+v]=in.readFloat();}}float unit=1.4f/111f;front=new net.minecraft.world.phys.Vec3(0,10.5*unit,-45.5*unit);rear=new net.minecraft.world.phys.Vec3(0,10.5*unit,44.5*unit);steer=front;
 var rig=resources.getResource(ResourceLocation.parse("goplanska_kukirin:models/scooter-rig.json"));if(rig.isPresent())try(var reader=new java.io.InputStreamReader(rig.get().open(),java.nio.charset.StandardCharsets.UTF_8)){var json=com.google.gson.JsonParser.parseReader(reader).getAsJsonObject();front=readPivot(json,"frontPivot");rear=readPivot(json,"rearPivot");steer=readPivot(json,"steeringPivot");}
 manager=resources;}catch(IOException e){throw new IllegalStateException("Could not load G2-style game mesh",e);}}
 private static net.minecraft.world.phys.Vec3 readPivot(com.google.gson.JsonObject json,String key)throws IOException{var a=json.getAsJsonArray(key);if(a==null||a.size()!=3)throw new IOException("Invalid scooter rig "+key);double x=a.get(0).getAsDouble(),y=a.get(1).getAsDouble(),z=a.get(2).getAsDouble();if(!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z)||Math.max(Math.abs(x),Math.max(Math.abs(y),Math.abs(z)))>4)throw new IOException("Unsafe scooter pivot");return new net.minecraft.world.phys.Vec3(x,y,z);}
}
