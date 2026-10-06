package pl.aridlin.steeringqa;

import com.google.gson.*;
import com.github.exopandora.shouldersurfing.api.client.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.minecraft.client.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.*;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import pl.aridlin.kukirin.*;

/** Only drives the disposable offline scene. Production implementation is untouched. */
@Mod("goplanska_steering_qa")
public final class SteeringQA {
 public static SteeringQA INSTANCE;public static final ThreadLocal<Scooter> DRAWN=new ThreadLocal<>();public static final ThreadLocal<Float> PARTIAL=new ThreadLocal<>();
 record Case(String name,double seed,int keySign,int gas,String terrain,boolean shoulder,boolean mouse,float cameraOffset,int duration){}
 final boolean closeup=Boolean.getBoolean("steeringqa.closeup");
 final Case[] cases=closeup?new Case[]{new Case("stationary_neutral",0,0,0,"flat",false,false,0,42),new Case("stationary_left",0,-1,0,"flat",false,false,0,42),new Case("stationary_right",0,1,0,"flat",false,false,0,42),new Case("camera_left_forward",.3,-1,1,"flat",false,false,0,48),new Case("camera_right_forward",.3,1,1,"flat",false,false,0,48)}:new Case[]{
  new Case("stationary_neutral",0,0,0,"flat",false,false,0,42),
  new Case("stationary_left",0,-1,0,"flat",false,false,0,42),new Case("stationary_right",0,1,0,"flat",false,false,0,42),
  new Case("hud_zero_left",.006,-1,0,"flat",false,false,0,48),
  new Case("blocked_left",.3,-1,1,"cage",false,false,0,90),new Case("blocked_right",.3,1,1,"cage",false,false,0,90),
  new Case("snow_stopped_left",0,-1,0,"snow",false,false,0,48),new Case("ice_stopped_right",0,1,0,"ice",false,false,0,48),
  new Case("water_stopped_left",0,-1,0,"water",false,false,0,48),
  new Case("camera_left_forward",.3,-1,1,"flat",false,false,0,48),new Case("camera_right_forward",.3,1,1,"flat",false,false,0,48),
  new Case("shoulder_left_forward",.3,-1,1,"flat",true,false,90,48),new Case("shoulder_right_forward",.3,1,1,"flat",true,false,-90,48),
  new Case("mouse_left_forward",.3,0,1,"flat",true,true,-30,48),new Case("mouse_right_forward",.3,0,1,"flat",true,true,30,48),
  new Case("s_rest_reverse",0,0,-1,"flat",false,false,0,60),new Case("s_forward_brake_reverse",.6,0,-1,"flat",false,false,0,90),
  new Case("w_reverse_brake_forward",-.16,0,1,"flat",false,false,0,60),new Case("w_s_rest_brake_only",0,0,2,"flat",false,false,0,48),
  new Case("reverse_left",-.12,-1,-1,"flat",false,false,0,48),new Case("reverse_right",-.12,1,-1,"flat",false,false,0,48)
 };
 final Path out=Path.of(System.getProperty("steeringqa.output","~/Documents/Codex/2026-10-02/make/outputs/steering-v34-qa"));
 final ConcurrentLinkedQueue<JsonObject> serverSamples=new ConcurrentLinkedQueue<>();
 final JsonArray clientSamples=new JsonArray(),renderSamples=new JsonArray();
 double preX,preZ;float preYaw;Vec3 preForward;Vector3f preRight;long preTick; boolean capturedPre;
 volatile UUID scooterId;volatile boolean prepared;volatile int phase=-1,ticks;int warmup;boolean done;long renderedTick=-1;int screenshotPhase=-1;
 public SteeringQA(){
  INSTANCE=this;
  NeoForge.EVENT_BUS.addListener(this::login);
  NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.HIGHEST,false,ClientTickEvent.Pre.class,this::before);
  NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.LOWEST,false,ClientTickEvent.Post.class,this::after);
  NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.LOWEST,false,RenderLevelStageEvent.class,this::render);
  NeoForge.EVENT_BUS.addListener(this::serverTick);
 }
 void login(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent e){
  if(!(e.getEntity() instanceof net.minecraft.server.level.ServerPlayer p))return;
  try{
   var world=p.serverLevel();p.setGameMode(net.minecraft.world.level.GameType.CREATIVE);p.stopRiding();
   var type=Class.forName("pl.aridlin.psychiatrykroles.RoleData");var get=type.getDeclaredMethod("get",net.minecraft.server.MinecraftServer.class);get.setAccessible(true);var add=type.getDeclaredMethod("addPatient",UUID.class);add.setAccessible(true);add.invoke(get.invoke(null,p.getServer()),p.getUUID());
   world.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,world.getServer());world.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,world.getServer());world.setDayTime(6000);world.setWeatherParameters(6000,0,false,false);
   // Only the fixture generates these chunks. Raw sections avoid neighbor updates.
   for(int cx=-4;cx<=4;cx++)for(int cz=-1;cz<=6;cz++){
    var chunk=world.getChunk(cx,cz);
    for(int x=0;x<16;x++)for(int z=0;z<16;z++)for(int y=99;y<=104;y++)chunk.getSection(chunk.getSectionIndex(y)).setBlockState(x,y&15,z,y==99?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState());
    chunk.setUnsaved(true);
   }
   p.getInventory().clearContent();p.teleportTo(world,.5,100,.5,0,25);
   var scooter=Kukirin.SCOOTER.get().create(world);scooter.setNoAi(true);scooter.setItemSlot(EquipmentSlot.FEET,new ItemStack(Kukirin.ITEM.get()));scooter.moveTo(.5,100,.5,0,0);world.addFreshEntity(scooter);
   if(!p.startRiding(scooter,true))throw new AssertionError("Real QA player could not mount scooter");
   scooterId=scooter.getUUID();prepared=true;
  }catch(Throwable x){x.printStackTrace();throw new RuntimeException(x);}
 }
 Scooter rider(){var p=Minecraft.getInstance().player;return p!=null&&p.getVehicle() instanceof Scooter s?s:null;}
 void before(ClientTickEvent.Pre e){
  if(done||phase<0)return;var mc=Minecraft.getInstance();var s=rider();if(s==null)return;
  try{
   var c=cases[phase];
   if(ticks<0){mc.options.keyLeft.setDown(false);mc.options.keyRight.setDown(false);mc.options.keyUp.setDown(false);mc.options.keyDown.setDown(false);mc.player.xxa=0;mc.player.zza=0;s.setDeltaMovement(0,s.getDeltaMovement().y,0);capturedPre=false;return;}
   mc.options.keyLeft.setDown(c.keySign<0);mc.options.keyRight.setDown(c.keySign>0);mc.options.keyUp.setDown(c.gas==1||c.gas==2);mc.options.keyDown.setDown(c.gas==-1||c.gas==2);mc.options.keyJump.setDown(false);mc.options.keyShift.setDown(false);
   preX=s.getX();preZ=s.getZ();preYaw=s.getYRot();preTick=s.level().getGameTime();preForward=s.getViewVector(1);preRight=mc.gameRenderer.getMainCamera().rotation().transform(new Vector3f(1,0,0));capturedPre=true;
   // No velocity injection while controls execute: stopped and blocked cases must use actual motion.
   if(c.shoulder){var camera=ShoulderSurfing.getInstance().getCamera();camera.setYRot(s.getYRot()+c.cameraOffset);camera.setXRot(20);}
   else{mc.player.setYRot(s.getYRot());mc.player.setXRot(closeup?73:28);}
  }catch(Throwable x){finish(x);}
 }
 void after(ClientTickEvent.Post e){
  if(done||!prepared)return;var mc=Minecraft.getInstance();var s=rider();if(s==null)return;
  try{
   if(phase<0){if(++warmup<80||mc.screen!=null)return;Files.createDirectories(out);phase=0;transition();return;}
   if(ticks<0){if(++ticks==0)begin();return;}
   if(capturedPre)clientSamples.add(sample(s,"client"));
   if(++ticks>=cases[phase].duration){phase++;ticks=0;if(phase>=cases.length){finish(null);return;}transition();}
  }catch(Throwable x){finish(x);}
 }
 void transition(){
  var mc=Minecraft.getInstance();var s=rider();var c=cases[phase];ticks=-24;capturedPre=false;renderedTick=-1;if(s==null)throw new AssertionError("Scooter lost between test phases");
  s.setPos(.5,100,.5);s.setYRot(0);s.yRotO=0;s.yBodyRot=0;s.yBodyRotO=0;s.setDeltaMovement(0,0,0);s.setOnGround(true);s.easyDrift(false);s.brakeTurn(c.gas==2);s.mouseSteering(c.mouse);
  // Reset only historical telemetry, not the steering implementation.
  for(String name:new String[]{"localSteer","requestedSteer","previousSteer","previousLean","lean","observedSpeed","previousSpeed","turnSpeed"})try{var f=Scooter.class.getDeclaredField(name);f.setAccessible(true);f.setFloat(s,name.endsWith("Speed")||name.equals("observedSpeed")?(float)Math.abs(c.seed):0);}catch(NoSuchFieldException ignored){}catch(Exception x){throw new RuntimeException(x);}
  var options=ScooterClientOptions.get();options.mouseSteering=c.mouse;options.audioEnabled=false;options.effectsEnabled=false;options.save();
  if(c.shoulder)ShoulderSurfing.getInstance().changePerspective(Perspective.SHOULDER_SURFING);else ShoulderSurfing.getInstance().changePerspective(Perspective.FIRST_PERSON);
  mc.player.setYRot(0);mc.player.setXRot(c.shoulder?20:closeup?73:28);mc.player.yHeadRot=0;
  var server=mc.getSingleplayerServer();if(server==null)throw new AssertionError("Integrated server missing");var id=scooterId;
  server.execute(()->{var remote=server.overworld().getEntity(id);if(remote instanceof Scooter rs){rs.setPos(.5,100,.5);rs.setYRot(0);rs.setDeltaMovement(0,0,0);rs.mouseSteering(c.mouse);rs.easyDrift(false);rs.brakeTurn(c.gas==2);prepareTerrain(server.overworld(),c.terrain);}});
  System.out.println("STEERING_QA phase "+c.name);
 }
 void begin(){
  var mc=Minecraft.getInstance();var s=rider();var c=cases[phase];
  s.setPos(.5,100,.5);s.setYRot(0);s.yRotO=0;s.yBodyRot=0;s.yBodyRotO=0;s.setDeltaMovement(0,0,c.seed);s.setOnGround(!c.terrain.equals("water"));
  // One initial-condition reset after neutral settling; no injection during measured controls.
  for(String name:new String[]{"localSteer","requestedSteer","previousSteer","previousLean","lean","observedSpeed","previousSpeed","turnSpeed"})try{var f=Scooter.class.getDeclaredField(name);f.setAccessible(true);f.setFloat(s,name.endsWith("Speed")||name.equals("observedSpeed")?(float)Math.abs(c.seed):0);}catch(NoSuchFieldException ignored){}catch(Exception x){throw new RuntimeException(x);}
  try{for(String n:new String[]{"lastX","lastZ"}){var f=Scooter.class.getDeclaredField(n);f.setAccessible(true);f.setDouble(s,.5);}}catch(Exception x){throw new RuntimeException(x);}
  mc.player.xxa=0;mc.player.zza=0;mc.player.setYRot(0);mc.player.yHeadRot=0;
  mc.getSingleplayerServer().execute(()->{var remote=mc.getSingleplayerServer().overworld().getEntity(scooterId);if(remote instanceof Scooter rs){rs.setPos(.5,100,.5);rs.setYRot(0);rs.setDeltaMovement(0,0,c.seed);}});
 }
 void prepareTerrain(net.minecraft.server.level.ServerLevel world,String terrain){
  for(int x=-8;x<=8;x++)for(int z=-8;z<=8;z++)for(int y=99;y<=104;y++)world.setBlock(new BlockPos(x,y,z),y==99?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),3);
  if(terrain.equals("cage"))for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)if(x!=0||z!=0)for(int y=100;y<=104;y++)world.setBlock(new BlockPos(x,y,z),Blocks.STONE.defaultBlockState(),3);
  if(terrain.equals("snow"))for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++)world.setBlock(new BlockPos(x,99,z),Blocks.SNOW_BLOCK.defaultBlockState(),3);
  if(terrain.equals("ice"))for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++)world.setBlock(new BlockPos(x,99,z),Blocks.ICE.defaultBlockState(),3);
  if(terrain.equals("water"))for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++)for(int y=99;y<=100;y++)world.setBlock(new BlockPos(x,y,z),Blocks.WATER.defaultBlockState(),3);
 }
 JsonObject sample(Scooter s,String side){
  var o=new JsonObject();o.addProperty("phase",phase);o.addProperty("name",phase>=0&&phase<cases.length?cases[phase].name:"finished");o.addProperty("tick",ticks);o.addProperty("game_tick",s.level().getGameTime());o.addProperty("side",side);o.addProperty("x",s.getX());o.addProperty("z",s.getZ());o.addProperty("yaw",s.getYRot());o.addProperty("vx",s.getDeltaMovement().x);o.addProperty("vz",s.getDeltaMovement().z);o.addProperty("speed",s.getDeltaMovement().horizontalDistance());o.addProperty("wheel",s.steering(1));o.addProperty("ground",s.onGround());o.addProperty("drift",s.drift());o.addProperty("local_control",s.isControlledByLocalInstance());o.addProperty("y",s.getY());o.addProperty("collision",s.horizontalCollision);o.addProperty("water",s.isInWater());
  if(side.equals("client")){o.addProperty("chord",Math.hypot(s.getX()-preX,s.getZ()-preZ));o.addProperty("dx",s.getX()-preX);o.addProperty("dz",s.getZ()-preZ);o.addProperty("yaw_step",net.minecraft.util.Mth.wrapDegrees(s.getYRot()-preYaw));var camera=Minecraft.getInstance().gameRenderer.getMainCamera();var right=camera.rotation().transform(new Vector3f(1,0,0));o.addProperty("camera_right_x",right.x);o.addProperty("camera_right_z",right.z);o.addProperty("camera_yaw",camera.getYRot());if(preForward!=null&&preRight!=null){var change=s.getViewVector(1).subtract(preForward);o.addProperty("screen_right_turn",change.x*preRight.x+change.z*preRight.z);}}
  o.addProperty("hud_kmh",Math.round(s.speed(1)*72));o.addProperty("signed_speed",s.getDeltaMovement().x*-Math.sin(Math.toRadians(s.getYRot()))+s.getDeltaMovement().z*Math.cos(Math.toRadians(s.getYRot())));
  try{o.addProperty("input",((Number)Scooter.class.getMethod("controlSteering").invoke(s)).floatValue());}catch(Exception ignored){}
  if(s.getControllingPassenger()!=null){o.addProperty("rider_yaw",s.getControllingPassenger().getYRot());o.addProperty("rider_strafe",s.getControllingPassenger().xxa);}
  return o;
 }
 void serverTick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post e){
  if(done||phase<0||phase>=cases.length||ticks<0||scooterId==null)return;var entity=e.getServer().overworld().getEntity(scooterId);if(entity instanceof Scooter s)serverSamples.add(sample(s,"server"));
 }
 void render(RenderLevelStageEvent e){
  if(done||e.getStage()!=RenderLevelStageEvent.Stage.AFTER_LEVEL||phase<0||ticks<18)return;var s=rider();if(s==null)return;
  try{
   if(ticks>=30&&screenshotPhase!=phase){screenshotPhase=phase;try(var image=Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget())){image.writeToFile(out.resolve(cases[phase].name+".png"));}}
   if(ticks==renderedTick)return;
   var f=ScooterVisual.class.getDeclaredField("ACTIVE");f.setAccessible(true);var visual=(ScooterVisual)((Map<?,?>)f.get(null)).get(s);if(visual==null||visual.pose()==null)return;
   var instance=((pl.aridlin.kukirin.mixin.ScooterVisualAccessor)(Object)visual).goplanskaScooterInstance();if(instance==null)return;
   var bone=visual.pose().boneMatrix("steering_pivot",new Matrix4f());
   var chassis=new Vector3f(0,0,-1);instance.pose.transformDirection(chassis);var front=new Vector3f(0,0,-1);bone.transformDirection(front);instance.pose.transformDirection(front);
   double chassisYaw=Math.toDegrees(Math.atan2(-chassis.x,chassis.z)),frontYaw=Math.toDegrees(Math.atan2(-front.x,front.z));
   var o=renderSample(s,"visual");o.addProperty("chassis_heading",chassisYaw);o.addProperty("front_heading",frontYaw);o.addProperty("model_wheel_angle",net.minecraft.util.Mth.wrapDegrees(frontYaw-chassisYaw));renderSamples.add(o);renderedTick=ticks;
  }catch(Throwable x){finish(x);}
 }
 JsonObject renderSample(Scooter s,String path){var mc=Minecraft.getInstance();var o=sample(s,"render");o.addProperty("render_path",path);o.addProperty("camera_type",mc.options.getCameraType().name());o.addProperty("shoulder_active",ShoulderSurfing.getInstance().isShoulderSurfing());o.addProperty("camera_decoupled",ShoulderSurfing.getInstance().isCameraDecoupled());o.addProperty("camera_yaw",mc.gameRenderer.getMainCamera().getYRot());o.addProperty("pose_quantum_seconds",com.wf.gemrender.render.PoseCache.getInstance().quantumSeconds());return o;}
 public static void submitted(com.wf.gemrender.gltf.GemRenderGltfModel model,Matrix4f[] palette,Matrix4f matrix){
  var self=INSTANCE;var s=DRAWN.get();if(self==null||s==null||self.done||self.phase<0||self.ticks<18||self.renderedTick==self.ticks)return;
  try{
   int slot=model.layout().nodeTable().slotOfName("steering_pivot");if(slot<0)throw new AssertionError("Actual submitted GLB has no steering_pivot");
   var direction=new Vector3f(0,0,-1);palette[slot].transformDirection(direction);
   // Authored -Z-forward model is rotated Y=180-yaw by the actual renderer.
   double angle=Math.toDegrees(Math.atan2(direction.x,-direction.z));
   var o=self.renderSample(s,"direct");float partial=PARTIAL.get()==null?1:PARTIAL.get();o.addProperty("render_partial",partial);o.addProperty("frame_wheel",s.steering(partial));
   int stemSlot=model.layout().nodeTable().slotOfName("stem");int bodySlot=model.layout().nodeTable().slotOfName("body");var pivotPoint=new Vector3f(.004729188f,.365504978f,-.368380453f);var axisPoint=new Vector3f(0,.961150574f*.22928676f,.276024590f*.22928676f);for(int end=0;end<=1;end++){var expectedPoint=new Vector3f(pivotPoint);if(end==1)expectedPoint.add(axisPoint);if(bodySlot>=0)palette[bodySlot].transformPosition(expectedPoint);var actualPoint=new Vector3f(end==0?new Vector3f():axisPoint);palette[stemSlot].transformPosition(actualPoint);o.addProperty("seam_ring_"+end+"_error",actualPoint.distance(expectedPoint));}
   var matrices=new JsonObject();for(String name:new String[]{"body","steering_pivot","stem","frontwheel","rearwheel"}){int node=model.layout().nodeTable().slotOfName(name);if(node>=0){var values=new JsonArray();for(float value:palette[node].get(new float[16]))values.add(value);matrices.add(name,values);}}o.add("palettes",matrices);o.addProperty("model_wheel_angle",angle);o.addProperty("palette_bone_slot",slot);
   int stem=model.layout().nodeTable().slotOfName("stem");
   for(boolean left:new boolean[]{true,false}){
    var expected=new Vector3f(left?-.244f:.244f,1.168f,-.308f);var pivot=palette[slot].getTranslation(new Vector3f());expected.sub(pivot);palette[stem].transformPosition(expected);expected.mul(1.25f);
    var actual=ScooterGripPose.grip(s,left,partial);o.addProperty(left?"left_grip_error_blocks":"right_grip_error_blocks",actual.distanceTo(new Vec3(expected.x,expected.y,expected.z)));
   }
   self.renderSamples.add(o);self.renderedTick=self.ticks;
  }catch(Throwable x){self.finish(x);}
 }
 void finish(Throwable error){
  if(done)return;done=true;var mc=Minecraft.getInstance();mc.options.keyLeft.setDown(false);mc.options.keyRight.setDown(false);mc.options.keyUp.setDown(false);mc.options.keyDown.setDown(false);
  var result=new JsonObject();result.addProperty("completed",error==null);result.addProperty("renderer",org.lwjgl.opengl.GL11.glGetString(org.lwjgl.opengl.GL11.GL_RENDERER));result.addProperty("scope","isolated full-pack integrated server; real controls with actual collision-clipped motion, stopped and reverse controls");result.add("client",clientSamples);result.add("render",renderSamples);var server=new JsonArray();for(var sample:serverSamples)server.add(sample);result.add("server",server);
  if(error!=null){result.addProperty("error",error.toString());error.printStackTrace();}
  try{Files.createDirectories(out);Files.writeString(out.resolve("runtime-report.json"),new GsonBuilder().setPrettyPrinting().create().toJson(result));}catch(Exception x){x.printStackTrace();}
  mc.stop();
 }
}
