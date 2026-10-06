package pl.aridlin.psychiatrykroles.pointblankfix.mixin;
import pl.aridlin.psychiatrykroles.pointblankfix.MountShotFilter;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets="com.vicmatskiv.pointblank.entity.SlowProjectile",remap=false)
public abstract class SlowProjectileMountMixin {
    @Shadow private HitResult hitScanTarget;
    @Inject(method="canHitEntity",at=@At("HEAD"),cancellable=true,require=1)
    private void psychiatryk$excludeOwnRig(Entity target,CallbackInfoReturnable<Boolean> callback) {
        if(MountShotFilter.protectedFromOwnShot(((Projectile)(Object)this).getOwner(),target))callback.setReturnValue(false);
    }
    @Inject(method="getHitResultOnMoveOrViewVector",at=@At("HEAD"),require=1)
    private void psychiatryk$discardUnsafeCachedTarget(CallbackInfoReturnable<HitResult> callback) {
        if(hitScanTarget instanceof EntityHitResult hit && MountShotFilter.protectedFromOwnShot(((Projectile)(Object)this).getOwner(),hit.getEntity()))hitScanTarget=null;
    }
}
