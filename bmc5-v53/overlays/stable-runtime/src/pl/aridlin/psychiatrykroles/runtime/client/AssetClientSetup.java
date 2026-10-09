package pl.aridlin.psychiatrykroles.runtime.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

@EventBusSubscriber(modid="psychiatryk_runtime",bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class AssetClientSetup {
    private AssetClientSetup() {}
    @SubscribeEvent public static void setup(FMLClientSetupEvent event){event.enqueueWork(AssetClient::install);}
}
