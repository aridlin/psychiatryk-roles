package pl.aridlin.psychiatrykroles.peeb.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;
import pl.aridlin.psychiatrykroles.peeb.PeebAdventuresPhysics;
import pl.aridlin.psychiatrykroles.peeb.PeebAttachment;
import pl.aridlin.psychiatrykroles.peeb.PeebConfig;
import pl.aridlin.psychiatrykroles.peeb.PeebGrapple;

/** Own hooked travel and the airborne coast following release. */
public final class PeebMovement {
    private static LocalPlayer previous;
    private static boolean jumping;
    private static boolean grounded;
    private static boolean coasting;
    private PeebMovement() {}

    public static boolean travel(LivingEntity entity, Vec3 input) {
        // Other entities travel too; they must not clear the local flight state.
        if (!(entity instanceof LocalPlayer player)) return false;
        if (player != Minecraft.getInstance().player || !PeebClient.localActive()
            || !player.isAlive() || player.isSleeping() || player.isSpectator()
            || player.getAbilities().flying || player.isFallFlying() || PeebGrapple.isScooterRider(player)
            || player.isInWater() || player.isInLava() || player.onClimbable()) {
            previous = null; coasting = false; jumping = false; grounded = false;
            return false;
        }
        var tether = PeebClient.grapple(player);
        if (tether.isEmpty() && (!coasting || player != previous || player.onGround())) {
            previous = null;
            jumping = false;
            grounded = false;
            coasting = false;
            return false;
        }
        if (player != previous) {
            previous = player;
            grounded = player.onGround();
            jumping = false;
        }
        coasting = true;
        boolean jump = player.input.jumping;
        // Vanilla may already have applied its jump before travel; restore the
        // original game's jump impulse on the same rising input edge.
        boolean takeoff = (player.onGround() || grounded) && jump && !jumping;
        jumping = jump;
        double yaw = Math.toRadians(player.getYRot());
        PeebConfig.Values settings = PeebClient.settings();
        double speed = Math.min(settings.maxHorizontalSpeed(),
            PeebAdventuresPhysics.SPRINT_SPEED * PeebAdventuresPhysics.SCALE / 20.0);
        double sideways = player.input.leftImpulse, forward = player.input.forwardImpulse;
        Vec3 desired = new Vec3((sideways * Math.cos(yaw) - forward * Math.sin(yaw)) * speed,
            0.0, (forward * Math.cos(yaw) + sideways * Math.sin(yaw)) * speed);
        Vec3 movement = PeebAdventuresPhysics.coastStep(player.getDeltaMovement(), desired,
            player.onGround() || takeoff, takeoff, PeebAdventuresPhysics.TICK_SECONDS);
        if (tether.isPresent()) {
            var hook = tether.get();
            Vec3 pivot = PeebAttachment.pivot(player, hook.attachmentYaw());
            movement = PeebGrapple.pullVelocity(pivot, movement, hook.anchor(), hook.length(), settings, player.onGround());
        }
        Vec3 before = player.position();
        player.setDeltaMovement(movement);
        player.move(MoverType.SELF, movement);
        Vec3 actual = player.position().subtract(before);
        // Keep collision response, but do not add vanilla drag/gravity on top of
        // Peeb's controller. The next step owns those terms exactly once.
        player.setDeltaMovement(new Vec3(
            Math.abs(actual.x - movement.x) > 1.0E-6 ? actual.x : movement.x,
            Math.abs(actual.y - movement.y) > 1.0E-6 ? actual.y : movement.y,
            Math.abs(actual.z - movement.z) > 1.0E-6 ? actual.z : movement.z));
        grounded = player.onGround();
        player.calculateEntityAnimation(false);
        return true;
    }
}
