package pl.aridlin.tools.install;

import amp_libs.org.tomlj.Toml;
import amp_libs.org.tomlj.TomlArray;
import amp_libs.org.tomlj.TomlTable;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Read-only prototype: all NeoForge mod declarations, all file-to-file overlaps. */
public final class ContainedModConflicts {
    private ContainedModConflicts() {}

    public record Conflict(Path localJar, Path managedJar, Set<String> sharedIds, Set<String> localOnlyIds) {
        public Conflict {
            sharedIds = Set.copyOf(sharedIds);
            localOnlyIds = Set.copyOf(localOnlyIds);
        }
    }

    public static Set<String> containedIds(Path jar) throws IOException {
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            ZipEntry metadata = zip.getEntry("META-INF/neoforge.mods.toml");
            if (metadata == null) metadata = zip.getEntry("META-INF/mods.toml");
            if (metadata == null) return Set.of();
            try (var reader = new InputStreamReader(zip.getInputStream(metadata), StandardCharsets.UTF_8)) {
                var parsed = Toml.parse(reader);
                if (parsed.hasErrors()) throw new IOException("Invalid mod metadata in " + jar.getFileName());
                TomlArray mods = parsed.getArray("mods");
                if (mods == null) return Set.of();
                Set<String> ids = new LinkedHashSet<>();
                for (int i = 0; i < mods.size(); i++) {
                    TomlTable mod = mods.getTable(i);
                    String id = mod.getString("modId");
                    if (id != null && !id.isBlank()) ids.add(id);
                }
                return Set.copyOf(ids);
            }
        }
    }

    public static List<Conflict> conflicts(Collection<Path> managed, Collection<Path> local) throws IOException {
        List<Conflict> result = new ArrayList<>();
        for (Path localJar : local) {
            Set<String> localIds = containedIds(localJar);
            for (Path managedJar : managed) {
                Set<String> managedIds = containedIds(managedJar);
                Set<String> overlap = new LinkedHashSet<>(localIds);
                overlap.retainAll(managedIds);
                if (overlap.isEmpty()) continue;
                Set<String> exclusive = new LinkedHashSet<>(localIds);
                exclusive.removeAll(managedIds);
                result.add(new Conflict(localJar, managedJar, overlap, exclusive));
            }
        }
        return List.copyOf(result);
    }
}
