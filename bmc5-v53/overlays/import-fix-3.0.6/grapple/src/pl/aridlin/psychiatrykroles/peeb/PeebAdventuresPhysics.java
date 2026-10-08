package pl.aridlin.psychiatrykroles.peeb;

import net.minecraft.world.phys.Vec3;

/** Peeb Adventures v0.05 movement laws expressed in blocks and 20 Hz velocities. */
public final class PeebAdventuresPhysics {
    public static final double SCALE = 1.4851409;
    public static final double TICK_SECONDS = 0.05;
    public static final double REST_LENGTH = 2.0 * SCALE;
    public static final double GRAVITY = 35.0;
    public static final double GRAVITY_CAP = -100.0;
    public static final double GROUND_SHIFT = 0.1;
    public static final double AIR_CONTROL = 0.2;
    public static final double SPRINT_SPEED = 11.0;
    public static final double JUMP_SPEED = 12.0;
    public static final double REEL_RATE = 0.02;
    public static final double FORCE_GAIN = 1.5 * 1.2 * 60.0;

    private PeebAdventuresPhysics() {}

    public static double response(double rate, double seconds) {
        return 1.0 - Math.pow(1.0 - rate, seconds * 60.0);
    }

    public static double reel(double length, double range, double seconds) {
        double target = Math.min(REST_LENGTH, range);
        double value = length + (target - length) * response(REEL_RATE, seconds);
        // Original JMath.RLI snaps within its epsilon; scale it with the model.
        return Math.abs(target - length) <= 0.001 * SCALE ? target : value;
    }

    /** The anchor force never projects position or deletes tangential velocity. */
    public static Vec3 pull(Vec3 pivot, Vec3 velocity, Vec3 anchor, double length,
                            double strengthScale, double massScale, double seconds) {
        Vec3 offset = anchor.subtract(pivot);
        double stretch = Math.max(0.0, offset.length() - length);
        if (length <= 0.0 || stretch == 0.0) return velocity;
        return velocity.add(offset.scale(stretch / length * FORCE_GAIN * seconds / 20.0
            * strengthScale * massScale));
    }

    /** Input damping, gravity, ground support and elastic hook pull, in game order. */
    public static Vec3 step(Vec3 pivot, Vec3 velocity, Vec3 desired, Vec3 anchor,
                           double reeledLength, boolean grounded, boolean jump,
                           double strengthScale, double seconds) {
        double blend = response(GROUND_SHIFT * (grounded ? 1.0 : AIR_CONTROL), seconds);
        Vec3 shifted = velocity.add(desired.subtract(velocity).scale(blend));
        double y = Math.max(GRAVITY_CAP * SCALE / 20.0,
            shifted.y - GRAVITY * SCALE * seconds / 20.0);
        if (grounded) y = Math.max(-4.0 * SCALE / 20.0, y);
        if (grounded && jump) y = JUMP_SPEED * SCALE / 20.0;
        return pull(pivot, new Vec3(shifted.x, y, shifted.z), anchor, reeledLength,
            strengthScale, 1.0, seconds);
    }

    /** Preserve airborne coast; movement keys steer without a passive air brake. */
    public static Vec3 coastStep(Vec3 velocity, Vec3 desired, boolean grounded,
                                 boolean jump, double seconds) {
        double blend = response(GROUND_SHIFT * (grounded ? 1.0 : AIR_CONTROL), seconds);
        boolean steering = desired.horizontalDistanceSqr() > 1.0E-10;
        double x = grounded || steering ? velocity.x + (desired.x - velocity.x) * blend : velocity.x;
        double z = grounded || steering ? velocity.z + (desired.z - velocity.z) * blend : velocity.z;
        double y = Math.max(GRAVITY_CAP * SCALE / 20.0,
            velocity.y - GRAVITY * SCALE * seconds / 20.0);
        if (grounded) y = Math.max(-4.0 * SCALE / 20.0, y);
        if (grounded && jump) y = JUMP_SPEED * SCALE / 20.0;
        return new Vec3(x, y, z);
    }

    /** Limit only impulse, never incoming momentum (including fast sideways swings). */
    public static Vec3 addWithinBudget(Vec3 incoming, Vec3 pulled, double horizontalImpulse, double totalImpulse) {
        Vec3 force = pulled.subtract(incoming);
        double horizontal = force.horizontalDistance();
        double fraction = horizontal > horizontalImpulse ? horizontalImpulse / horizontal : 1.0;
        double length = force.length();
        if (length > totalImpulse) fraction = Math.min(fraction, totalImpulse / length);
        return incoming.add(force.scale(fraction));
    }
}
