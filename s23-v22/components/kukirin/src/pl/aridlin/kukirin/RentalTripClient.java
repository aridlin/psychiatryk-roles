package pl.aridlin.kukirin;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.api.distmarker.Dist;
/** Render-only tumble interpolated from a single authoritative event. */
@EventBusSubscriber(modid="goplanska_kukirin",value=Dist.CLIENT)
public final class RentalTripClient {
 record Trip(long start,int length){}
 static final java.util.Map<java.util.UUID,Trip> ACTIVE=new java.util.HashMap<>();
 static net.minecraft.client.multiplayer.ClientLevel world;
 public static void receive(RentalTripPacket packet){var level=Minecraft.getInstance().level;if(level!=world){ACTIVE.clear();world=level;}if(level!=null)ACTIVE.put(packet.player(),new Trip(level.getGameTime(),packet.ticks()));}
 public static float progress(Player player,float partial){if(player.level()!=world)return -1;var t=ACTIVE.get(player.getUUID());if(t==null)return -1;float age=player.level().getGameTime()-t.start()+partial;if(age>=t.length()){ACTIVE.remove(player.getUUID());return -1;}return age/t.length();}
 public static float weight(float progress){if(progress<0)return 0;return progress<.25f?net.minecraft.util.Mth.sin(progress/.25f*(float)Math.PI/2):progress>.7f?net.minecraft.util.Mth.cos((progress-.7f)/.3f*(float)Math.PI/2):1;}
 @SubscribeEvent public static void input(net.neoforged.neoforge.client.event.MovementInputUpdateEvent e){if(progress(e.getEntity(),0)>=0){var input=e.getInput();input.forwardImpulse=0;input.leftImpulse=0;input.jumping=false;input.up=false;input.down=false;input.left=false;input.right=false;}}
 @SubscribeEvent public static void camera(net.neoforged.neoforge.client.event.ViewportEvent.ComputeCameraAngles e){var mc=Minecraft.getInstance();if(mc.player==null||!mc.options.getCameraType().isFirstPerson())return;float t=progress(mc.player,(float)e.getPartialTick());if(t>=0){e.setRoll(e.getRoll()+weight(t)*35);e.setPitch(e.getPitch()+weight(t)*28);}}
}
