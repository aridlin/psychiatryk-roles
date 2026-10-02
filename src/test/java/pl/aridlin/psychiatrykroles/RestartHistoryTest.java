package pl.aridlin.psychiatrykroles;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class RestartHistoryTest {
    @TempDir Path temporary;
    @Test void downtimeSurvivesSeparateShutdownAndStartupInstances() throws Exception {
        Path file = temporary.resolve("data/history.json");
        RestartHistory before = RestartHistory.read(file);
        assertEquals(0, before.estimateSeconds());
        before.shutdownStartedAt = 1000;
        before.write(file);
        RestartHistory after = RestartHistory.read(file);
        assertEquals(123400, after.recordStartup(124400));
        after.write(file);
        RestartHistory next = RestartHistory.read(file);
        assertEquals(124, next.estimateSeconds());
        assertEquals(0, next.shutdownStartedAt);
        assertEquals(0, next.recordStartup(200000));
        assertEquals(1, next.durationsMillis.size());
    }
    @Test void medianResistsSlowOutlierAndIgnoresLongOfflinePeriods() {
        RestartHistory history = new RestartHistory();
        history.durationsMillis.addAll(java.util.List.of(100000L, 120000L, 3600000L, 110000L, 130000L));
        assertEquals(120, history.estimateSeconds());
        history.shutdownStartedAt = 1000;
        assertEquals(0, history.recordStartup(1000 + 25 * 3600000L));
        assertEquals(5, history.durationsMillis.size());
    }
}
