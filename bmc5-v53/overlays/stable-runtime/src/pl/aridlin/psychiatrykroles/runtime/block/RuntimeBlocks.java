package pl.aridlin.psychiatrykroles.runtime.block;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** One stable registry entry; the server can change its bounded visual variants live. */
public final class RuntimeBlocks {
    private RuntimeBlocks() {}
    public static final String MODID="psychiatryk_runtime";
    public static final DeferredRegister.Blocks BLOCKS=DeferredRegister.createBlocks(MODID);
    public static final DeferredRegister.Items ITEMS=DeferredRegister.createItems(MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES=DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE,MODID);

    public static final DeferredBlock<RuntimeVariantBlock> RUNTIME_BLOCK=BLOCKS.register("runtime_block",
        ()->new RuntimeVariantBlock(BlockBehaviour.Properties.of().strength(2.0f).noOcclusion()));
    public static final DeferredItem<RuntimeVariantItem> RUNTIME_ITEM=ITEMS.register("runtime_block",
        ()->new RuntimeVariantItem(RUNTIME_BLOCK.get(),new Item.Properties()));
    /** Fixed registry identity; server data supplies names, recipes, actions, and bounded visual variants. */
    public static final DeferredItem<Item> RUNTIME_GENERIC_ITEM=ITEMS.register("runtime_item",
        ()->new Item(new Item.Properties()));
    public static final DeferredHolder<BlockEntityType<?>,BlockEntityType<RuntimeVariantBlockEntity>> RUNTIME_ENTITY=
        BLOCK_ENTITIES.register("runtime_block",()->BlockEntityType.Builder.of(RuntimeVariantBlockEntity::new,RUNTIME_BLOCK.get()).build(null));

    /** Call once from the mod constructor before registry events fire. */
    public static void register(IEventBus bus){
        BLOCKS.register(bus);
        ITEMS.register(bus);
        BLOCK_ENTITIES.register(bus);
    }
}
