package pl.aridlin.partymarkers;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.AABB;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import pl.aridlin.psychiatrykroles.MarkSync;
@EventBusSubscriber(modid="goplanska_party_markers",value=Dist.CLIENT)
public final class MarkChams {
 private static List<MarkSync.Mark> marks=List.of();private static Object connection;private static long received;
 public static void receive(MarkSync packet){marks=packet.marks();connection=Minecraft.getInstance().getConnection();received=System.nanoTime();}
 @SubscribeEvent public static void render(RenderLevelStageEvent e){if(e.getStage()!=RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS||!ChamsEffect.ready()||qouteall.imm_ptl.core.render.context_management.PortalRendering.isRendering())return;var mc=Minecraft.getInstance();if(mc.level==null||mc.getConnection()!=connection||System.nanoTime()-received>3_000_000_000L)return;int count=0;for(var m:marks){if(!ChamsCategories.enabled(m.chalk()?ChamsCategories.Kind.CHALK:ChamsCategories.Kind.AREAS))continue;if(++count>16)break;var bounds=new AABB(m.x(),m.y(),m.z(),m.X(),m.Y(),m.Z());if(m.chalk())ChamsEffect.chalk(e,m.id(),bounds,m.color());else if(Math.abs(m.Y()-m.y()-1)<.0001)ChamsEffect.plane(e,m.id(),bounds,m.color(),ChamsRenderOptions.markingTorchHalftone());else ChamsEffect.box(e,m.id(),bounds,m.color(),ChamsRenderOptions.markingTorchHalftone());}}
}
