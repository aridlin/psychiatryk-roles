package pl.aridlin.psychiatrykroles.runtime.grapple;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

/** Server-side NeoForge event; KubeJS NativeEvents listeners may change the impulse after script-only reload. */
public final class GrapplePullEvent extends Event implements ICancellableEvent {
    private final ServerPlayer owner;
    private final Entity affected;
    private final String kind;
    private final Vec3 incoming;
    private final double horizontalBudget;
    private final double totalBudget;
    private Vec3 requested;

    public GrapplePullEvent(ServerPlayer owner, Entity affected, String kind, Vec3 incoming,
                            Vec3 proposed, double horizontalBudget, double totalBudget) {
        this.owner = owner;
        this.affected = affected;
        this.kind = kind;
        this.incoming = incoming;
        this.requested = proposed;
        this.horizontalBudget = horizontalBudget;
        this.totalBudget = totalBudget;
    }

    public ServerPlayer getOwner() { return owner; }
    public Entity getAffected() { return affected; }
    /** teammate, teammate_scooter, or scooter. */
    public String getKind() { return kind; }
    public Vec3 getIncomingVelocity() { return incoming; }
    public Vec3 getRequestedVelocity() { return requested; }
    public double getHorizontalBudget() { return horizontalBudget; }
    public double getTotalBudget() { return totalBudget; }
    public double getImpulseX() { return requested.x - incoming.x; }
    public double getImpulseY() { return requested.y - incoming.y; }
    public double getImpulseZ() { return requested.z - incoming.z; }

    /** Reject non-finite/huge script values before the native force budget is applied. */
    public boolean setImpulse(double x, double y, double z) {
        if (!safe(x) || !safe(y) || !safe(z)) return false;
        requested = incoming.add(x, y, z);
        return true;
    }

    public boolean setRequestedVelocity(Vec3 velocity) {
        if (velocity == null) return false;
        return setImpulse(velocity.x - incoming.x, velocity.y - incoming.y, velocity.z - incoming.z);
    }

    private static boolean safe(double value) { return Double.isFinite(value) && Math.abs(value) <= 16; }
}
