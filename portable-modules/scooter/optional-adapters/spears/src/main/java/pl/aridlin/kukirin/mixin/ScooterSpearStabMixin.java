package pl.aridlin.kukirin.mixin;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "com.notunanancyowen.spears.components.PiercingWeapon", remap = false)
public abstract class ScooterSpearStabMixin {
    @Inject(method = "stab(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/EquipmentSlot;)Z",
            at = @At("RETURN"), require = 1)
    private void scooterLunge(LivingEntity entity, EquipmentSlot slot, CallbackInfoReturnable<Boolean> callback) {
        pl.aridlin.kukirin.ScooterSpearLunge.afterAttack(entity, slot);
    }
}
