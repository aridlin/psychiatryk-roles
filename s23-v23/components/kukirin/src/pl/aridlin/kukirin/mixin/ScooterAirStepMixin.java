package pl.aridlin.kukirin.mixin;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
/** Reuses vanilla candidate-height and full collision sweeps for airborne scooters. */
@Mixin(Entity.class)
public abstract class ScooterAirStepMixin {
 @Redirect(method="collide",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/Entity;onGround()Z"))
 private boolean scooterMayStepInAir(Entity entity){return entity.onGround()||entity instanceof pl.aridlin.kukirin.Scooter scooter&&scooter.isVehicle();}
}
