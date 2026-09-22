package pl.aridlin.psychiatrykroles;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecipeResourcesTest {
    @Test
    void everyConsultantRecipeCarriesFunctionalMarkersInItsResult() throws Exception {
        Map<String, String> recipes = Map.ofEntries(
            Map.entry("recipe_book", "psychiatrykRecipeBook"),
            Map.entry("consultant_sword", "psychiatrykConsultantSword"),
            Map.entry("consultant_pickaxe", "psychiatrykConsultantPickaxe"),
            Map.entry("consultant_item_extractor", "psychiatrykExtractor"),
            Map.entry("importer", "psychiatrykImporter"),
            Map.entry("travel_staff", "psychiatrykTravelStaff"),
            Map.entry("return_mirror", "psychiatrykReturnMirror")
        );

        for (var recipe : recipes.entrySet()) {
            String path = "/data/psychiatryk_roles/recipes/" + recipe.getKey() + ".json";
            var stream = RecipeResourcesTest.class.getResourceAsStream(path);
            assertNotNull(stream, path);
            JsonObject json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            CompoundTag resultTag = TagParser.parseTag(json.getAsJsonObject("result").get("nbt").getAsString());
            assertTrue(resultTag.getBoolean("psychiatrykConsultantItem"), recipe.getKey());
            assertTrue(resultTag.getBoolean(recipe.getValue()), recipe.getKey());
            CompoundTag display = resultTag.getCompound("display");
            assertTrue(display.contains("Name", 8), recipe.getKey() + " fallback name");
            assertTrue(display.getList("Lore", 8).size() >= 2, recipe.getKey() + " fallback lore");
        }
    }

    @Test
    void recipeBookUsesExactlyOneStickAndHasReadableFallbackPages() throws Exception {
        var stream = RecipeResourcesTest.class.getResourceAsStream(
            "/data/psychiatryk_roles/recipes/recipe_book.json"
        );
        assertNotNull(stream);
        JsonObject json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        assertEquals("minecraft:crafting_shapeless", json.get("type").getAsString());
        assertEquals(1, json.getAsJsonArray("ingredients").size());
        assertEquals("minecraft:stick", json.getAsJsonArray("ingredients").get(0)
            .getAsJsonObject().get("item").getAsString());
        CompoundTag resultTag = TagParser.parseTag(json.getAsJsonObject("result").get("nbt").getAsString());
        assertTrue(resultTag.getList("pages", 8).size() >= 1);
        assertTrue(resultTag.contains("title", 8));
        assertTrue(resultTag.contains("author", 8));
    }

    @Test
    void consultantPickaxeRecipeIncludesAdventureModeMiningTargets() throws Exception {
        var stream = RecipeResourcesTest.class.getResourceAsStream(
            "/data/psychiatryk_roles/recipes/consultant_pickaxe.json"
        );
        assertNotNull(stream);
        JsonObject json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        CompoundTag resultTag = TagParser.parseTag(json.getAsJsonObject("result").get("nbt").getAsString());
        assertEquals(2, resultTag.getList("CanDestroy", 8).size());
    }
}
