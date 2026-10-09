package pl.aridlin.psychiatrykroles.runtime.grapple;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Properties;
import java.util.Set;

/** Small, server-owned defaults for the native grapple event hooks. No script or client file is read here. */
public final class GrappleRules {
    public record Snapshot(double teammateImpulseScale, double scooterImpulseScale,
                           boolean redstoneOnAttach, int redstoneCooldownTicks) {
        public boolean valid() {
            return Double.isFinite(teammateImpulseScale) && teammateImpulseScale >= 0 && teammateImpulseScale <= 2
                && Double.isFinite(scooterImpulseScale) && scooterImpulseScale >= 0 && scooterImpulseScale <= 2
                && redstoneCooldownTicks >= 4 && redstoneCooldownTicks <= 200;
        }
    }

    public static final Snapshot DEFAULT = new Snapshot(1, 1, false, 20);
    public static final Path FILE = Path.of("config/psychiatryk-runtime/grapple.properties");
    private static final GrappleRules SERVER = new GrappleRules(FILE);
    private static final Set<String> KEYS = Set.of(
        "teammateImpulseScale", "scooterImpulseScale", "redstoneOnAttach", "redstoneCooldownTicks");

    private final Path file;
    private Snapshot current = DEFAULT;
    private FileTime lastModified;
    private long lastSize = -1;
    private long nextPoll;

    public GrappleRules(Path file) { this.file = file; }

    /** File changes become live within one second; malformed writes retain the last valid snapshot. */
    public static Snapshot server() { return SERVER.snapshot(); }

    public synchronized Snapshot reload() {
        nextPoll = 0;
        lastModified = null;
        return snapshot();
    }

    public synchronized Snapshot snapshot() {
        long now = System.currentTimeMillis();
        if (now < nextPoll) return current;
        nextPoll = now + 1000;
        try {
            if (!Files.exists(file)) {
                // A transient gap during an atomic replacement must not reset live rules.
                if (lastModified == null) writeExample();
                return current;
            }
            long size = Files.size(file);
            FileTime modified = Files.getLastModifiedTime(file);
            if (modified.equals(lastModified) && size == lastSize) return current;
            if (size > 8192) throw new IllegalArgumentException("file exceeds 8 KiB");
            try {
                current = parse(Files.readString(file, StandardCharsets.UTF_8));
            } finally {
                // A malformed unchanged file is warned about once, not every player tick.
                lastModified = modified;
                lastSize = size;
            }
        } catch (Exception error) {
            System.err.println("[Psychiatryk grapple] Invalid server rules; keeping previous values: " + error.getMessage());
        }
        return current;
    }

    public static Snapshot parse(String content) throws IOException {
        if (content == null || content.length() > 8192) throw new IllegalArgumentException("rules exceed 8 KiB");
        Properties properties = new Properties();
        properties.load(new StringReader(content));
        for (String key : properties.stringPropertyNames()) {
            if (!KEYS.contains(key)) throw new IllegalArgumentException("unknown key: " + key);
        }
        String enabled = properties.getProperty("redstoneOnAttach", "false").trim();
        if (!enabled.equals("true") && !enabled.equals("false"))
            throw new IllegalArgumentException("redstoneOnAttach must be true or false");
        Snapshot result = new Snapshot(
            Double.parseDouble(properties.getProperty("teammateImpulseScale", "1")),
            Double.parseDouble(properties.getProperty("scooterImpulseScale", "1")),
            Boolean.parseBoolean(enabled),
            Integer.parseInt(properties.getProperty("redstoneCooldownTicks", "20")));
        if (!result.valid()) throw new IllegalArgumentException("out-of-range grapple rule");
        return result;
    }

    private void writeExample() throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file,
            "# Server-only grapple defaults. Edited values apply within one second.\n"
          + "# Impulse scales 0..2; final movement is always limited to native force budgets.\n"
          + "teammateImpulseScale=1.0\n"
          + "scooterImpulseScale=1.0\n"
          + "# Only a visible vanilla lever/button may be activated on a validated block hook.\n"
          + "redstoneOnAttach=false\nredstoneCooldownTicks=20\n",
          StandardCharsets.UTF_8);
    }
}
