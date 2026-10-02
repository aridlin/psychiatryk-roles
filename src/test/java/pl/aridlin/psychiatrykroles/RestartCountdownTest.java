package pl.aridlin.psychiatrykroles;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class RestartCountdownTest {
    @Test void acceptsCompoundDurationsAndRejectsInvalidOrOverflowInput() {
        assertEquals(300, RestartCountdown.parse("5m"));
        assertEquals(5410, RestartCountdown.parse("1h30m10s"));
        assertEquals(86400, RestartCountdown.parse("1D"));
        for (String bad : List.of("", "0s", "-5m", "5", "5m!", "x5m", "31d", "999999999999999999999h"))
            assertThrows(IllegalArgumentException.class, () -> RestartCountdown.parse(bad), bad);
    }
    @Test void fiveMinutesAnnouncesImmediatelyThenOnlyRequestedMilestones() {
        assertEquals(List.of(60L, 30L, 10L, 5L), RestartCountdown.milestones(300));
        RestartCountdown timer = new RestartCountdown(300, 0);
        assertEquals(300, timer.remainingSeconds(0));
        assertFalse(timer.announcementDue(300));
        for (long value : List.of(60L, 30L, 10L, 5L)) {
            assertTrue(timer.announcementDue(value));
            assertFalse(timer.announcementDue(value));
        }
        assertEquals(0, timer.remainingSeconds(300_000_000_000L));
    }
    @Test void arbitraryDurationAndLagDoNotBurstStaleAnnouncements() {
        RestartCountdown timer = new RestartCountdown(17, 0);
        assertEquals(17, timer.remainingSeconds(100));
        assertTrue(timer.announcementDue(4));
        assertFalse(timer.announcementDue(3));
        assertTrue(RestartCountdown.milestones(301).contains(300L));
        assertEquals(List.of(), RestartCountdown.milestones(3));
    }
}
