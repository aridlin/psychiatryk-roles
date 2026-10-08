package pl.aridlin.psychiatrykroles.peeb;

import net.minecraft.world.phys.Vec3;

/** Small deterministic rules shared by authoritative party pulling and fixtures. */
public final class PeebPartyPull {
    private PeebPartyPull() {}

    public static boolean sameParty(int ownerParty, int targetParty) {
        return ownerParty > 0 && ownerParty == targetParty;
    }

    /** These are freshly sampled server facts, never facts supplied by a packet. */
    public record PeerState(int party, boolean current, boolean alive, boolean removed,
                            boolean disconnected, boolean changingDimension,
                            boolean spectator, boolean sleeping, boolean sameDimension,
                            boolean movable) {}

    public static boolean allowed(int ownerParty, PeerState target) {
        return target != null && sameParty(ownerParty, target.party())
            && target.current() && target.alive() && !target.removed()
            && !target.disconnected() && !target.changingDimension()
            && !target.spectator() && !target.sleeping() && target.sameDimension()
            && target.movable();
    }

    /** Party rescue cannot keep tugging a player beyond the configured reach. */
    public static boolean withinRange(Vec3 eye, Vec3 anchor, double range) {
        return withinRange(eye, anchor, range, PeebGrapple.TETHER_RANGE_EPSILON);
    }

    /** Initiation transport grace lasts only one state-sync interval. */
    public static double rangeGrace(long ticksSinceAttach) {
        return ticksSinceAttach >= 0 && ticksSinceAttach <= PeebGrapple.ROPE_SYNC_INTERVAL_TICKS
            ? PeebGrapple.ATTACH_RANGE_GRACE : PeebGrapple.TETHER_RANGE_EPSILON;
    }

    public static boolean withinRange(Vec3 eye, Vec3 anchor, double range, double grace) {
        return finite(eye) && finite(anchor) && Double.isFinite(range)
            && Double.isFinite(grace) && grace >= 0 && grace <= PeebGrapple.ATTACH_RANGE_GRACE
            && range >= PeebGrapple.MIN_ROPE_LENGTH
            && eye.distanceToSqr(anchor) <= Math.pow(range + grace, 2);
    }

    /** Budget the combined impulse when two Peebs tug the same target in a tick.
     * Existing tangential momentum is never truncated or reset by a hook.
     */
    public static Vec3 addImpulse(Vec3 incoming, Vec3 applied, Vec3 impulse,
                                   double horizontalBudget, double totalBudget) {
        if (!finite(incoming) || !finite(applied) || !finite(impulse)
            || !Double.isFinite(horizontalBudget) || !Double.isFinite(totalBudget)
            || horizontalBudget < 0 || totalBudget < 0) return incoming;
        return PeebAdventuresPhysics.addWithinBudget(incoming, applied.add(impulse),
            horizontalBudget, totalBudget);
    }

    private static boolean finite(Vec3 value) {
        return value != null && Double.isFinite(value.x) && Double.isFinite(value.y)
            && Double.isFinite(value.z);
    }
}
