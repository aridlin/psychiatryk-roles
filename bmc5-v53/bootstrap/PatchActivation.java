import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.time.*;
import java.util.*;
import java.util.zip.*;

/** Apply only a verified, narrowly scoped addon patch during normal startup. */
public final class PatchActivation {
    static final Set<String> ALLOWED = Set.of(
        "mods/psychiatryk_roles-3.0.0-bmc5.jar",
        "automodpack/host-modpack/main/mods/psychiatryk_roles-3.0.0-bmc5.jar",
        "config/goplanska-scooter/music-sources.json",
        "goplanska-release.json",
        "automodpack/host-modpack/main/goplanska-release.json",
        "mods/sophisticatedbackpacks-1.21.1-3.25.69.1979.jar",
        "mods/sophisticatedcore-1.21.1-1.4.70.2131.jar",
        "automodpack/host-modpack/main/mods/sophisticatedbackpacks-1.21.1-3.25.69.1979.jar",
        "automodpack/host-modpack/main/mods/sophisticatedcore-1.21.1-1.4.70.2131.jar");
    static final Set<String> REMOVABLE = Set.of();
    static String sha(Path path) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = Files.newInputStream(path)) {
            byte[] buffer = new byte[1048576];
            for (int n; (n = input.read(buffer)) > 0;) digest.update(buffer, 0, n);
        }
        return HexFormat.of().formatHex(digest.digest());
    }
    static void move(Path from, Path to) throws IOException {
        Files.createDirectories(to.getParent());
        Files.move(from, to, StandardCopyOption.ATOMIC_MOVE);
    }
    static void replace(Path from, Path to) throws IOException {
        Files.createDirectories(to.getParent());
        Files.move(from, to, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }
    static void recover(Path root, Path journal) throws Exception {
        Properties before = new Properties();
        try (Reader reader = Files.newBufferedReader(journal.resolve("before.properties"))) { before.load(reader); }
        for (String name : before.stringPropertyNames()) {
            if (!ALLOWED.contains(name)) throw new IOException("Invalid transaction journal path");
            Path current = root.resolve(name);
            if ("absent".equals(before.getProperty(name))) {
                Files.deleteIfExists(current);
            } else {
                Path saved = journal.resolve(name);
                if (!sha(saved).equals(before.getProperty(name))) throw new IOException("Rollback snapshot SHA mismatch");
                Path temporary = saved.resolveSibling(saved.getFileName() + ".restoring");
                Files.copy(saved, temporary, StandardCopyOption.REPLACE_EXISTING);
                replace(temporary, current);
            }
        }
        Files.move(journal, root.resolve("bmc5-patch-recovered-" + System.currentTimeMillis()));
        Path stage = root.resolve("bmc5-patch-verified-stage");
        if (Files.exists(stage)) Files.move(stage, root.resolve("bmc5-patch-unused-stage-" + System.currentTimeMillis()));
        System.out.println("[Patch] Previous interrupted transaction restored before starting Minecraft.");
    }
    public static void apply(Path root, Instant now) throws Exception {
        Path flag = root.resolve("bmc5-patch.properties");
        if (!Files.exists(flag)) return;
        Properties options = new Properties();
        try (Reader reader = Files.newBufferedReader(flag)) { options.load(reader); }
        Path stage = root.resolve("bmc5-patch-verified-stage");
        Path journal = root.resolve("bmc5-patch-journal");
        if (Files.exists(journal)) {
            try { recover(root, journal); }
            catch(Exception fatal) { throw new IllegalStateException("Interrupted patch cannot be recovered safely", fatal); }
        }
        if (now.isBefore(Instant.parse(options.getProperty("notBefore")))) {
            System.out.println("[Patch] Verified addon patch remains scheduled for the daily restart.");
            return;
        }
        if (!"true".equals(options.getProperty("runtimeVerified"))) throw new IOException("Runtime proof required");
        Path zip = root.resolve("bmc5-patch.zip");
        if (!sha(zip).equals(options.getProperty("zipSha256"))) throw new IOException("Patch ZIP SHA mismatch");
        if (!sha(root.resolve("mods/psychiatryk_roles-3.0.0-bmc5.jar")).equals(options.getProperty("baselineSha256")))
            throw new IOException("Current addon differs from verified patch baseline");
        if (Files.exists(stage) || Files.exists(journal)) throw new IOException("Previous patch journal requires recovery");
        Files.createDirectory(stage);
        LinkedHashMap<String, String> hashes = new LinkedHashMap<>();
        try (ZipFile archive = new ZipFile(zip.toFile())) {
            ZipEntry proof = archive.getEntry("manifest.properties");
            if (proof == null) throw new IOException("Patch manifest missing");
            Properties manifest = new Properties();
            try (InputStream input = archive.getInputStream(proof)) { manifest.load(input); }
            for (String name : new TreeSet<>(manifest.stringPropertyNames())) {
                if (!ALLOWED.contains(name)) throw new IOException("Patch path not allowed: " + name);
                if ("absent".equals(manifest.getProperty(name)) && !REMOVABLE.contains(name))
                    throw new IOException("This patch does not permit removals");
                hashes.put(name, manifest.getProperty(name));
            }
            String server = "mods/psychiatryk_roles-3.0.0-bmc5.jar";
            String client = "automodpack/host-modpack/main/" + server;
            if (!Objects.equals(hashes.get(server), options.getProperty("addonSha256")) ||
                !Objects.equals(hashes.get(server), hashes.get(client))) throw new IOException("Server/client addon differs");
            for (String name : hashes.keySet()) {
                if (name.startsWith("mods/")) {
                    String paired = "automodpack/host-modpack/main/" + name;
                    if (!Objects.equals(hashes.get(name), hashes.get(paired))) throw new IOException("Server/client dependency differs");
                }
            }
            Set<String> seen = new HashSet<>();
            Enumeration<? extends ZipEntry> entries = archive.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();
                if (name.equals("manifest.properties")) continue;
                if (!hashes.containsKey(name) || entry.isDirectory() || !seen.add(name)) throw new IOException("Unexpected patch entry");
                Path file = stage.resolve(name).normalize();
                if (!file.startsWith(stage)) throw new IOException("Unsafe patch path");
                Files.createDirectories(file.getParent());
                try (InputStream input = archive.getInputStream(entry)) { Files.copy(input, file); }
                if (!sha(file).equals(hashes.get(name))) throw new IOException("Patch entry SHA mismatch");
            }
            Set<String> requiredEntries = new HashSet<>(hashes.keySet());
            requiredEntries.removeIf(name -> "absent".equals(hashes.get(name)));
            if (!seen.equals(requiredEntries)) throw new IOException("Incomplete patch archive");
        } catch (Exception rejected) {
            // Do not touch the running release after a rejected package. Retain diagnostics.
            Files.move(stage, root.resolve("bmc5-patch-rejected-" + now.toEpochMilli()));
            throw rejected;
        }
        Path building = root.resolve("bmc5-patch-journal-building");
        Files.createDirectory(building);
        Properties before = new Properties();
        // Snapshot the entire small allowlisted file set before replacing anything.
        // The old world, player files and baseline pack directories are outside this transaction.
        for (String name : hashes.keySet()) {
            Path current = root.resolve(name);
            if (Files.isSymbolicLink(current) || Files.exists(current) && !Files.isRegularFile(current)) throw new IOException("Patch destination is not a regular file");
            if (Files.exists(current)) {
                Path saved = building.resolve(name); Files.createDirectories(saved.getParent());
                Files.copy(current, saved); before.setProperty(name, sha(saved));
            } else before.setProperty(name, "absent");
        }
        try (Writer writer = Files.newBufferedWriter(building.resolve("before.properties"))) { before.store(writer, "Small addon transaction snapshots"); }
        // Only a complete journal can request recovery on the next startup.
        move(building, journal);
        try {
            for (String name : hashes.keySet()) {
                Path current = root.resolve(name);
                if ("absent".equals(hashes.get(name))) Files.deleteIfExists(current);
                else replace(stage.resolve(name), current);
                if (name.equals("config/goplanska-scooter/music-sources.json"))
                    Files.setPosixFilePermissions(current, java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));
            }
            Files.writeString(root.resolve("bmc5-patch-applied.json"),
                "{\"addonSha256\":\"" + options.getProperty("addonSha256") + "\",\"appliedAt\":\"" + now + "\",\"journalRetained\":true}\n");
            Files.move(flag, root.resolve("bmc5-patch-applied.properties"));
            System.out.println("[Patch] Verified addon patch activated for server and AutoModpack; prior bytes retained in transaction journal.");
        } catch (Exception failure) {
            try { recover(root, journal); }
            catch(Exception fatal) { throw new IllegalStateException("Patch rollback failed; refusing to launch mixed files", fatal); }
            throw failure;
        }
    }
    public static void main(String[] args) throws Exception {
        apply(Path.of(args[0]).toAbsolutePath(), Instant.parse(args[1]));
    }
}
