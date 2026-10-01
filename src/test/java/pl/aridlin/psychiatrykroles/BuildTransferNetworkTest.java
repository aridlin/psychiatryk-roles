package pl.aridlin.psychiatrykroles;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildTransferNetworkTest {
    @Test
    void excavatedAirNeedsAnExplicitPristineComparison() {
        assertTrue(BuildTransferNetwork.shouldTransfer((byte) 1, false));
        assertTrue(BuildTransferNetwork.shouldTransfer((byte) 1, true));
        assertFalse(BuildTransferNetwork.shouldTransfer((byte) 2, false));
        assertTrue(BuildTransferNetwork.shouldTransfer((byte) 2, true));
        assertFalse(BuildTransferNetwork.shouldTransfer((byte) 0, true));

        assertDoesNotThrow(() -> BuildTransferNetwork.requireCarveEvidence(false, "heuristic"));
        assertThrows(IllegalArgumentException.class,
            () -> BuildTransferNetwork.requireCarveEvidence(true, "heuristic"));
        assertDoesNotThrow(() -> BuildTransferNetwork.requireCarveEvidence(true, "pristine-diff"));
    }
}
