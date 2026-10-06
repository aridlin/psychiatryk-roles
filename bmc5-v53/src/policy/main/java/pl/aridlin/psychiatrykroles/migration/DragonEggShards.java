package pl.aridlin.psychiatrykroles.migration;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

@EventBusSubscriber(modid="psychiatryk_roles",bus=EventBusSubscriber.Bus.MOD)
public final class DragonEggShards {
    public static Item ITEM;
    @SubscribeEvent public static void items(net.neoforged.neoforge.registries.RegisterEvent event) {
        event.register(Registries.ITEM,h->h.register(ResourceLocation.fromNamespaceAndPath("psychiatryk_roles","dragon_egg_shard"),ITEM=new Item(new Item.Properties().rarity(Rarity.EPIC))));
    }
}
