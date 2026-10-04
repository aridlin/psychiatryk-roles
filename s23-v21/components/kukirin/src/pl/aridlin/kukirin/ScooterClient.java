package pl.aridlin.kukirin;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
@EventBusSubscriber(modid="goplanska_kukirin",value=Dist.CLIENT)
public final class ScooterClient {
 private static float previousSpeed,speed,previousRoll,roll;private static boolean jump,forwardDown;private static long lastForwardTap;
 private static final java.util.Map<Integer,Motor> motors=new java.util.HashMap<>();private static Wind wind;private static int controlled=-1;private static boolean sentMouse;
 @SubscribeEvent public static void settings(net.neoforged.neoforge.client.event.RegisterClientCommandsEvent e){e.getDispatcher().register(net.minecraft.commands.Commands.literal("scootersettings").executes(c->{Minecraft.getInstance().execute(()->Minecraft.getInstance().setScreen(new ScooterSettingsScreen()));return 1;}));}
 @SubscribeEvent public static void storageKey(net.neoforged.neoforge.client.event.InputEvent.Key event){var mc=Minecraft.getInstance();if(event.getAction()==org.lwjgl.glfw.GLFW.GLFW_PRESS&&mc.screen==null&&mc.player!=null&&mc.player.getVehicle() instanceof Scooter s&&ScooterUpgradeRecipe.has(s.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.FEET),"chest")&&mc.options.keyInventory.matches(event.getKey(),event.getScanCode())){while(mc.options.keyInventory.consumeClick()){}net.neoforged.neoforge.network.PacketDistributor.sendToServer(new ScooterStorageOpen());}}
 @SubscribeEvent public static void forwardTap(net.neoforged.neoforge.client.event.InputEvent.Key event){var mc=Minecraft.getInstance();if(event.getAction()!=org.lwjgl.glfw.GLFW.GLFW_PRESS||mc.screen!=null||mc.player==null||!(mc.player.getVehicle() instanceof Scooter)||!mc.options.keyUp.matches(event.getKey(),event.getScanCode()))return;long now=System.nanoTime();if(lastForwardTap!=0&&now-lastForwardTap<350_000_000L){net.neoforged.neoforge.network.PacketDistributor.sendToServer(new ScooterLunge());lastForwardTap=0;}else lastForwardTap=now;}
 @SubscribeEvent public static void tick(ClientTickEvent.Post e){
  var mc=Minecraft.getInstance();var options=ScooterClientOptions.get();previousSpeed=speed;previousRoll=roll;if(net.neoforged.fml.ModList.get().isLoaded("lambdynlights_runtime"))ScooterHeadlights.tick();
  Scooter rider=mc.player!=null&&mc.player.getVehicle() instanceof Scooter s?s:null;
  if(rider!=null&&(controlled!=rider.getId()||sentMouse!=options.mouseSteering||mc.player.tickCount%4==0)){net.neoforged.neoforge.network.PacketDistributor.sendToServer(new ScooterControl(options.mouseSteering,rider.steering(1)));rider.mouseSteering(options.mouseSteering);controlled=rider.getId();sentMouse=options.mouseSteering;}if(rider==null){controlled=-1;lastForwardTap=0;}
  boolean down=mc.options.keyJump.isDown();if(down&&!jump&&rider!=null&&mc.screen==null)net.neoforged.neoforge.network.PacketDistributor.sendToServer(new ScooterBoost());jump=down;
  float target=rider==null||!options.effectsEnabled?0:Math.min(1,rider.speed(1)/(float)ScooterHandling.TOP_SPEED);
  speed=Mth.lerp(.15f,speed,target);roll=Mth.lerp(.15f,roll,rider==null||!options.effectsEnabled?0:-rider.steering(1)/28*target*options.cameraLeanDegrees);
  motors.entrySet().removeIf(entry->entry.getValue().isStopped()||(mc.level!=null&&mc.level.getGameTime()-entry.getValue().created>10&&!mc.getSoundManager().isActive(entry.getValue())));
  if(mc.level!=null&&mc.player!=null&&options.audioEnabled&&mc.options.getSoundSourceVolume(SoundSource.MASTER)>0){for(var entity:mc.level.entitiesForRendering())if(entity instanceof Scooter s&&s.isVehicle()&&s.distanceToSqr(mc.player)<32*32&&!motors.containsKey(s.getId())){var motor=new Motor(s);motors.put(s.getId(),motor);mc.getSoundManager().play(motor);}}
  if(rider!=null&&options.audioEnabled&&(wind==null||wind.isStopped()||(mc.level.getGameTime()-wind.created>10&&!mc.getSoundManager().isActive(wind)))){wind=new Wind(rider);mc.getSoundManager().play(wind);}
 }
 private record RiderHead(float head,float oldHead){}
 private static final java.util.Map<java.util.UUID,RiderHead> riderHeads=new java.util.HashMap<>();
 @SubscribeEvent(priority=net.neoforged.bus.api.EventPriority.LOWEST) public static void playerStep(net.neoforged.neoforge.client.event.RenderPlayerEvent.Pre e){if(!e.isCanceled()&&e.getEntity().getVehicle() instanceof Scooter s){var p=e.getEntity();riderHeads.put(p.getUUID(),new RiderHead(p.yHeadRot,p.yHeadRotO));p.yHeadRot=s.getYRot();p.yHeadRotO=s.yRotO;s.yBodyRot=s.getYRot();s.yBodyRotO=s.yRotO;e.getPoseStack().pushPose();e.getPoseStack().translate(0,s.stepOffset(e.getPartialTick()),0);}}
 @SubscribeEvent public static void playerStepEnd(net.neoforged.neoforge.client.event.RenderPlayerEvent.Post e){if(e.getEntity().getVehicle() instanceof Scooter){var saved=riderHeads.remove(e.getEntity().getUUID());if(saved!=null){e.getEntity().yHeadRot=saved.head();e.getEntity().yHeadRotO=saved.oldHead();}e.getPoseStack().popPose();}}
 @SubscribeEvent public static void hideVehicleHearts(net.neoforged.neoforge.client.event.RenderGuiLayerEvent.Pre e){var p=Minecraft.getInstance().player;if(p!=null&&p.getVehicle() instanceof Scooter&&e.getName().equals(net.neoforged.neoforge.client.gui.VanillaGuiLayers.VEHICLE_HEALTH))e.setCanceled(true);}
 @SubscribeEvent public static void hud(net.neoforged.neoforge.client.event.RenderGuiEvent.Post e){var mc=Minecraft.getInstance();if(mc.player==null||!(mc.player.getVehicle() instanceof Scooter s)||!ScooterClientOptions.get().speedometerEnabled||mc.options.hideGui)return;int x=e.getGuiGraphics().guiWidth()/2+95,y=e.getGuiGraphics().guiHeight()-43;String text=String.format(java.util.Locale.ROOT,"%.0f km/h",s.speed(1)*72);e.getGuiGraphics().fill(x-5,y-5,x+65,y+24,0x990b1016);e.getGuiGraphics().drawString(mc.font,text,x,y,0xffffa537,true);e.getGuiGraphics().drawString(mc.font,s.headlights()?"LIGHTS ON":"KuKirin",x,y+11,0xffd1d5dc,true);}
 @SubscribeEvent public static void fov(ViewportEvent.ComputeFov e){if(e.usedConfiguredFov()){var mc=Minecraft.getInstance();e.setFOV(e.getFOV()+Mth.lerp((float)e.getPartialTick(),previousSpeed,speed)*ScooterClientOptions.get().fovBoostDegrees*mc.options.fovEffectScale().get());}}
 @SubscribeEvent public static void camera(ViewportEvent.ComputeCameraAngles e){var mc=Minecraft.getInstance();if(mc.options.getCameraType().isFirstPerson())e.setRoll(e.getRoll()+Mth.lerp((float)e.getPartialTick(),previousRoll,roll));}
 private static boolean alive(Scooter s){var mc=Minecraft.getInstance();return mc.level==s.level()&&!s.isRemoved()&&s.isVehicle()&&mc.player!=null&&s.distanceToSqr(mc.player)<40*40&&ScooterClientOptions.get().audioEnabled;}
 static final class Motor extends AbstractTickableSoundInstance {
  final Scooter scooter;final long created;float lastMotionSpeed,load;Motor(Scooter s){super(Kukirin.MOTOR.get(),SoundSource.NEUTRAL,net.minecraft.util.RandomSource.create());scooter=s;created=s.level().getGameTime();looping=true;delay=0;volume=.01f;}
  @Override public void tick(){if(!alive(scooter)){stop();return;}float current=(float)(scooter.isControlledByLocalInstance()?scooter.getDeltaMovement().horizontalDistance():scooter.speed(1));float acceleration=Math.clamp((current-lastMotionSpeed)/.027f,-1,1);lastMotionSpeed=current;load=Mth.lerp(.22f,load,acceleration);float speed=Math.clamp(current/(float)ScooterHandling.TOP_SPEED,0,1.25f),positive=Math.max(0,load),negative=Math.max(0,-load);pitch=Mth.lerp(.18f,pitch,.65f+.55f*speed+.65f*positive-.22f*negative);volume=Mth.lerp(.18f,volume,ScooterClientOptions.get().motorVolume*Math.clamp(.06f+.30f*speed+.60f*positive-.15f*negative,.02f,1));x=scooter.getX();y=scooter.getY();z=scooter.getZ();}
 }
 static final class Wind extends AbstractTickableSoundInstance {
  final Scooter scooter;final long created;Wind(Scooter s){super(Kukirin.WIND.get(),SoundSource.PLAYERS,net.minecraft.util.RandomSource.create());scooter=s;created=s.level().getGameTime();looping=true;relative=true;attenuation=Attenuation.NONE;volume=.001f;}
  @Override public void tick(){var mc=Minecraft.getInstance();if(!alive(scooter)||mc.player.getVehicle()!=scooter){stop();return;}float speed=Math.min(1,scooter.speed(1)/(float)ScooterHandling.TOP_SPEED);volume=Mth.lerp(.1f,volume,ScooterClientOptions.get().windVolume*speed*speed);pitch=.8f+speed*.4f;}
 }
}
