package pl.aridlin.psychiatrykroles;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** First point where a moving entity hitbox touches a rendered portal plane. */
final class PortalSweep {
    private PortalSweep() {}

    static double firstContact(AABB plane, AABB currentBox, Vec3 from, Vec3 to) {
        // Expand the plane by the box's offsets from the entity position, then
        // intersect the position segment. This catches an entire crossing between ticks.
        double[] low = {
            plane.minX - (currentBox.maxX - to.x),
            plane.minY - (currentBox.maxY - to.y),
            plane.minZ - (currentBox.maxZ - to.z)
        };
        double[] high = {
            plane.maxX - (currentBox.minX - to.x),
            plane.maxY - (currentBox.minY - to.y),
            plane.maxZ - (currentBox.minZ - to.z)
        };
        double[] start = { from.x, from.y, from.z };
        double[] motion = { to.x - from.x, to.y - from.y, to.z - from.z };
        double enter = 0, leave = 1;
        for (int axis = 0; axis < 3; axis++) {
            if (Math.abs(motion[axis]) < 1.0e-9) {
                if (start[axis] < low[axis] || start[axis] > high[axis]) return Double.NaN;
                continue;
            }
            double a = (low[axis] - start[axis]) / motion[axis];
            double b = (high[axis] - start[axis]) / motion[axis];
            enter = Math.max(enter, Math.min(a, b));
            leave = Math.min(leave, Math.max(a, b));
            if (enter > leave) return Double.NaN;
        }
        return enter;
    }
}
