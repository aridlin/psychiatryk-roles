package pl.aridlin.psychiatrykroles.mixin;

import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.ShapedRecipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pl.aridlin.psychiatrykroles.VoidDoors;

@Mixin(ShapedRecipe.class)
abstract class VoidDoorRecipeMixin {
    @Inject(method = "assemble(Lnet/minecraft/world/item/crafting/CraftingInput;Lnet/minecraft/core/HolderLookup$Provider;)Lnet/minecraft/world/item/ItemStack;", at = @At("RETURN"))
    private void psychiatrykRoles$pair(CraftingInput input, HolderLookup.Provider registries,
                                      CallbackInfoReturnable<ItemStack> callback) {
        VoidDoors.assignCraftedPair(callback.getReturnValue());
        pl.aridlin.psychiatrykroles.VoidTrapdoors.assignCraftedPair(callback.getReturnValue());
    }
}
