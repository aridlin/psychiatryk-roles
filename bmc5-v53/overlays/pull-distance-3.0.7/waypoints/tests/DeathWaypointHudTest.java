import java.lang.reflect.Field;
import java.lang.reflect.Method;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pl.aridlin.psychiatrykroles.peeb.client.mixin.DeathWaypointHudMixin;
import sun.misc.Unsafe;
import xaero.common.minimap.waypoints.Waypoint;
import xaero.hud.minimap.waypoint.WaypointPurpose;
import xaero.hud.minimap.waypoint.render.world.WaypointWorldRenderContext;
import xaero.hud.minimap.waypoint.render.world.WaypointWorldRenderReader;

/** Executes the compiled injector against real installed Xaero waypoint classes. */
public class DeathWaypointHudTest {
    private static int checks;
    private static void check(boolean value, String label) {
        checks++;
        if (!value) throw new AssertionError(label);
    }
    public static void main(String[] args) throws Exception {
        Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        Unsafe unsafe = (Unsafe) unsafeField.get(null);
        DeathWaypointHudMixin injector = new DeathWaypointHudMixin() {};
        Method method = DeathWaypointHudMixin.class.getDeclaredMethod(
            "psychiatryk$hideDeathWaypointInWorld", Waypoint.class,
            WaypointWorldRenderContext.class, CallbackInfoReturnable.class);
        method.setAccessible(true);
        Field purposeField = Waypoint.class.getDeclaredField("purpose");
        purposeField.setAccessible(true);
        WaypointWorldRenderReader vanillaReader = new WaypointWorldRenderReader(null);
        // Constructors are skipped solely to avoid starting Minecraft registries in a headless test.
        // The actual Waypoint getter, actual purpose enum and compiled injector execute normally.
        for (WaypointPurpose purpose : WaypointPurpose.values()) {
            Waypoint waypoint = (Waypoint) unsafe.allocateInstance(Waypoint.class);
            purposeField.set(waypoint, purpose);
            waypoint.setName("Death"); // A normal named "Death" waypoint must remain visible.
            waypoint.setX(17); waypoint.setY(64); waypoint.setZ(-32);
            check(!vanillaReader.isHidden(waypoint, null), "Installed reader originally visible: " + purpose);
            for (boolean alreadyHidden : new boolean[] {false, true}) {
                CallbackInfoReturnable<Boolean> callback = new CallbackInfoReturnable<>(
                    "isHidden", true, alreadyHidden);
                method.invoke(injector, waypoint, null, callback);
                boolean isDeath = purpose == WaypointPurpose.DEATH || purpose == WaypointPurpose.OLD_DEATH;
                check(callback.isCancelled() == isDeath, "Death-only cancellation: " + purpose);
                check(callback.getReturnValue() == (isDeath || alreadyHidden), "Visibility preserved: " + purpose);
            }
            check(waypoint.getPurpose() == purpose, "Purpose is not modified: " + purpose);
            check(waypoint.getName().equals("Death"), "Name is not modified: " + purpose);
            check(waypoint.getX() == 17 && waypoint.getY() == 64 && waypoint.getZ() == -32,
                "Coordinates are not modified: " + purpose);
        }
        for (Waypoint waypoint : new Waypoint[] {null, (Waypoint) unsafe.allocateInstance(Waypoint.class)}) {
            CallbackInfoReturnable<Boolean> callback = new CallbackInfoReturnable<>("isHidden", true, false);
            method.invoke(injector, waypoint, null, callback);
            check(!callback.isCancelled(), "Null/missing purpose remains native");
        }
        System.out.println("{\"success\":true,\"checks\":" + checks
            + ",\"actual_xaero_classes\":true,\"game_launched\":false}");
    }
}
