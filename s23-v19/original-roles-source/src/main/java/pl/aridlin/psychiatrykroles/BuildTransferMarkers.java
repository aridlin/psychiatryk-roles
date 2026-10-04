package pl.aridlin.psychiatrykroles;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The two physical corners of a creative-only build-transfer selection. */
final class BuildTransferMarkers {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(PsychiatrykRoles.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(PsychiatrykRoles.MOD_ID);

    static final DeferredBlock<Block> FIRST = BLOCKS.registerSimpleBlock(
        "build_corner_first", BlockBehaviour.Properties.of().strength(0.2F).sound(SoundType.GLASS).noOcclusion());
    static final DeferredBlock<Block> SECOND = BLOCKS.registerSimpleBlock(
        "build_corner_second", BlockBehaviour.Properties.of().strength(0.2F).sound(SoundType.GLASS).noOcclusion());
    static final DeferredItem<BlockItem> FIRST_ITEM = ITEMS.registerSimpleBlockItem(FIRST, new Item.Properties().stacksTo(1));
    static final DeferredItem<BlockItem> SECOND_ITEM = ITEMS.registerSimpleBlockItem(SECOND, new Item.Properties().stacksTo(1));

    static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
        bus.addListener((BuildCreativeModeTabContentsEvent event) -> {
            if (event.getTabKey() == CreativeModeTabs.OP_BLOCKS) {
                event.accept(FIRST_ITEM);
                event.accept(SECOND_ITEM);
            }
        });
    }

    @SubscribeEvent
    public void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (!event.getPlacedBlock().is(FIRST) && !event.getPlacedBlock().is(SECOND)) return;
        if (!(event.getEntity() instanceof Player player) || !player.isCreative()) event.setCanceled(true);
    }
}
