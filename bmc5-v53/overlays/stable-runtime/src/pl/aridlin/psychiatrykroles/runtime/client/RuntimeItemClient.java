package pl.aridlin.psychiatrykroles.runtime.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import pl.aridlin.psychiatrykroles.runtime.block.RuntimeBlocks;

/** Fixed client hook; future visual variants arrive as preloaded, verified server data. */
@EventBusSubscriber(modid="psychiatryk_runtime",bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class RuntimeItemClient {
    private static RuntimeItemRenderer renderer;
    private RuntimeItemClient() {}

    @SubscribeEvent public static void setup(FMLClientSetupEvent event){
        event.enqueueWork(()->AssetClient.onReady(RuntimeItemVisuals::install));
    }

    @SubscribeEvent public static void extensions(RegisterClientExtensionsEvent event){
        event.registerItem(new IClientItemExtensions(){
            @Override public BlockEntityWithoutLevelRenderer getCustomRenderer(){
                if(renderer==null){var mc=Minecraft.getInstance();renderer=new RuntimeItemRenderer(
                    mc.getBlockEntityRenderDispatcher(),mc.getEntityModels());}
                return renderer;
            }
        },RuntimeBlocks.RUNTIME_GENERIC_ITEM.get());
    }
}
