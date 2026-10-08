package pl.aridlin.psychiatrykroles.peeb.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import pl.aridlin.psychiatrykroles.peeb.PeebDye;
import pl.aridlin.psychiatrykroles.peeb.PeebMode;

@EventBusSubscriber(modid = "psychiatryk_peeb", value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class PeebDyeClient {
   private PeebDyeClient() {}
   @SubscribeEvent public static void colors(RegisterColorHandlersEvent.Item event) {
      event.register((stack, layer) -> layer == 0 ? 0xFF000000 | PeebDye.color(stack) : 0xFFFFFFFF, PeebMode.PEEB.get());
   }
}
