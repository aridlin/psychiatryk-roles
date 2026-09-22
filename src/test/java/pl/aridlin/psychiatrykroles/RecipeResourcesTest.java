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
        Map<String, String> recipes = Map.of(
            "consultant_sword", "psychiatrykConsultantSword",
            "consultant_pickaxe", "psychiatrykConsultantPickaxe",
            "consultant_item_extractor", "psychiatrykExtractor",
            "importer", "psychiatrykImporter",
            "travel_staff", "psychiatrykTravelStaff",
            "return_mirror", "psychiatrykReturnMirror"
        );

        for (var recipe : recipes.entrySet()) {
            String path = "/data/psychiatryk_roles/recipes/" + recipe.getKey() + ".json";
            var stream = RecipeResourcesTest.class.getResourceAsStream(path);
            assertNotNull(stream, path);
            JsonObject json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            CompoundTag resultTag = TagParser.parseTag(json.getAsJsonObject("result").get("nbt").getAsString());
            assertTrue(resultTag.getBoolean("psychiatrykConsultantItem"), recipe.getKey());
            assertTrue(resultTag.getBoolean(recipe.getValue()), recipe.getKey());
        }
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
