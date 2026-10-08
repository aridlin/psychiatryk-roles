package pl.aridlin.kukirin;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.EventBusSubscriber.Bus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

@EventBusSubscriber(
   modid = "goplanska_kukirin",
   value = {Dist.CLIENT},
   bus = Bus.MOD
)
public final class MusicImportClientBootstrap {
   @SubscribeEvent
   public static void setup(FMLClientSetupEvent var0) {
      var0.enqueueWork(() -> {
         MusicImports.clientHandler(YouTubeMusicScreen::receive);
         MusicImports.completedHandler(YouTubeMusicScreen::completed);
      });
   }

   private MusicImportClientBootstrap() {
   }
}
