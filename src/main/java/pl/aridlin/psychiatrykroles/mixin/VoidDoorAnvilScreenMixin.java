package pl.aridlin.psychiatrykroles.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AnvilScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import pl.aridlin.psychiatrykroles.VoidDoorAnvilInputState;
import pl.aridlin.psychiatrykroles.VoidDoors;

/** Keep the item name readable while offering an empty field for a pair's code. */
@Mixin(AnvilScreen.class)
abstract class VoidDoorAnvilScreenMixin {
    @Unique private VoidDoorAnvilInputState psychiatrykRoles$inputState;

    @WrapOperation(method = "slotChanged(Lnet/minecraft/world/inventory/AbstractContainerMenu;ILnet/minecraft/world/item/ItemStack;)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/EditBox;setValue(Ljava/lang/String;)V"))
    private void psychiatrykRoles$initialCodeField(EditBox name, String itemName, Operation<Void> original,
                                                   AbstractContainerMenu menu, int slot, ItemStack stack) {
        if (psychiatrykRoles$inputState == null) psychiatrykRoles$inputState = new VoidDoorAnvilInputState();
        switch (psychiatrykRoles$inputState.onInput(VoidDoors.pair(stack))) {
            case CLEAR -> original.call(name, "");
            case VANILLA -> original.call(name, itemName);
            case KEEP -> { /* Keep both the text and caret while this pair's input slot refreshes. */ }
        }
    }
}
