package pl.aridlin.psychiatrykroles;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    @Test
    void seasonBorderShrinksGentlyAtFirstAndFasterAtFinale() {
        double day0 = diameter(0);
        double day1 = diameter(1);
        double day60 = diameter(60);
        double day119 = diameter(119);
        double day120 = diameter(120);

        assertEquals(200_000.0, day0);
        assertEquals(150_024.0, day60);
        assertEquals(96.0, day120);
        assertTrue(day0 > day1 && day1 > day60 && day60 > day119 && day119 > day120);
        assertTrue(day0 - day1 < day119 - day120);
        assertEquals(day0, diameter(-1));
        assertEquals(day120, diameter(121));
    }

    private static double diameter(long day) {
        return SeasonProgression.borderDiameter(200_000.0, 96.0, 120, day);
    }
}
