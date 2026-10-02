package pl.aridlin.psychiatrykroles;

import net.minecraft.core.component.DataComponents;
import net.minecraft.advancements.critereon.BlockPredicate;
import net.minecraft.server.network.Filterable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.AdventureModePredicate;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.Unbreakable;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.block.Block;

import java.util.List;
import java.util.Arrays;
import java.util.Optional;
import java.util.function.Consumer;

/** Persistent custom item metadata on 1.21's data component system. */
final class ItemTagCompat {
    private ItemTagCompat() {}

    static boolean has(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null && !data.isEmpty();
    }

    static CompoundTag read(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
    }

    static void update(ItemStack stack, Consumer<CompoundTag> action) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, action);
    }

    static void set(ItemStack stack, CompoundTag tag) {
        if (tag == null || tag.isEmpty()) stack.remove(DataComponents.CUSTOM_DATA);
        else CustomData.set(DataComponents.CUSTOM_DATA, stack, tag);
    }

    static void putBoolean(ItemStack stack, String key, boolean value) {
        update(stack, tag -> tag.putBoolean(key, value));
    }

    static void putString(ItemStack stack, String key, String value) {
        update(stack, tag -> tag.putString(key, value));
    }

    static void putLong(ItemStack stack, String key, long value) {
        update(stack, tag -> tag.putLong(key, value));
    }

    static void remove(ItemStack stack, String key) {
        update(stack, tag -> tag.remove(key));
    }

    static void setName(ItemStack stack, Component name) {
        stack.set(DataComponents.CUSTOM_NAME, name);
    }

    static void setLore(ItemStack stack, List<Component> lines) {
        stack.set(DataComponents.LORE, new ItemLore(lines));
    }

    static void appendLore(ItemStack stack, Component... lines) {
        ItemLore old = stack.getOrDefault(DataComponents.LORE, ItemLore.EMPTY);
        java.util.ArrayList<Component> all = new java.util.ArrayList<>(old.lines());
        all.addAll(List.of(lines));
        setLore(stack, all);
    }

    static void setWrittenBook(ItemStack stack, String title, String author, String[] pages) {
        stack.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(
            Filterable.passThrough(title), author, 0,
            Arrays.stream(pages).map(page -> Filterable.<Component>passThrough(Component.literal(page))).toList(), true));
    }

    static void setUnbreakable(ItemStack stack) {
        stack.set(DataComponents.UNBREAKABLE, new Unbreakable(false));
    }

    static void setCanPlaceAnywhere(ItemStack stack) {
        stack.set(DataComponents.CAN_PLACE_ON, new AdventureModePredicate(
            List.of(new BlockPredicate(Optional.empty(), Optional.empty(), Optional.empty())), false));
    }

    static void setCanBreak(ItemStack stack, Block... blocks) {
        stack.set(DataComponents.CAN_BREAK, new AdventureModePredicate(
            List.of(BlockPredicate.Builder.block().of(blocks).build()), false));
    }

    static void setCanBreakAnywhere(ItemStack stack) {
        stack.set(DataComponents.CAN_BREAK, new AdventureModePredicate(
            List.of(new BlockPredicate(Optional.empty(), Optional.empty(), Optional.empty())), false));
    }
}
