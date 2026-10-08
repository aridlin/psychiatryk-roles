package pl.aridlin.psychiatrykroles.runtime.block;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Kept on the client event bus; dedicated servers never load the renderer class. */
@EventBusSubscriber(modid="psychiatryk_runtime",bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class RuntimeVariantClientEvents {
    private RuntimeVariantClientEvents(){}
    @SubscribeEvent public static void register(EntityRenderersEvent.RegisterRenderers event){
        event.registerBlockEntityRenderer(RuntimeBlocks.RUNTIME_ENTITY.get(),RuntimeVariantBlockRenderer::new);
    }
}
