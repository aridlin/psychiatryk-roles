package pl.aridlin.kukirin;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.Vec3;
public final class Scooter extends Horse {
 public Scooter(EntityType<? extends Scooter> type,Level level){super(type,level);setTamed(true);setInvulnerable(true);setPersistenceRequired();}
 @Override protected void registerGoals(){}
 @Override public boolean isSaddled(){return true;}
 @Override public boolean isFood(net.minecraft.world.item.ItemStack s){return false;}
 @Override public boolean canMate(net.minecraft.world.entity.animal.Animal a){return false;}
 @Override public void onPlayerJump(int power){}
 private static final net.minecraft.network.syncher.EntityDataAccessor<Float> STEER=net.minecraft.network.syncher.SynchedEntityData.defineId(Scooter.class,net.minecraft.network.syncher.EntityDataSerializers.FLOAT);
 private float throttle,previousSteer,previousLean,lean;private double wheel,previousWheel,lastX,lastZ;private boolean visualReady;
 @Override protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder b){super.defineSynchedData(b);b.define(STEER,0f);}
 @Override public boolean shouldRiderSit(){return false;}
 @Override public boolean isInvulnerableTo(net.minecraft.world.damagesource.DamageSource s){return true;}
 @Override public boolean fireImmune(){return true;}
 @Override protected net.minecraft.world.phys.Vec2 getRiddenRotation(net.minecraft.world.entity.LivingEntity p){float desired=Math.abs(p.xxa)>.01f?-p.xxa*28f:net.minecraft.util.Mth.clamp(net.minecraft.util.Mth.wrapDegrees(p.getYRot()-getYRot())*1.15f,-28,28);float steer=net.minecraft.util.Mth.lerp(.85f,entityData.get(STEER),desired);entityData.set(STEER,steer);float turn=turnRate(getDeltaMovement().horizontalDistance())*(throttle<0?-1f:1f);float yaw=getYRot()+steer/28f*turn;return new net.minecraft.world.phys.Vec2(0,yaw);}
 static float turnRate(double speed){return net.minecraft.util.Mth.lerp(net.minecraft.util.Mth.clamp((float)speed/.35f,0f,1f),12f,5f);}
 @Override protected Vec3 getRiddenInput(Player p,Vec3 input){float target=p.zza>0?1:p.zza<0?-.25f:0;if(p.zza<0&&throttle>0)throttle=Math.max(0,throttle-.10f);else if(target!=0)throttle=net.minecraft.util.Mth.approach(throttle,target,.035f);else throttle*=.97f;if(horizontalCollision)throttle*=.65f;return new Vec3(0,0,throttle);}
 @Override public void tick(){previousSteer=entityData.get(STEER);previousLean=lean;previousWheel=wheel;super.tick();if(visualReady){double dx=getX()-lastX,dz=getZ()-lastZ,travel=Math.sqrt(dx*dx+dz*dz);if(travel<2){double forward=dx*-Math.sin(Math.toRadians(getYRot()))+dz*Math.cos(Math.toRadians(getYRot()));wheel+=Math.copySign(travel,forward)/(10.5*1.4/111)*180/Math.PI;}}visualReady=true;lastX=getX();lastZ=getZ();lean=net.minecraft.util.Mth.lerp(.2f,lean,-entityData.get(STEER)*Math.min(1,(float)getDeltaMovement().horizontalDistance()*3)*.25f);if(!isVehicle())throttle=0;}
 public float steering(float partial){return net.minecraft.util.Mth.lerp(partial,previousSteer,entityData.get(STEER));}
 public float lean(float partial){return net.minecraft.util.Mth.lerp(partial,previousLean,lean);}
 public float wheelRotation(float partial){return (float)((previousWheel+(wheel-previousWheel)*partial)%360);}
 @Override protected float getRiddenSpeed(Player p){return .35f;}
 @Override protected Vec3 getPassengerAttachmentPoint(Entity e,EntityDimensions d,float scale){return new Vec3(0,.23,0).add(e.getVehicleAttachmentPoint(this));}
 @Override protected net.minecraft.sounds.SoundEvent getAmbientSound(){return null;}
 @Override protected net.minecraft.sounds.SoundEvent getHurtSound(net.minecraft.world.damagesource.DamageSource s){return net.minecraft.sounds.SoundEvents.IRON_GOLEM_HURT;}
 @Override protected net.minecraft.sounds.SoundEvent getDeathSound(){return net.minecraft.sounds.SoundEvents.IRON_GOLEM_DEATH;}
 @Override protected void playStepSound(net.minecraft.core.BlockPos p,net.minecraft.world.level.block.state.BlockState s){}
 @Override public InteractionResult mobInteract(Player p,InteractionHand hand){if(p.isShiftKeyDown()&&!isVehicle()){if(!level().isClientSide){spawnAtLocation(Kukirin.ITEM.get());discard();}return InteractionResult.sidedSuccess(level().isClientSide);}if(!isVehicle()){if(!level().isClientSide)p.startRiding(this);return InteractionResult.sidedSuccess(level().isClientSide);}return InteractionResult.PASS;}
 @Override protected net.minecraft.resources.ResourceKey<net.minecraft.world.level.storage.loot.LootTable> getDefaultLootTable(){return net.minecraft.world.level.storage.loot.BuiltInLootTables.EMPTY;}
 @Override protected void dropCustomDeathLoot(net.minecraft.server.level.ServerLevel level,net.minecraft.world.damagesource.DamageSource s,boolean b){super.dropCustomDeathLoot(level,s,b);spawnAtLocation(Kukirin.ITEM.get());}
}
