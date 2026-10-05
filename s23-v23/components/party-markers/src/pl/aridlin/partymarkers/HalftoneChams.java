package pl.aridlin.partymarkers;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.*;
import net.minecraft.client.player.*;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.*;
import com.mojang.math.Axis;
import java.util.*;
public final class HalftoneChams {
 private static ShaderInstance shader;
 private static Object connection;
 private static final Map<UUID,net.minecraft.world.entity.LivingEntity> clones=new HashMap<>();
 @EventBusSubscriber(modid="goplanska_party_markers",value=Dist.CLIENT,bus=EventBusSubscriber.Bus.MOD)
 public static class Shaders {
  @SubscribeEvent public static void register(RegisterShadersEvent e)throws java.io.IOException{e.registerShader(new ShaderInstance(e.getResourceProvider(),ResourceLocation.fromNamespaceAndPath("goplanska_party_markers","party_halftone"),DefaultVertexFormat.NEW_ENTITY),value->{shader=value;ChamsPass.mask=value;});e.registerShader(new ShaderInstance(e.getResourceProvider(),ResourceLocation.fromNamespaceAndPath("goplanska_party_markers","item_mask"),DefaultVertexFormat.NEW_ENTITY),value->ChamsPass.itemMask=value);e.registerShader(new ShaderInstance(e.getResourceProvider(),ResourceLocation.fromNamespaceAndPath("goplanska_party_markers","occlusion_mask"),DefaultVertexFormat.BLOCK),value->ChamsPass.occlusionMask=value);e.registerShader(new ShaderInstance(e.getResourceProvider(),ResourceLocation.fromNamespaceAndPath("goplanska_party_markers","entity_occlusion_mask"),DefaultVertexFormat.NEW_ENTITY),value->ChamsPass.entityOcclusionMask=value);e.registerShader(new ShaderInstance(e.getResourceProvider(),ResourceLocation.fromNamespaceAndPath("goplanska_party_markers","party_composite"),DefaultVertexFormat.POSITION_TEX),value->ChamsPass.composite=value);}
 }
 @EventBusSubscriber(modid="goplanska_party_markers",value=Dist.CLIENT)
 public static class Events {
  @SubscribeEvent(priority=net.neoforged.bus.api.EventPriority.HIGHEST) public static void startFrame(RenderLevelStageEvent e){if(e.getStage()==RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS&&!qouteall.imm_ptl.core.render.context_management.PortalRendering.isRendering())ChamsPass.frame++;}
  @SubscribeEvent public static void disableOldOutline(ClientTickEvent.Post e){var mc=Minecraft.getInstance();if(mc.level!=null){for(var entity:mc.level.entitiesForRendering())if(entity instanceof net.minecraft.world.entity.player.Player||ClientWaypoints.members().stream().anyMatch(m->m.kind().equals("zombie")&&m.uuid().equals(entity.getUUID()))){if(entity instanceof dev.tr7zw.entityculling.access.Cullable c){c.setCulled(false);c.setOutOfCamera(false);c.setTimeout();}}}if(mc.player!=null){var way=(net.deadlydiamond98.way.util.mixin.IWayPlayer)mc.player;way.way$setSeeOutline(false);way.way$setSeeHeadOutline(false);}}
  @SubscribeEvent public static void render(RenderLevelStageEvent e){
   if(e.getStage()!=RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS||shader==null||ChamsPass.composite==null||qouteall.imm_ptl.core.render.context_management.PortalRendering.isRendering())return;
   var mc=Minecraft.getInstance();if(mc.level==null||mc.player==null)return;if(connection!=mc.getConnection()){clones.clear();ChamsPass.clear();connection=mc.getConnection();}
   var camera=e.getCamera().getPosition();var pose=e.getPoseStack();var buffers=mc.renderBuffers().bufferSource();float partial=e.getPartialTick().getGameTimeDeltaPartialTick(false);
   for(var member:ClientWaypoints.members()){
    if(!member.kind().equals("scooter")&&!ChamsCategories.enabled(member.kind().equals("player")?ChamsCategories.Kind.PARTY:ChamsCategories.Kind.MARKED))continue;
    if(!member.dimension().equals(mc.level.dimension().location().toString()))continue;
    net.minecraft.world.entity.LivingEntity player=mc.level.getEntity(member.entityId()) instanceof net.minecraft.world.entity.LivingEntity p&&p.getUUID().equals(member.uuid())?p:null;
    if(player==null&&member.kind().equals("scooter"))continue;
    if(player==null){var clone=clones.computeIfAbsent(member.uuid(),id->member.kind().equals("zombie")?new net.minecraft.world.entity.monster.Zombie(net.minecraft.world.entity.EntityType.ZOMBIE,mc.level):new RemotePlayer(mc.level,new com.mojang.authlib.GameProfile(id,member.name())));var pos=ClientWaypoints.position(member).subtract(0,clone.getBbHeight()+.5,0);clone.setPos(pos.x,pos.y,pos.z);clone.xo=pos.x;clone.yo=pos.y;clone.zo=pos.z;clone.setUUID(member.uuid());player=clone;}
    if(!(mc.getEntityRenderDispatcher().getRenderer(player) instanceof net.minecraft.client.renderer.entity.LivingEntityRenderer renderer)&&!member.kind().equals("scooter"))continue;
    var pos=clones.containsValue(player)?player.position():new net.minecraft.world.phys.Vec3(Mth.lerp(partial,player.xOld,player.getX()),Mth.lerp(partial,player.yOld,player.getY()),Mth.lerp(partial,player.zOld,player.getZ()));
    if(pos.distanceTo(camera)>64)continue;
    ChamsEffect.entityAt(e,player,member.color(),pos);
   }

  }
 }
}
