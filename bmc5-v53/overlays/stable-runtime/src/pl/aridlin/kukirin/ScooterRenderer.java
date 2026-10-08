package pl.aridlin.kukirin;
import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import pl.aridlin.psychiatrykroles.runtime.client.ScooterVisuals;
import pl.aridlin.psychiatrykroles.runtime.client.VisualExpressions;
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
 static final ResourceLocation TEX=ResourceLocation.parse("goplanska_kukirin:textures/model/dyes/default.png");
 static ResourceLocation texture(Scooter entity,float partial){int variant=ScooterEasterEggs.variant(entity,partial);String path=variant<=0?"dyes/default":variant<=16?"dyes/"+net.minecraft.world.item.DyeColor.byId(variant-1).getName():"rentals/"+new String[]{"lime","bolt","city"}[Math.min(variant-17,2)];return ResourceLocation.parse("goplanska_kukirin:textures/model/"+path+".png");}
 static void drawPart(int part,Scooter entity,float partial,PoseStack pose,MultiBufferSource buffers,int light,VertexConsumer fallback){if(buffers instanceof MultiBufferSource.BufferSource&&G2Mesh.cachedAvailable())G2Mesh.drawCached(part,pose,light,texture(entity,partial));else G2Mesh.draw(part,pose,fallback,light,ScooterEasterEggs.variant(entity,partial));}
 private final double[] visualVariables=new double[VisualExpressions.VARIABLE_COUNT];
 final ModelPart model;ScooterRenderer(EntityRendererProvider.Context c){super(c);model=c.bakeLayer(LAYER);shadowRadius=.4f;}
 static LayerDefinition layer(){var mesh=new MeshDefinition();var root=mesh.getRoot();var black=CubeListBuilder.create().texOffs(0,0).addBox(-3,-4,-9,6,2,18).addBox(-1,-18,7,2,16,2).addBox(-6,-19,7,12,2,2);root.addOrReplaceChild("black",black,PartPose.ZERO);var accents=CubeListBuilder.create().texOffs(0,0).addBox(-3.5f,-4.2f,-7,7,.7f,14).addBox(-3,-6,-10,6,1,5).addBox(-3,-6,6,6,1,5);root.addOrReplaceChild("orange",accents,PartPose.ZERO);for(int i=0;i<2;i++){var z=i==0?-8:8;root.addOrReplaceChild("wheel"+i,CubeListBuilder.create().texOffs(0,0).addBox(-1.5f,-3,-2,3,6,4).addBox(-1.5f,-2,-3,3,4,6),PartPose.offset(0,-3,z));}return LayerDefinition.create(mesh,16,16);}
 @Override public void render(Scooter e,float yaw,float partial,PoseStack pose,MultiBufferSource buffers,int light){
  double[] values=visualVariables;
  values[0]=(e.tickCount+partial)/20.0;values[1]=e.getDeltaMovement().horizontalDistance();
  values[2]=yaw;values[3]=e.getXRot();values[4]=e.steering(partial);values[5]=e.lean(partial);
  values[6]=e.wheelRotation(partial);values[7]=e.suspension(partial);values[8]=e.stepOffset(partial);
  values[9]=e.throttle();values[10]=e.batteryCharge();values[11]=e.heat();
  values[12]=e.onGround()?0:1;values[13]=e.getDeltaMovement().y;values[15]=e.rocketActive()?1:0;
  values[16]=partial;
  pose.pushPose();ScooterVisuals.apply("pre",pose,values);
  pose.translate(0,e.suspension(partial)+e.stepOffset(partial),0);
  if(ScooterEasterEggs.inverted(e)){pose.translate(0,e.getBbHeight(),0);pose.mulPose(Axis.ZP.rotationDegrees(180));}
  pose.scale(1.25f,1.25f,1.25f);pose.mulPose(Axis.YP.rotationDegrees(180-yaw));
  pose.mulPose(Axis.ZP.rotationDegrees(e.lean(partial)));pose.scale(1,-1,-1);
  ScooterVisuals.apply("root",pose,values);
  var vertex=buffers.getBuffer(RenderType.entityCutoutNoCull(texture(e,partial)));
  pose.pushPose();ScooterVisuals.apply("deck",pose,values);drawPart(0,e,partial,pose,buffers,light,vertex);pose.popPose();
  var front=G2Mesh.pivot("frontPivot");var rear=G2Mesh.pivot("rearPivot");var steer=G2Mesh.pivot("steeringPivot");
  pose.pushPose();pose.translate(steer.x,-steer.y,-steer.z);
  pose.mulPose(ScooterSteeringRig.fallbackRotation(e.steering(partial)));
  pose.translate(-steer.x,steer.y,steer.z);
  pose.pushPose();ScooterVisuals.apply("steering",pose,values);drawPart(1,e,partial,pose,buffers,light,vertex);pose.popPose();
  pose.translate(front.x,-front.y,-front.z);pose.mulPose(Axis.XP.rotationDegrees(e.wheelRotation(partial)));
  pose.translate(-front.x,front.y,front.z);
  pose.pushPose();ScooterVisuals.apply("front_wheel",pose,values);drawPart(2,e,partial,pose,buffers,light,vertex);pose.popPose();
  pose.popPose();
  pose.pushPose();pose.translate(rear.x,-rear.y,-rear.z);pose.mulPose(Axis.XP.rotationDegrees(e.wheelRotation(partial)));
  pose.translate(-rear.x,rear.y,rear.z);
  ScooterVisuals.apply("rear_wheel",pose,values);drawPart(3,e,partial,pose,buffers,light,vertex);
  pose.popPose();
  pose.scale(1,-1,-1);
  pose.pushPose();ScooterVisuals.apply("trim",pose,values);ScooterTrim.draw(e,pose,buffers,light,partial);pose.popPose();
  ScooterRocketVisual.draw(e,pose,buffers,light);ScooterRentalClient.rendered(e);
  pose.popPose();super.render(e,yaw,partial,pose,buffers,light);
 }
 @Override public ResourceLocation getTextureLocation(Scooter e){return texture(e,0);}
 @EventBusSubscriber(modid="goplanska_kukirin",value=Dist.CLIENT,bus=EventBusSubscriber.Bus.MOD)
 public static class Events {
 @SubscribeEvent public static void shaders(net.neoforged.neoforge.client.event.RegisterShadersEvent event)throws java.io.IOException{event.registerShader(new net.minecraft.client.renderer.ShaderInstance(event.getResourceProvider(),ResourceLocation.parse("goplanska_kukirin:scooter_cached"),com.mojang.blaze3d.vertex.DefaultVertexFormat.NEW_ENTITY),value->G2Mesh.shader=value);}
 @SubscribeEvent public static void itemColors(net.neoforged.neoforge.client.event.RegisterColorHandlersEvent.Item event){event.register((stack,index)->{return ScooterMarkerColor.item(stack);},Kukirin.ITEM.get());}
 @SubscribeEvent public static void reload(net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent e){e.registerReloadListener((net.minecraft.server.packs.resources.ResourceManagerReloadListener)manager->{G2Mesh.invalidate();ScooterTrim.invalidate();});}

@SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers e){e.registerEntityRenderer(Kukirin.SCOOTER.get(),ScooterRenderer::new);}@SubscribeEvent public static void layers(EntityRenderersEvent.RegisterLayerDefinitions e){e.registerLayerDefinition(LAYER,ScooterRenderer::layer);}}
}
