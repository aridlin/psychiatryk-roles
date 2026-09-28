package pl.aridlin.psychiatrykroles.mixin;

import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pl.aridlin.psychiatrykroles.VoidDoors;

@Mixin(value = BlockBehaviour.class, remap = false)
abstract class VoidDoorDropsMixin {
    @Inject(method = {"getDrops", "m_49635_"}, at = @At("RETURN"))
    private void psychiatrykRoles$preserveLink(BlockState state, LootParams.Builder params,
                                              CallbackInfoReturnable<List<ItemStack>> callback) {
        VoidDoors.preserveDropLink(state, params, callback.getReturnValue());
    }
}
