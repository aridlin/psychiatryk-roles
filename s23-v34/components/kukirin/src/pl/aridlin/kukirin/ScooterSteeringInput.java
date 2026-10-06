package pl.aridlin.kukirin;

import net.minecraft.world.entity.LivingEntity;

/** Common entry point; client and optional camera classes are resolved only on the client. */
public final class ScooterSteeringInput {
    public static float mouseYaw(LivingEntity rider, float scooterYaw) {
        if (!rider.level().isClientSide) return rider.getYRot();
        return Client.mouseYaw(rider, scooterYaw);
    }

    private static final class Client {
        private static float mouseYaw(LivingEntity rider, float scooterYaw) {
            var minecraft = net.minecraft.client.Minecraft.getInstance();
            if (rider != minecraft.player || !net.neoforged.fml.ModList.get().isLoaded("shouldersurfing"))
                return rider.getYRot();
            return Shoulder.mouseYaw(rider.getYRot(), scooterYaw);
        }
    }

    /** This class is never loaded when the optional Shoulder Surfing mod is absent. */
    private static final class Shoulder {
        private static float mouseYaw(float riderYaw, float scooterYaw) {
            var shoulder = com.github.exopandora.shouldersurfing.api.client.IShoulderSurfing.getInstance();
            if (!shoulder.isShoulderSurfing() || shoulder.isTemporaryFirstPerson()) return riderYaw;
            // Free look must remain a camera action, including when mouse steering is enabled.
            if (shoulder.isFreeLooking()) return scooterYaw;
            float yaw = shoulder.getCamera().getYRot();
            return Float.isFinite(yaw) ? yaw : riderYaw;
        }
    }

    private ScooterSteeringInput() {}
}
