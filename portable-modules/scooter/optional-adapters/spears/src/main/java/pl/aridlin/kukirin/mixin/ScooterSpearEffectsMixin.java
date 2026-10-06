package pl.aridlin.kukirin.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "com.notunanancyowen.spears.packets.TriggerStabEffectsC2SPacket", remap = false)
public abstract class ScooterSpearEffectsMixin {
    @Inject(method = "trigger(Lnet/minecraft/server/level/ServerPlayer;)V", at = @At("RETURN"), require = 1)
    private void scooterLunge(ServerPlayer player, CallbackInfo callback) {
        var hand = player.swingingArm == null ? InteractionHand.MAIN_HAND : player.swingingArm;
        pl.aridlin.kukirin.ScooterSpearLunge.afterAttack(player,
                hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
    }
}
