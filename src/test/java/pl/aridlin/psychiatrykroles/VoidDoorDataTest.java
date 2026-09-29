package pl.aridlin.psychiatrykroles;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class VoidDoorDataTest {
    @Test
    void minedDoorCanMoveAcrossDimensionsWithoutLosingItsPair() {
        VoidDoorData data = new VoidDoorData();
        UUID pair = UUID.randomUUID();
        var first = new VoidDoorData.DoorPosition("minecraft:overworld", new BlockPos(1, 64, 2));
        var second = new VoidDoorData.DoorPosition("minecraft:the_nether", new BlockPos(1, 64, 2));
        assertTrue(data.addDoor(pair, first));
        assertTrue(data.addDoor(pair, second));
        assertEquals(pair, data.pairAt(first.dimension(), first.pos()));
        assertFalse(data.addDoor(UUID.randomUUID(), first));
        assertTrue(data.removeDoor(first.dimension(), first.pos()));
        var moved = new VoidDoorData.DoorPosition("minecraft:the_end", new BlockPos(8, 80, 8));
        assertTrue(data.addDoor(pair, moved));
        VoidDoorData restored = VoidDoorData.load(data.save(new CompoundTag()));
        assertEquals(moved, restored.partner(second.dimension(), second.pos()));
        assertEquals(pair, restored.pairAt(moved.dimension(), moved.pos()));
    }

    @Test
    void linksExactlyTwoPlacedDoorsAndPersistsAcrossReload() {
        VoidDoorData data = new VoidDoorData();
        UUID pair = UUID.randomUUID();
        VoidDoorData.DoorPosition first = new VoidDoorData.DoorPosition("minecraft:overworld", new BlockPos(1, 64, 2));
        VoidDoorData.DoorPosition second = new VoidDoorData.DoorPosition("minecraft:the_nether", new BlockPos(3, 72, 4));

        assertTrue(data.addDoor(pair, first));
        assertNull(data.partner(first.dimension(), first.pos()));
        assertTrue(data.addDoor(pair, second));
        assertFalse(data.addDoor(pair, new VoidDoorData.DoorPosition("minecraft:overworld", new BlockPos(9, 64, 9))));

        VoidDoorData restored = VoidDoorData.load(data.save(new CompoundTag()));
        assertEquals(second, restored.partner(first.dimension(), first.pos()));
        assertEquals(first, restored.partner(second.dimension(), second.pos()));
        assertTrue(restored.removeDoor(first.dimension(), first.pos()));
        assertNull(restored.partner(second.dimension(), second.pos()));
    }
}
