package pl.aridlin.psychiatrykroles.runtime.grapple;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

/** Fired after the native block/range/line-of-sight checks, before attachment is committed. */
public final class GrappleHitBlockEvent extends Event implements ICancellableEvent {
    private final ServerPlayer owner;
    private final BlockHitResult anchorHit;
    private final BlockHitResult redstoneHit;
    private final String redstoneBlockId;
    private boolean activateRedstone;

    public GrappleHitBlockEvent(ServerPlayer owner, BlockHitResult anchorHit,
                                BlockHitResult redstoneHit, String redstoneBlockId,
                                boolean activateRedstone) {
        this.owner = owner;
        this.anchorHit = anchorHit;
        this.redstoneHit = redstoneHit;
        this.redstoneBlockId = redstoneBlockId;
        this.activateRedstone = activateRedstone;
    }

    public ServerPlayer getOwner() { return owner; }
    public BlockHitResult getAnchorHit() { return anchorHit; }
    /** May be null if no visible vanilla lever/button is on the hook face. */
    public BlockHitResult getRedstoneHit() { return redstoneHit; }
    /** Empty when no safe redstone candidate is present. */
    public String getRedstoneBlockId() { return redstoneBlockId; }
    public boolean isActivateRedstone() { return activateRedstone; }
    public void setActivateRedstone(boolean activate) { this.activateRedstone = activate; }
}
