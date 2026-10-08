package pl.aridlin.releaseqa;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SmithingRecipeInput;
import net.minecraft.world.item.enchantment.Enchantments;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import pl.aridlin.kukirin.Kukirin;
import pl.aridlin.kukirin.ScooterDyeRecipe;
import pl.aridlin.kukirin.ScooterEnchants;
import pl.aridlin.kukirin.ScooterUpgradeRecipe;
import pl.aridlin.psychiatrykroles.migration.MigrationPolicy;
import pl.aridlin.psychiatrykroles.peeb.PeebMode;

/** Tests loaded recipes and mode APIs; no user inventory/world is accessed. */
public final class ScooterUpgradeQA {
    public static JsonObject verify(MinecraftServer server) {
        JsonObject report = new JsonObject();
        JsonArray checks = new JsonArray();
        try {
            var level = server.overworld();
            var registries = level.registryAccess();
            var loyalty = registries.lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.LOYALTY);
            ItemStack original = new ItemStack(Kukirin.ITEM.get());
            original.set(DataComponents.CUSTOM_NAME, Component.literal("QA component preservation"));
            original.enchant(loyalty, 3);
            CompoundTag custom = new CompoundTag();
            custom.putBoolean(ScooterEnchants.BOUND, true);
            custom.putUUID(ScooterEnchants.OWNER, UUID.fromString("00000000-0000-4000-8000-000000000003"));
            custom.putBoolean(ScooterUpgradeRecipe.key("chest"), true);
            CompoundTag cargo = new CompoundTag();
            cargo.putString("item", "minecraft:diamond"); cargo.putInt("count", 7);
            custom.put("qaStoredCargo", cargo);
            custom.putString("qaMusicState", "retained song");
            original.set(DataComponents.CUSTOM_DATA, CustomData.of(custom));

            int restored = 0;
            for (DyeColor color : DyeColor.values()) {
                String id = "scooter_dye_" + color.getName();
                Recipe<SmithingRecipeInput> recipe = loaded(server, id, "goplanska_kukirin:scooter_dye", checks);
                restored++;
                check(checks, recipe instanceof ScooterDyeRecipe, id + " uses the registered dye serializer");
                SmithingRecipeInput input = new SmithingRecipeInput(ItemStack.EMPTY, original, new ItemStack(DyeItem.byColor(color)));
                check(checks, recipe.matches(input, level), id + " matches an empty template, scooter and correct dye");
                check(checks, server.getRecipeManager().getRecipeFor(RecipeType.SMITHING, input, level).isPresent(), id + " is discoverable through native smithing lookup");
                ItemStack result = recipe.assemble(input, registries);
                check(checks, ScooterDyeRecipe.variant(result) == color.getId() + 1, id + " sets the intended accent color");
                preserved(checks, original, result, ScooterDyeRecipe.COLOR, id);
                check(checks, !recipe.matches(new SmithingRecipeInput(ItemStack.EMPTY, result, input.addition()), level), id + " rejects already matching color");
            }

            var boundRecipe = loaded(server, "bound_scooter", "goplanska_kukirin:bound_scooter", checks);
            restored++;
            ItemStack unbound = original.copy();
            CustomData.update(DataComponents.CUSTOM_DATA, unbound, tag -> {tag.remove(ScooterEnchants.BOUND); tag.remove(ScooterEnchants.OWNER);});
            var starInput = new SmithingRecipeInput(ItemStack.EMPTY, unbound, new ItemStack(Items.NETHER_STAR));
            check(checks, boundRecipe.matches(starInput, level), "loaded Nether Star recipe matches an unbound scooter");
            check(checks, server.getRecipeManager().getRecipeFor(RecipeType.SMITHING, starInput, level).isPresent(), "Nether Star binding is discoverable through native smithing lookup");
            ItemStack bound = boundRecipe.assemble(starInput, registries);
            check(checks, ScooterEnchants.bound(bound), "loaded Nether Star assembly sets binding flag");
            preserved(checks, unbound, bound, ScooterEnchants.BOUND, "Nether Star binding");
            check(checks, !boundRecipe.matches(new SmithingRecipeInput(ItemStack.EMPTY, bound, starInput.addition()), level), "Nether Star recipe rejects an already bound scooter");
            var crafter = FakePlayerFactory.getMinecraft(level);
            ScooterEnchants.bind(bound, crafter);
            check(checks, crafter.getUUID().equals(ScooterEnchants.owner(bound)) && bound.getEnchantmentLevel(loyalty) == 3, "normal owner binding preserves existing Loyalty III");

            for (String upgrade : List.of("chest", "jukebox", "noteblock", "saddle", "netherite", "infinite")) {
                String id = upgrade.equals("netherite") ? "scooter_upgrade_netherite" : "scooter_" + upgrade;
                var recipe = loaded(server, id, "goplanska_kukirin:scooter_upgrade", checks); restored++;
                var item = switch (upgrade) {case "chest" -> Items.CHEST; case "jukebox" -> Items.JUKEBOX; case "noteblock" -> Items.NOTE_BLOCK; case "saddle" -> Items.SADDLE; case "netherite" -> Items.NETHERITE_BLOCK; default -> Items.COMMAND_BLOCK;};
                ItemStack base = original.copy();
                CustomData.update(DataComponents.CUSTOM_DATA, base, tag -> tag.remove(ScooterUpgradeRecipe.key(upgrade)));
                var input = new SmithingRecipeInput(ItemStack.EMPTY, base, new ItemStack(item));
                check(checks, recipe.matches(input, level), id + " matches its original addition and empty template");
                check(checks, server.getRecipeManager().getRecipeFor(RecipeType.SMITHING, input, level).isPresent(), id + " is discoverable through native smithing lookup");
                ItemStack result = recipe.assemble(input, registries);
                check(checks, ScooterUpgradeRecipe.has(result, upgrade), id + " sets the intended upgrade");
                preserved(checks, base, result, ScooterUpgradeRecipe.key(upgrade), id);
                check(checks, !recipe.matches(new SmithingRecipeInput(ItemStack.EMPTY, result, input.addition()), level), id + " rejects an already installed upgrade");
                if (upgrade.equals("netherite") || upgrade.equals("infinite")) {
                    CustomData.update(DataComponents.CUSTOM_DATA, base, tag -> tag.putBoolean("GoplanskaRental", true));
                    check(checks, !recipe.matches(input, level), id + " preserves the rental restriction");
                }
            }
            check(checks, restored == 23, "all 23 existing modification recipes are loaded and assembled");
            report.addProperty("actual_loaded_recipes_verified", true);
            report.addProperty("assembly_verified", true);
            report.addProperty("restored_recipe_count", restored);

            for (String id : List.of("goplanska_kukirin:scooter_dismantler", "goplanska_kukirin:unlisted_upgrade", "psychiatryk_roles:unlisted_custom_creation")) {
                check(checks, !MigrationPolicy.allowRecipe(ResourceLocation.parse(id), JsonParser.parseString("{\"type\":\"goplanska_kukirin:scooter_upgrade\",\"upgrade\":\"chest\"}")), "policy still blocks " + id);
                check(checks, server.getRecipeManager().byKey(ResourceLocation.parse(id)).isEmpty(), "loaded RecipeManager has no blocked " + id);
            }
            check(checks, MigrationPolicy.allowRecipe(ResourceLocation.parse("minecraft:qa_external"), JsonParser.parseString("{\"type\":\"minecraft:crafting_shapeless\"}")), "unrelated external recipe policy remains unchanged");
            check(checks, !MigrationPolicy.allowRecipe(ResourceLocation.parse("minecraft:qa_station"), JsonParser.parseString("{\"result\":{\"id\":\"morevillagers:gardening_table\"}}")), "disabled villager workstation output remains blocked");

            for (var slot : List.of(EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND, EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET)) crafter.setItemSlot(slot, ItemStack.EMPTY);
            crafter.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET));
            crafter.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.DIAMOND_CHESTPLATE));
            crafter.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.DIAMOND_LEGGINGS));
            crafter.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.DIAMOND_BOOTS));
            crafter.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(PeebMode.PEEB.get()));
            check(checks, PeebMode.holding(crafter), "mainhand held Peeb is active with full armor");
            crafter.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            crafter.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(PeebMode.PEEB.get()));
            check(checks, PeebMode.holding(crafter), "offhand held Peeb is active with full armor");
            report.addProperty("held_peeb_with_armor_verified", true);
            crafter.setShiftKeyDown(true);
            var use = PeebMode.PEEB.get().use(level, crafter, InteractionHand.OFF_HAND);
            check(checks, use.getResult() == InteractionResult.FAIL && crafter.getItemBySlot(EquipmentSlot.CHEST).is(Items.DIAMOND_CHESTPLATE), "shift equip is still blocked while other armor slots are occupied");
            check(checks, !PeebMode.PEEB.get().canEquip(crafter.getOffhandItem(), EquipmentSlot.CHEST, crafter), "wearing API still rejects other armor");
            crafter.setShiftKeyDown(false);
            crafter.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            crafter.setItemSlot(EquipmentSlot.CHEST, new ItemStack(PeebMode.PEEB.get()));
            check(checks, PeebMode.worn(crafter) && !PeebMode.holding(crafter), "worn Peeb with other armor is inactive");
            for (var slot : List.of(EquipmentSlot.HEAD, EquipmentSlot.LEGS, EquipmentSlot.FEET)) crafter.setItemSlot(slot, ItemStack.EMPTY);
            check(checks, PeebMode.holding(crafter), "worn Peeb without other armor is active");
            check(checks, PeebMode.PEEB.get().canEquip(crafter.getItemBySlot(EquipmentSlot.CHEST), EquipmentSlot.CHEST, crafter), "wearing API accepts empty other armor slots");
            crafter.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
            check(checks, !PeebMode.holding(crafter), "no held or worn Peeb is inactive");
            report.addProperty("worn_peeb_requires_other_armor_empty_verified", true);
            report.addProperty("success", true);
        } catch (Throwable error) {
            report.addProperty("success", false); report.addProperty("error", error.toString()); error.printStackTrace();
        }
        report.add("checks", checks);
        return report;
    }

    @SuppressWarnings("unchecked")
    private static Recipe<SmithingRecipeInput> loaded(MinecraftServer server, String path, String type, JsonArray checks) throws Exception {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("goplanska_kukirin", path);
        var holder = server.getRecipeManager().byKey(id);
        check(checks, holder.isPresent(), "actual loaded RecipeManager contains " + id);
        var json = new JsonObject(); json.addProperty("type", type);
        check(checks, MigrationPolicy.allowRecipe(id, json), "policy allows exact typed modification " + id);
        json.addProperty("type", "minecraft:crafting_shaped");
        check(checks, !MigrationPolicy.allowRecipe(id, json), "policy rejects substituted recipe type at " + id);
        return (Recipe<SmithingRecipeInput>) holder.orElseThrow().value();
    }

    private static void preserved(JsonArray checks, ItemStack original, ItemStack result, String changedKey, String label) {
        var before = original.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        var after = result.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        before.remove(changedKey); after.remove(changedKey);
        check(checks, before.equals(after), label + " preserves owner, storage, music and other custom state");
        var normalized = result.copy(); normalized.set(DataComponents.CUSTOM_DATA, original.get(DataComponents.CUSTOM_DATA));
        check(checks, result.getCount() == 1 && ItemStack.isSameItemSameComponents(original, normalized), label + " preserves every other component including name and Loyalty");
    }

    private static void check(JsonArray checks, boolean passed, String description) {
        JsonObject row = new JsonObject(); row.addProperty("passed", passed); row.addProperty("description", description); checks.add(row);
        if (!passed) throw new AssertionError(description);
    }
}
