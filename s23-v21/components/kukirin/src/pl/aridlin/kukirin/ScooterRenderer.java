package pl.aridlin.kukirin;
import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import net.minecraft.client.model.geom.*;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
public final class ScooterRenderer extends EntityRenderer<Scooter> {
 static final ModelLayerLocation LAYER=new ModelLayerLocation(ResourceLocation.parse("goplanska_kukirin:scooter"),"main");
 static final ResourceLocation TEX=ResourceLocation.withDefaultNamespace("textures/block/white_concrete.png");
 final com.wf.gemrender.gltf.blend.AnimationBlend blend=new com.wf.gemrender.gltf.blend.AnimationBlend(2);
 final ModelPart model;ScooterRenderer(EntityRendererProvider.Context c){super(c);model=c.bakeLayer(LAYER);shadowRadius=.4f;ScooterVisual.MODEL.get();}
 static LayerDefinition layer(){var mesh=new MeshDefinition();var root=mesh.getRoot();var black=CubeListBuilder.create().texOffs(0,0).addBox(-3,-4,-9,6,2,18).addBox(-1,-18,7,2,16,2).addBox(-6,-19,7,12,2,2);root.addOrReplaceChild("black",black,PartPose.ZERO);var accents=CubeListBuilder.create().texOffs(0,0).addBox(-3.5f,-4.2f,-7,7,.7f,14).addBox(-3,-6,-10,6,1,5).addBox(-3,-6,6,6,1,5);root.addOrReplaceChild("orange",accents,PartPose.ZERO);for(int i=0;i<2;i++){var z=i==0?-8:8;root.addOrReplaceChild("wheel"+i,CubeListBuilder.create().texOffs(0,0).addBox(-1.5f,-3,-2,3,6,4).addBox(-1.5f,-2,-3,3,4,6),PartPose.offset(0,-3,z));}return LayerDefinition.create(mesh,16,16);}
 @Override public void render(Scooter e,float yaw,float partial,PoseStack pose,MultiBufferSource buffers,int light){pose.pushPose();pose.translate(0,e.suspension(partial)+e.stepOffset(partial),0);pose.scale(1.25f,1.25f,1.25f);pose.mulPose(Axis.YP.rotationDegrees(180-yaw));pose.mulPose(Axis.ZP.rotationDegrees(e.lean(partial)));var gpu=ScooterVisual.MODEL.get();if(buffers instanceof MultiBufferSource.BufferSource && gpu!=null){blend.clear();blend.override(gpu.animation("steering"),(e.steering(partial)+28)/56,1);blend.override(gpu.animation("wheels"),((e.wheelRotation(partial)%360)+360)%360/360f,1);var evaluated=com.wf.gemrender.render.PoseCache.getInstance().pose(gpu.layout(),gpu.bounds(),gpu.morphs(),blend,0);
var palette=new org.joml.Matrix4f[gpu.jointCount()];for(int i=0;i<palette.length;i++)palette[i]=evaluated.boneMatrix(i,new org.joml.Matrix4f());
com.wf.gemrender.direct.DirectRenderer.submit(gpu,palette,null,pose.last().pose(),light,OverlayTexture.NO_OVERLAY,0xffffffff,com.wf.gemrender.direct.DirectPass.LEVEL,gpu.variant(ScooterDyeRecipe.variant(e.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.FEET))),com.wf.gemrender.texture.Paint.NONE,0,null);
ScooterTrimWorld.queue(e,pose.last().pose(),light,evaluated);pose.popPose();super.render(e,yaw,partial,pose,buffers,light);return;}pose.scale(1,-1,-1);var vertex=buffers.getBuffer(RenderType.entityCutout(TEX));G2Mesh.draw(0,pose,vertex,light);var front=G2Mesh.pivot("frontPivot");var rear=G2Mesh.pivot("rearPivot");var steer=G2Mesh.pivot("steeringPivot");pose.pushPose();pose.translate(steer.x,-steer.y,-steer.z);pose.mulPose(Axis.YP.rotationDegrees(e.steering(partial)));pose.translate(-steer.x,steer.y,steer.z);G2Mesh.draw(1,pose,vertex,light);pose.translate(front.x,-front.y,-front.z);pose.mulPose(Axis.XP.rotationDegrees(e.wheelRotation(partial)));pose.translate(-front.x,front.y,front.z);G2Mesh.draw(2,pose,vertex,light);pose.popPose();pose.pushPose();pose.translate(rear.x,-rear.y,-rear.z);pose.mulPose(Axis.XP.rotationDegrees(e.wheelRotation(partial)));pose.translate(-rear.x,rear.y,rear.z);G2Mesh.draw(3,pose,vertex,light);pose.popPose();pose.popPose();super.render(e,yaw,partial,pose,buffers,light);}
 @Override public ResourceLocation getTextureLocation(Scooter e){return TEX;}
 @EventBusSubscriber(modid="goplanska_kukirin",value=Dist.CLIENT,bus=EventBusSubscriber.Bus.MOD)
 public static class Events {
 @SubscribeEvent public static void itemColors(net.neoforged.neoforge.client.event.RegisterColorHandlersEvent.Item event){event.register((stack,index)->{int variant=ScooterDyeRecipe.variant(stack);return 0xff000000|(variant==0?0xf9801d:net.minecraft.world.item.DyeColor.byId(variant-1).getTextureDiffuseColor());},Kukirin.ITEM.get());}
 @SubscribeEvent public static void reload(net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent e){e.registerReloadListener((net.minecraft.server.packs.resources.ResourceManagerReloadListener)manager->{G2Mesh.invalidate();ScooterTrim.invalidate();});}
 @SubscribeEvent public static void setup(net.neoforged.fml.event.lifecycle.FMLClientSetupEvent e){e.enqueueWork(()->dev.engine_room.flywheel.lib.visualization.SimpleEntityVisualizer.builder(Kukirin.SCOOTER.get()).factory(ScooterVisual::new).skipVanillaRender(entity->true).apply());}
@SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers e){e.registerEntityRenderer(Kukirin.SCOOTER.get(),ScooterRenderer::new);}@SubscribeEvent public static void layers(EntityRenderersEvent.RegisterLayerDefinitions e){e.registerLayerDefinition(LAYER,ScooterRenderer::layer);}}
}
