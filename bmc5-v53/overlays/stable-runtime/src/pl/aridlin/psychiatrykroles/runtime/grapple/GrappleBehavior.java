package pl.aridlin.psychiatrykroles.runtime.grapple;

import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult.Type;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.common.NeoForge;
import pl.aridlin.psychiatrykroles.peeb.PeebAdventuresPhysics;

/** Guarded server adapter around stable native grapple motion and block hits. */
public final class GrappleBehavior {
    private static final Map<ServerPlayer, Long> LAST_REDSTONE = new WeakHashMap<>();
    private static long lastErrorLog;

    private GrappleBehavior() {}

    public static Vec3 adjustPull(ServerPlayer owner, Entity affected, String kind,
                                  Vec3 incoming, Vec3 nativePulled,
                                  double horizontalBudget, double totalBudget) {
        if (!finite(incoming) || !finite(nativePulled)
            || !Double.isFinite(horizontalBudget) || !Double.isFinite(totalBudget)
            || horizontalBudget < 0 || totalBudget < 0) return incoming;
        GrappleRules.Snapshot rules = GrappleRules.server();
        double scale = kind.equals("teammate") ? rules.teammateImpulseScale() : rules.scooterImpulseScale();
        Vec3 defaultPulled = capped(incoming, incoming.add(nativePulled.subtract(incoming).scale(scale)),
                                   horizontalBudget, totalBudget);
        GrapplePullEvent event = new GrapplePullEvent(owner, affected, kind, incoming, defaultPulled,
                                                     horizontalBudget, totalBudget);
        try {
            NeoForge.EVENT_BUS.post(event);
        } catch (Exception error) {
            log(error);
            return defaultPulled;
        }
        if (event.isCanceled()) return incoming;
        return capped(incoming, event.getRequestedVelocity(), horizontalBudget, totalBudget);
    }

    /** Reapply native per-tick force budgets after any script adjustment. Existing momentum is preserved. */
    static Vec3 capped(Vec3 incoming, Vec3 proposed, double horizontalBudget, double totalBudget) {
        if (!finite(incoming) || !finite(proposed)) return incoming;
        Vec3 delta = proposed.subtract(incoming);
        if (!finite(delta) || Math.abs(delta.x) > 16 || Math.abs(delta.y) > 16 || Math.abs(delta.z) > 16)
            return incoming;
        Vec3 result = PeebAdventuresPhysics.addWithinBudget(incoming, proposed, horizontalBudget, totalBudget);
        return finite(result) ? result : incoming;
    }

    /** The event can veto a validated anchor, but cannot replace its hit position or bypass line of sight. */
    public static GrappleHitBlockEvent beforeBlockAttach(ServerPlayer owner, BlockHitResult anchorHit) {
        BlockHitResult redstoneHit = redstoneCandidate(owner, anchorHit);
        String blockId = redstoneHit == null ? "" : BuiltInRegistries.BLOCK
            .getKey(owner.level().getBlockState(redstoneHit.getBlockPos()).getBlock()).toString();
        boolean defaultActivate = redstoneHit != null && GrappleRules.server().redstoneOnAttach();
        GrappleHitBlockEvent event = new GrappleHitBlockEvent(owner, anchorHit, redstoneHit, blockId, defaultActivate);
        try {
            NeoForge.EVENT_BUS.post(event);
            return event;
        } catch (Exception error) {
            log(error);
            return new GrappleHitBlockEvent(owner, anchorHit, redstoneHit, blockId, defaultActivate);
        }
    }

    /** Only a visible vanilla button/lever touching the validated anchor face can be activated. */
    private static BlockHitResult redstoneCandidate(ServerPlayer owner, BlockHitResult anchorHit) {
        if (anchorHit == null || anchorHit.getType() != Type.BLOCK || anchorHit.isInside()) return null;
        Vec3 eye = owner.getEyePosition();
        Vec3 end = anchorHit.getLocation();
        Vec3 ray = end.subtract(eye);
        if (!finite(ray) || ray.lengthSqr() < 0.01) return null;
        BlockHitResult outline = owner.level().clip(new ClipContext(eye, end.add(ray.normalize().scale(0.03)),
                                                       Block.OUTLINE, Fluid.NONE, owner));
        if (outline.getType() != Type.BLOCK || outline.isInside()
            || outline.getLocation().distanceToSqr(end) > 0.8 * 0.8) return null;
        BlockPos anchor = anchorHit.getBlockPos();
        BlockPos front = anchor.relative(anchorHit.getDirection());
        BlockPos candidate = outline.getBlockPos();
        if (!candidate.equals(anchor) && !candidate.equals(front)) return null;
        if (!owner.level().hasChunkAt(candidate)) return null;
        return safeRedstone(owner.level().getBlockState(candidate)) ? outline : null;
    }

    private static boolean safeRedstone(BlockState state) {
        var block = state.getBlock();
        var id = BuiltInRegistries.BLOCK.getKey(block);
        return id != null && id.getNamespace().equals("minecraft")
            && (block instanceof ButtonBlock || block instanceof LeverBlock);
    }

    public static void afterBlockAttach(GrappleHitBlockEvent event) {
        if (event == null || event.isCanceled() || !event.isActivateRedstone()) return;
        BlockHitResult hit = event.getRedstoneHit();
        if (hit == null || hit.getType() != Type.BLOCK) return;
        ServerPlayer owner = event.getOwner();
        BlockPos pos = hit.getBlockPos();
        if (!owner.level().hasChunkAt(pos) || !owner.mayInteract(owner.level(), pos)) return;
        BlockState state = owner.level().getBlockState(pos);
        if (!safeRedstone(state)) return;
        long tick = owner.level().getGameTime();
        Long previous = LAST_REDSTONE.get(owner);
        int cooldown = GrappleRules.server().redstoneCooldownTicks();
        if (previous != null && tick >= previous && tick - previous < cooldown) return;
        try {
            // Match normal NeoForge permission/cancellation checks, but never use the held item.
            var interact = CommonHooks.onRightClickBlock(owner, InteractionHand.MAIN_HAND, pos, hit);
            if (interact.isCanceled() || interact.getUseBlock().isFalse()) return;
            if (state.useWithoutItem(owner.level(), owner, hit).consumesAction()) LAST_REDSTONE.put(owner, tick);
        } catch (Exception error) {
            log(error);
        }
    }

    private static boolean finite(Vec3 v) {
        return v != null && Double.isFinite(v.x) && Double.isFinite(v.y) && Double.isFinite(v.z);
    }

    private static void log(Exception error) {
        long now = System.currentTimeMillis();
        if (now - lastErrorLog >= 30000) {
            lastErrorLog = now;
            System.err.println("[Psychiatryk grapple] Server event failed; native behavior retained: " + error);
        }
    }
}
