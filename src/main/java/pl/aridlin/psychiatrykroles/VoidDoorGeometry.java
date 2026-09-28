package pl.aridlin.psychiatrykroles;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

final class VoidDoorGeometry {
    private VoidDoorGeometry() {}

    static Vec3 center(BlockPos lower, Direction facing) {
        // The closed leaf is 3/16 thick at the back of the block. Put the void inside it.
        return Vec3.atBottomCenterOf(lower).add(-facing.getStepX() * 13.0 / 32.0, 0,
            -facing.getStepZ() * 13.0 / 32.0);
    }

    static boolean crossed(BlockPos lower, Direction facing, Vec3 from, Vec3 to, double height) {
        if (from.distanceToSqr(to) > 16) return false; // Ignore external teleports.
        Vec3 center = center(lower, facing);
        double a = (from.x - center.x) * facing.getStepX() + (from.z - center.z) * facing.getStepZ();
        double b = (to.x - center.x) * facing.getStepX() + (to.z - center.z) * facing.getStepZ();
        if (a == b || !((a < 0 && b >= 0) || (a > 0 && b <= 0))) return false;
        Vec3 hit = from.lerp(to, a / (a - b));
        double sideways = facing.getAxis() == Direction.Axis.X ? hit.z - center.z : hit.x - center.x;
        return Math.abs(sideways) < 0.5 && hit.y < lower.getY() + 2 && hit.y + height > lower.getY();
    }
}
