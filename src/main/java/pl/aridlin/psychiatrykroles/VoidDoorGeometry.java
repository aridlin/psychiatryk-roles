package pl.aridlin.psychiatrykroles;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import net.minecraft.util.Mth;

import java.util.function.Predicate;

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

    static boolean touches(BlockPos lower, Direction facing, AABB hitbox) {
        Vec3 center = center(lower, facing);
        AABB plane = facing.getAxis() == Direction.Axis.X
            ? new AABB(center.x - .025, lower.getY() + .1, center.z - .38,
                center.x + .025, lower.getY() + 1.85, center.z + .38)
            : new AABB(center.x - .38, lower.getY() + .1, center.z - .025,
                center.x + .38, lower.getY() + 1.85, center.z + .025);
        return plane.intersects(hitbox);
    }

    record Contact(double lateral, double height, Direction approach) {}

    static Contact contact(BlockPos lower, Direction facing, AABB hitbox, Vec3 from, Vec3 to, Vec3 velocity) {
        if (!touches(lower, facing, hitbox) || from.distanceToSqr(to) > 16) return null;
        Vec3 center = center(lower, facing);
        double oldDistance = (from.x - center.x) * facing.getStepX()
            + (from.z - center.z) * facing.getStepZ();
        double newDistance = (to.x - center.x) * facing.getStepX()
            + (to.z - center.z) * facing.getStepZ();
        double motion = newDistance - oldDistance;
        if (Math.abs(motion) < .001) motion = velocity.x * facing.getStepX() + velocity.z * facing.getStepZ();
        int sign = motion > .001 ? 1 : motion < -.001 ? -1 : oldDistance <= 0 ? 1 : -1;
        double extent = facing.getAxis() == Direction.Axis.X ? hitbox.getXsize() / 2 : hitbox.getZsize() / 2;
        double edge = sign > 0 ? -extent : extent;
        double fraction = Math.abs(newDistance - oldDistance) < .001 ? 1
            : Mth.clamp((edge - oldDistance) / (newDistance - oldDistance), 0, 1);
        Vec3 hit = from.lerp(to, fraction);
        Direction approach = sign > 0 ? facing : facing.getOpposite();
        double lateral = (hit.x - center.x) * -approach.getStepZ()
            + (hit.z - center.z) * approach.getStepX();
        return new Contact(Mth.clamp(lateral, -.35, .35),
            Mth.clamp(hit.y - lower.getY(), .01, 1.6), approach);
    }

    static Vec3 rotate(Vec3 motion, Direction from, Direction to) {
        double radians = Math.toRadians(Mth.wrapDegrees(to.toYRot() - from.toYRot()));
        double cos = Math.cos(radians), sin = Math.sin(radians);
        return new Vec3(motion.x * cos - motion.z * sin, motion.y,
            motion.x * sin + motion.z * cos);
    }

    static Vec3 firstClearExit(BlockPos lower, Direction facing, Predicate<Vec3> clear) {
        Vec3 center = Vec3.atBottomCenterOf(lower).add(0, 0.01, 0);
        for (Direction side : new Direction[] { facing, facing.getOpposite() }) {
            Vec3 exit = center.add(side.getStepX() * 1.0, 0, side.getStepZ() * 1.0);
            if (clear.test(exit)) return exit;
        }
        return null;
    }
}
