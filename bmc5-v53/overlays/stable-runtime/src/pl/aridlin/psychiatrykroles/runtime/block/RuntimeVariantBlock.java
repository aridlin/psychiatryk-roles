package pl.aridlin.psychiatrykroles.runtime.block;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

/** Ordinary collidable block whose visible geometry comes from a bounded client renderer. */
public final class RuntimeVariantBlock extends Block implements EntityBlock {
    public RuntimeVariantBlock(BlockBehaviour.Properties properties){super(properties);}
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new RuntimeVariantBlockEntity(pos,state);}
    @Override protected RenderShape getRenderShape(BlockState state){return RenderShape.INVISIBLE;}

    @Override public ItemStack getCloneItemStack(LevelReader level,BlockPos pos,BlockState state){
        BlockEntity entity=level.getBlockEntity(pos);
        return RuntimeVariantItem.stack(entity instanceof RuntimeVariantBlockEntity variant?variant.variantId():RuntimeVariantBlockEntity.DEFAULT_VARIANT);
    }
    @Override protected List<ItemStack> getDrops(BlockState state,LootParams.Builder loot){
        BlockEntity entity=loot.getOptionalParameter(LootContextParams.BLOCK_ENTITY);
        String variant=entity instanceof RuntimeVariantBlockEntity block?block.variantId():RuntimeVariantBlockEntity.DEFAULT_VARIANT;
        return List.of(RuntimeVariantItem.stack(variant));
    }

    /** Server-side hook for commands or scripted interactions; the registry never changes. */
    public static boolean setVariant(Level level,BlockPos pos,String id){
        if(level==null||level.isClientSide||!(level.getBlockEntity(pos) instanceof RuntimeVariantBlockEntity block))return false;
        block.setVariantId(id);
        return true;
    }
}
