package pl.aridlin.psychiatrykroles;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.item.ItemEntity;

import java.util.UUID;

final class DroppedItemOwnership {
    private static final String OWNER = "psychiatrykItemEntityOwner";

    private DroppedItemOwnership() {}

    static void mark(ItemEntity item, UUID owner) {
        mark(item.getPersistentData(), owner);
        item.setTarget(owner);
    }

    static boolean isOwnedBy(ItemEntity item, UUID player) {
        return isOwnedBy(item.getPersistentData(), player)
            || item.getOwner() != null && item.getOwner().getUUID().equals(player);
    }

    static void mark(CompoundTag entityData, UUID owner) { entityData.putUUID(OWNER, owner); }

    static boolean isOwnedBy(CompoundTag entityData, UUID player) {
        return entityData.hasUUID(OWNER) && entityData.getUUID(OWNER).equals(player);
    }
}
