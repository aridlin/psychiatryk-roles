package pl.aridlin.psychiatrykroles;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
class PokerActivityTest {
    @Test void logIsBoundedAndPrivateToItsViewer() {
        UUID first = UUID.randomUUID(), other = UUID.randomUUID();
        for (int i = 0; i < 30; i++) PokerActivity.record(first, Component.literal("Message " + i));
        assertEquals(12, PokerActivity.recent(first).size());
        assertEquals("Message 29", PokerActivity.recent(first).get(11).getString());
        assertTrue(PokerActivity.recent(other).isEmpty());
    }
}
