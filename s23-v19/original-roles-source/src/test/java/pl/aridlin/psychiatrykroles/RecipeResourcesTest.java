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
    private static CompoundTag resultCustomData(JsonObject recipe) throws Exception {
        return TagParser.parseTag(recipe.getAsJsonObject("result").getAsJsonObject("components")
            .get("minecraft:custom_data").getAsString());
    }

    @Test
    void chatBookRecipeMakesMarkedWritableBook() throws Exception {
        try (var stream = RecipeResourcesTest.class.getResourceAsStream("/data/psychiatryk_roles/recipe/chat_book.json")) {
            assertNotNull(stream);
            JsonObject json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            assertEquals("minecraft:crafting_shapeless", json.get("type").getAsString());
            assertEquals("minecraft:writable_book", json.getAsJsonArray("ingredients").get(0).getAsJsonObject().get("item").getAsString());
            assertEquals("minecraft:amethyst_shard", json.getAsJsonArray("ingredients").get(1).getAsJsonObject().get("item").getAsString());
            assertEquals("minecraft:writable_book", json.getAsJsonObject("result").get("id").getAsString());
            assertTrue(resultCustomData(json).getBoolean("psychiatrykChatBook"));
        }
    }

    @Test
    void pokerRecipeCraftsAFunctionalMenuClock() throws Exception {
        try (var stream = RecipeResourcesTest.class.getResourceAsStream("/data/psychiatryk_roles/recipe/poker_menu.json")) {
            assertNotNull(stream);
            JsonObject json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            assertEquals("minecraft:crafting_shapeless", json.get("type").getAsString());
            assertEquals("minecraft:clock", json.getAsJsonArray("ingredients").get(0).getAsJsonObject().get("item").getAsString());
            assertEquals("minecraft:paper", json.getAsJsonArray("ingredients").get(1).getAsJsonObject().get("item").getAsString());
            assertEquals("minecraft:clock", json.getAsJsonObject("result").get("id").getAsString());
            assertTrue(resultCustomData(json).getBoolean("psychiatrykPokerMenu"));
        }
    }

    @Test
    void voidDoorRecipeMakesOneMarkedPairFromWoodAndPearl() throws Exception {
        var stream = RecipeResourcesTest.class.getResourceAsStream(
            "/data/psychiatryk_roles/recipe/void_door.json");
        assertNotNull(stream);
        JsonObject json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        assertEquals("minecraft:crafting_shaped", json.get("type").getAsString());
        assertEquals("WW ", json.getAsJsonArray("pattern").get(0).getAsString());
        assertEquals("WE ", json.getAsJsonArray("pattern").get(1).getAsString());
        assertEquals("WW ", json.getAsJsonArray("pattern").get(2).getAsString());
        assertEquals("minecraft:planks", json.getAsJsonObject("key").getAsJsonObject("W").get("tag").getAsString());
        assertEquals("minecraft:ender_pearl", json.getAsJsonObject("key").getAsJsonObject("E").get("item").getAsString());
        assertEquals("minecraft:dark_oak_door", json.getAsJsonObject("result").get("id").getAsString());
        assertEquals(2, json.getAsJsonObject("result").get("count").getAsInt());
        assertTrue(resultCustomData(json).getBoolean("psychiatrykVoidDoor"));
    }

    @Test
    void voidTrapdoorRecipeMakesOneMarkedPair() throws Exception {
        try (var stream = RecipeResourcesTest.class.getResourceAsStream(
            "/data/psychiatryk_roles/recipe/void_trapdoor.json")) {
            assertNotNull(stream);
            JsonObject json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            assertEquals("minecraft:dark_oak_trapdoor", json.getAsJsonObject("result").get("id").getAsString());
            assertEquals(2, json.getAsJsonObject("result").get("count").getAsInt());
            assertTrue(resultCustomData(json).getBoolean("psychiatrykVoidTrapdoor"));
        }
    }

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
            String path = "/data/psychiatryk_roles/recipe/" + recipe.getKey() + ".json";
            var stream = RecipeResourcesTest.class.getResourceAsStream(path);
            assertNotNull(stream, path);
            JsonObject json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            CompoundTag resultTag = resultCustomData(json);
            assertTrue(resultTag.getBoolean("psychiatrykConsultantItem"), recipe.getKey());
            assertTrue(resultTag.getBoolean(recipe.getValue()), recipe.getKey());
            assertFalse(resultTag.contains("display"), recipe.getKey() + " stores no localized presentation");
            assertFalse(resultTag.contains("psychiatrykLoreLanguage"), recipe.getKey() + " stores no language");
        }
    }

    @Test
    void recipeBookUsesExactlyOneStickAndHasNeutralWrittenBookFallback() throws Exception {
        var stream = RecipeResourcesTest.class.getResourceAsStream(
            "/data/psychiatryk_roles/recipe/recipe_book.json"
        );
        assertNotNull(stream);
        JsonObject json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        assertEquals("minecraft:crafting_shapeless", json.get("type").getAsString());
        assertEquals(1, json.getAsJsonArray("ingredients").size());
        assertEquals("minecraft:stick", json.getAsJsonArray("ingredients").get(0)
            .getAsJsonObject().get("item").getAsString());
        CompoundTag resultTag = resultCustomData(json);
        assertFalse(resultTag.contains("pages"));
        assertFalse(resultTag.contains("title"));
        assertFalse(resultTag.contains("author"));
        var content = json.getAsJsonObject("result").getAsJsonObject("components")
            .getAsJsonObject("minecraft:written_book_content");
        assertEquals("Psychiatryk", content.get("title").getAsString());
        assertEquals(1, content.getAsJsonArray("pages").size());
    }

    @Test
    void consultantPickaxeRecipeIncludesAdventureModeMiningTargets() throws Exception {
        var stream = RecipeResourcesTest.class.getResourceAsStream(
            "/data/psychiatryk_roles/recipe/consultant_pickaxe.json"
        );
        assertNotNull(stream);
        JsonObject json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        var canBreak = json.getAsJsonObject("result").getAsJsonObject("components")
            .getAsJsonObject("minecraft:can_break");
        assertEquals(2, canBreak.getAsJsonArray("predicates").size());
    }

    @Test
    void passageStaffRecipeUsesNativeUnbreakableComponent() throws Exception {
        try (var stream = RecipeResourcesTest.class.getResourceAsStream(
            "/data/psychiatryk_roles/recipe/travel_staff.json")) {
            assertNotNull(stream);
            JsonObject json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            assertTrue(json.getAsJsonObject("result").getAsJsonObject("components")
                .has("minecraft:unbreakable"));
        }
    }
}
