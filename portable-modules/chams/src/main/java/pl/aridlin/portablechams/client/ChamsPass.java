package pl.aridlin.portablechams.client;
import com.mojang.blaze3d.pipeline.*;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import org.lwjgl.opengl.*;
import org.joml.Matrix4f;
import java.util.*;
import net.minecraft.world.phys.*;
public final class ChamsPass {
 static ShaderInstance mask,itemMask,occlusionMask,entityOcclusionMask,composite;
 // Iris may replace the world buffer source with a deferred batch whose
 // endBatch(RenderType) is a no-op. Masks must finish on their own framebuffer.
 private static MaskBuffers maskBuffers;
 static MaskBuffers buffers(){if(maskBuffers==null)maskBuffers=new MaskBuffers();return maskBuffers;}
 static void discardPendingBuffers(){if(maskBuffers!=null&&maskBuffers.pending()){maskBuffers.close();maskBuffers=null;}}
 static final class MaskBuffers extends MultiBufferSource.BufferSource implements AutoCloseable {
  MaskBuffers(){super(new ByteBufferBuilder(4096),new LinkedHashMap<>());}
  boolean pending(){return !startedBuilders.isEmpty();}
  @Override public VertexConsumer getBuffer(RenderType type){fixedBuffers.computeIfAbsent(type,t->new ByteBufferBuilder(t.bufferSize()));return super.getBuffer(type);}
  @Override public void close(){startedBuilders.clear();lastSharedType=null;for(var buffer:fixedBuffers.values())buffer.close();fixedBuffers.clear();sharedBuffer.close();}
 }

