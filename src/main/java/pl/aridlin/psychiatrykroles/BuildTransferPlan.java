package pl.aridlin.psychiatrykroles;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Pure planning core shared by the client preview and creative-only server paste. */
public final class BuildTransferPlan {
    public record Cell(int x, int y, int z) {
        Cell offset(int dx, int dy, int dz) { return new Cell(x + dx, y + dy, z + dz); }
    }
    public enum Origin { PLAYER_PLACED, PLAYER_REMOVED, UNKNOWN }
    public record Change(Cell source, String state, Origin origin) {}
    public record Paste(Cell destination, String state, Origin origin) {}
    public record Ground(int topY, String supportState) {}

    private BuildTransferPlan() {}

    /**
     * The baseline is a pristine world generated with the original seed and
     * exact old worldgen mod set. Missing baseline samples remain UNKNOWN and
     * are never silently copied into another world.
     */
    public static List<Change> compare(Map<Cell, String> current, Map<Cell, String> pristine) {
        List<Change> result = new ArrayList<>();
        for (var entry : current.entrySet()) {
            Cell cell = entry.getKey();
            String before = pristine.get(cell);
            String after = Objects.requireNonNull(entry.getValue());
            if (before == null) {
                result.add(new Change(cell, after, Origin.UNKNOWN));
            } else if (!before.equals(after)) {
                result.add(new Change(cell, after,
                    isAir(after) ? Origin.PLAYER_REMOVED : Origin.PLAYER_PLACED));
            }
        }
        return List.copyOf(result);
    }

    /** Anchor by the median ground height, then support short gaps under built floors. */
    public static List<Paste> blend(List<Change> changes, Cell sourceAnchor, Cell targetAnchor,
                                    int sourceGroundMedian, Map<Cell, Ground> targetColumns) {
        if (changes.size() > 65536) throw new IllegalArgumentException("Selection exceeds 65,536 blocks");
        if (targetColumns.isEmpty()) throw new IllegalArgumentException("Target ground is not sampled");
        List<Integer> heights = targetColumns.values().stream().map(Ground::topY).sorted().toList();
        int targetGroundMedian = heights.get(heights.size() / 2);
        int dy = targetGroundMedian - sourceGroundMedian;
        int dx = targetAnchor.x - sourceAnchor.x;
        int dz = targetAnchor.z - sourceAnchor.z;
        List<Paste> planned = new ArrayList<>();
        Map<Cell, String> placed = new HashMap<>();
        Map<Cell, Integer> lowestBuilt = new HashMap<>();
        for (Change change : changes) {
            if (change.origin == Origin.UNKNOWN) continue;
            Cell dest = change.source.offset(dx, dy, dz);
            // Air changes are excavations only inside the original player edit.
            planned.add(new Paste(dest, change.state, change.origin));
            placed.put(dest, change.state);
            if (change.origin == Origin.PLAYER_PLACED && !isAir(change.state)) {
                lowestBuilt.merge(new Cell(dest.x, 0, dest.z), dest.y, Math::min);
            }
        }
        // A short, narrow foundation closes floating gaps, using the local
        // ground material. This leaves all untouched terrain unchanged.
        for (Paste paste : List.copyOf(planned)) {
            if (paste.origin != Origin.PLAYER_PLACED || isAir(paste.state)) continue;
            Ground ground = targetColumns.get(new Cell(paste.destination.x, 0, paste.destination.z));
            if (ground == null) continue;
            if (paste.destination.y != lowestBuilt.get(new Cell(paste.destination.x, 0, paste.destination.z))) continue;
            int gap = paste.destination.y - ground.topY() - 1;
            if (gap < 1 || gap > 4) continue;
            for (int y = ground.topY() + 1; y < paste.destination.y; y++) {
                Cell support = new Cell(paste.destination.x, y, paste.destination.z);
                if (!placed.containsKey(support)) {
                    planned.add(new Paste(support, ground.supportState(), Origin.PLAYER_PLACED));
                    placed.put(support, ground.supportState());
                }
            }
        }
        planned.sort(Comparator.comparingInt((Paste p) -> p.destination.y)
            .thenComparingInt(p -> p.destination.x).thenComparingInt(p -> p.destination.z));
        return List.copyOf(planned);
    }

    private static boolean isAir(String state) {
        return state.equals("minecraft:air") || state.equals("minecraft:cave_air") || state.equals("minecraft:void_air");
    }
}
