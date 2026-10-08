package pl.aridlin.psychiatrykroles.peeb.client.mixin;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pl.aridlin.psychiatrykroles.peeb.client.PeebMovement;

@Mixin(LivingEntity.class)
public abstract class PeebTravelMixin {
    @Inject(method = "travel", at = @At("HEAD"), cancellable = true)
    private void peeb$originalHookTravel(Vec3 input, CallbackInfo callback) {
        if (PeebMovement.travel((LivingEntity)(Object)this, input)) callback.cancel();
    }
}
