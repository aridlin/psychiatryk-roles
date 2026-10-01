package pl.aridlin.psychiatrykroles;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VoidDoorGeometryTest {
    private static AABB smallBox(Vec3 position) {
        return new AABB(position.x - .005, position.y - .005, position.z - .005,
            position.x + .005, position.y + .005, position.z + .005);
    }

    private static VoidDoorGeometry.Contact movingContact(int mask, double lateral, double height) {
        BlockPos lower = new BlockPos(0, 64, 0);
        Vec3 center = VoidDoorGeometry.center(lower, Direction.EAST);
        Vec3 from = center.add(-1, height, lateral);
        Vec3 to = center.add(1, height, lateral);
        return VoidDoorGeometry.contact(lower, Direction.EAST, mask, smallBox(to),
            from, to, to.subtract(from));
    }

    @Test void aFastCrossingAndTheVisibleBottomStripTouchTheDoorPlane() {
        assertNotNull(movingContact(31, 0, .005));
    }

    @Test void onlyTheUnframedSideAndTopExtendTheContactArea() {
        assertNull(movingContact(31, .44, .5));
        assertNotNull(movingContact(31 & ~4, .44, .5));
        assertNull(movingContact(31 & ~4, .44, 1.5));
        assertNull(movingContact(31, 0, 1.95));
        assertNotNull(movingContact(31 & ~16, 0, 1.95));
    }

    @Test void planeOnlyExpandsWhereAFrameSegmentIsHidden() {
        assertEquals(new VoidDoors.PlaneSection(-.415, .415, 0, 1),
            VoidDoors.planeSection(31, false));
        assertEquals(new VoidDoors.PlaneSection(-.415, .415, 1, 1.89),
            VoidDoors.planeSection(31, true));
        assertEquals(new VoidDoors.PlaneSection(-.5, .415, 0, 1),
            VoidDoors.planeSection(30, false));
        assertEquals(new VoidDoors.PlaneSection(-.415, .415, 1, 1.89),
            VoidDoors.planeSection(30, true));
        assertEquals(new VoidDoors.PlaneSection(-.415, .415, 1, 2),
            VoidDoors.planeSection(15, true));
    }

    @Test void usesTheOtherSideWhenTheFacingSideIsBlocked() {
        BlockPos lower = new BlockPos(997, 77, 573);
        Vec3 south = Vec3.atBottomCenterOf(lower).add(0, .01, 1);
        Vec3 north = Vec3.atBottomCenterOf(lower).add(0, .01, -1);
        assertEquals(north, VoidDoorGeometry.firstClearExit(lower, Direction.SOUTH,
            candidate -> candidate.equals(north)));
        assertEquals(south, VoidDoorGeometry.firstClearExit(lower, Direction.SOUTH,
            candidate -> true));
        assertNull(VoidDoorGeometry.firstClearExit(lower, Direction.SOUTH, candidate -> false));
    }

    @Test void allFacingsWorkFromBothSidesOnlyWhenCrossed() {
        BlockPos lower = new BlockPos(2, 64, 3);
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            Vec3 center = VoidDoorGeometry.center(lower, facing);
            Vec3 forward = new Vec3(facing.getStepX(), 0, facing.getStepZ());
            Vec3 before = center.subtract(forward.scale(.3)), after = center.add(forward.scale(.3));
            assertTrue(VoidDoorGeometry.crossed(lower, facing, before, after, 1.8), facing.toString());
            assertTrue(VoidDoorGeometry.crossed(lower, facing, after, before, 1.8));
            assertFalse(VoidDoorGeometry.crossed(lower, facing, before, before, 1.8));
            assertFalse(VoidDoorGeometry.crossed(lower, facing, before, center.subtract(forward.scale(.1)), 1.8));
            assertFalse(VoidDoorGeometry.crossed(lower, facing, before.add(0, 2, 0), after.add(0, 2, 0), 1.8));
            Vec3 sideways = new Vec3(facing.getStepZ(), 0, facing.getStepX());
            assertFalse(VoidDoorGeometry.crossed(lower, facing, before.add(sideways), after.add(sideways), 1.8));
        }
    }
}
