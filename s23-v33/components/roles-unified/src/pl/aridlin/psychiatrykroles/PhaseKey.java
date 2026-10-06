package pl.aridlin.psychiatrykroles;
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
@EventBusSubscriber(modid="psychiatryk_roles",value=Dist.CLIENT)
public final class PhaseKey {
 public static final KeyMapping KEY=new KeyMapping("key.psychiatryk_roles.phase_charm",KeyConflictContext.IN_GAME,InputConstants.Type.KEYSYM,GLFW.GLFW_KEY_RIGHT_ALT,"key.categories.goplanska");
 @SubscribeEvent public static void tick(ClientTickEvent.Post e){var mc=Minecraft.getInstance();while(KEY.consumeClick())if(mc.player!=null&&mc.screen==null&&mc.getConnection()!=null)PacketDistributor.sendToServer(PhasePacket.INSTANCE);}
 @EventBusSubscriber(modid="psychiatryk_roles",value=Dist.CLIENT,bus=EventBusSubscriber.Bus.MOD)
 public static final class Registration {@SubscribeEvent public static void keys(RegisterKeyMappingsEvent e){e.register(KEY);}}
}
