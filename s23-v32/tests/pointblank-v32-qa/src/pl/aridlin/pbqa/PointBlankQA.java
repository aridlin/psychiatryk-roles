package pl.aridlin.pbqa;

import java.nio.file.*;
import java.util.*;
import com.google.gson.*;
import com.vicmatskiv.pointblank.attachment.Attachments;
import com.vicmatskiv.pointblank.client.GunClientState;
import com.vicmatskiv.pointblank.feature.PipFeature;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.GameRules;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.*;
import org.lwjgl.opengl.*;

/** Observe scoped-gun GUI/world rendering without changing its implementation. */
@Mod("goplanska_pointblank_qa")
public final class PointBlankQA {
 static PointBlankQA INSTANCE;
 public static void probe(String stage){if(INSTANCE!=null)INSTANCE.render(stage);}
 static final Path OUT=Path.of(System.getProperty("user.home"), "Documents/Codex/2026-10-02/make/outputs/save-io-v32/pointblank-qa");
 final String[] names={"baseline_empty","gun_unselected","inventory_unselected","equipped_firstperson","inventory_selected","after_inventory","aim","unscope","removed","removed_inventory","plain_gun","plain_inventory","plain_after_inventory","plain_removed","portal_empty","portal_equipped","portal_aim","portal_unscope","portal_removed"};
 final JsonArray samples=new JsonArray();final JsonObject result=new JsonObject();
 int warmup,ticks,phase=-1;volatile boolean prepared;volatile String portalUuid;volatile boolean portalValid;boolean done;ItemStack gun;final Set<String> observed=new HashSet<>();
 public PointBlankQA(){
  INSTANCE=this;
  NeoForge.EVENT_BUS.addListener(this::login);
  NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.HIGHEST,true,RenderHandEvent.class,e->render("before_hand"));
  NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.LOWEST,false,RenderLevelStageEvent.class,e->{if(e.getStage()==RenderLevelStageEvent.Stage.AFTER_LEVEL)render("after_level");else if(e.getStage()==RenderLevelStageEvent.Stage.AFTER_SKY)render("after_sky");else if(e.getStage()==RenderLevelStageEvent.Stage.AFTER_SOLID_BLOCKS)render("after_solid");});
  NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.LOWEST,true,RenderHandEvent.class,e->render("during_hand"));
  NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.LOWEST,false,ClientTickEvent.Post.class,this::tick);
  NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.LOWEST,false,RenderGuiEvent.Post.class,e->render("after_hud"));
  NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.LOWEST,false,ScreenEvent.Render.Post.class,e->{if(e.getScreen() instanceof InventoryScreen)render("after_inventory_gui");});
 }
 void login(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent e){
  if(!(e.getEntity() instanceof net.minecraft.server.level.ServerPlayer p))return;
  var world=p.serverLevel();p.stopRiding();p.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
  try{var type=Class.forName("pl.aridlin.psychiatrykroles.RoleData");var get=type.getDeclaredMethod("get",net.minecraft.server.MinecraftServer.class);get.setAccessible(true);var add=type.getDeclaredMethod("addPatient",UUID.class);add.setAccessible(true);add.invoke(get.invoke(null,p.getServer()),p.getUUID());}catch(Exception x){throw new RuntimeException(x);}
  world.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,world.getServer());world.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,world.getServer());world.setDayTime(6000);world.setWeatherParameters(6000,0,false,false);
  for(int x=-8;x<=8;x++)for(int z=-6;z<=14;z++)for(int y=99;y<=105;y++)world.setBlock(new BlockPos(x,y,z),y==99?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),2);
  for(int x=-5;x<=5;x++)for(int y=100;y<=104;y++)world.setBlock(new BlockPos(x,y,10),(x%3==0?Blocks.RED_CONCRETE:x%3==1?Blocks.BLUE_CONCRETE:Blocks.WHITE_CONCRETE).defaultBlockState(),2);
  p.getInventory().clearContent();p.teleportTo(world,.5,100,.5,0,0);prepared=true;
 }
 void tick(ClientTickEvent.Post e){
  var mc=Minecraft.getInstance();
  if(Boolean.getBoolean("pointblankqa.allocatorOnly")){if(!done&&mc.screen instanceof net.minecraft.client.gui.screens.TitleScreen&&++warmup>=20){probeAllocator();finish(null);}return;}
  if(done||!prepared||mc.player==null||mc.level==null)return;
  try{
   if(phase<0){if(++warmup<100||mc.screen!=null)return;Files.createDirectories(OUT);var item=BuiltInRegistries.ITEM.get(ResourceLocation.parse("pointblank:m4a1"));var scope=BuiltInRegistries.ITEM.get(ResourceLocation.parse("pointblank:eaglescope"));if(item==Items.AIR||scope==Items.AIR)throw new AssertionError("Scope/gun registry item missing");gun=new ItemStack(item);Attachments.addAttachment(gun,new ItemStack(scope),true);Attachments.ensureValidAttachmentsSelected(gun);result.addProperty("pip_zoom",PipFeature.getZoom(gun).orElse(-1f));result.addProperty("gun",BuiltInRegistries.ITEM.getKey(gun.getItem()).toString());result.addProperty("scope",BuiltInRegistries.ITEM.getKey(scope).toString());phase=0;transition();}
   if(++ticks>=50){phase++;ticks=0;if(phase>=names.length){finish(null);return;}transition();}
   var state=GunClientState.getMainHeldState(mc.player);if(state!=null)state.setAiming(phase==6||phase==16);
   mc.options.keyUse.setDown(false);mc.options.setCameraType(CameraType.FIRST_PERSON);mc.player.setYRot(0);mc.player.setXRot(0);mc.player.yHeadRot=0;
  }catch(Throwable x){finish(x);}
 }
 void transition(){
  var mc=Minecraft.getInstance();mc.options.keyUse.setDown(false);
  if(phase==0){mc.player.getInventory().clearContent();mc.player.getInventory().selected=0;}
  if(phase==1){mc.player.getInventory().setItem(1,gun.copy());mc.player.getInventory().selected=0;}
  if(phase==2||phase==4||phase==9||phase==11)mc.setScreen(new InventoryScreen(mc.player));else if(mc.screen instanceof InventoryScreen)mc.setScreen(null);
  if(phase==3)mc.player.getInventory().selected=1;
  if(phase==15){mc.player.getInventory().setItem(1,gun.copy());mc.player.getInventory().selected=1;}
  if(phase==10){mc.player.getInventory().setItem(1,new ItemStack(gun.getItem()));mc.player.getInventory().selected=1;}
  if(phase==8||phase==13||phase==14||phase==18){var state=GunClientState.getMainHeldState(mc.player);if(state!=null)state.setAiming(false);mc.player.getInventory().clearContent();mc.player.getInventory().selected=0;}
  // Use the actual integrated server inventory so opening inventory does not
  // overwrite a client-only fabricated gun. This is only the disposable world.
  var single=mc.getSingleplayerServer();if(single!=null){int current=phase;var copy=phase>=10&&phase<13?new ItemStack(gun.getItem()):gun.copy();var id=mc.player.getUUID();single.execute(()->{var p=single.getPlayerList().getPlayer(id);if(p==null)return;p.getInventory().clearContent();if((current>=1&&current<8)||(current>=10&&current<13)||(current>=15&&current<18))p.getInventory().setItem(1,copy);p.getInventory().selected=((current>=3&&current<8)||(current>=10&&current<13)||(current>=15&&current<18))?1:0;p.containerMenu.broadcastChanges();if(current==14)spawnPortal(p.serverLevel());});}
  System.out.println("POINTBLANK_QA phase "+names[phase]);
 }
 void spawnPortal(net.minecraft.server.level.ServerLevel world){
  for(int x=-8;x<=8;x++)for(int z=35;z<=49;z++)for(int y=99;y<=105;y++)world.setBlock(new BlockPos(x,y,z),y==99?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),2);for(int x=-5;x<=5;x++)for(int y=100;y<=104;y++)world.setBlock(new BlockPos(x,y,46),(x%2==0?Blocks.LIME_CONCRETE:Blocks.GOLD_BLOCK).defaultBlockState(),2);
  var portal=new qouteall.imm_ptl.core.portal.Portal(qouteall.imm_ptl.core.portal.Portal.ENTITY_TYPE,world);portal.setOriginPos(new net.minecraft.world.phys.Vec3(.5,101.5,5));portal.setDestinationDimension(world.dimension());portal.setDestination(new net.minecraft.world.phys.Vec3(.5,101.5,40));portal.setOrientationAndSize(new net.minecraft.world.phys.Vec3(-1,0,0),new net.minecraft.world.phys.Vec3(0,1,0),3,3);portal.setTeleportable(false);portal.portalTag="pointblank-render-qa";portalValid=portal.isPortalValid();world.addFreshEntity(portal);portal.reloadAndSyncToClient();portalUuid=portal.getUUID().toString();
 }
 void render(String stage){
  if(com.vicmatskiv.pointblank.client.ClientSystem.getInstance().getAuxLevelRenderer().isRenderingPip())stage+="_aux";
  if(done||phase<0||ticks<30||!observed.add(phase+":"+stage+":"+qouteall.imm_ptl.core.render.context_management.PortalRendering.getPortalLayer()))return;
  try{
   var mc=Minecraft.getInstance();var rt=mc.getMainRenderTarget();var entry=new JsonObject();entry.addProperty("phase",names[phase]);entry.addProperty("portal_layer",qouteall.imm_ptl.core.render.context_management.PortalRendering.getPortalLayer());entry.addProperty("stage",stage);entry.addProperty("screen",mc.screen==null?"none":mc.screen.getClass().getSimpleName());entry.addProperty("main_fbo",rt.frameBufferId);entry.addProperty("draw_fbo",GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING));entry.addProperty("read_fbo",GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING));entry.addProperty("stencil_enabled",GL11.glIsEnabled(GL11.GL_STENCIL_TEST));entry.addProperty("depth_enabled",GL11.glIsEnabled(GL11.GL_DEPTH_TEST));entry.addProperty("scissor_enabled",GL11.glIsEnabled(GL11.GL_SCISSOR_TEST));entry.addProperty("depth_write",GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK));entry.addProperty("depth_func",GL11.glGetInteger(GL11.GL_DEPTH_FUNC));var range=org.lwjgl.BufferUtils.createFloatBuffer(2);GL11.glGetFloatv(GL11.GL_DEPTH_RANGE,range);entry.addProperty("depth_range",range.get(0)+","+range.get(1));var viewport=org.lwjgl.BufferUtils.createIntBuffer(4);GL11.glGetIntegerv(GL11.GL_VIEWPORT,viewport);entry.addProperty("viewport",viewport.get(0)+","+viewport.get(1)+","+viewport.get(2)+","+viewport.get(3));entry.add("cache_depth",cache("DEPTH"));entry.add("cache_color",cache("COLOR_MASK"));entry.addProperty("projection",com.mojang.blaze3d.systems.RenderSystem.getProjectionMatrix().toString());entry.addProperty("modelview",com.mojang.blaze3d.systems.RenderSystem.getModelViewMatrix().toString());entry.addProperty("shader_color",java.util.Arrays.toString(com.mojang.blaze3d.systems.RenderSystem.getShaderColor()));
   int[] params={GL11.GL_STENCIL_WRITEMASK,GL11.GL_STENCIL_FUNC,GL11.GL_STENCIL_REF,GL11.GL_STENCIL_VALUE_MASK,GL11.GL_STENCIL_FAIL,GL11.GL_STENCIL_PASS_DEPTH_FAIL,GL11.GL_STENCIL_PASS_DEPTH_PASS,GL20.GL_STENCIL_BACK_WRITEMASK,GL20.GL_STENCIL_BACK_FUNC,GL20.GL_STENCIL_BACK_REF,GL20.GL_STENCIL_BACK_VALUE_MASK};
   String[] keys={"raw_mask","raw_func","raw_ref","raw_value_mask","raw_fail","raw_zfail","raw_zpass","raw_back_mask","raw_back_func","raw_back_ref","raw_back_value_mask"};for(int n=0;n<params.length;n++)entry.addProperty(keys[n],GL11.glGetInteger(params[n]));
   var color=org.lwjgl.BufferUtils.createByteBuffer(4);GL11.glGetBooleanv(GL11.GL_COLOR_WRITEMASK,color);entry.addProperty("color_write",color.get(0)+","+color.get(1)+","+color.get(2)+","+color.get(3));
   entry.addProperty("neoforge_stencil",rt.isStencilEnabled());entry.addProperty("ip_stencil",((qouteall.imm_ptl.core.ducks.IEFrameBuffer)rt).ip_getIsStencilBufferEnabled());
   entry.addProperty("draw_fbo_complete",GL30.glCheckFramebufferStatus(GL30.GL_DRAW_FRAMEBUFFER));entry.addProperty("draw_stencil_attachment_type",GL30.glGetFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER,GL30.GL_STENCIL_ATTACHMENT,GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE));entry.addProperty("draw_stencil_bits",GL30.glGetFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER,GL30.GL_STENCIL_ATTACHMENT,GL30.GL_FRAMEBUFFER_ATTACHMENT_STENCIL_SIZE));
   entry.addProperty("pb_stencil",((com.vicmatskiv.pointblank.client.render.RenderTargetExt)rt).isPointblankStencilEnabled());
   var field=com.mojang.blaze3d.platform.GlStateManager.class.getDeclaredField("STENCIL");field.setAccessible(true);var stencil=field.get(null);for(String name:new String[]{"mask","fail","zfail","zpass"})entry.addProperty("cached_"+name,number(stencil,name));var f=stencil.getClass().getDeclaredField("func");f.setAccessible(true);var func=f.get(stencil);for(String name:new String[]{"func","ref","mask"})entry.addProperty("cached_func_"+name,number(func,name));
   var state=GunClientState.getMainHeldState(mc.player);entry.addProperty("gun_state",state!=null);entry.addProperty("aiming",state!=null&&state.isAiming());entry.addProperty("pip_enabled",com.vicmatskiv.pointblank.Config.pipScopesEnabled);entry.addProperty("pip_fallback",com.vicmatskiv.pointblank.Config.isPipFallbackRequired());entry.addProperty("gl_error",GL11.glGetError());
   try{var vf=Class.forName("foundry.veil.impl.client.render.pipeline.VeilFirstPersonRenderer");var ff=vf.getDeclaredField("firstPerson");ff.setAccessible(true);var fbo=ff.get(null);entry.addProperty("veil_firstperson_present",fbo!=null);entry.addProperty("main_color_texture",rt.getColorTextureId());if(fbo!=null){entry.addProperty("veil_source_has_stencil",(Boolean)fbo.getClass().getMethod("hasStencilAttachment").invoke(fbo));var depthMethod=fbo.getClass().getMethod("getDepthTextureAttachment");depthMethod.setAccessible(true);var depth=depthMethod.invoke(fbo);var depthIdMethod=depth.getClass().getMethod("getId");depthIdMethod.setAccessible(true);int depthId=((Number)depthIdMethod.invoke(depth)).intValue();entry.addProperty("veil_depth_id",depthId);entry.addProperty("veil_depth_width",textureValue(depthId,GL11.GL_TEXTURE_WIDTH));entry.addProperty("veil_depth_format",textureValue(depthId,GL11.GL_TEXTURE_INTERNAL_FORMAT));var attachment=fbo.getClass().getMethod("getColorTextureAttachment",int.class);attachment.setAccessible(true);var colorAttachment=attachment.invoke(fbo,0);var id=colorAttachment.getClass().getMethod("getId");id.setAccessible(true);entry.addProperty("veil_color_texture",((Number)id.invoke(colorAttachment)).intValue());}}catch(Throwable probe){entry.addProperty("veil_probe_error",probe.toString());}
   entry.addProperty("wall_pixels",readWallPixels());entry.addProperty("portal_pixels",readPortalPixels());entry.add("stencil_pixels",readStencilPixels());entry.addProperty("probe_gl_error",GL11.glGetError());
   samples.add(entry);
   if((stage.equals("after_level")||stage.equals("before_hand")||stage.equals("after_hud")||stage.equals("after_inventory_gui"))&&(stage.equals("after_inventory_gui")||!(mc.screen instanceof InventoryScreen)))try(var image=Screenshot.takeScreenshot(rt)){image.writeToFile(OUT.resolve(names[phase]+"-"+stage+".png"));}
   Files.writeString(OUT.resolve("running-state.json"),new GsonBuilder().setPrettyPrinting().create().toJson(samples));
  }catch(Throwable x){finish(x);}
 }
 JsonObject cache(String name)throws Exception{var f=com.mojang.blaze3d.platform.GlStateManager.class.getDeclaredField(name);f.setAccessible(true);var v=f.get(null);var o=new JsonObject();for(var p:v.getClass().getDeclaredFields()){p.setAccessible(true);var n=p.get(v);if(n instanceof Boolean)o.addProperty(p.getName(),(Boolean)n);else if(n instanceof Number)o.addProperty(p.getName(),(Number)n);else if(p.getName().equals("mode")){var enabled=n.getClass().getDeclaredField("enabled");enabled.setAccessible(true);o.addProperty("enabled",enabled.getBoolean(n));}}return o;}
 JsonObject readStencilPixels(){int w=Minecraft.getInstance().getMainRenderTarget().width,h=Minecraft.getInstance().getMainRenderTarget().height;var buffer=org.lwjgl.BufferUtils.createByteBuffer(w*h);int alignment=GL11.glGetInteger(GL11.GL_PACK_ALIGNMENT);GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT,1);try{GL11.glReadPixels(0,0,w,h,GL11.GL_STENCIL_INDEX,GL11.GL_UNSIGNED_BYTE,buffer);}finally{GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT,alignment);}int[] counts=new int[4];for(int i=0;i<w*h;i++){int n=Byte.toUnsignedInt(buffer.get(i));counts[n<=2?n:3]++;}var out=new JsonObject();out.addProperty("zero",counts[0]);out.addProperty("one",counts[1]);out.addProperty("two",counts[2]);out.addProperty("other",counts[3]);return out;}
 int readPortalPixels(){int w=Minecraft.getInstance().getMainRenderTarget().width,h=Minecraft.getInstance().getMainRenderTarget().height;var b=org.lwjgl.BufferUtils.createByteBuffer(w*h*4);GL11.glReadPixels(0,0,w,h,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,b);int count=0;for(int y=100;y<Math.min(360,h);y++)for(int x=270;x<Math.min(590,w);x++){int i=((h-y-1)*w+x)*4,r=Byte.toUnsignedInt(b.get(i)),g=Byte.toUnsignedInt(b.get(i+1)),bl=Byte.toUnsignedInt(b.get(i+2));if((g>90&&g>r*1.3&&g>bl*1.3)||(r>100&&g>90&&bl<65))count++;}return count;}
 int readWallPixels(){
  var mc=Minecraft.getInstance();int w=mc.getMainRenderTarget().width,h=mc.getMainRenderTarget().height;var buf=org.lwjgl.BufferUtils.createByteBuffer(w*h*4);GL11.glReadPixels(0,0,w,h,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,buf);int count=0;for(int y=120;y<Math.min(295,h);y++)for(int x=230;x<Math.min(620,w);x++){int i=((h-y-1)*w+x)*4,r=Byte.toUnsignedInt(buf.get(i)),g=Byte.toUnsignedInt(buf.get(i+1)),b=Byte.toUnsignedInt(buf.get(i+2));if((r>100&&g<90&&b<90)||(b>60&&r<70&&g<70))count++;}return count;
 }
 int textureValue(int id,int parameter){int old=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);try{GL11.glBindTexture(GL11.GL_TEXTURE_2D,id);return GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,parameter);}finally{GL11.glBindTexture(GL11.GL_TEXTURE_2D,old);}}
 void probeAllocator(){
  int draw=GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING),read=GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);while(GL11.glGetError()!=GL11.GL_NO_ERROR){}
  foundry.veil.api.client.render.framebuffer.AdvancedFbo fbo=null;var proof=new JsonObject();
  try{fbo=foundry.veil.api.client.render.framebuffer.AdvancedFbo.withSize(8,8).addColorTextureBuffer().setFormat(foundry.veil.api.client.render.framebuffer.FramebufferAttachmentDefinition.Format.DEPTH32F_STENCIL8).setDepthTextureBuffer().build(true);proof.addProperty("build_returned",true);proof.addProperty("has_stencil",fbo.hasStencilAttachment());int id=fbo.getDepthTextureAttachment().getId();proof.addProperty("depth_texture_id",id);proof.addProperty("depth_width",textureValue(id,GL11.GL_TEXTURE_WIDTH));proof.addProperty("depth_format",textureValue(id,GL11.GL_TEXTURE_INTERNAL_FORMAT));GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,fbo.getId());proof.addProperty("fbo_status",GL30.glCheckFramebufferStatus(GL30.GL_DRAW_FRAMEBUFFER));proof.addProperty("gl_error",GL11.glGetError());}
  catch(Throwable x){proof.addProperty("build_returned",false);proof.addProperty("error",x.toString());proof.addProperty("gl_error",GL11.glGetError());}
  finally{if(fbo!=null)fbo.free();GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,draw);GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,read);result.add("allocator",proof);System.out.println("POINTBLANK_QA allocator "+proof);}
 }
 int number(Object value,String name)throws Exception{var f=value.getClass().getDeclaredField(name);f.setAccessible(true);return ((Number)f.get(value)).intValue();}
 void finish(Throwable error){if(done)return;done=true;result.add("samples",samples);result.addProperty("portal_uuid",portalUuid);result.addProperty("portal_valid",portalValid);result.addProperty("completed",error==null);result.addProperty("renderer",GL11.glGetString(GL11.GL_RENDERER));if(error!=null){result.addProperty("error",error.toString());error.printStackTrace();}try{Files.createDirectories(OUT);Files.writeString(OUT.resolve("runtime-report.json"),new GsonBuilder().setPrettyPrinting().create().toJson(result));}catch(Exception x){x.printStackTrace();}Minecraft.getInstance().stop();}
}
