package pl.aridlin.psychiatrykroles.mixin;

import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pl.aridlin.psychiatrykroles.VoidDoors;

@Mixin(ItemStack.class)
abstract class VoidDoorStackMixin {
    @Inject(method = "getMaxStackSize", at = @At("HEAD"), cancellable = true)
    private void psychiatrykRoles$pairStackLimit(CallbackInfoReturnable<Integer> callback) {
        if (VoidDoors.isVoidDoor((ItemStack) (Object) this)
            || pl.aridlin.psychiatrykroles.VoidTrapdoors.isVoidTrapdoor((ItemStack) (Object) this))
            callback.setReturnValue(2);
    }
}
