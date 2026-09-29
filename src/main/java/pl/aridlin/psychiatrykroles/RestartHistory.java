package pl.aridlin.psychiatrykroles;

import com.google.gson.Gson;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

final class RestartHistory {
    private static final Gson JSON = new Gson();
    long shutdownStartedAt;
    List<Long> durationsMillis = new ArrayList<>();

    static RestartHistory read(Path path) throws IOException {
        if (!Files.exists(path)) return new RestartHistory();
        try {
            RestartHistory history = JSON.fromJson(Files.readString(path), RestartHistory.class);
            if (history == null || history.durationsMillis == null) throw new IllegalArgumentException("Missing history");
            history.durationsMillis.removeIf(value -> value == null || value <= 0);
            return history;
        } catch (RuntimeException invalid) { throw new IOException("Invalid restart history: " + path, invalid); }
    }

    void write(Path path) throws IOException {
        Files.createDirectories(path.getParent());
        Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
        Files.writeString(temporary, JSON.toJson(this), StandardCharsets.UTF_8);
        try { Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
        catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    long recordStartup(long now) {
        long elapsed = shutdownStartedAt == 0 ? 0 : now - shutdownStartedAt;
        shutdownStartedAt = 0;
        // Long offline periods and clock changes should not poison the estimate.
        if (elapsed <= 0 || elapsed > 24 * 3600_000L) return 0;
        durationsMillis.add(elapsed);
        while (durationsMillis.size() > 10) durationsMillis.remove(0);
        return elapsed;
    }

    long estimateSeconds() {
        if (durationsMillis.isEmpty()) return 0;
        List<Long> recent = durationsMillis.subList(Math.max(0, durationsMillis.size() - 5), durationsMillis.size())
            .stream().sorted().toList();
        return (recent.get(recent.size() / 2) + 999) / 1000;
    }
}
