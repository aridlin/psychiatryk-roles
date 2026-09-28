package pl.aridlin.psychiatrykroles.mixin;

import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pl.aridlin.psychiatrykroles.VoidDoors;

@Mixin(value = ItemStack.class, remap = false)
abstract class VoidDoorStackMixin {
    @Inject(method = {"getMaxStackSize", "m_41741_"}, at = @At("HEAD"), cancellable = true)
    private void psychiatrykRoles$pairStackLimit(CallbackInfoReturnable<Integer> callback) {
        if (VoidDoors.isVoidDoor((ItemStack) (Object) this)) callback.setReturnValue(2);
    }
}
