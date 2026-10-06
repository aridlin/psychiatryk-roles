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
 public static SteeringQA INSTANCE;public static final ThreadLocal<Scooter> DRAWN=new ThreadLocal<>();
 record Case(String name,double speed,int keySign,boolean shoulder,boolean mouse,float cameraOffset){}
 final Case[] cases={
  new Case("fp_low_left",.15,-1,false,false,0),new Case("fp_low_right",.15,1,false,false,0),
  new Case("fp_normal_left",.72,-1,false,false,0),new Case("fp_normal_right",.72,1,false,false,0),
  new Case("fp_high_left",200/72d,-1,false,false,0),new Case("fp_high_right",200/72d,1,false,false,0),
  new Case("shoulder_low_left",.15,-1,true,false,90),new Case("shoulder_low_right",.15,1,true,false,-90),
  new Case("shoulder_normal_left",.72,-1,true,false,90),new Case("shoulder_normal_right",.72,1,true,false,-90),
  new Case("shoulder_mouse_left",.72,0,true,true,30),new Case("shoulder_mouse_right",.72,0,true,true,-30),
  new Case("shoulder_mouse_off_left",.72,0,true,false,30),new Case("shoulder_mouse_off_right",.72,0,true,false,-30)
 };
 final Path out=Path.of(System.getProperty("steeringqa.output","outputs/steering-v33-qa"));
 final ConcurrentLinkedQueue<JsonObject> serverSamples=new ConcurrentLinkedQueue<>();
 final JsonArray clientSamples=new JsonArray(),renderSamples=new JsonArray();
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
   var c=cases[phase];mc.options.keyLeft.setDown(c.keySign<0);mc.options.keyRight.setDown(c.keySign>0);mc.options.keyUp.setDown(false);mc.options.keyDown.setDown(false);mc.options.keyJump.setDown(false);mc.options.keyShift.setDown(false);
   double yaw=Math.toRadians(s.getYRot());var v=s.getDeltaMovement();double n=v.horizontalDistance();
   // Hold test speed, never overwrite steering, chassis yaw or movement heading.
   s.setDeltaMovement(n>1e-6?v.x*c.speed/n:-Math.sin(yaw)*c.speed,v.y,n>1e-6?v.z*c.speed/n:Math.cos(yaw)*c.speed);
   if(c.shoulder){var camera=ShoulderSurfing.getInstance().getCamera();camera.setYRot(s.getYRot()+c.cameraOffset);camera.setXRot(20);}
   else{mc.player.setYRot(s.getYRot());mc.player.setXRot(28);}
  }catch(Throwable x){finish(x);}
 }
 void after(ClientTickEvent.Post e){
  if(done||!prepared)return;var mc=Minecraft.getInstance();var s=rider();if(s==null)return;
  try{
   if(phase<0){if(++warmup<80||mc.screen!=null)return;Files.createDirectories(out);phase=0;transition();return;}
   if(ticks>=18)clientSamples.add(sample(s,"client"));
   if(++ticks>=(cases[phase].speed>2?35:45)){phase++;ticks=0;if(phase>=cases.length){finish(null);return;}transition();}
  }catch(Throwable x){finish(x);}
 }
 void transition(){
  var mc=Minecraft.getInstance();var s=rider();var c=cases[phase];if(s==null)throw new AssertionError("Scooter lost between test phases");
  s.setPos(.5,100,.5);s.setYRot(0);s.yRotO=0;s.yBodyRot=0;s.yBodyRotO=0;s.setDeltaMovement(0,0,c.speed);s.setOnGround(true);s.easyDrift(false);s.brakeTurn(false);s.mouseSteering(c.mouse);
  // Reset only historical telemetry, not the steering implementation.
  for(String name:new String[]{"localSteer","requestedSteer","previousSteer","previousLean","lean","observedSpeed","previousSpeed","turnSpeed"})try{var f=Scooter.class.getDeclaredField(name);f.setAccessible(true);f.setFloat(s,name.endsWith("Speed")||name.equals("observedSpeed")?(float)c.speed:0);}catch(NoSuchFieldException ignored){}catch(Exception x){throw new RuntimeException(x);}
  var options=ScooterClientOptions.get();options.mouseSteering=c.mouse;options.audioEnabled=false;options.effectsEnabled=false;options.save();
  if(c.shoulder)ShoulderSurfing.getInstance().changePerspective(Perspective.SHOULDER_SURFING);else ShoulderSurfing.getInstance().changePerspective(Perspective.FIRST_PERSON);
  mc.player.setYRot(0);mc.player.setXRot(c.shoulder?20:28);mc.player.yHeadRot=0;
  var server=mc.getSingleplayerServer();if(server==null)throw new AssertionError("Integrated server missing");var id=scooterId;
  server.execute(()->{var remote=server.overworld().getEntity(id);if(remote instanceof Scooter rs){rs.setPos(.5,100,.5);rs.setYRot(0);rs.setDeltaMovement(0,0,c.speed);rs.mouseSteering(c.mouse);rs.easyDrift(false);rs.brakeTurn(false);}});
  System.out.println("STEERING_QA phase "+c.name);
 }
 JsonObject sample(Scooter s,String side){
  var o=new JsonObject();o.addProperty("phase",phase);o.addProperty("name",phase>=0&&phase<cases.length?cases[phase].name:"finished");o.addProperty("tick",ticks);o.addProperty("game_tick",s.level().getGameTime());o.addProperty("side",side);o.addProperty("x",s.getX());o.addProperty("z",s.getZ());o.addProperty("yaw",s.getYRot());o.addProperty("vx",s.getDeltaMovement().x);o.addProperty("vz",s.getDeltaMovement().z);o.addProperty("speed",s.getDeltaMovement().horizontalDistance());o.addProperty("wheel",s.steering(1));o.addProperty("ground",s.onGround());o.addProperty("drift",s.drift());o.addProperty("local_control",s.isControlledByLocalInstance());
  try{o.addProperty("input",((Number)Scooter.class.getMethod("controlSteering").invoke(s)).floatValue());}catch(Exception ignored){}
  if(s.getControllingPassenger()!=null){o.addProperty("rider_yaw",s.getControllingPassenger().getYRot());o.addProperty("rider_strafe",s.getControllingPassenger().xxa);}
  return o;
 }
 void serverTick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post e){
  if(done||phase<0||phase>=cases.length||ticks<18||scooterId==null)return;var entity=e.getServer().overworld().getEntity(scooterId);if(entity instanceof Scooter s)serverSamples.add(sample(s,"server"));
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
   var o=self.renderSample(s,"direct");o.addProperty("model_wheel_angle",angle);o.addProperty("palette_bone_slot",slot);
   int stem=model.layout().nodeTable().slotOfName("stem");
   for(boolean left:new boolean[]{true,false}){
    var expected=new Vector3f(left?-.244f:.244f,1.168f,-.308f);var pivot=palette[slot].getTranslation(new Vector3f());expected.sub(pivot);palette[stem].transformPosition(expected);expected.mul(1.25f);
    var actual=ScooterGripPose.grip(s,left,1);o.addProperty(left?"left_grip_error_blocks":"right_grip_error_blocks",actual.distanceTo(new Vec3(expected.x,expected.y,expected.z)));
   }
   self.renderSamples.add(o);self.renderedTick=self.ticks;
  }catch(Throwable x){self.finish(x);}
 }
 void finish(Throwable error){
  if(done)return;done=true;var mc=Minecraft.getInstance();mc.options.keyLeft.setDown(false);mc.options.keyRight.setDown(false);
  var result=new JsonObject();result.addProperty("completed",error==null);result.addProperty("renderer",org.lwjgl.opengl.GL11.glGetString(org.lwjgl.opengl.GL11.GL_RENDERER));result.addProperty("scope","isolated full-pack integrated server; constant-speed steering only");result.add("client",clientSamples);result.add("render",renderSamples);var server=new JsonArray();for(var sample:serverSamples)server.add(sample);result.add("server",server);
  if(error!=null){result.addProperty("error",error.toString());error.printStackTrace();}
  try{Files.createDirectories(out);Files.writeString(out.resolve("runtime-report.json"),new GsonBuilder().setPrettyPrinting().create().toJson(result));}catch(Exception x){x.printStackTrace();}
  mc.stop();
 }
}
