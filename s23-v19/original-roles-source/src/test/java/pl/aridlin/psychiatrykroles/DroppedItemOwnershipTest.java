package pl.aridlin.psychiatrykroles;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DroppedItemOwnershipTest {
    @Test
    void ownershipLivesOnTheEntityAndDoesNotRequireChangingItemNbt() {
        CompoundTag entityData = new CompoundTag();
        CompoundTag ordinaryItemTag = new CompoundTag();
        UUID owner = UUID.randomUUID();

        DroppedItemOwnership.mark(entityData, owner);

        assertTrue(DroppedItemOwnership.isOwnedBy(entityData, owner));
        assertFalse(DroppedItemOwnership.isOwnedBy(entityData, UUID.randomUUID()));
        assertTrue(ordinaryItemTag.isEmpty());
    }
}
