package pl.aridlin.kukirin;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import com.mojang.blaze3d.vertex.*;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.math.Axis;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.api.distmarker.Dist;
@EventBusSubscriber(modid="goplanska_kukirin",value=Dist.CLIENT)
public final class ScooterTrim {
 private static final java.util.Map<String,ResourceLocation> textures=new java.util.HashMap<>();
 private static ResourceLocation texture(Scooter s){var trim=s.getItemBySlot(EquipmentSlot.FEET).get(DataComponents.TRIM);if(trim==null)return null;var pattern=trim.pattern().value().assetId();String material=trim.material().value().assetName(),key=pattern+"/"+material;if(textures.containsKey(key))return textures.get(key);var mc=Minecraft.getInstance();ResourceLocation result=null;try{var manager=mc.getResourceManager();try(var input=manager.getResourceOrThrow(ResourceLocation.fromNamespaceAndPath(pattern.getNamespace(),"textures/trims/models/armor/"+pattern.getPath()+".png")).open();var baseInput=manager.getResourceOrThrow(ResourceLocation.withDefaultNamespace("textures/trims/color_palettes/trim_palette.png")).open();var colorInput=manager.getResourceOrThrow(palette(trim,manager)).open();var base=NativeImage.read(baseInput);var color=NativeImage.read(colorInput)){var original=NativeImage.read(input);var image=new NativeImage(12,12,true);int sx=Math.max(1,original.getWidth()/64),sy=Math.max(1,original.getHeight()/32);int[] front=region(original,sx,sy,new int[]{20,20,8,12});int[] side=region(original,sx,sy,new int[]{16,20,4,12});for(int x=0;x<12;x++)for(int y=0;y<12;y++){int[] r=x<8?front:side;int width=x<8?8:4,column=x<8?x:x-8;image.setPixelRGBA(x,y,original.getPixelRGBA((r[0]+column*r[2]/width)*sx,(r[1]+y*r[3]/12)*sy));}original.close();var palette=new java.util.HashMap<Integer,Integer>();for(int x=0;x<Math.min(base.getWidth(),color.getWidth());x++)palette.put(base.getPixelRGBA(x,0)&0xffffff,color.getPixelRGBA(x,0)&0xffffff);for(int x=0;x<image.getWidth();x++)for(int y=0;y<image.getHeight();y++){int pixel=image.getPixelRGBA(x,y);image.setPixelRGBA(x,y,(pixel&0xff000000)|remap(pixel&0xffffff,palette));}result=mc.getTextureManager().register("scooter_trim_"+textures.size(),new DynamicTexture(image));}}catch(Exception ex){org.slf4j.LoggerFactory.getLogger("ScooterTrim").warn("Cannot load scooter trim {}",key,ex);}textures.put(key,result);return result;}
 private static int[] region(NativeImage image,int sx,int sy,int[] preferred){for(int[] r:new int[][]{preferred,{44,20,4,12},{40,8,8,8},{32,8,8,8}}){for(int x=0;x<r[2];x++)for(int y=0;y<r[3];y++)if((image.getPixelRGBA((r[0]+x)*sx,(r[1]+y)*sy)>>>24)!=0)return r;}return preferred;}
 private static ResourceLocation palette(net.minecraft.world.item.armortrim.ArmorTrim trim,net.minecraft.server.packs.resources.ResourceManager manager){
  var vanilla=ResourceLocation.withDefaultNamespace("textures/trims/color_palettes/"+trim.material().value().assetName()+".png");
  if(manager.getResource(vanilla).isPresent())return vanilla;
  var id=trim.material().unwrapKey().orElseThrow().location();
  return ResourceLocation.fromNamespaceAndPath(id.getNamespace(),"textures/trims/color_palettes/"+id.getPath()+".png");
 }
 private static int remap(int rgb,java.util.Map<Integer,Integer> palette){var exact=palette.get(rgb);if(exact!=null)return exact;int nearest=rgb,best=Integer.MAX_VALUE;for(var e:palette.entrySet()){int base=e.getKey(),r=(rgb&255)-(base&255),g=((rgb>>8)&255)-((base>>8)&255),b=((rgb>>16)&255)-((base>>16)&255),distance=r*r+g*g+b*b;if(distance<best){best=distance;nearest=e.getValue();}}return nearest;}
 public static void draw(Scooter s,PoseStack pose,net.minecraft.client.renderer.MultiBufferSource source,int light,float partial){var texture=texture(s);if(texture==null)return;var v=source.getBuffer(RenderType.entityCutout(texture));G2Mesh.trim(0,pose,v,light);var pivot=G2Mesh.pivot("steeringPivot");pose.pushPose();pose.translate(pivot.x,pivot.y,pivot.z);pose.mulPose(Axis.YP.rotationDegrees(s.steering(partial)));pose.translate(-pivot.x,-pivot.y,-pivot.z);G2Mesh.trim(1,pose,v,light);pose.popPose();}
 @SubscribeEvent public static void render(net.neoforged.neoforge.client.event.RenderLevelStageEvent e){if(e.getStage()!=net.neoforged.neoforge.client.event.RenderLevelStageEvent.Stage.AFTER_ENTITIES)return;var mc=Minecraft.getInstance();if(mc.level==null)return;float partial=e.getPartialTick().getGameTimeDeltaPartialTick(false);var camera=e.getCamera().getPosition();var buffers=mc.renderBuffers().bufferSource();for(var entity:mc.level.entitiesForRendering())if(entity instanceof Scooter s&&s.distanceToSqr(camera)<64*64&&s.getItemBySlot(EquipmentSlot.FEET).has(DataComponents.TRIM)){var pose=e.getPoseStack();pose.pushPose();pose.translate(net.minecraft.util.Mth.lerp(partial,s.xOld,s.getX())-camera.x,net.minecraft.util.Mth.lerp(partial,s.yOld,s.getY())-camera.y+s.suspension(partial)+s.stepOffset(partial),net.minecraft.util.Mth.lerp(partial,s.zOld,s.getZ())-camera.z);pose.scale(1.25f,1.25f,1.25f);pose.mulPose(Axis.YP.rotationDegrees(180-net.minecraft.util.Mth.rotLerp(partial,s.yRotO,s.getYRot())));pose.mulPose(Axis.ZP.rotationDegrees(s.lean(partial)));draw(s,pose,buffers,net.minecraft.client.renderer.LevelRenderer.getLightColor(mc.level,s.blockPosition()),partial);pose.popPose();}buffers.endBatch();}
 public static void invalidate(){for(var t:textures.values())if(t!=null)Minecraft.getInstance().getTextureManager().release(t);textures.clear();}
 /** Use the GPU's evaluated node palette, including its animation time bucket. */
 public static void drawPosed(Scooter s,PoseStack pose,net.minecraft.client.renderer.MultiBufferSource source,int light,com.wf.gemrender.render.PoseCache.Pose evaluated){
  var texture=texture(s);if(texture==null)return;var v=source.getBuffer(RenderType.entityCutout(texture));
  pose.pushPose();pose.mulPose(evaluated.boneMatrix("body",new org.joml.Matrix4f()));G2Mesh.trim(0,pose,v,light);pose.popPose();
  var pivot=G2Mesh.pivot("steeringPivot");pose.pushPose();pose.mulPose(evaluated.boneMatrix("stem",new org.joml.Matrix4f()));
  // CPU surface triangles are in bind-world space; the GLB stem is pivot-local.
  pose.translate(-pivot.x,-pivot.y,-pivot.z);G2Mesh.trim(1,pose,v,light);pose.popPose();
 }

}
