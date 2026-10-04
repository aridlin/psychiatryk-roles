package pl.aridlin.kukirin;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.api.distmarker.Dist;
@EventBusSubscriber(modid="goplanska_kukirin",value=Dist.CLIENT)
public final class ScooterStorageButtons {
 @SubscribeEvent public static void init(net.neoforged.neoforge.client.event.ScreenEvent.Init.Post e){if(!(e.getScreen() instanceof net.minecraft.client.gui.screens.inventory.ContainerScreen screen)||!screen.getTitle().getString().equals("Scooter storage"))return;int x=screen.width/2+92,y=screen.height/2-77;
 e.addListener(net.minecraft.client.gui.components.Button.builder(net.minecraft.network.chat.Component.literal("Settings"),b->{var mc=net.minecraft.client.Minecraft.getInstance();mc.player.closeContainer();mc.setScreen(new ScooterSettingsScreen(()->{mc.setScreen(null);net.neoforged.neoforge.network.PacketDistributor.sendToServer(new ScooterStorageOpen());}));}).bounds(Math.min(x,screen.width-83),y,80,20).build());
 e.addListener(net.minecraft.client.gui.components.Button.builder(net.minecraft.network.chat.Component.literal("Music"),b->{var mc=net.minecraft.client.Minecraft.getInstance();mc.player.closeContainer();ScooterMusicScreen.send("open","");}).bounds(Math.min(x,screen.width-83),y+24,80,20).build());
 }
}
