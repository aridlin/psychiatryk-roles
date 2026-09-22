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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecipeResourcesTest {
    @Test
    void everyConsultantRecipeCarriesFunctionalMarkersInItsResult() throws Exception {
        Map<String, String> recipes = Map.ofEntries(
            Map.entry("recipe_book", "psychiatrykRecipeBook"),
            Map.entry("escort_compass", "psychiatrykEscortCompass"),
            Map.entry("temporary_chalk", "psychiatrykTemporaryChalk"),
            Map.entry("cleanup_bag", "psychiatrykCleanupBag"),
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
            assertFalse(resultTag.contains("display"), recipe.getKey() + " stores no localized presentation");
            assertFalse(resultTag.contains("psychiatrykLoreLanguage"), recipe.getKey() + " stores no language");
        }
    }

    @Test
    void recipeBookUsesExactlyOneStickAndStoresNoLocalizedBookText() throws Exception {
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
        assertFalse(resultTag.contains("pages"));
        assertFalse(resultTag.contains("title"));
        assertFalse(resultTag.contains("author"));
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
