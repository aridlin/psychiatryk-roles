package pl.aridlin.starter;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;
@EventBusSubscriber(modid="goplanska_starter",value=Dist.CLIENT)
public final class MirrorKey {
 public static final KeyMapping KEY=new KeyMapping("key.goplanska_starter.return_mirror",KeyConflictContext.IN_GAME,InputConstants.Type.KEYSYM,GLFW.GLFW_KEY_K,"key.categories.goplanska");
 @SubscribeEvent public static void tick(ClientTickEvent.Post e){var mc=Minecraft.getInstance();while(KEY.consumeClick())if(mc.player!=null&&mc.screen==null&&mc.getConnection()!=null)PacketDistributor.sendToServer(MirrorPacket.INSTANCE);}
 @EventBusSubscriber(modid="goplanska_starter",value=Dist.CLIENT,bus=EventBusSubscriber.Bus.MOD)
 public static final class Registration {@SubscribeEvent public static void keys(RegisterKeyMappingsEvent e){e.register(KEY);}}
}
