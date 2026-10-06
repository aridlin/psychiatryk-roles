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
 public static float progress(net.minecraft.world.entity.LivingEntity player,float partial){if(player.level()!=world)return -1;var t=ACTIVE.get(player.getUUID());if(t==null)return -1;float age=player.level().getGameTime()-t.start()+partial;if(age>=t.length()){ACTIVE.remove(player.getUUID());return -1;}return age/t.length();}
 public static float weight(float progress){if(progress<0)return 0;return progress<.25f?net.minecraft.util.Mth.sin(progress/.25f*(float)Math.PI/2):progress>.7f?net.minecraft.util.Mth.cos((progress-.7f)/.3f*(float)Math.PI/2):1;}
 @SubscribeEvent public static void input(net.neoforged.neoforge.client.event.MovementInputUpdateEvent e){if(progress(e.getEntity(),0)>=0){var input=e.getInput();input.forwardImpulse=0;input.leftImpulse=0;input.jumping=false;input.up=false;input.down=false;input.left=false;input.right=false;}}
 /** Fast forward faceplant, short grounded hold, then a smooth recovery. */
 public static float pitch(float t){if(t<0||t>=1)return 0;if(t<.20f){float u=t/.20f;return 68*u*u*(3-2*u);}if(t<.58f)return 68+4*net.minecraft.util.Mth.sin((t-.20f)/.38f*(float)Math.PI);float u=(t-.58f)/.42f;return 68*(1-u*u*(3-2*u));}
 public static float roll(float t){return weight(t)*22+weight(t)*net.minecraft.util.Mth.sin(t*(float)Math.PI*3)*7;}
 @SubscribeEvent public static void camera(net.neoforged.neoforge.client.event.ViewportEvent.ComputeCameraAngles e){var mc=Minecraft.getInstance();if(mc.player==null||!mc.options.getCameraType().isFirstPerson())return;float t=progress(mc.player,(float)e.getPartialTick());if(t>=0){e.setRoll(e.getRoll()+roll(t));e.setPitch(e.getPitch()+pitch(t));}}
}
