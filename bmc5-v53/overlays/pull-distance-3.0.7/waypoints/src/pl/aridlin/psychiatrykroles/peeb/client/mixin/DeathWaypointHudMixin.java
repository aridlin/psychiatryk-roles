package pl.aridlin.psychiatrykroles.peeb.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xaero.common.minimap.waypoints.Waypoint;
import xaero.hud.minimap.waypoint.render.world.WaypointWorldRenderContext;

/** Hides death markers only in Xaero's world overlay. Map markers stay intact. */
@Pseudo
@Mixin(targets = "xaero.hud.minimap.waypoint.render.world.WaypointWorldRenderReader", remap = false)
public abstract class DeathWaypointHudMixin {
    @Inject(
        method = "isHidden(Lxaero/common/minimap/waypoints/Waypoint;Lxaero/hud/minimap/waypoint/render/world/WaypointWorldRenderContext;)Z",
        at = @At("HEAD"),
        cancellable = true,
        remap = false
    )
    private void psychiatryk$hideDeathWaypointInWorld(
        Waypoint waypoint, WaypointWorldRenderContext context,
        CallbackInfoReturnable<Boolean> callback
    ) {
        if (waypoint != null && waypoint.getPurpose() != null && waypoint.getPurpose().isDeath()) {
            callback.setReturnValue(true);
        }
    }
}
