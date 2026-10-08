package pl.aridlin.kukirin;

public final class VanillaMusicQA {
    private static int checks;
    private static void check(boolean value, String name) {
        if (!value) throw new AssertionError(name);
        checks++;
    }

    public static void main(String[] args) {
        var catalog = VanillaMusicCatalog.catalog();
        check(catalog.size() == 79, "all Minecraft soundtrack and disc resources");
        check(catalog.keySet().stream().filter(id -> id.startsWith("Minecraft Disc - ")).count() == 19, "all native discs");
        var aria = VanillaMusicCatalog.get("Minecraft - Aria Math.wav");
        check(aria != null, "Aria Math selectable by name");
        check(aria.url().equals("minecraftsound:goplanska_kukirin:vanilla_music/game/creative/aria_math"), "Aria Math resolves a specific native track");
        check(aria.durationTicks() > 20 * 60 && aria.durationTicks() < 20 * 60 * 60, "native measured Aria Math duration");
        check(VanillaMusicCatalog.get("Minecraft Disc - Creator.wav") != null, "newer native disc selectable");
        check(VanillaMusicCatalog.forUrl("minecraftsound:minecraft:entity.creeper.primed") == null, "unlisted sounds rejected");
        check(VanillaMusicCatalog.forUrl("minecraftsound:goplanska_kukirin:vanilla_music/../records/13") == null, "traversal rejected");
        check(VanillaMusicCatalog.forUrl("https://example.invalid/sound.wav") == null, "HTTP not treated as native resource");
        check(VanillaMusicCatalog.forUrl(null) == null, "null native URL rejected");
        for (var song : catalog.values()) {
            check(VanillaMusicCatalog.forUrl(song.url()).equals(song), "all selectable names resolve the same allowed native track");
            check(song.bearer().isEmpty(), "native music has no credentials");
            check(song.durationTicks() > 0 && song.bytes() > 0, "all resources measured");
        }
        System.out.println("{\"success\":true,\"checks\":" + checks + ",\"native_tracks\":79,\"runtime_listening_verified\":false}");
    }
}
