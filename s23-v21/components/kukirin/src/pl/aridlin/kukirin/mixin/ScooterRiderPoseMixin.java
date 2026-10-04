package pl.aridlin.kukirin.mixin;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(HumanoidModel.class)
public abstract class ScooterRiderPoseMixin {
 @Shadow public ModelPart leftArm;
 @Shadow public ModelPart rightArm;
 @Shadow public ModelPart head;
 @Shadow public ModelPart hat;
 @Inject(method="setupAnim",at=@At("RETURN"))
 private void holdHandlebars(LivingEntity entity,float swing,float amount,float age,float yaw,float pitch,CallbackInfo ci){
  rightArm.yScale=1;leftArm.yScale=1;
  if(!(entity instanceof Player p)||!(p.getVehicle() instanceof pl.aridlin.kukirin.Scooter s))return;
  float partial=net.minecraft.util.Mth.clamp(age-entity.tickCount,0,1);
  head.yRot=net.minecraft.util.Mth.clamp(net.minecraft.util.Mth.wrapDegrees(net.minecraft.util.Mth.rotLerp(partial,playerYawOld(p),p.getYRot())-net.minecraft.util.Mth.rotLerp(partial,s.yRotO,s.getYRot())),-85,85)*(float)Math.PI/180;
  hat.copyFrom(head);
  boolean rightMain=p.getMainArm()==HumanoidArm.RIGHT;
  if((rightMain?p.getMainHandItem():p.getOffhandItem()).isEmpty())grip(rightArm,false,s,p,partial);
  if((rightMain?p.getOffhandItem():p.getMainHandItem()).isEmpty())grip(leftArm,true,s,p,partial);
 }
 private static float playerYawOld(Player p){return p.yRotO;}
 private static void grip(ModelPart arm,boolean left,pl.aridlin.kukirin.Scooter scooter,Player player,float partial){
  // Grip positions are measured from the actual scooter mesh, in its authored coordinates.
  var pivot=new net.minecraft.world.phys.Vec3(.00476325,.12631217,-.47555844);
  var point=new net.minecraft.world.phys.Vec3(left?-.244:.244,1.168,-.308);
  point=point.subtract(pivot).yRot(-scooter.steering(partial)*(float)Math.PI/180).add(pivot).scale(1.25);
  float vehicleYaw=net.minecraft.util.Mth.rotLerp(partial,scooter.yRotO,scooter.getYRot());
  point=point.yRot((180-vehicleYaw)*(float)Math.PI/180);
  var vehiclePos=lerp(scooter,partial).add(0,scooter.suspension(partial)+scooter.stepOffset(partial),0);
  var playerPos=lerp(player,partial).add(0,scooter.stepOffset(partial),0);
  float body=net.minecraft.util.Mth.rotLerp(partial,scooter.yBodyRotO,scooter.yBodyRot);
  float head=net.minecraft.util.Mth.rotLerp(partial,player.yHeadRotO,player.yHeadRot);
  float relative=net.minecraft.util.Mth.wrapDegrees(head-body),clamped=net.minecraft.util.Mth.clamp(relative,-85,85);body=head-clamped;if(clamped*clamped>2500)body+=clamped*.2f;
  var delta=vehiclePos.add(point).subtract(playerPos).yRot(-(180-body)*(float)Math.PI/180);
  double scale=.9375*player.getScale();
  double dx=-delta.x/scale*16-arm.x,dy=(1.501-delta.y/scale)*16-arm.y,dz=delta.z/scale*16-arm.z;
  double length=Math.sqrt(dx*dx+dy*dy+dz*dz);
  arm.xRot=-(float)Math.acos(net.minecraft.util.Mth.clamp(dy/Math.max(.001,length),-1,1));
  arm.yRot=(float)Math.atan2(-dx,-dz);arm.zRot=0;
  arm.yScale=(float)Math.clamp(length/10,.7,1.45);
 }
 private static net.minecraft.world.phys.Vec3 lerp(net.minecraft.world.entity.Entity entity,float partial){return new net.minecraft.world.phys.Vec3(net.minecraft.util.Mth.lerp(partial,entity.xOld,entity.getX()),net.minecraft.util.Mth.lerp(partial,entity.yOld,entity.getY()),net.minecraft.util.Mth.lerp(partial,entity.zOld,entity.getZ()));}
}
