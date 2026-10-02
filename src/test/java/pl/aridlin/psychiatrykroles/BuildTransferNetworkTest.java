package pl.aridlin.psychiatrykroles;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildTransferNetworkTest {
    @Test
    void baseSizedSelectionsExceedTheOld64kLimitButRemainBounded() {
        assertTrue(BuildTransferPlan.validSize(128, 128, 16));
        assertTrue(BuildTransferPlan.validSize(64, 64, 64));
        assertFalse(BuildTransferPlan.validSize(128, 128, 17));
        assertFalse(BuildTransferPlan.validSize(129, 16, 16));
        assertFalse(BuildTransferPlan.validSize(0, 128, 64));
    }

    @Test
    void plannerAcceptsMoreThanTheOld64kChangeLimit() {
        List<BuildTransferPlan.Change> changes = new ArrayList<>();
        Map<BuildTransferPlan.Cell, BuildTransferPlan.Ground> ground = new HashMap<>();
        for (int i = 0; i < 65_537; i++) {
            int x = i & 63, y = (i >> 6) & 63, z = i >> 12;
            changes.add(new BuildTransferPlan.Change(new BuildTransferPlan.Cell(x, y, z),
                "minecraft:oak_planks", BuildTransferPlan.Origin.PLAYER_PLACED));
            ground.put(new BuildTransferPlan.Cell(x, 0, z),
                new BuildTransferPlan.Ground(-1, "minecraft:stone"));
        }
        assertEquals(65_537, BuildTransferPlan.blend(changes,
            new BuildTransferPlan.Cell(0, 0, 0), new BuildTransferPlan.Cell(0, 0, 0), 0, ground).size());
    }

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
