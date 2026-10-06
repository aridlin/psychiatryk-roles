package pl.aridlin.kukirin;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
/** Surface overlays consume the same instance transform and bone palette as the GPU model. */
@EventBusSubscriber(modid="goplanska_kukirin",value=Dist.CLIENT)
public final class ScooterTrimWorld {
 static record Frame(Scooter scooter,org.joml.Matrix4f matrix,int light,com.wf.gemrender.render.PoseCache.Pose pose){}
 static final java.util.Map<Scooter,Frame> DIRECT=new java.util.IdentityHashMap<>();
 static void queue(Scooter scooter,org.joml.Matrix4f matrix,int light,com.wf.gemrender.render.PoseCache.Pose pose){ScooterRentalClient.rendered(scooter);DIRECT.put(scooter,new Frame(scooter,new org.joml.Matrix4f(matrix),light,pose));}
 @SubscribeEvent public static void render(RenderLevelStageEvent event){
  if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_PARTICLES)return;
  var mc=net.minecraft.client.Minecraft.getInstance();if(mc.level==null)return;
  var buffers=mc.renderBuffers().bufferSource();var camera=event.getCamera().getPosition();var pose=event.getPoseStack();
  for(var frame:DIRECT.values()){var local=new com.mojang.blaze3d.vertex.PoseStack();local.mulPose(frame.matrix());ScooterRocketVisual.draw(frame.scooter(),local,buffers,frame.light(),frame.pose());RentalBrandingVisual.draw(frame.scooter(),local,buffers,frame.light(),frame.pose());}
  DIRECT.clear();
  for(var entry:ScooterVisual.ACTIVE.entrySet()){
   var scooter=entry.getKey();if(scooter.isRemoved()||scooter.level()!=mc.level||!event.getFrustum().isVisible(scooter.getBoundingBox().inflate(1)))continue;
   ScooterRentalClient.rendered(scooter);entry.getValue().drawTrim(pose,buffers,net.minecraft.client.renderer.LevelRenderer.getLightColor(mc.level,scooter.blockPosition()),camera);
  }
  buffers.endBatch();
 }
}
