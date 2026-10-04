package pl.aridlin.kukirin;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.Vec3;
public final class Scooter extends PathfinderMob {
 public Scooter(EntityType<? extends Scooter> type,Level level){super(type,level);setInvulnerable(true);setPersistenceRequired();}
 @Override protected void registerGoals(){}
 private static final net.minecraft.network.syncher.EntityDataAccessor<Float> STEER=net.minecraft.network.syncher.SynchedEntityData.defineId(Scooter.class,net.minecraft.network.syncher.EntityDataSerializers.FLOAT);
 private static final net.minecraft.network.syncher.EntityDataAccessor<Boolean> MOUSE=net.minecraft.network.syncher.SynchedEntityData.defineId(Scooter.class,net.minecraft.network.syncher.EntityDataSerializers.BOOLEAN);
 public void remoteSteering(float steer){entityData.set(STEER,net.minecraft.util.Mth.clamp(steer,-28,28));}
 public void mouseSteering(boolean value){entityData.set(MOUSE,value);}
 private static final net.minecraft.network.syncher.EntityDataAccessor<Integer> BOOST=net.minecraft.network.syncher.SynchedEntityData.defineId(Scooter.class,net.minecraft.network.syncher.EntityDataSerializers.INT);
 private float suspension,previousSuspension,suspensionVelocity,drift,stepOffset,previousStepOffset;
 private static final net.minecraft.network.syncher.EntityDataAccessor<Float> BOOST_POWER=net.minecraft.network.syncher.SynchedEntityData.defineId(Scooter.class,net.minecraft.network.syncher.EntityDataSerializers.FLOAT);
 private final java.util.Map<java.util.UUID,Long> impactCooldown=new java.util.HashMap<>();
 private static final net.minecraft.network.syncher.EntityDataAccessor<Integer> LUNGE=net.minecraft.network.syncher.SynchedEntityData.defineId(Scooter.class,net.minecraft.network.syncher.EntityDataSerializers.INT);
 private static final net.minecraft.network.syncher.EntityDataAccessor<Float> LUNGE_POWER=net.minecraft.network.syncher.SynchedEntityData.defineId(Scooter.class,net.minecraft.network.syncher.EntityDataSerializers.FLOAT);
 private static final net.minecraft.network.syncher.EntityDataAccessor<Integer> DISC_SERIAL=net.minecraft.network.syncher.SynchedEntityData.defineId(Scooter.class,net.minecraft.network.syncher.EntityDataSerializers.INT);
 private static final net.minecraft.network.syncher.EntityDataAccessor<Boolean> DISC_PLAYING=net.minecraft.network.syncher.SynchedEntityData.defineId(Scooter.class,net.minecraft.network.syncher.EntityDataSerializers.BOOLEAN);
 private long discUntil;
 public int discSerial(){return entityData.get(DISC_SERIAL);}
 public boolean discPlaying(){return entityData.get(DISC_PLAYING);}
 public void stopDisc(){if(entityData.get(DISC_PLAYING)){entityData.set(DISC_PLAYING,false);entityData.set(DISC_SERIAL,discSerial()+1);}}
 public void startDisc(){var stack=ScooterMusic.disc(getItemBySlot(EquipmentSlot.FEET),this);var playable=stack.get(net.minecraft.core.component.DataComponents.JUKEBOX_PLAYABLE);if(playable!=null)playable.song().unwrap(registryAccess()).ifPresent(song->{discUntil=level().getGameTime()+song.value().lengthInTicks();entityData.set(DISC_PLAYING,true);entityData.set(DISC_SERIAL,discSerial()+1);});}
 private int lastLunge;private long nextLunge;
 public boolean lunge(net.minecraft.server.level.ServerPlayer p){int l=ScooterEnchants.level(this,"lunge");if(level().isClientSide||p.getVehicle()!=this||l==0||level().getGameTime()<nextLunge)return false;nextLunge=level().getGameTime()+40;entityData.set(LUNGE_POWER,.14f+.07f*l);entityData.set(LUNGE,entityData.get(LUNGE)+1);return true;}
 private static final net.minecraft.network.syncher.EntityDataAccessor<Integer> WALL_KICK=net.minecraft.network.syncher.SynchedEntityData.defineId(Scooter.class,net.minecraft.network.syncher.EntityDataSerializers.INT);
 private static final net.minecraft.network.syncher.EntityDataAccessor<Float> WALL_X=net.minecraft.network.syncher.SynchedEntityData.defineId(Scooter.class,net.minecraft.network.syncher.EntityDataSerializers.FLOAT);
 private static final net.minecraft.network.syncher.EntityDataAccessor<Float> WALL_Z=net.minecraft.network.syncher.SynchedEntityData.defineId(Scooter.class,net.minecraft.network.syncher.EntityDataSerializers.FLOAT);
 private int lastWallKick;private long nextWallKick;
 private int lastBoost;private long nextBoostTick;
 private float localSteer,turnSpeed;
 private float throttle,previousSteer,previousLean,lean,previousSpeed,observedSpeed;private double wheel,previousWheel,lastX,lastZ;private boolean visualReady;
 @Override protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder b){super.defineSynchedData(b);b.define(WALL_KICK,0);b.define(WALL_X,0f);b.define(WALL_Z,0f);b.define(DISC_SERIAL,0);b.define(DISC_PLAYING,false);b.define(STEER,0f);b.define(MOUSE,true);b.define(BOOST,0);b.define(BOOST_POWER,.82f);b.define(LUNGE,0);b.define(LUNGE_POWER,0f);}
 @Override public boolean shouldRiderSit(){return false;}
 @Override public boolean isInvulnerableTo(net.minecraft.world.damagesource.DamageSource s){return true;}
 @Override public boolean fireImmune(){return true;}
 protected net.minecraft.world.phys.Vec2 getRiddenRotation(net.minecraft.world.entity.LivingEntity p){float desired=Math.abs(p.xxa)>.01f?-p.xxa*28f:entityData.get(MOUSE)?net.minecraft.util.Mth.clamp(net.minecraft.util.Mth.wrapDegrees(p.getYRot()-getYRot())*1.15f,-28,28):0;float steer=net.minecraft.util.Mth.lerp(.85f,localSteer,desired);localSteer=steer;float turn=Math.min(16,turnRate(turnSpeed)*(1+.10f*ScooterEnchants.level(this,"sweeping_edge")))*(throttle<0?-1f:1f)*(onGround()?1f:.35f);float yaw=getYRot()+steer/28f*turn;return new net.minecraft.world.phys.Vec2(0,yaw);}
 static float turnRate(double speed){return ScooterHandling.turnRate(speed);}
 @Override public LivingEntity getControllingPassenger(){return getFirstPassenger() instanceof Player p?p:null;}
 @Override protected void tickRidden(Player p,Vec3 input){super.tickRidden(p,input);if(!isControlledByLocalInstance()){yBodyRot=getYRot();yHeadRot=getYRot();return;}var rotation=getRiddenRotation(p);setRot(rotation.y,rotation.x);yBodyRot=getYRot();yHeadRot=getYRot();}
 @Override protected Vec3 getRiddenInput(Player p,Vec3 input){throttle=p.zza;return new Vec3(0,0,p.zza);}
 @Override public void aiStep(){
  // LivingEntity applies an extra 0.98 remote-AI decay even to a locally ridden client vehicle.
  // Cancel it here; travel owns drag on this dedicated vehicle.
  if(level().isClientSide&&isControlledByLocalInstance())setDeltaMovement(getDeltaMovement().scale(1/.98));
  super.aiStep();
 }
 @Override public void travel(Vec3 input){
  if(!isControlledByLocalInstance())return;
  if(isInWater()||isInLava()){super.travel(input);if(isInWater()&&ScooterEnchants.level(this,"depth_strider")>0){var v=getDeltaMovement();double a=.012*ScooterEnchants.level(this,"depth_strider")*input.z;setDeltaMovement(v.add(-Math.sin(Math.toRadians(getYRot()))*a,0,Math.cos(Math.toRadians(getYRot()))*a));}return;}
  if(ScooterRental.isRental(this)&&getFirstPassenger() instanceof net.minecraft.world.entity.monster.Zombie)input=new Vec3(0,0,.6);
  Vec3 old=getDeltaMovement();
  drift=ScooterHandling.driftNext(drift,old.horizontalDistance(),steering(1),onGround());drift*=1-.08f*ScooterEnchants.level(this,"density");
  var motion=ScooterHandling.step(old.x,old.z,getYRot(),input.z,onGround(),isVehicle(),drift,cruiseSpeed(),.045*(1+.15*ScooterEnchants.level(this,"density")),1+.12*ScooterEnchants.level(this,"quick_charge"));
  if(onGround()&&ScooterEnchants.level(this,"soul_speed")>0&&getBlockStateOn().is(net.minecraft.tags.BlockTags.SOUL_SPEED_BLOCKS)){double factor=1+.015*ScooterEnchants.level(this,"soul_speed");motion=new ScooterHandling.Motion(motion.x()*factor,motion.z()*factor);}
  if(!onGround()&&isVehicle())motion=ScooterHandling.airSteer(motion,steering(1));
  double y=old.y-(isNoGravity()?0:ScooterHandling.fallingGravity(old.y,getGravity(),isVehicle()));
  if(ScooterRental.isRental(this)&&motion.speed()>RentalPolicy.MAX_BLOCKS_PER_TICK){double f=RentalPolicy.MAX_BLOCKS_PER_TICK/motion.speed();motion=new ScooterHandling.Motion(motion.x()*f,motion.z()*f);}
  setDeltaMovement(motion.x(),y,motion.z());
  move(MoverType.SELF,getDeltaMovement());
  Vec3 clipped=getDeltaMovement();
  var slide=ScooterHandling.wallSlide(motion,new ScooterHandling.Motion(clipped.x,clipped.z),horizontalCollision);setDeltaMovement(slide.x(),verticalCollision?0:clipped.y*.98,slide.z());
  calculateEntityAnimation(false);
 }
 public double cruiseSpeed(){if(ScooterRental.isRental(this))return RentalPolicy.MAX_BLOCKS_PER_TICK;return ScooterHandling.TOP_SPEED*(1+.08*ScooterEnchants.level(this,"efficiency"));}
 public float speed(float partial){return net.minecraft.util.Mth.lerp(partial,previousSpeed,observedSpeed);}
 public float throttle(){return throttle;}
 public boolean windBoost(net.minecraft.server.level.ServerPlayer p){
  if(level().isClientSide||p.getVehicle()!=this)return false;
  if(!onGround()&&wallKick(p))return true;
  if(level().getGameTime()<nextBoostTick)return false;
  net.minecraft.world.item.ItemStack charge=null;
  for(int i=0;i<9;i++){var stack=p.getInventory().getItem(i);if(stack.is(net.minecraft.world.item.Items.WIND_CHARGE)){charge=stack;break;}}
  if(charge==null&&!onGround())return false;
  if(charge!=null&&!p.isCreative()){charge.shrink(1);p.getInventory().setChanged();}
  nextBoostTick=level().getGameTime()+10;entityData.set(BOOST_POWER,(charge!=null?.82f:.5475f)+.06f*ScooterEnchants.level(this,"wind_burst"));entityData.set(BOOST,entityData.get(BOOST)+1);
  if(charge!=null){
   ((net.minecraft.server.level.ServerLevel)level()).sendParticles(net.minecraft.core.particles.ParticleTypes.GUST,getX(),getY()+.2,getZ(),8,.25,.1,.25,.02);
   level().playSound(null,blockPosition(),net.minecraft.sounds.SoundEvents.WIND_CHARGE_BURST.value(),net.minecraft.sounds.SoundSource.PLAYERS,.7f,1.1f);
  }return true;
 }

 public Vec3 touchingWallNormal(){
  var box=getBoundingBox().deflate(.002,.05,.002);
  Vec3 best=null;double bestDot=Double.POSITIVE_INFINITY;
  for(var normal:java.util.List.of(new Vec3(-1,0,0),new Vec3(1,0,0),new Vec3(0,0,-1),new Vec3(0,0,1))){
   if(level().getBlockCollisions(this,box.move(normal.scale(-.14))).iterator().hasNext()){
    double dot=getDeltaMovement().dot(normal);if(best==null||dot<bestDot){best=normal;bestDot=dot;}
   }
  }return best;
 }
 public boolean wallKick(net.minecraft.server.level.ServerPlayer p){
  if(level().isClientSide||p.getVehicle()!=this||onGround()||level().getGameTime()<nextWallKick)return false;
  var normal=touchingWallNormal();if(normal==null)return false;
  nextWallKick=level().getGameTime()+8;nextBoostTick=level().getGameTime()+8;
  entityData.set(WALL_X,(float)normal.x);entityData.set(WALL_Z,(float)normal.z);entityData.set(WALL_KICK,entityData.get(WALL_KICK)+1);
  ((net.minecraft.server.level.ServerLevel)level()).sendParticles(net.minecraft.core.particles.ParticleTypes.GUST,getX(),getY()+.35,getZ(),4,.15,.1,.15,.01);
  level().playSound(null,blockPosition(),net.minecraft.sounds.SoundEvents.WIND_CHARGE_BURST.value(),net.minecraft.sounds.SoundSource.PLAYERS,.35f,1.4f);return true;
 }
 @Override public void tick(){
  ScooterRental.tick(this);if(isRemoved())return;
  if(!level().isClientSide&&ScooterEnchants.bound(getItemBySlot(EquipmentSlot.FEET))){var owner=ScooterEnchants.owner(getItemBySlot(EquipmentSlot.FEET));for(var rider:java.util.List.copyOf(getPassengers()))if(!rider.getUUID().equals(owner))rider.stopRiding();}
  ScooterFrostWalker.tick(this);
  if(!level().isClientSide&&discPlaying()&&level().getGameTime()>discUntil)stopDisc();
  if(!level().isClientSide&&ScooterEnchants.bound(getItemBySlot(EquipmentSlot.FEET))){var data=getPersistentData();if(getY()<level().getMinBuildHeight()-16){var safe=data.contains("ScooterSafePos")?net.minecraft.core.BlockPos.of(data.getLong("ScooterSafePos")):level().getSharedSpawnPos();teleportTo(safe.getX()+.5,safe.getY()+1,safe.getZ()+.5);setDeltaMovement(Vec3.ZERO);}else if(onGround())data.putLong("ScooterSafePos",blockPosition().asLong());}

  int kick=entityData.get(WALL_KICK);if(kick!=lastWallKick){lastWallKick=kick;if(isControlledByLocalInstance()&&isVehicle()){
   var v=getDeltaMovement();var m=ScooterHandling.wallKick(v.x,v.z,getYRot(),steering(1),entityData.get(WALL_X),entityData.get(WALL_Z),cruiseSpeed()*1.5);
   setDeltaMovement(m.x(),Math.max(.38,v.y),m.z());float yaw=(float)Math.toDegrees(Math.atan2(-m.x(),m.z()));setYRot(yaw);yBodyRot=yaw;yHeadRot=yaw;localSteer=0;
   if(getControllingPassenger() instanceof Player rider){rider.setYRot(yaw);rider.yHeadRot=yaw;}
  }}
  int lunge=entityData.get(LUNGE);if(lunge!=lastLunge){lastLunge=lunge;if(isControlledByLocalInstance()&&isVehicle()){double angle=Math.toRadians(getYRot()),power=entityData.get(LUNGE_POWER);var v=getDeltaMovement().add(-Math.sin(angle)*power,0,Math.cos(angle)*power);double speed=v.horizontalDistance();double limit=cruiseSpeed()*1.5;if(speed>limit)v=new Vec3(v.x*limit/speed,v.y,v.z*limit/speed);setDeltaMovement(v);}}
  int boost=entityData.get(BOOST);if(boost!=lastBoost){lastBoost=boost;if(isControlledByLocalInstance()&&isVehicle())setDeltaMovement(getDeltaMovement().x,Math.max(getDeltaMovement().y,entityData.get(BOOST_POWER)),getDeltaMovement().z);}
if(!level().isClientSide&&ScooterEnchants.bound(getItemBySlot(EquipmentSlot.FEET)))ScooterIndex.get(getServer()).track(this);
previousSteer=steering(1);previousLean=lean;previousWheel=wheel;previousSpeed=observedSpeed;previousSuspension=suspension;previousStepOffset=stepOffset;stepOffset*=.55f;double beforePositionY=getY();double beforeY=getDeltaMovement().y;super.tick();double climbed=getY()-beforePositionY;if((onGround()||climbed>Math.max(0,beforeY)+.05)&&climbed>.08&&climbed<=1.35)stepOffset-=climbed;stepOffset=net.minecraft.util.Mth.clamp(stepOffset,-1.35f,0);double impact=beforeY-getDeltaMovement().y;if(onGround()&&Math.abs(impact)>.08)suspensionVelocity-=Math.min(.035,Math.abs(impact)*.025);suspensionVelocity+=-suspension*.32;suspensionVelocity*=.62;suspension=net.minecraft.util.Mth.clamp(suspension+suspensionVelocity,-.055f,.025f);if(visualReady){double dx=getX()-lastX,dz=getZ()-lastZ,travel=Math.sqrt(dx*dx+dz*dz);observedSpeed=travel<2?(float)travel:0;if(travel<2){double forward=dx*-Math.sin(Math.toRadians(getYRot()))+dz*Math.cos(Math.toRadians(getYRot()));wheel+=Math.copySign(travel,forward)/(10.5*1.4/111)*180/Math.PI;}}turnSpeed=Math.max((float)getDeltaMovement().horizontalDistance(),Math.max(observedSpeed,turnSpeed*.92f));visualReady=true;lastX=getX();lastZ=getZ();lean=net.minecraft.util.Mth.lerp(.2f,lean,ScooterRental.isRental(this)&&!isVehicle()?78:-steering(1)*Math.min(1,observedSpeed*2)*.25f);if(!isVehicle())throttle=0;if(!level().isClientSide&&isVehicle()){int pierce=ScooterEnchants.level(this,"piercing"),knockback=ScooterEnchants.level(this,"knockback");if((pierce>0||knockback>0)&&observedSpeed>.22&&getControllingPassenger() instanceof Player rider){for(var target:level().getEntitiesOfClass(LivingEntity.class,getBoundingBox().inflate(.2),t->t!=this&&t!=rider&&t.getVehicle()!=this)){if(target instanceof Player player&&!rider.canHarmPlayer(player))continue;if(level().getGameTime()<impactCooldown.getOrDefault(target.getUUID(),0L))continue;impactCooldown.put(target.getUUID(),level().getGameTime()+20);if(pierce>0)target.hurt(damageSources().playerAttack(rider),Math.min(8,(float)(2*pierce*observedSpeed/.72)));double shove=.4+.45*knockback;target.push(getDeltaMovement().x*shove,.12+.04*knockback,getDeltaMovement().z*shove);}}impactCooldown.entrySet().removeIf(e->e.getValue()<level().getGameTime());}}
 public float stepOffset(float partial){return net.minecraft.util.Mth.lerp(partial,previousStepOffset,stepOffset);}
 public float drift(){return drift;}
 public float suspension(float partial){return net.minecraft.util.Mth.lerp(partial,previousSuspension,suspension);}
 public boolean headlights(){long time=Math.floorMod(level().getDayTime(),24000);boolean night=level().dimensionType().hasSkyLight()&&time>12500&&time<23500;return isVehicle()&&(night||level().getMaxLocalRawBrightness(blockPosition())<8);}
 public float steering(float partial){return net.minecraft.util.Mth.lerp(partial,previousSteer,level().isClientSide&&isControlledByLocalInstance()?localSteer:entityData.get(STEER));}
 public float lean(float partial){return net.minecraft.util.Mth.lerp(partial,previousLean,lean);}
 public float wheelRotation(float partial){return (float)((previousWheel+(wheel-previousWheel)*partial)%360);}
 @Override public boolean causeFallDamage(float distance,float multiplier,net.minecraft.world.damagesource.DamageSource source){return false;}
 @Override protected float getRiddenSpeed(Player p){return .35f;}
 @Override protected Vec3 getPassengerAttachmentPoint(Entity e,EntityDimensions d,float scale){return new Vec3(0,.23,-.20).yRot(-getYRot()*(float)Math.PI/180).add(e.getVehicleAttachmentPoint(this));}
 @Override protected net.minecraft.sounds.SoundEvent getAmbientSound(){return null;}
 @Override protected net.minecraft.sounds.SoundEvent getHurtSound(net.minecraft.world.damagesource.DamageSource s){return net.minecraft.sounds.SoundEvents.IRON_GOLEM_HURT;}
 @Override protected net.minecraft.sounds.SoundEvent getDeathSound(){return net.minecraft.sounds.SoundEvents.IRON_GOLEM_DEATH;}
 @Override protected void playStepSound(net.minecraft.core.BlockPos p,net.minecraft.world.level.block.state.BlockState s){}
 private net.minecraft.world.item.ItemStack scooterItem(){var stored=getItemBySlot(EquipmentSlot.FEET);return stored.is(Kukirin.ITEM.get())?stored.copyWithCount(1):new net.minecraft.world.item.ItemStack(Kukirin.ITEM.get());}
 @Override public InteractionResult mobInteract(Player p,InteractionHand hand){if(ScooterRental.isRental(this))return ScooterRental.interact(this,p);if(ScooterEnchants.bound(getItemBySlot(EquipmentSlot.FEET))&&!p.getUUID().equals(ScooterEnchants.owner(getItemBySlot(EquipmentSlot.FEET))))return InteractionResult.FAIL;if(p.getItemInHand(hand).has(net.minecraft.core.component.DataComponents.JUKEBOX_PLAYABLE)&&ScooterUpgradeRecipe.has(getItemBySlot(EquipmentSlot.FEET),"jukebox")){if(!level().isClientSide&&p instanceof net.minecraft.server.level.ServerPlayer sp)ScooterMusic.insertDisc(this,sp,hand);return InteractionResult.sidedSuccess(level().isClientSide);}if(p.isShiftKeyDown()&&!isVehicle()){if(!level().isClientSide){ScooterMusic.stop(this);spawnAtLocation(scooterItem());ScooterIndex.get(getServer()).remove(getUUID());discard();}return InteractionResult.sidedSuccess(level().isClientSide);}if(!isVehicle()){if(!level().isClientSide)p.startRiding(this);return InteractionResult.sidedSuccess(level().isClientSide);}return InteractionResult.PASS;}
 @Override protected net.minecraft.resources.ResourceKey<net.minecraft.world.level.storage.loot.LootTable> getDefaultLootTable(){return net.minecraft.world.level.storage.loot.BuiltInLootTables.EMPTY;}
 @Override protected void dropCustomDeathLoot(net.minecraft.server.level.ServerLevel level,net.minecraft.world.damagesource.DamageSource s,boolean b){super.dropCustomDeathLoot(level,s,b);if(!ScooterRental.isRental(this))spawnAtLocation(scooterItem());}
}
