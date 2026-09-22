package pl.aridlin.psychiatrykroles;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

final class OwnershipMetadata {
    private OwnershipMetadata() {}

    static void clearTemporary(CompoundTag tag) {
        tag.remove("psychiatrykImportedItem");
        tag.remove("psychiatrykImportedOwner");
        tag.remove("psychiatrykKillLootOwner");
        tag.remove("psychiatrykMineLootOwner");
        tag.remove("psychiatrykDroppedItemOwner");

        if (!tag.contains("display", Tag.TAG_COMPOUND)) {
            return;
        }
        CompoundTag display = tag.getCompound("display");
        if (display.contains("Lore", Tag.TAG_LIST)) {
            ListTag lore = display.getList("Lore", Tag.TAG_STRING);
            for (int index = lore.size() - 1; index >= 0; index--) {
                String line = lore.getString(index);
                if (line.contains("Zaimportowano:") || line.contains("Imported:")) {
                    lore.remove(index);
                }
            }
            if (lore.isEmpty()) {
                display.remove("Lore");
            } else {
                display.put("Lore", lore);
            }
        }
        if (display.isEmpty()) {
            tag.remove("display");
        } else {
            tag.put("display", display);
        }
    }
}
