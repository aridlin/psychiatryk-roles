package pl.aridlin.partymarkers;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import java.nio.file.Files;
import java.util.EnumSet;

/** Runs in a disposable working directory, without a Minecraft launch. */
public final class ScooterToggleQA {
    private static int checks;
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
        System.out.println("PASS: " + message);
    }
    public static void main(String[] args) throws Exception {
        Files.createDirectories(ChamsCategories.config.getParent());
        Files.writeString(ChamsCategories.config, "{\"enabled\":[\"PARTY\",\"CHESTS\"],\"oreRadius\":12}");
        ChamsCategories.loaded = false;
        check(ChamsCategories.memberEnabled("scooter"), "legacy client settings preserve previously visible scooter highlights");
        check(ChamsCategories.memberEnabled("player"), "party member preference is preserved during migration");
        check(!ChamsCategories.memberEnabled("zombie"), "marked-entity disabled preference is preserved during migration");
        check(ChamsCategories.radius == 12, "migration preserves ore scan radius");
        var categories = EnumSet.copyOf(ChamsCategories.enabled);
        categories.remove(ChamsCategories.Kind.SCOOTER);

        ChamsCategories.enabled.remove(ChamsCategories.Kind.SCOOTER);
        ChamsCategories.save();
        check(!ChamsCategories.memberEnabled("scooter"), "shared world-chams/HUD-dot predicate suppresses scooters when off");
        check(ChamsCategories.memberEnabled("player") && !ChamsCategories.memberEnabled("zombie"), "scooter toggle leaves player and marked predicates unchanged");
        ChamsCategories.enabled.clear();
        ChamsCategories.loaded = false;
        check(!ChamsCategories.memberEnabled("scooter"), "off preference survives fresh config reload");
        check(ChamsCategories.enabled.equals(categories), "reload preserves every other enabled category");

        ChamsCategories.enabled.add(ChamsCategories.Kind.SCOOTER);
        ChamsCategories.save();
        ChamsCategories.enabled.clear();
        ChamsCategories.loaded = false;
        check(ChamsCategories.memberEnabled("scooter"), "on preference survives fresh config reload");
        var saved = com.google.gson.JsonParser.parseString(Files.readString(ChamsCategories.config)).getAsJsonObject();
        check(saved.getAsJsonArray("enabled").asList().stream().noneMatch(value -> value.getAsString().equals("SCOOTER")),
              "legacy enabled array remains readable by v31 clients during rollback");
        var dispatcher = new CommandDispatcher<CommandSourceStack>();
        ChamsCategories.commands(new RegisterClientCommandsEvent(dispatcher, null));
        var scooter = dispatcher.getRoot().getChild("chams").getChild("scooter");
        check(scooter != null && scooter.getChild("off").getCommand() != null && scooter.getChild("on").getCommand() != null,
              "/chams scooter off and on are registered client commands");
        System.out.println("SUCCESS: " + checks + " checks; no game launched");
    }
}
