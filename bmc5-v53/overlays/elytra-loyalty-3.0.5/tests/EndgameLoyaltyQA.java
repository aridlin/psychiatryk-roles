package pl.aridlin.releaseqa;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.SmithingRecipeInput;
import net.minecraft.world.item.enchantment.Enchantments;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import pl.aridlin.kukirin.Kukirin;
import pl.aridlin.kukirin.ScooterEnchants;
import pl.aridlin.kukirin.ScooterUpgradeRecipe;
import pl.aridlin.kukirin.BoundScooterRecipe;

/** Invoked in the isolated dedicated gate; tests the actual loaded RecipeManager factory. */
public final class EndgameLoyaltyQA {
    public static JsonObject verify(MinecraftServer server) {
        var report = new JsonObject();
        var checks = new JsonArray();
        try {
            var level = server.overworld();
            var registries = level.registryAccess();
            var loyalty = registries.lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.LOYALTY);
            var shard = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(ResourceLocation.parse("psychiatryk_roles:dragon_egg_shard"));
            var input = CraftingInput.of(3, 3, List.of(new ItemStack(Items.IRON_INGOT), new ItemStack(shard), new ItemStack(Items.IRON_INGOT),
                    ItemStack.EMPTY, new ItemStack(Items.ELYTRA), ItemStack.EMPTY, new ItemStack(Items.IRON_INGOT), new ItemStack(Items.REDSTONE_BLOCK), new ItemStack(Items.IRON_INGOT)));
            @SuppressWarnings("unchecked") var recipe = (Recipe<CraftingInput>) server.getRecipeManager().byKey(ResourceLocation.parse("goplanska_kukirin:kukirin_scooter")).orElseThrow().value();
            check(checks, recipe.matches(input, level), "the exact Elytra and egg-shard recipe still matches");
            check(checks, recipe.getResultItem(registries).getEnchantmentLevel(loyalty) == 1, "actual recipe preview has default Loyalty I");
            var crafted = recipe.assemble(input, registries);
            check(checks, crafted.is(Kukirin.ITEM.get()) && crafted.getCount() == 1, "actual recipe factory outputs exactly one scooter");
            check(checks, ScooterEnchants.bound(crafted), "factory retains the bound scooter flag");
            check(checks, crafted.getEnchantmentLevel(loyalty) == 1, "actual assembled Elytra scooter has Loyalty I");
            var crafter = FakePlayerFactory.getMinecraft(level);
            new pl.aridlin.psychiatrykroles.migration.MigrationPolicy().crafted(new PlayerEvent.ItemCraftedEvent(crafter, crafted, new SimpleContainer(9)));
            check(checks, crafter.getUUID().equals(ScooterEnchants.owner(crafted)), "actual crafting hook still binds the result to its crafter");
            check(checks, crafted.getEnchantmentLevel(loyalty) == 1, "owner binding preserves default Loyalty I");
            var upgraded = new ScooterUpgradeRecipe("chest").assemble(new SmithingRecipeInput(ItemStack.EMPTY, crafted, new ItemStack(Items.CHEST)), registries);
            check(checks, ScooterUpgradeRecipe.has(upgraded, "chest") && upgraded.getEnchantmentLevel(loyalty) == 1 && ScooterEnchants.owner(upgraded).equals(crafter.getUUID()), "actual chest upgrade preserves Loyalty, binding and owner");
            crafted.enchant(loyalty, 3);
            var higher = new ScooterUpgradeRecipe("noteblock").assemble(new SmithingRecipeInput(ItemStack.EMPTY, crafted, new ItemStack(Items.NOTE_BLOCK)), registries);
            check(checks, higher.getEnchantmentLevel(loyalty) == 3 && ScooterUpgradeRecipe.has(higher, "noteblock"), "existing higher Loyalty is preserved by subsequent upgrades");
            var ordinary = new ItemStack(Kukirin.ITEM.get());
            check(checks, ordinary.getEnchantmentLevel(loyalty) == 0, "ordinary scooters do not gain default Loyalty");
            var boundOrdinary = new BoundScooterRecipe().assemble(new SmithingRecipeInput(ItemStack.EMPTY, ordinary, new ItemStack(Items.NETHER_STAR)), registries);
            check(checks, boundOrdinary.getEnchantmentLevel(loyalty) == 0 && ScooterEnchants.bound(boundOrdinary), "ordinary Nether Star binding does not gain default Loyalty");
            report.addProperty("success", true);
        } catch (Throwable error) {
            report.addProperty("success", false);
            report.addProperty("error", error.toString());
        }
        report.addProperty("actual_factory_verified", report.get("success").getAsBoolean());
        report.add("checks", checks);
        return report;
    }

    private static void check(JsonArray checks, boolean passed, String description) {
        var row = new JsonObject();
        row.addProperty("passed", passed);
        row.addProperty("description", description);
        checks.add(row);
        if (!passed) throw new AssertionError(description);
    }
}
