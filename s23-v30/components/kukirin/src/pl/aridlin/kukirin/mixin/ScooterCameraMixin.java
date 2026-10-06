package pl.aridlin.kukirin.mixin;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(Camera.class)
public abstract class ScooterCameraMixin {
 @Shadow protected abstract void setPosition(double x,double y,double z);
 @Inject(method="setup",at=@At("RETURN"))
 private void smoothScooterStep(BlockGetter level,Entity entity,boolean detached,boolean reverse,float partial,CallbackInfo ci){
  if(entity.getVehicle() instanceof pl.aridlin.kukirin.Scooter s){var camera=(Camera)(Object)this;var pos=camera.getPosition();setPosition(pos.x,pos.y+s.stepOffset(partial),pos.z);}
 }
}
