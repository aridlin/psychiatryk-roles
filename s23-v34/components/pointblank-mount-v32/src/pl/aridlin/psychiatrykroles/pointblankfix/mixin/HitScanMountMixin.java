package pl.aridlin.psychiatrykroles.pointblankfix.mixin;
import pl.aridlin.psychiatrykroles.pointblankfix.MountShotFilter;

import java.util.List;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Pseudo
@Mixin(targets="com.vicmatskiv.pointblank.util.HitScan",remap=false)
public abstract class HitScanMountMixin {
    @Redirect(method={
        "getNearestObjectInCrosshair(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;FDLjava/util/function/Predicate;Ljava/util/function/Predicate;Ljava/util/List;)Lnet/minecraft/world/phys/HitResult;",
        "ensureEntityInCrosshair(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;FDF)Lnet/minecraft/world/phys/HitResult;"
    },at=@At(value="INVOKE",target="Lnet/minecraft/world/level/Level;getEntities(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;)Ljava/util/List;"),require=2)
    private static List<Entity> psychiatryk$excludeOwnScooterAssembly(Level level,Entity shooter,AABB bounds) {
        return MountShotFilter.targets(shooter,level.getEntities(shooter,bounds));
    }
}
