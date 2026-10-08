package pl.aridlin.kukirin;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

/** A disconnected server's prediction settings must not leak into another connection. */
@EventBusSubscriber(modid="goplanska_kukirin", value=Dist.CLIENT)
public final class ScooterTuningClient {
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        ScooterTuning.resetClient();
    }
}
