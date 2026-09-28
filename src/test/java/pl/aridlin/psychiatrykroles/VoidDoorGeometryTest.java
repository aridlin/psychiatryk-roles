package pl.aridlin.psychiatrykroles;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VoidDoorGeometryTest {
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
