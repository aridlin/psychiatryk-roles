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
            try {
                Class<?> api=Class.forName("com.github.exopandora.shouldersurfing.api.client.IShoulderSurfing");
                Object shoulder=api.getMethod("getInstance").invoke(null);
                if(!(boolean)api.getMethod("isShoulderSurfing").invoke(shoulder)||(boolean)api.getMethod("isTemporaryFirstPerson").invoke(shoulder))return riderYaw;
                if((boolean)api.getMethod("isFreeLooking").invoke(shoulder))return scooterYaw;
                Object camera=api.getMethod("getCamera").invoke(shoulder);
                float yaw=((Number)camera.getClass().getMethod("getYRot").invoke(camera)).floatValue();
                return Float.isFinite(yaw)?yaw:riderYaw;
            } catch(ReflectiveOperationException|LinkageError unavailable){return riderYaw;}
        }
    }

    private ScooterSteeringInput() {}
}
