package pl.aridlin.kukirin;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.api.distmarker.Dist;
@EventBusSubscriber(modid="goplanska_kukirin",value=Dist.CLIENT)
public final class ScooterStorageButtons {
 @SubscribeEvent public static void init(net.neoforged.neoforge.client.event.ScreenEvent.Init.Post e){if(!(e.getScreen() instanceof net.minecraft.client.gui.screens.inventory.ContainerScreen screen)||!screen.getTitle().getString().equals("Scooter storage"))return;int x=Math.min(screen.width/2+92,screen.width-83),y=screen.height/2-77;
 String[] keys={"settings","music","guide"};for(int i=0;i<keys.length;i++){String key=keys[i];e.addListener(net.minecraft.client.gui.components.Button.builder(net.minecraft.network.chat.Component.translatable("scooter.menu."+key),b->{var mc=net.minecraft.client.Minecraft.getInstance();mc.player.closeContainer();Runnable back=ScooterMenus::backToStorage;if(key.equals("settings"))mc.setScreen(new ScooterSettingsScreen(back));else if(key.equals("music"))ScooterMenus.requestMusic(back);else mc.setScreen(new ScooterGuideScreen(back));}).bounds(x,y+i*24,80,20).build());}
 }
}
