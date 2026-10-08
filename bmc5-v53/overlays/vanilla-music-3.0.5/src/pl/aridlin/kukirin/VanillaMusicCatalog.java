package pl.aridlin.kukirin;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Metadata and resource references only; audio is resolved by Minecraft's client resources. */
public final class VanillaMusicCatalog {
    public static final String URL_PREFIX = "minecraftsound:";
    private static final Map<String, ScooterMusicSources.Song> SONGS = load();
    private static final Map<String, ScooterMusicSources.Song> URLS = byUrl();

    private static Map<String, ScooterMusicSources.Song> load() {
        try (InputStream stream = VanillaMusicCatalog.class.getResourceAsStream("/data/goplanska_kukirin/vanilla-music.json")) {
            if (stream == null) throw new IllegalStateException("Vanilla music metadata missing");
            JsonObject root = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            Map<String, ScooterMusicSources.Song> songs = new LinkedHashMap<>();
            for (JsonElement element : root.getAsJsonArray("songs")) {
                JsonObject row = element.getAsJsonObject();
                String id = row.get("id").getAsString();
                String url = row.get("url").getAsString();
                int ticks = row.get("durationTicks").getAsInt();
                if (!ScooterMusicSources.validName(id) || !url.matches("minecraftsound:goplanska_kukirin:vanilla_(music|records)/[a-z0-9_/.]+") || url.contains("..") || ticks <= 0) {
                    throw new IllegalStateException("Invalid vanilla music metadata");
                }
                ScooterMusicSources.Song song = new ScooterMusicSources.Song(id, row.get("title").getAsString(), url, "", row.get("sha256").getAsString(), row.get("bytes").getAsInt(), ticks);
                if (songs.put(id, song) != null) throw new IllegalStateException("Duplicate vanilla song");
            }
            return Map.copyOf(songs);
        } catch (Exception error) {
            throw new ExceptionInInitializerError(error);
        }
    }

    private static Map<String, ScooterMusicSources.Song> byUrl() {
        Map<String, ScooterMusicSources.Song> urls = new LinkedHashMap<>();
        SONGS.values().forEach(song -> urls.put(song.url(), song));
        return Map.copyOf(urls);
    }

    public static Map<String, ScooterMusicSources.Song> catalog() { return SONGS; }
    public static List<String> songs() { return SONGS.keySet().stream().sorted().toList(); }
    public static ScooterMusicSources.Song get(String id) { return id == null ? null : SONGS.get(id); }
    public static ScooterMusicSources.Song forUrl(String url) { return url == null ? null : URLS.get(url); }
    private VanillaMusicCatalog() {}
}
