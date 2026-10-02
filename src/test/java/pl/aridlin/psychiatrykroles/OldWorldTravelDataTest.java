package pl.aridlin.psychiatrykroles;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OldWorldTravelDataTest {
    @Test
    void persistsEachAdminsReturnPointWithoutImportingPlayerData() {
        OldWorldTravelData data = new OldWorldTravelData();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        var firstPosition = new OldWorldTravelData.ReturnPosition("minecraft:overworld", 12.25, 82, -99.5, 145, 20);
        var secondPosition = new OldWorldTravelData.ReturnPosition("minecraft:overworld", -24, 70, 200, -45, 0);
        data.remember(first, firstPosition);
        data.remember(second, secondPosition);
        OldWorldTravelData restored = OldWorldTravelData.load(data.save(new CompoundTag()));
        assertEquals(firstPosition, restored.getReturn(first));
        assertEquals(secondPosition, restored.getReturn(second));
        restored.forget(first);
        assertNull(OldWorldTravelData.load(restored.save(new CompoundTag())).getReturn(first));
        assertEquals(secondPosition, restored.getReturn(second));
    }

    @Test
    void rejectsInvalidBookmarkCoordinates() {
        var valid = new OldWorldTravelData.ReturnPosition("minecraft:overworld", 0, 64, 0, 0, 0);
        var invalid = new OldWorldTravelData.ReturnPosition("minecraft:overworld", Double.NaN, 64, 0, 0, 0);
        assertTrue(valid.valid());
        assertFalse(invalid.valid());
        assertThrows(IllegalArgumentException.class, () -> new OldWorldTravelData().remember(UUID.randomUUID(), invalid));
    }
}