 static final Map<UUID,Float> entityKeys=new HashMap<>();
 static TextureTarget entityCover;static long frame,entityCoverFrame=-1;
 static net.minecraft.client.renderer.culling.Frustum coverFrustum;
 /** Diagnostic switch; normal rendering skips geometry outside the main camera view. */
 public static boolean cullOffscreenCover=true;
 public static int lastCoverCandidates,lastCoverRendered;
 static boolean visibleCover(net.minecraft.world.entity.Entity entity){
  // Keep a generous margin for animation, model layers and interpolated movement.
  // Frustum rejection is geometric only: wall-occluded entities still obscure chams.
  return !cullOffscreenCover||coverFrustum==null||coverFrustum.isVisible(entity.getBoundingBoxForCulling().inflate(2).minmax(entity.getBoundingBoxForCulling().move(entity.xOld-entity.getX(),entity.yOld-entity.getY(),entity.zOld-entity.getZ()).inflate(2)));
 }
 static TextureTarget occlusion;static Matrix4f worldFromView;
 static TextureTarget target;
 public static boolean cropComposite=true;
 static float cropX=-1,cropY=-1,cropRight=1,cropTop=1;
 static void crop(Matrix4f matrix,Vec3 camera,AABB body){
  cropX=-1;cropY=-1;cropRight=1;cropTop=1;if(!cropComposite)return;
  float x=1,y=1,right=-1,top=-1;
  for(int i=0;i<8;i++){var v=matrix.transform(new org.joml.Vector4f((float)(((i&1)==0?body.minX:body.maxX)-camera.x),(float)(((i&2)==0?body.minY:body.maxY)-camera.y),(float)(((i&4)==0?body.minZ:body.maxZ)-camera.z),1));if(v.w<=.01f)return;float a=v.x/v.w,b=v.y/v.w;if(!Float.isFinite(a)||!Float.isFinite(b))return;x=Math.min(x,a);y=Math.min(y,b);right=Math.max(right,a);top=Math.max(top,b);}
  var w=Minecraft.getInstance().getWindow();float padX=8f/w.getWidth(),padY=8f/w.getHeight();cropX=Math.max(-1,x-padX);cropY=Math.max(-1,y-padY);cropRight=Math.min(1,right+padX);cropTop=Math.min(1,top+padY);
 }
 static int coverTexture;
 static java.util.Set<net.minecraft.core.BlockPos> blockExclusions=java.util.Set.of();
 record Cache(Vec3 camera,AABB body,long time,PixelCover.Data data){}
 static final Map<UUID,Cache> caches=new HashMap<>();
 static final RenderType TYPE=RenderType.create("portable_chams_position_mask",DefaultVertexFormat.NEW_ENTITY,VertexFormat.Mode.QUADS,4096,false,true,RenderType.CompositeState.builder().setShaderState(new RenderStateShard.ShaderStateShard(()->mask)).setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST).setWriteMaskState(RenderStateShard.COLOR_DEPTH_WRITE).setCullState(RenderStateShard.NO_CULL).createCompositeState(false));
 static final Map<RenderType,RenderType> itemTypes=new java.util.IdentityHashMap<>();
 static final Map<RenderType,RenderType> occlusionTypes=new java.util.IdentityHashMap<>();
 static final Map<RenderType,RenderType> entityCoverTypes=new java.util.IdentityHashMap<>();
 static final Map<RenderType,Float> cutoffs=new java.util.IdentityHashMap<>();
 static RenderType itemType(RenderType original){return textureType(original,false,false);}
 static RenderType textureType(RenderType original,boolean cover){return textureType(original,cover,false);}
 static RenderType textureType(RenderType original,boolean cover,boolean entity){return (entity?entityCoverTypes:cover?occlusionTypes:itemTypes).computeIfAbsent(original,type->{
  RenderStateShard.EmptyTextureStateShard textureState=new RenderStateShard.TextureStateShard(net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS,false,false);
  RenderStateShard.CullStateShard cull=cover?RenderStateShard.CULL:RenderStateShard.NO_CULL;
  try{var sf=type.getClass().getDeclaredField("state");sf.setAccessible(true);var state=sf.get(type);var tf=state.getClass().getDeclaredField("textureState");tf.setAccessible(true);textureState=(RenderStateShard.EmptyTextureStateShard)tf.get(state);if(cover){var cf=state.getClass().getDeclaredField("cullState");cf.setAccessible(true);cull=(RenderStateShard.CullStateShard)cf.get(state);}}catch(ReflectiveOperationException ignored){}
  var mapped=RenderType.create(cover?"texture_occlusion_mask":"item_position_mask",cover&&!entity?DefaultVertexFormat.BLOCK:DefaultVertexFormat.NEW_ENTITY,VertexFormat.Mode.QUADS,4096,false,true,RenderType.CompositeState.builder().setShaderState(new RenderStateShard.ShaderStateShard(()->cover?(entity?entityOcclusionMask:occlusionMask):itemMask)).setTextureState(textureState).setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST).setWriteMaskState(RenderStateShard.COLOR_DEPTH_WRITE).setCullState(cull).createCompositeState(false));
  float cutoff=entity?(type.toString().startsWith("entity_solid")?0f:.1f):type==RenderType.cutoutMipped()?.5f:type==RenderType.cutout()?.1f:0f;cutoffs.put(mapped,cutoff);return mapped;
 });}
 static void begin(Matrix4f worldFromView){RenderSystem.activeTexture(GL13.GL_TEXTURE0);ChamsPass.worldFromView=new Matrix4f(worldFromView);var mc=Minecraft.getInstance();var main=mc.getMainRenderTarget();if(target==null||target.width!=main.width||target.height!=main.height){if(target!=null)target.destroyBuffers();target=new TextureTarget(main.width,main.height,true,Minecraft.ON_OSX);RenderSystem.bindTexture(target.getColorTextureId());GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,GL30.GL_RGBA32F,main.width,main.height,0,GL11.GL_RGBA,GL11.GL_FLOAT,(java.nio.FloatBuffer)null);target.setClearColor(0,0,0,0);target.checkStatus();}target.clear(Minecraft.ON_OSX);target.bindWrite(true);mask.getUniform("WorldFromView").set(worldFromView);if(itemMask!=null)itemMask.getUniform("WorldFromView").set(worldFromView);}
 static void end(UUID id,int color,float thickness,Vec3 camera,AABB body,org.joml.Vector2f anchor){end(id,color,thickness,camera,body,anchor,true);}
 static void end(UUID id,int color,float thickness,Vec3 camera,AABB body,org.joml.Vector2f anchor,boolean fill){var mc=Minecraft.getInstance();mc.getMainRenderTarget().bindWrite(true);
  long now=System.nanoTime();if(caches.size()>256)caches.entrySet().removeIf(e->now-e.getValue().time()>2_000_000_000L);var cached=fill?caches.get(id):new Cache(camera,body,now,new PixelCover.Data(new float[8],0,true,List.of()));if(cached==null||now-cached.time()>150_000_000L||cached.camera().distanceToSqr(camera)>.01||cached.body().getCenter().distanceToSqr(body.getCenter())>.01){cached=new Cache(camera,body,now,PixelCover.gather(mc.level,camera,body,blockExclusions));caches.put(id,cached);}var data=cached.data();drawTexturedCover(data,camera,id,body,fill);mc.getMainRenderTarget().bindWrite(true);
  // Cached geometry is translated to the current camera without quantizing coordinates.
  if(coverTexture==0)coverTexture=GL11.glGenTextures();RenderSystem.activeTexture(GL13.GL_TEXTURE0);RenderSystem.bindTexture(coverTexture);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_NEAREST);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_NEAREST);GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,GL30.GL_RGBA32F,2,Math.max(1,data.count()),0,GL11.GL_RGBA,GL11.GL_FLOAT,data.boxes());
  composite.getUniform("DotSpacing").set((float)ChamsConfig.get().dotSpacing());composite.getUniform("OutlineRadius").set(ChamsConfig.get().outlineRadius());composite.getUniform("Opacity").set((float)ChamsConfig.get().opacity());composite.getUniform("TargetEntityKey").set(entityKeys.getOrDefault(id,0f));composite.getUniform("HalftoneEnabled").set(fill?1:0);composite.setSampler("PositionMask",target.getColorTextureId());composite.setSampler("TextureCover",occlusion.getColorTextureId());composite.setSampler("EntityCover",entityCover.getColorTextureId());composite.setSampler("CoverBoxes",coverTexture);composite.getUniform("BoxCount").set(data.count());composite.getUniform("CoverOffset").set((float)(cached.camera().x-camera.x),(float)(cached.camera().y-camera.y),(float)(cached.camera().z-camera.z));composite.getUniform("FallbackCover").set(0f); /* Never fill texture holes using a whole-entity fallback. */composite.getUniform("TeamColor").set(((color>>16)&255)/255f,((color>>8)&255)/255f,(color&255)/255f,1);composite.getUniform("PatternAnchor").set(anchor.x,anchor.y);
  RenderSystem.disableDepthTest();RenderSystem.depthMask(false);RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();RenderSystem.setShader(()->composite);var b=Tesselator.getInstance().begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_TEX);b.addVertex(cropX,cropY,0).setUv((cropX+1)*.5f,(cropY+1)*.5f);b.addVertex(cropRight,cropY,0).setUv((cropRight+1)*.5f,(cropY+1)*.5f);b.addVertex(cropRight,cropTop,0).setUv((cropRight+1)*.5f,(cropTop+1)*.5f);b.addVertex(cropX,cropTop,0).setUv((cropX+1)*.5f,(cropTop+1)*.5f);BufferUploader.drawWithShader(b.buildOrThrow());RenderSystem.disableBlend();RenderSystem.depthMask(true);RenderSystem.enableDepthTest();
 }
 static void drawTexturedCover(PixelCover.Data data,Vec3 camera,UUID id,AABB body,boolean fill){RenderSystem.activeTexture(GL13.GL_TEXTURE0);var mc=Minecraft.getInstance();var main=mc.getMainRenderTarget();if(occlusion==null||occlusion.width!=main.width||occlusion.height!=main.height){if(occlusion!=null)occlusion.destroyBuffers();occlusion=new TextureTarget(main.width,main.height,true,Minecraft.ON_OSX);RenderSystem.bindTexture(occlusion.getColorTextureId());GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,GL30.GL_RGBA32F,main.width,main.height,0,GL11.GL_RGBA,GL11.GL_FLOAT,(java.nio.FloatBuffer)null);occlusion.setClearColor(0,0,0,0);occlusion.checkStatus();}occlusion.clear(Minecraft.ON_OSX);occlusion.bindWrite(true);if(occlusionMask==null||!fill){drawSharedEntityCover(camera,null);return;}occlusionMask.getUniform("WorldFromView").set(worldFromView);var pose=new PoseStack();pose.last().pose().set(new Matrix4f(RenderSystem.getModelViewMatrix()).invert().mul(new Matrix4f(worldFromView).invert()));var used=new java.util.HashSet<RenderType>();MultiBufferSource source=type->{var mapped=textureType(type,true);used.add(mapped);return buffers().getBuffer(mapped);};for(var pos:data.textured()){pose.pushPose();pose.translate(pos.getX()-camera.x,pos.getY()-camera.y,pos.getZ()-camera.z);var state=mc.level.getBlockState(pos);var modelData=mc.level.getModelDataManager().getAt(pos);if(modelData==null)modelData=net.neoforged.neoforge.client.model.data.ModelData.EMPTY;var model=mc.getBlockRenderer().getBlockModel(state);var random=net.minecraft.util.RandomSource.create(state.getSeed(pos));for(var layer:model.getRenderTypes(state,random,modelData)){var mapped=textureType(layer,true);used.add(mapped);mc.getBlockRenderer().renderBatched(state,pos,mc.level,pose,buffers().getBuffer(mapped),true,random,modelData,layer);}pose.popPose();}flushCover(used,false);drawSharedEntityCover(camera,pose);}
 static void flushCover(java.util.Set<RenderType> used,boolean entity){var shader=entity?entityOcclusionMask:occlusionMask;for(var type:used){shader.getUniform("AlphaCutoff").set(cutoffs.getOrDefault(type,.1f));buffers().endBatch(type);}}
 @SuppressWarnings({"rawtypes","unchecked"})
 static void drawSharedEntityCover(Vec3 camera,PoseStack pose){RenderSystem.activeTexture(GL13.GL_TEXTURE0);var mc=Minecraft.getInstance();var main=mc.getMainRenderTarget();if(entityCover==null||entityCover.width!=main.width||entityCover.height!=main.height){if(entityCover!=null)entityCover.destroyBuffers();entityCover=new TextureTarget(main.width,main.height,true,Minecraft.ON_OSX);RenderSystem.bindTexture(entityCover.getColorTextureId());GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,GL30.GL_RGBA32F,main.width,main.height,0,GL11.GL_RGBA,GL11.GL_FLOAT,(java.nio.FloatBuffer)null);entityCover.setClearColor(0,0,0,0);entityCoverFrame=-1;}if(entityCoverFrame==frame)return;entityCoverFrame=frame;entityKeys.clear();entityCover.clear(Minecraft.ON_OSX);entityCover.bindWrite(true);if(entityOcclusionMask==null)return;if(pose==null){pose=new PoseStack();pose.last().pose().set(new Matrix4f(RenderSystem.getModelViewMatrix()).invert().mul(new Matrix4f(worldFromView).invert()));}entityOcclusionMask.getUniform("WorldFromView").set(worldFromView);float partial=mc.getTimer().getGameTimeDeltaPartialTick(false);double maxDistance=ChamsConfig.get().maxDistance();var entities=new java.util.ArrayList<net.minecraft.world.entity.Entity>();lastCoverCandidates=0;for(var entity:mc.level.entitiesForRendering()){if(entity==mc.getCameraEntity()||entity.isRemoved()||entity.position().distanceTo(camera)>maxDistance)continue;lastCoverCandidates++;if(!visibleCover(entity))continue;entities.add(entity);}entities.sort(java.util.Comparator.comparingDouble(x->x.position().distanceToSqr(camera)));if(entities.size()>64)entities.subList(64,entities.size()).clear();lastCoverRendered=entities.size();var used=new java.util.HashSet<RenderType>();MultiBufferSource source=type->{if(type.format()!=DefaultVertexFormat.NEW_ENTITY||type.toString().toLowerCase(java.util.Locale.ROOT).contains("glint"))return ChamsEffect.EMPTY;var mapped=textureType(type,true,true);used.add(mapped);return buffers().getBuffer(mapped);};for(var entity:entities){float key=entityKeys.size()+1;entityKeys.put(entity.getUUID(),key);entityOcclusionMask.getUniform("EntityKey").set(key);var p=new Vec3(net.minecraft.util.Mth.lerp(partial,entity.xOld,entity.getX()),net.minecraft.util.Mth.lerp(partial,entity.yOld,entity.getY()),net.minecraft.util.Mth.lerp(partial,entity.zOld,entity.getZ()));pose.pushPose();pose.translate(p.x-camera.x,p.y-camera.y,p.z-camera.z);var renderer=mc.getEntityRenderDispatcher().getRenderer(entity);renderer.render(entity,net.minecraft.util.Mth.rotLerp(partial,entity.yRotO,entity.getYRot()),partial,pose,source,0xf000f0);pose.popPose();flushCover(used,true);used.clear();}}
 static void release(){clear();if(maskBuffers!=null){maskBuffers.close();maskBuffers=null;}for(var buffer:new TextureTarget[]{target,occlusion,entityCover})if(buffer!=null)buffer.destroyBuffers();target=null;occlusion=null;entityCover=null;if(coverTexture!=0)GL11.glDeleteTextures(coverTexture);coverTexture=0;itemTypes.clear();occlusionTypes.clear();entityCoverTypes.clear();cutoffs.clear();}
 static void clear(){caches.clear();entityKeys.clear();entityCoverFrame=-1;coverFrustum=null;}
}
