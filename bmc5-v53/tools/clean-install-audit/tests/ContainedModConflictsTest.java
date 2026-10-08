import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;
import pl.aridlin.tools.install.ContainedModConflicts;

public class ContainedModConflictsTest {
    static int checks;
    static void check(boolean value, String name) { checks++; if (!value) throw new AssertionError(name); }
    static Path jar(Path folder, String filename, String... ids) throws Exception {
        Path path = folder.resolve(filename);
        try (var zip = new ZipOutputStream(Files.newOutputStream(path))) {
            zip.putNextEntry(new ZipEntry("META-INF/neoforge.mods.toml"));
            StringBuilder text = new StringBuilder("modLoader=\"javafml\"\nloaderVersion=\"[4,)\"\nlicense=\"test\"\n");
            for (String id : ids) text.append("[[mods]]\nmodId=\"").append(id).append("\"\nversion=\"test\"\n");
            zip.write(text.toString().getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return path;
    }
    public static void main(String[] args) throws Exception {
        Path merged = Path.of(args[0]);
        Path folder = Path.of(args[1]); Files.createDirectories(folder);
        byte[] before = Files.readAllBytes(merged);
        Set<String> expected = Set.of("psychiatryk_roles", "goplanska_kukirin", "goplanska_parties", "goplanska_party_markers", "portable_chams", "psychiatryk_peeb");
        check(ContainedModConflicts.containedIds(merged).equals(expected), "actual six-component JAR index");
        Path scooter = jar(folder, "old-scooter.jar", "goplanska_kukirin");
        Path markers = jar(folder, "old-markers.jar", "goplanska_party_markers");
        Path foreign = jar(folder, "foreign.jar", "foreign_mod");
        var collisions = ContainedModConflicts.conflicts(List.of(merged), List.of(scooter, markers, foreign));
        check(collisions.size() == 2, "one managed JAR conflicts with both old component files");
        check(collisions.stream().map(c -> c.localJar()).collect(java.util.stream.Collectors.toSet()).equals(Set.of(scooter, markers)), "no unrelated file is classified");
        check(collisions.stream().allMatch(c -> c.sharedIds().size() == 1 && c.localOnlyIds().isEmpty()), "exact overlaps preserved");
        Path reordered = jar(folder, "reordered.jar", "psychiatryk_peeb", "portable_chams", "goplanska_party_markers", "goplanska_parties", "goplanska_kukirin", "psychiatryk_roles");
        check(ContainedModConflicts.containedIds(reordered).equals(expected), "independent of TOML declaration order");
        check(ContainedModConflicts.conflicts(List.of(reordered), List.of(scooter, markers)).size() == 2, "all two overlaps with reversed order");
        Path bundled = jar(folder, "local-combined.jar", "goplanska_kukirin", "foreign_mod");
        var bundledCollision = ContainedModConflicts.conflicts(List.of(merged), List.of(bundled)).getFirst();
        check(bundledCollision.sharedIds().equals(Set.of("goplanska_kukirin")), "combined local conflict");
        check(bundledCollision.localOnlyIds().equals(Set.of("foreign_mod")), "retains non-conflicting local component safety information");
        Path peeb = jar(folder, "standalone-peeb.jar", "psychiatryk_peeb");
        check(ContainedModConflicts.conflicts(List.of(merged), List.of(peeb)).size() == 1, "existing primary duplicate remains covered");
        check(ContainedModConflicts.conflicts(List.of(merged), List.of(foreign)).isEmpty(), "unrelated mod unchanged");
        check(Arrays.equals(before, Files.readAllBytes(merged)), "actual merged payload untouched");
        check(Files.isRegularFile(scooter) && Files.isRegularFile(markers) && Files.isRegularFile(foreign) && Files.isRegularFile(bundled), "prototype never removes or renames input files");
        System.out.println("{\"success\":true,\"checks\":"+checks+",\"all_contained_mod_ids_verified\":true,\"one_to_many_conflicts_verified\":true,\"unrelated_components_preserved_verified\":true,\"prototype_is_read_only_verified\":true,\"automodpack_patched\":false}");
    }
}
