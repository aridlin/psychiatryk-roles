package pl.aridlin.psychiatrykroles.runtime.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** A generic item can carry any approved server variant without a new item registry ID. */
public final class RuntimeVariantItem extends BlockItem {
    private static final String DATA_KEY="psychiatryk_runtime_variant";
    public RuntimeVariantItem(Block block,Properties properties){super(block,properties);}

    public static ItemStack stack(String variant){
        String checked=RuntimeVariantBlockEntity.requireVariantId(variant);
        ItemStack stack=new ItemStack(RuntimeBlocks.RUNTIME_ITEM.get());
        CompoundTag tag=new CompoundTag();tag.putString(DATA_KEY,checked);
        stack.set(DataComponents.CUSTOM_DATA,CustomData.of(tag));
        return stack;
    }
    public static String variantId(ItemStack stack){
        if(stack==null||stack.isEmpty())return RuntimeVariantBlockEntity.DEFAULT_VARIANT;
        CustomData data=stack.get(DataComponents.CUSTOM_DATA);
        if(data==null)return RuntimeVariantBlockEntity.DEFAULT_VARIANT;
        String candidate=data.copyTag().getString(DATA_KEY);
        return RuntimeVariantBlockEntity.validVariantId(candidate)?candidate:RuntimeVariantBlockEntity.DEFAULT_VARIANT;
    }
    @Override protected boolean updateCustomBlockEntityTag(BlockPos pos,Level level,Player player,ItemStack stack,BlockState state){
        // Ignore vanilla operator-only block-entity NBT. Only this small identifier is accepted.
        return RuntimeVariantBlock.setVariant(level,pos,variantId(stack));
    }
}
