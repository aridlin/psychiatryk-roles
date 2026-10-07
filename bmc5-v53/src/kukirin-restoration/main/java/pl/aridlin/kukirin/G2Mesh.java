package pl.aridlin.kukirin;
import java.io.*;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
/** Production licensed G2 geometry, UV atlas and vertex normals; four cached GPU parts. */
final class G2Mesh {
 private static net.minecraft.world.phys.Vec3 front,rear,steer,axis;
 private static float[][] parts,uvNormals,trimData;private static int[][] colors;private static Object manager;private static final VertexBuffer[] gpu=new VertexBuffer[4];static net.minecraft.client.renderer.ShaderInstance shader;
 static net.minecraft.world.phys.Vec3 pivot(String name){load();return name.equals("rearPivot")?rear:name.equals("steeringPivot")?steer:front;}
 static net.minecraft.world.phys.Vec3 axis(){load();return axis;}
 static void invalidate(){parts=null;uvNormals=null;trimData=null;colors=null;manager=null;front=null;rear=null;steer=null;axis=null;var old=gpu.clone();java.util.Arrays.fill(gpu,null);com.mojang.blaze3d.pipeline.RenderCall dispose=()->{for(var buffer:old)if(buffer!=null)buffer.close();};if(com.mojang.blaze3d.systems.RenderSystem.isOnRenderThread())dispose.execute();else com.mojang.blaze3d.systems.RenderSystem.recordRenderCall(dispose);}
 private static void disposeGpu(){for(int i=0;i<gpu.length;i++)if(gpu[i]!=null){gpu[i].close();gpu[i]=null;}}
 static void draw(int part,PoseStack pose,VertexConsumer out,int light){draw(part,pose,out,light,0);}
 static void draw(int part,PoseStack pose,VertexConsumer out,int light,int variant){load();var data=parts[part];var details=uvNormals[part];for(int triangle=0;triangle<colors[part].length;triangle++){int base=triangle*12;for(int vertex=0;vertex<4;vertex++){int corner=Math.min(vertex,2),p=base+3+corner*3,a=(triangle*3+corner)*5;out.addVertex(pose.last().pose(),data[p],data[p+1],data[p+2]).setColor(-1).setUv(details[a],details[a+1]).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose.last(),details[a+2],details[a+3],details[a+4]);}}}
 static boolean cachedAvailable(){return shader!=null;}
 static void drawCached(int part,PoseStack pose,int light,ResourceLocation texture){
  load();com.mojang.blaze3d.systems.RenderSystem.assertOnRenderThread();
  if(gpu[part]==null){
   var data=parts[part];var details=uvNormals[part];var format=com.mojang.blaze3d.vertex.DefaultVertexFormat.NEW_ENTITY;
   try(var memory=new com.mojang.blaze3d.vertex.ByteBufferBuilder(colors[part].length*4*format.getVertexSize())){
    var builder=new com.mojang.blaze3d.vertex.BufferBuilder(memory,com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS,format);
    for(int triangle=0;triangle<colors[part].length;triangle++)for(int vertex=0;vertex<4;vertex++){int corner=Math.min(vertex,2),p=triangle*12+3+corner*3,a=(triangle*3+corner)*5;builder.addVertex(data[p],data[p+1],data[p+2]).setColor(-1).setUv(details[a],details[a+1]).setOverlay(OverlayTexture.NO_OVERLAY).setLight(0).setNormal(details[a+2],details[a+3],details[a+4]);}
    var buffer=new VertexBuffer(VertexBuffer.Usage.STATIC);buffer.bind();try{buffer.upload(builder.buildOrThrow());gpu[part]=buffer;}catch(RuntimeException failure){buffer.close();throw failure;}finally{VertexBuffer.unbind();}
   }
  }
  var type=net.minecraft.client.renderer.RenderType.entityCutoutNoCull(texture);type.setupRenderState();
  try{
   com.mojang.blaze3d.systems.RenderSystem.setShader(()->shader);
   shader.safeGetUniform("ScooterLight").set((light&65535)/16f,((light>>>16)&65535)/16f);
   shader.safeGetUniform("ScooterNormalMat").set(pose.last().normal());
   var modelView=new org.joml.Matrix4f(com.mojang.blaze3d.systems.RenderSystem.getModelViewMatrix()).mul(pose.last().pose());
   gpu[part].bind();gpu[part].drawWithShader(modelView,com.mojang.blaze3d.systems.RenderSystem.getProjectionMatrix(),shader);
  }finally{VertexBuffer.unbind();type.clearRenderState();}
 }

 private static int accentColor(int color,int variant){if(variant<=0||variant>16)return color;int r=(color>>>16)&255,g=(color>>>8)&255,b=color&255;if(r<=100||g<=35||g>=r*.8||b>=g*.45)return color;int dye=net.minecraft.world.item.DyeColor.byId(variant-1).getTextureDiffuseColor();return (color&0xff000000)|((((dye>>>16)&255)*r/255)<<16)|((((dye>>>8)&255)*r/255)<<8)|((dye&255)*r/255);}
 /** Trim follows the actual deck and steering-column surface triangles, with a small normal bias. */
 static void trim(int part,PoseStack pose,VertexConsumer out,int light){load();var data=trimData[part];for(int i=0;i<data.length;i+=8)out.addVertex(pose.last().pose(),data[i],data[i+1],data[i+2]).setColor(-1).setUv(data[i+3],data[i+4]).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose.last(),data[i+5],data[i+6],data[i+7]);}
 private static void buildTrim(){
  trimData=new float[4][];
  for(int part=0;part<4;part++){var values=new java.util.ArrayList<Float>();var data=parts[part];if(part<2)for(int triangle=0;triangle<colors[part].length;triangle++){
   int b=triangle*12;double x=0,y=0,z=0;for(int k=0;k<3;k++){x+=data[b+3+k*3]/3.;y-=data[b+4+k*3]/3.;z-=data[b+5+k*3]/3.;}
   float nx=data[b],ny=-data[b+1],nz=-data[b+2];boolean selected=part==0?ny>.8&&y>.15&&y<.198&&z>-.22&&z<.40&&Math.abs(x-.0048)<.098:Math.abs(nz)>.65&&y>.38&&y<1.03&&Math.abs(x-.0048)<.038;
   if(!selected)continue;
   for(int k=0;k<4;k++){int i=b+3+Math.min(k,2)*3;float px=data[i],py=-data[i+1],pz=-data[i+2];float u=part==0?Math.clamp((px+.094f)/.197f,0,1)*8/12f:8/12f+Math.clamp((px+.033f)/.076f,0,1)*4/12f;float v=part==0?Math.clamp((pz+.22f)/.62f,0,1):1-Math.clamp((py-.38f)/.65f,0,1);for(float value:new float[]{px+nx*.0015f,py+ny*.0015f,pz+nz*.0015f,u,v,nx,ny,nz})values.add(value);}
  }trimData[part]=new float[values.size()];for(int i=0;i<values.size();i++)trimData[part][i]=values.get(i);}
 }

 private static void load(){var resources=Minecraft.getInstance().getResourceManager();if(manager==resources&&parts!=null)return;disposeGpu();try(var in=new DataInputStream(resources.getResourceOrThrow(ResourceLocation.parse("goplanska_kukirin:models/kukirin_g2.mesh")).open())){int[] counts=new int[4];for(int i=0;i<4;i++){counts[i]=in.readInt();if(counts[i]<0||counts[i]>100000)throw new IOException("Invalid mesh size");}parts=new float[4][];colors=new int[4][];for(int i=0;i<4;i++){parts[i]=new float[counts[i]*12];colors[i]=new int[counts[i]];for(int t=0;t<counts[i];t++){colors[i][t]=in.readInt();for(int v=0;v<12;v++)parts[i][t*12+v]=in.readFloat();}}float unit=1.4f/111f;front=new net.minecraft.world.phys.Vec3(0,10.5*unit,-45.5*unit);rear=new net.minecraft.world.phys.Vec3(0,10.5*unit,44.5*unit);steer=front;axis=new net.minecraft.world.phys.Vec3(0,1,0);
 var rig=resources.getResource(ResourceLocation.parse("goplanska_kukirin:models/scooter-rig.json"));if(rig.isPresent())try(var reader=new java.io.InputStreamReader(rig.get().open(),java.nio.charset.StandardCharsets.UTF_8)){var json=com.google.gson.JsonParser.parseReader(reader).getAsJsonObject();front=readPivot(json,"frontPivot");rear=readPivot(json,"rearPivot");steer=readPivot(json,"steeringPivot");if(json.has("steeringAxis")){axis=readPivot(json,"steeringAxis");if(Math.abs(axis.length()-1)>.00001||axis.y<.5)throw new IOException("Unsafe scooter steering axis");}}
 loadUvNormals(resources,counts);buildTrim();manager=resources;}catch(IOException e){throw new IllegalStateException("Could not load G2-style game mesh",e);}}
 private static void loadUvNormals(net.minecraft.server.packs.resources.ResourceManager resources,int[] counts)throws IOException{
  try(var in=new DataInputStream(resources.getResourceOrThrow(ResourceLocation.parse("goplanska_kukirin:models/kukirin_g2.uvnorm")).open())){
   if(in.readInt()!=0x4b553031)throw new IOException("Invalid production model UV format");
   for(int count:counts)if(in.readInt()!=count)throw new IOException("Production UV and geometry counts differ");
   uvNormals=new float[4][];
   for(int part=0;part<4;part++){uvNormals[part]=new float[counts[part]*15];for(int i=0;i<uvNormals[part].length;i++){float value=in.readFloat();if(!Float.isFinite(value))throw new IOException("Non-finite production model UV/normal");uvNormals[part][i]=value;}}
   if(in.read()!=-1)throw new IOException("Trailing production model UV data");
  }
 }
 private static net.minecraft.world.phys.Vec3 readPivot(com.google.gson.JsonObject json,String key)throws IOException{var a=json.getAsJsonArray(key);if(a==null||a.size()!=3)throw new IOException("Invalid scooter rig "+key);double x=a.get(0).getAsDouble(),y=a.get(1).getAsDouble(),z=a.get(2).getAsDouble();if(!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z)||Math.max(Math.abs(x),Math.max(Math.abs(y),Math.abs(z)))>4)throw new IOException("Unsafe scooter pivot");return new net.minecraft.world.phys.Vec3(x,y,z);}
}
