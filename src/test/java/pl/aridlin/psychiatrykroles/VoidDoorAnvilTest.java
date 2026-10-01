package pl.aridlin.psychiatrykroles;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class VoidDoorAnvilTest {
    @Test
    void newlyInsertedPairWaitsForRenamePacketBeforeProducingCode() {
        assertNull(VoidDoors.normalizedAnvilName(null));
    }

    @Test
    void emptyRenameRemainsDistinctFromNoRenamePacket() {
        assertEquals("", VoidDoors.normalizedAnvilName("   "));
        assertEquals("door code", VoidDoors.normalizedAnvilName("  door code  "));
    }
}
