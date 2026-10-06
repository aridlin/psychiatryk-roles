package pl.aridlin.partymarkers;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import pl.aridlin.portablechams.api.Highlights;
import pl.aridlin.portablechams.api.HighlightStyle;

/** Permissions/party membership come from the server; the renderer remains independent. */
@EventBusSubscriber(modid="goplanska_party_markers",value=Dist.CLIENT,bus=EventBusSubscriber.Bus.MOD)
public final class HighlightsAdapter {
    @SubscribeEvent public static void setup(net.neoforged.fml.event.lifecycle.FMLClientSetupEvent event) {
        event.enqueueWork(()->Highlights.register(ResourceLocation.fromNamespaceAndPath("goplanska_parties","members"),targets->{
            var mc=Minecraft.getInstance();if(mc.player==null||mc.level==null)return;
            for(var member:ClientWaypoints.members()) {
                if(!member.dimension().equals(mc.level.dimension().location().toString()))continue;
                var entity=mc.level.getEntity(member.entityId());
                if(entity==null||!entity.getUUID().equals(member.uuid())||entity==mc.player)continue;
                if(mc.options.getCameraType().isFirstPerson()&&mc.player.getVehicle()==entity)continue;
                targets.entity(entity,HighlightStyle.halftone(member.color()));
            }
            if(pl.aridlin.psychiatrykroles.LootLens.active(mc.player)) {
                var range=mc.player.getBoundingBox().inflate(32);
                for(var entity:mc.level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,range))
                    targets.entity(entity,HighlightStyle.halftone(pl.aridlin.psychiatrykroles.LootLens.color(entity.getItem())));
            }
        }));
    }
}
