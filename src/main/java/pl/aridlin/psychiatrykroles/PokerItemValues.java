package pl.aridlin.psychiatrykroles;

import net.minecraft.world.item.Item;
import net.minecraft.core.registries.BuiltInRegistries;

import java.util.LinkedHashMap;
import java.util.Map;

final class PokerItemValues {
    private static final Map<String, Integer> VALUES = values();

    private PokerItemValues() {}

    static int value(Item item) { return value(BuiltInRegistries.ITEM.getKey(item).toString()); }
    static int value(String itemId) { return VALUES.getOrDefault(itemId, 0); }
    static Map<String, Integer> all() { return Map.copyOf(VALUES); }

    private static Map<String, Integer> values() {
        Map<String, Integer> values = new LinkedHashMap<>();
        values.put("minecraft:iron_nugget", 1); values.put("minecraft:gold_nugget", 2);
        values.put("minecraft:amethyst_shard", 3); values.put("minecraft:iron_ingot", 9);
        values.put("minecraft:gold_ingot", 18); values.put("minecraft:name_tag", 20);
        values.put("minecraft:saddle", 20); values.put("minecraft:dragon_breath", 25);
        values.put("minecraft:emerald", 30); values.put("minecraft:goat_horn", 40);
        for (String disc : new String[]{"5", "11", "13", "blocks", "cat", "chirp", "far", "mall", "mellohi", "stal", "strad", "wait", "ward"})
            values.put("minecraft:music_disc_" + disc, 40);
        values.put("minecraft:nautilus_shell", 60); values.put("minecraft:shulker_shell", 80);
        values.put("minecraft:echo_shard", 90); values.put("minecraft:music_disc_pigstep", 90);
        values.put("minecraft:music_disc_otherside", 90); values.put("minecraft:music_disc_relic", 90);
        values.put("minecraft:diamond", 100); values.put("minecraft:ancient_debris", 180);
        values.put("minecraft:netherite_scrap", 225); values.put("minecraft:wither_skeleton_skull", 250);
        values.put("minecraft:heart_of_the_sea", 300); values.put("minecraft:totem_of_undying", 500);
        values.put("minecraft:enchanted_golden_apple", 600); values.put("minecraft:netherite_ingot", 900);
        values.put("minecraft:elytra", 1000); values.put("minecraft:nether_star", 1000);
        return values;
    }
}
