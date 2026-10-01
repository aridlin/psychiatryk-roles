package pl.aridlin.psychiatrykroles;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.UUID;

/** Vanilla anvil screen used only as a text input. Its placeholder never enters inventory. */
final class VoidDoorCodeMenu extends AnvilMenu {
    private final VoidDoors doors;
    private final ServerLevel level;
    private final BlockPos door;
    private final UUID pair;
    private String attempt = "";

    VoidDoorCodeMenu(int id, Inventory inventory, VoidDoors doors, ServerLevel level, BlockPos door, UUID pair) {
        super(id, inventory, ContainerLevelAccess.NULL);
        this.doors = doors;
        this.level = level;
        this.door = door;
        this.pair = pair;
        ItemStack prompt = new ItemStack(Items.PAPER);
        // AnvilScreen copies this item's hover name into the input field on open.
        // The menu title supplies the prompt, while an empty item name leaves the field blank.
        ItemTagCompat.setName(prompt, Component.empty());
        inputSlots.setItem(0, prompt);
        broadcastChanges();
    }

    @Override public boolean setItemName(String name) {
        attempt = name == null ? "" : name.strip();
        if (attempt.length() > 50) attempt = "";
        createResult();
        broadcastChanges();
        return true;
    }

    @Override public void createResult() {
        if (resultSlots == null) return;
        ItemStack result = ItemStack.EMPTY;
        if (attempt != null && !attempt.isEmpty()) {
            result = new ItemStack(Items.PAPER);
            ItemTagCompat.setName(result, Component.literal(PsychiatrykRoles.isEnglish(player)
                ? "Submit code" : "Zatwierdź kod"));
        }
        resultSlots.setItem(0, result);
        setMaximumCost(0);
    }

    @Override protected boolean mayPickup(Player player, boolean hasStack) { return hasStack; }
    @Override protected void onTake(Player player, ItemStack stack) { /* clicked handles it without granting paper. */ }

    @Override public void clicked(int slot, int button, ClickType type, Player player) {
        if (slot == getResultSlot() && !resultSlots.getItem(0).isEmpty() && player instanceof ServerPlayer serverPlayer) {
            String submitted = attempt;
            serverPlayer.closeContainer();
            doors.unlock(serverPlayer, level, door, pair, submitted);
            // A vanilla client predicts that shift-click moved the visual paper into
            // inventory. Send the authoritative empty cursor and inventory immediately.
            serverPlayer.inventoryMenu.sendAllDataToRemote();
        }
        // Ignore every other slot: the paper is a visual prompt, not a real item.
    }

    @Override public ItemStack quickMoveStack(Player player, int slot) {
        if (slot == getResultSlot()) clicked(slot, 0, ClickType.QUICK_MOVE, player);
        return ItemStack.EMPTY;
    }

    @Override public boolean stillValid(Player player) {
        return player.level() == level && player.distanceToSqr(door.getX() + .5, door.getY() + .5,
            door.getZ() + .5) <= 64;
    }

    @Override public void removed(Player player) {
        inputSlots.setItem(0, ItemStack.EMPTY);
        resultSlots.setItem(0, ItemStack.EMPTY);
        super.removed(player);
    }
}
