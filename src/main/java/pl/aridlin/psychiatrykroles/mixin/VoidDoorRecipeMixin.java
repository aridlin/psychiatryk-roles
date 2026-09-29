package pl.aridlin.psychiatrykroles.mixin;

import net.minecraft.core.RegistryAccess;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.ShapedRecipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pl.aridlin.psychiatrykroles.VoidDoors;

@Mixin(value = ShapedRecipe.class, remap = false)
abstract class VoidDoorRecipeMixin {
    // Explicit official/SRG names support both development and the reobfuscated server.
    // Assemble runs BEFORE quick-move copies the result into the inventory.
    @Inject(method = {
        "assemble(Lnet/minecraft/world/inventory/CraftingContainer;Lnet/minecraft/core/RegistryAccess;)Lnet/minecraft/world/item/ItemStack;",
        "m_5874_(Lnet/minecraft/world/inventory/CraftingContainer;Lnet/minecraft/core/RegistryAccess;)Lnet/minecraft/world/item/ItemStack;"
    }, at = @At("RETURN"))
    private void psychiatrykRoles$pair(CraftingContainer input, RegistryAccess registries,
                                      CallbackInfoReturnable<ItemStack> callback) {
        VoidDoors.assignCraftedPair(callback.getReturnValue());
        pl.aridlin.psychiatrykroles.VoidTrapdoors.assignCraftedPair(callback.getReturnValue());
    }
}
