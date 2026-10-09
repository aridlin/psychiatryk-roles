package pl.aridlin.psychiatrykroles.runtime.client;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import pl.aridlin.psychiatrykroles.runtime.*;
@EventBusSubscriber(modid="psychiatryk_runtime",bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class RuntimeClient {
 @SubscribeEvent public static void setup(FMLClientSetupEvent e){e.enqueueWork(()->{
  RuntimeNetwork.receiver=raw->{if(!SignalClient.tryAccept(raw)&&!HudClient.tryAccept(raw))RuntimeClient.receive(raw);};
  AssetClient.onProgress(JoinProgressOverlay::accept);
  AssetClient.onReady(()->{
   String revision=AssetClient.revision();
   pl.aridlin.psychiatrykroles.peeb.client.PeebMesh.installVisuals(AssetClient.json("peeb/clips").orElse(null),revision);
   ScooterVisuals.install(AssetClient.json("scooter/rules").orElse(null),revision);
   pl.aridlin.psychiatrykroles.runtime.block.RuntimeVariantVisuals.install();
  });
 });}
 static void receive(String raw){try{var view=Schema.view(raw);var mc=Minecraft.getInstance();if(mc.level!=null&&mc.getConnection()!=null)mc.setScreen(new RuntimeScreen(view));}catch(Exception ex){var mc=Minecraft.getInstance();if(mc.player!=null)mc.player.displayClientMessage(Component.literal("This server menu could not be displayed safely. Existing gameplay remains available."),false);}}
}
