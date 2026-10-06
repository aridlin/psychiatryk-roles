package pl.aridlin.kukirin;

import com.github.exopandora.shouldersurfing.api.client.event.handler.ForceVanillaPlayerInputEventHandler;
import com.github.exopandora.shouldersurfing.api.event.IEventBus;
import com.github.exopandora.shouldersurfing.api.plugin.IShoulderSurfingPlugin;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/** Loaded by Shoulder Surfing's optional client plugin loader, before its event bus freezes. */
public final class ScooterShoulderPlugin implements IShoulderSurfingPlugin {
    @Override
    public void register(IEventBus events) {
        events.register(-10000, (ForceVanillaPlayerInputEventHandler) event -> {
            if (!isLocalDriver(event.getCameraEntity(), Minecraft.getInstance().player)) return;
            // Camera-relative walking rotates both impulses and the player's yaw. A scooter
            // instead needs its A/D steering and W/S throttle to keep their original meanings.
            event.setResult(true);
            event.cancel();
        });
    }

    public static boolean isLocalDriver(Entity cameraEntity, Player localPlayer) {
        return localPlayer != null && cameraEntity == localPlayer
                && localPlayer.getVehicle() instanceof Scooter scooter
                && scooter.getControllingPassenger() == localPlayer;
    }
}
