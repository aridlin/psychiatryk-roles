package pl.aridlin.psychiatrykroles;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SeasonProgressionTest {
    @Test
    void seasonBorderAppliesOnFirstTickThenRefreshesEachMinute() {
        assertTrue(SeasonProgression.borderUpdateDue(false, 1));
        assertFalse(SeasonProgression.borderUpdateDue(true, 2));
        assertFalse(SeasonProgression.borderUpdateDue(true, 1199));
        assertTrue(SeasonProgression.borderUpdateDue(true, 1200));
    }
}
