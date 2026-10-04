package pl.aridlin.starter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
@EventBusSubscriber(modid="goplanska_starter",value=Dist.CLIENT)
public final class PreviewClient {
 private static PreviewPacket data;private static Object connection;private static long expires;
 public static void receive(PreviewPacket p){data=p.visible()?p:null;connection=Minecraft.getInstance().getConnection();expires=System.nanoTime()+60_000_000_000L;}
 @SubscribeEvent public static void render(RenderLevelStageEvent e){
  if(e.getStage()!=RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS||data==null)return;
  var mc=Minecraft.getInstance();if(connection!=mc.getConnection()||System.nanoTime()>expires){data=null;return;}
  if(qouteall.imm_ptl.core.render.context_management.PortalRendering.isRendering())return;
  var pose=e.getPoseStack();var camera=e.getCamera().getPosition();var buffers=mc.renderBuffers().bufferSource();var lines=buffers.getBuffer(RenderType.lines());
  pose.pushPose();pose.translate(-camera.x,-camera.y,-camera.z);
  float red=data.valid()?.15f:1f,green=data.valid()?1f:.15f;
  for(var piece:StarterLayout.create(data.origin(),data.rotation())){var p=piece.pos();LevelRenderer.renderLineBox(pose,lines,p.getX()+.01,p.getY()+.01,p.getZ()+.01,p.getX()+.99,p.getY()+.99,p.getZ()+.99,red,green,.5f,.85f);}
  pose.popPose();buffers.endBatch(RenderType.lines());
 }
}
