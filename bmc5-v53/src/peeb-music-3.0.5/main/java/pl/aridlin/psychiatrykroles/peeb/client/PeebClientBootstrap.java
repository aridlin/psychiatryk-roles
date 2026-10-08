package pl.aridlin.psychiatrykroles.peeb.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.InputConstants.Type;
import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.EventBusSubscriber.Bus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import pl.aridlin.psychiatrykroles.peeb.PeebPackets;

@EventBusSubscriber(
   modid = "psychiatryk_peeb",
   value = {Dist.CLIENT},
   bus = Bus.MOD
)
public final class PeebClientBootstrap {
   public static final KeyMapping INTERACT = new KeyMapping(
      "key.psychiatryk_peeb.interact", Type.KEYSYM, InputConstants.UNKNOWN.getValue(), "key.categories.psychiatryk_peeb"
   );
   public static final KeyMapping FREELOOK = new KeyMapping("key.psychiatryk_peeb.freelook", Type.KEYSYM, 342, "key.categories.psychiatryk_peeb");

   @SubscribeEvent
   public static void keys(RegisterKeyMappingsEvent var0) {
      var0.register(INTERACT);
      var0.register(FREELOOK);
   }

   @SubscribeEvent
   public static void setup(FMLClientSetupEvent var0) {
      var0.enqueueWork(() -> {
         PeebPackets.clientHandler(PeebClient::receive);
         PeebPackets.clientConfigHandler(PeebClient::receiveConfig);
      });
   }

   private PeebClientBootstrap() {
   }
}
