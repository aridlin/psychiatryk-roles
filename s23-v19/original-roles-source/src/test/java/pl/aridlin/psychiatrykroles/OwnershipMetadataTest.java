package pl.aridlin.psychiatrykroles;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OwnershipMetadataTest {
    @Test
    void cleanupRemovesTemporaryOwnershipAndImporterLoreButPreservesRealData() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("psychiatrykImportedItem", true);
        tag.putString("psychiatrykImportedOwner", "owner");
        tag.putString("psychiatrykKillLootOwner", "owner");
        tag.putString("psychiatrykMineLootOwner", "owner");
        tag.putString("psychiatrykDroppedItemOwner", "owner");
        tag.putInt("Damage", 7);

        ListTag lore = new ListTag();
        lore.add(StringTag.valueOf("{\"text\":\"Original lore\"}"));
        lore.add(StringTag.valueOf("{\"text\":\"Imported: this item can be dropped and picked up again.\"}"));
        CompoundTag display = new CompoundTag();
        display.put("Lore", lore);
        tag.put("display", display);

        OwnershipMetadata.clearTemporary(tag);

        assertFalse(tag.contains("psychiatrykImportedItem"));
        assertFalse(tag.contains("psychiatrykImportedOwner"));
        assertFalse(tag.contains("psychiatrykKillLootOwner"));
        assertFalse(tag.contains("psychiatrykMineLootOwner"));
        assertFalse(tag.contains("psychiatrykDroppedItemOwner"));
        assertEquals(7, tag.getInt("Damage"));
        ListTag remainingLore = tag.getCompound("display").getList("Lore", 8);
        assertEquals(1, remainingLore.size());
        assertTrue(remainingLore.getString(0).contains("Original lore"));
    }
}
