package pl.aridlin.psychiatrykroles.peeb;

import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import java.util.function.BiFunction;

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
    public static final double GRAPPLE_STEP_HEIGHT = 1.3;

    private PeebAdventuresPhysics() {}

    /** A scooter-style step path: sweep up, across, then back down to the
     * original vertical trajectory. No position snap or rope-length change.
     * The lifted sweep must make more horizontal progress than the unstepped
     * path. A corner can clip one horizontal axis while the other clears a lip;
     * rejecting that useful partial sweep makes terrain unnecessarily sticky.
     * Zero means no step.
     */
    public static double grappleStepRise(Vec3 movement, AABB box,
                                          BiFunction<AABB, Vec3, Vec3> collide) {
        if (!finite(movement) || movement.horizontalDistanceSqr() < 1.0E-10
            || movement.y > GRAPPLE_STEP_HEIGHT) return 0.0;
        Vec3 ordinary = collide.apply(box, movement);
        if (ordinary.horizontalDistanceSqr() >= movement.horizontalDistanceSqr() - 1.0E-10) return 0.0;
        Vec3 up = collide.apply(box, new Vec3(0, GRAPPLE_STEP_HEIGHT, 0));
        if (up.y <= 1.0E-6) return 0.0;
        Vec3 across = new Vec3(movement.x, 0, movement.z);
        Vec3 allowed = collide.apply(box.move(up), across);
        if (!finite(allowed) || allowed.horizontalDistanceSqr() <= ordinary.horizontalDistanceSqr() + 1.0E-10) return 0.0;
        // Verify the settlement sweep before the actual move. It may land on
        // the step, but cannot enter the solid volume or pass a thin ceiling.
        Vec3 down = collide.apply(box.move(up).move(allowed), new Vec3(0, movement.y - up.y, 0));
        if (!finite(down)) return 0.0;
        return up.y;
    }

    public static double response(double rate, double seconds) {
        return 1.0 - Math.pow(1.0 - rate, seconds * 60.0);
    }

    public static double reel(double length, double range, double seconds) {
        return reel(length, range, 0.0, seconds);
    }

    public static double targetLength(double range, double stopDistance) {
        return Math.max(0.0, Math.min(range, stopDistance));
    }

    public static double reel(double length, double range, double stopDistance, double seconds) {
        double target = targetLength(range, stopDistance);
        double value = length + (target - length) * response(REEL_RATE, seconds);
        // Original JMath.RLI snaps within its epsilon; scale it with the model.
        return Math.abs(target - length) <= 0.001 * SCALE ? target : value;
    }

    /** The anchor force never projects position or deletes tangential velocity. */
    public static Vec3 pull(Vec3 pivot, Vec3 velocity, Vec3 anchor, double length,
                            double strengthScale, double massScale, double seconds) {
        Vec3 offset = anchor.subtract(pivot);
        double stretch = Math.max(0.0, offset.length() - length);
        if (length < 0.0 || stretch == 0.0) return velocity;
        // This floor limits spring stiffness near a zero-length target; it does
        // not create slack or stop pulling short of the actual anchor.
        double stiffnessLength = Math.max(0.35, length);
        return velocity.add(offset.scale(stretch / stiffnessLength * FORCE_GAIN * seconds / 20.0
            * strengthScale * massScale));
    }

    /** Moving the tusk endpoint with the view can load an already taut rope.
     * Body and anchor movement are held constant here: their spring force is
     * integrated separately. Slack absorbs endpoint movement before any force.
     * The returned value is an impulse to ADD, never a replacement velocity.
     */
    public static Vec3 cameraTug(Vec3 pivot, Vec3 previousLook, Vec3 currentLook,
                                 Vec3 anchor, double restLength) {
        if (!finite(pivot) || !finite(previousLook) || !finite(currentLook)
            || !finite(anchor) || !Double.isFinite(restLength) || restLength < 0.0
            || previousLook.lengthSqr() < 1.0E-12 || currentLook.lengthSqr() < 1.0E-12) return Vec3.ZERO;
        Vec3 before = pivot.add(previousLook.normalize().scale(0.55));
        Vec3 after = pivot.add(currentLook.normalize().scale(0.55));
        Vec3 radial = anchor.subtract(after);
        double distance = radial.length();
        double extension = distance - Math.max(anchor.distanceTo(before), restLength);
        if (extension <= 0.0 || distance < 1.0E-9) return Vec3.ZERO;
        return radial.scale(Math.min(0.14, extension * 0.7) / distance);
    }

    private static boolean finite(Vec3 value) {
        return value != null && Double.isFinite(value.x) && Double.isFinite(value.y) && Double.isFinite(value.z);
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
