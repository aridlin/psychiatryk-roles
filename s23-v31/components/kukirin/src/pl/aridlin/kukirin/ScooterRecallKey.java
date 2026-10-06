package pl.aridlin.kukirin;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.api.distmarker.Dist;
@EventBusSubscriber(modid="goplanska_kukirin",value=Dist.CLIENT,bus=EventBusSubscriber.Bus.MOD)
public final class ScooterRecallKey {
 public static final net.minecraft.client.KeyMapping RECALL=new net.minecraft.client.KeyMapping("key.goplanska_kukirin.recall",net.neoforged.neoforge.client.settings.KeyConflictContext.IN_GAME,com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM,org.lwjgl.glfw.GLFW.GLFW_KEY_R,"key.categories.goplanska_kukirin");
 @SubscribeEvent public static void keys(net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent e){e.register(RECALL);}
 @EventBusSubscriber(modid="goplanska_kukirin",value=Dist.CLIENT)public static final class Tick {
 @SubscribeEvent public static void tick(net.neoforged.neoforge.client.event.ClientTickEvent.Post e){var mc=net.minecraft.client.Minecraft.getInstance();while(RECALL.consumeClick())if(mc.player!=null&&mc.screen==null&&mc.getConnection()!=null)mc.getConnection().sendCommand("scooterrecall");}
 }
}
