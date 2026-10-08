package pl.aridlin.psychiatrykroles.peeb.client;

/** V0.05 GlideController.Update movement-facing yaw, expressed in Minecraft units. */
public final class PeebTurning {
    // Actual level3 GlideController prefab: moveSpeed = 7.0 Unity units/second.
    private static final double ORIGINAL_MOVE_SPEED = 7.0;
    private static final double MODEL_SCALE = 1.4851409;

    private PeebTurning() {}

    public static float towardMovement(float currentYaw, double velocityX,
                                       double velocityZ, float seconds) {
        if (!Float.isFinite(currentYaw) || !Double.isFinite(velocityX)
            || !Double.isFinite(velocityZ) || !Float.isFinite(seconds)) {
            return Float.isFinite(currentYaw) ? currentYaw : 0.0F;
        }
        double speed = Math.hypot(velocityX, velocityZ);
        if (!(speed > 1.0E-8) || !(seconds > 0.0F)) return currentYaw;
        float goal = (float)Math.toDegrees(Math.atan2(-velocityX, velocityZ));
        double normalizedSpeed = speed * 20.0 / MODEL_SCALE / ORIGINAL_MOVE_SPEED;
        float blend = (float)Math.clamp(0.2 * normalizedSpeed * seconds * 60.0, 0.0, 1.0);
        // For upright rotations, Quaternion.Slerp is the shortest-arc yaw lerp.
        return currentYaw + wrapDegrees(goal - currentYaw) * blend;
    }

    private static float wrapDegrees(float angle) {
        float wrapped = angle % 360.0F;
        if (wrapped >= 180.0F) wrapped -= 360.0F;
        if (wrapped < -180.0F) wrapped += 360.0F;
        return wrapped;
    }
}
