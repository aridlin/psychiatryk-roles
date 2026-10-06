package pl.aridlin.kukirin;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.Vec3;
public final class Scooter extends PathfinderMob {
 private static final net.minecraft.network.syncher.EntityDataAccessor<Integer> CHARGE=net.minecraft.network.syncher.SynchedEntityData.defineId(Scooter.class,net.minecraft.network.syncher.EntityDataSerializers.INT);
 private static final net.minecraft.network.syncher.EntityDataAccessor<Boolean> CHARGING=net.minecraft.network.syncher.SynchedEntityData.defineId(Scooter.class,net.minecraft.network.syncher.EntityDataSerializers.BOOLEAN),INFINITE=net.minecraft.network.syncher.SynchedEntityData.defineId(Scooter.class,net.minecraft.network.syncher.EntityDataSerializers.BOOLEAN);
 public int batteryCharge(){return level().isClientSide?entityData.get(CHARGE):ScooterBattery.stored(getItemBySlot(EquipmentSlot.FEET));}
 public boolean charging(){return entityData.get(CHARGING);}
 public boolean infiniteBattery(){return level().isClientSide?entityData.get(INFINITE):ScooterBattery.infinite(getItemBySlot(EquipmentSlot.FEET));}
 public boolean hasBatteryPower(){return ScooterRental.isRental(this)||infiniteBattery()||batteryCharge()>0;}
 public void batteryState(int charge,boolean charging,boolean infinite){entityData.set(CHARGE,charge);entityData.set(CHARGING,charging);entityData.set(INFINITE,infinite);}
 private net.minecraft.world.item.enchantment.ItemEnchantments cachedEnchantments;
 private final java.util.Map<String,Integer> enchantmentLevels=new java.util.HashMap<>();
 public int cachedEnchantLevel(String name){var component=getItemBySlot(EquipmentSlot.FEET).getOrDefault(net.minecraft.core.component.DataComponents.ENCHANTMENTS,net.minecraft.world.item.enchantment.ItemEnchantments.EMPTY);if(component!=cachedEnchantments){cachedEnchantments=component;enchantmentLevels.clear();}return enchantmentLevels.computeIfAbsent(name,n->{var key=net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.ENCHANTMENT,net.minecraft.resources.ResourceLocation.parse(n.equals("lunge")?"goplanska_kukirin:lunge":"minecraft:"+n));return component.getLevel(registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT).getOrThrow(key));});}
 private static final net.minecraft.network.syncher.EntityDataAccessor<Integer> HEAT=net.minecraft.network.syncher.SynchedEntityData.defineId(Scooter.class,net.minecraft.network.syncher.EntityDataSerializers.INT);
 private static final net.minecraft.network.syncher.EntityDataAccessor<Boolean> HOT=net.minecraft.network.syncher.SynchedEntityData.defineId(Scooter.class,net.minecraft.network.syncher.EntityDataSerializers.BOOLEAN);
 public int heat(){return entityData.get(HEAT);}
 public boolean overheated(){return entityData.get(HOT);}
 public void heatState(int heat,boolean hot){entityData.set(HEAT,heat);entityData.set(HOT,hot);}
 public Scooter(EntityType<? extends Scooter> type,Level level){super(type,level);setInvulnerable(true);setPersistenceRequired();}
 @Override protected void registerGoals(){}
 private static final net.minecraft.network.syncher.EntityDataAccessor<Integer> BREACH_SERIAL=net.minecraft.network.syncher.SynchedEntityData.defineId(Scooter.class,net.minecraft.network.syncher.EntityDataSerializers.INT);
 private static final net.minecraft.network.syncher.EntityDataAccessor<Float> BREACH_X=net.minecraft.network.syncher.SynchedEntityData.defineId(Scooter.class,net.minecraft.network.syncher.EntityDataSerializers.FLOAT),BREACH_Z=net.minecraft.network.syncher.SynchedEntityData.defineId(Scooter.class,net.minecraft.network.syncher.EntityDataSerializers.FLOAT);
 private int lastBreach;
 public void breachMomentum(Vec3 v){entityData.set(BREACH_X,(float)v.x);entityData.set(BREACH_Z,(float)v.z);entityData.set(BREACH_SERIAL,entityData.get(BREACH_SERIAL)+1);setDeltaMovement(v.x,getDeltaMovement().y,v.z);}
 private static final net.minecraft.network.syncher.EntityDataAccessor<Float> STEER=net.minecraft.network.syncher.SynchedEntityData.defineId(Scooter.class,net.minecraft.network.syncher.EntityDataSerializers.FLOAT);
 private static final net.minecraft.network.syncher.EntityDataAccessor<Boolean> NO_PARKING=net.minecraft.network.syncher.SynchedEntityData.defineId(Scooter.class,net.minecraft.network.syncher.EntityDataSerializers.BOOLEAN);
 public boolean rentalNoParking(){return entityData.get(NO_PARKING);}
 public void rentalNoParking(boolean value){entityData.set(NO_PARKING,value);}
 private static final net.minecraft.network.syncher.EntityDataAccessor<Boolean> HEADLIGHTS=net.minecraft.network.syncher.SynchedEntityData.defineId(Scooter.class,net.minecraft.network.syncher.EntityDataSerializers.BOOLEAN);
 private static final net.minecraft.network.syncher.EntityDataAccessor<Boolean> MOUSE=net.minecraft.network.syncher.SynchedEntityData.defineId(Scooter.class,net.minecraft.network.syncher.EntityDataSerializers.BOOLEAN);
 public void remoteSteering(float steer){entityData.set(STEER,net.minecraft.util.Mth.clamp(steer,-28,28));}
 public void mouseSteering(boolean value){entityData.set(MOUSE,value);}
 private static final net.minecraft.network.syncher.EntityDataAccessor<Integer> BOOST=net.minecraft.network.syncher.SynchedEntityData.defineId(Scooter.class,net.minecraft.network.syncher.EntityDataSerializers.INT);
 private static final net.minecraft.network.syncher.EntityDataAccessor<Long> ROCKET_END=net.minecraft.network.syncher.SynchedEntityData.defineId(Scooter.class,net.minecraft.network.syncher.EntityDataSerializers.LONG);
 private static final net.minecraft.network.syncher.EntityDataAccessor<Boolean> THRUST_UP=net.minecraft.network.syncher.SynchedEntityData.defineId(Scooter.class,net.minecraft.network.syncher.EntityDataSerializers.BOOLEAN);
 public void thrustUp(boolean up){entityData.set(THRUST_UP,up);}
 public boolean rocketActive(){return hasBatteryPower()&&isVehicle()&&level().getGameTime()<entityData.get(ROCKET_END);}
 public boolean flightUpgraded(){return !ScooterRental.isRental(this)&&ScooterUpgradeRecipe.has(getItemBySlot(EquipmentSlot.FEET),"netherite");}
 public boolean igniteRocket(net.minecraft.server.level.ServerPlayer p,InteractionHand hand){if(!hasBatteryPower()||level().isClientSide||getControllingPassenger()!=p||ScooterRental.isRental(this)||rocketActive())return false;var item=p.getItemInHand(hand);if(!item.is(net.minecraft.world.item.Items.FIREWORK_ROCKET))return false;if(!p.isCreative())item.shrink(1);entityData.set(ROCKET_END,level().getGameTime()+ScooterFlight.ROCKET_TICKS);return true;}
 private float suspension,previousSuspension,suspensionVelocity,drift,driftEffect,stepOffset,previousStepOffset;
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
 public int lungeSerial(){return entityData.get(LUNGE);}
 public boolean lunge(net.minecraft.server.level.ServerPlayer p){int l=ScooterEnchants.level(this,"lunge");if(!hasBatteryPower()||level().isClientSide||getControllingPassenger()!=p||l==0||level().getGameTime()<nextLunge)return false;nextLunge=level().getGameTime()+40;entityData.set(LUNGE_POWER,.14f+.07f*l);entityData.set(LUNGE,entityData.get(LUNGE)+1);level().playSound(null,getX(),getY(),getZ(),net.minecraft.sounds.SoundEvents.TRIDENT_RIPTIDE_1,net.minecraft.sounds.SoundSource.PLAYERS,.7f,1.35f);return true;}
 private static final net.minecraft.network.syncher.EntityDataAccessor<Integer> WALL_KICK=net.minecraft.network.syncher.SynchedEntityData.defineId(Scooter.class,net.minecraft.network.syncher.EntityDataSerializers.INT);
 private static final net.minecraft.network.syncher.EntityDataAccessor<Float> WALL_X=net.minecraft.network.syncher.SynchedEntityData.defineId(Scooter.class,net.minecraft.network.syncher.EntityDataSerializers.FLOAT);
 private static final net.minecraft.network.syncher.EntityDataAccessor<Float> WALL_Z=net.minecraft.network.syncher.SynchedEntityData.defineId(Scooter.class,net.minecraft.network.syncher.EntityDataSerializers.FLOAT);
 private final WallKickMomentum wallKickMomentum=new WallKickMomentum();
 private int lastWallKick;private long nextWallKick;private float wallTurnTarget;private int wallTurnTicks;
 private int lastBoost;private long nextBoostTick;
 private float localSteer,turnSpeed;
 private static final net.minecraft.network.syncher.EntityDataAccessor<Boolean> DRIFT_HELD=net.minecraft.network.syncher.SynchedEntityData.defineId(Scooter.class,net.minecraft.network.syncher.EntityDataSerializers.BOOLEAN);
 public void easyDrift(boolean enabled){entityData.set(DRIFT_HELD,enabled);}
 public void brakeTurn(boolean value){entityData.set(BRAKE_TURN,value);}
 private static final net.minecraft.network.syncher.EntityDataAccessor<Boolean> BRAKE_TURN=net.minecraft.network.syncher.SynchedEntityData.defineId(Scooter.class,net.minecraft.network.syncher.EntityDataSerializers.BOOLEAN);
 public boolean driftHeld(){return isVehicle()&&entityData.get(DRIFT_HELD);}
 private float throttle,previousSteer,previousLean,lean,previousSpeed,observedSpeed;private double wheel,previousWheel,lastX,lastZ;private boolean visualReady;
 @Override protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder b){super.defineSynchedData(b);b.define(BRAKE_TURN,false);b.define(DRIFT_HELD,false);b.define(HEAT,0);b.define(HOT,false);b.define(CHARGE,ScooterBattery.CAPACITY);b.define(CHARGING,false);b.define(INFINITE,false);b.define(HEADLIGHTS,false);b.define(NO_PARKING,false);b.define(BREACH_SERIAL,0);b.define(BREACH_X,0f);b.define(BREACH_Z,0f);b.define(ROCKET_END,0L);b.define(THRUST_UP,false);b.define(WALL_KICK,0);b.define(WALL_X,0f);b.define(WALL_Z,0f);b.define(DISC_SERIAL,0);b.define(DISC_PLAYING,false);b.define(STEER,0f);b.define(MOUSE,true);b.define(BOOST,0);b.define(BOOST_POWER,.82f);b.define(LUNGE,0);b.define(LUNGE_POWER,0f);}
 @Override protected boolean canAddPassenger(Entity p){return getPassengers().size()<(ScooterPassengers.enabled(this)?2:1);}
 @Override public boolean shouldRiderSit(){return false;}
 @Override public boolean isInvulnerableTo(net.minecraft.world.damagesource.DamageSource s){return true;}
 @Override public boolean fireImmune(){return true;}
 protected net.minecraft.world.phys.Vec2 getRiddenRotation(net.minecraft.world.entity.LivingEntity p){if(wallTurnTicks>0)return new net.minecraft.world.phys.Vec2(0,getYRot());float desired=Math.abs(p.xxa)>.01f?-p.xxa*28f:entityData.get(MOUSE)?net.minecraft.util.Mth.clamp(net.minecraft.util.Mth.wrapDegrees(p.getYRot()-getYRot())*1.15f,-28,28):0;float steer=net.minecraft.util.Mth.lerp(.85f,localSteer,desired);localSteer=steer;double measuredSpeed=Math.max(turnSpeed,Math.max(observedSpeed,getDeltaMovement().horizontalDistance()));float yaw=getYRot()+ScooterHandling.steeringYawStep(measuredSpeed,steer,ScooterEnchants.level(this,"sweeping_edge"),onGround(),flightUpgraded())*(float)ScooterTuning.get(level().isClientSide).steering()*(entityData.get(BRAKE_TURN)?1.25f:1);yaw=getYRot()+net.minecraft.util.Mth.clamp(yaw-getYRot(),-16,16);return new net.minecraft.world.phys.Vec2(0,yaw);}
 static float turnRate(double speed){return ScooterHandling.turnRate(speed);}
 @Override public LivingEntity getControllingPassenger(){return getFirstPassenger() instanceof Player p?p:null;}
 @Override protected void tickRidden(Player p,Vec3 input){super.tickRidden(p,input);if(!isControlledByLocalInstance()){yBodyRot=getYRot();yHeadRot=getYRot();return;}var rotation=getRiddenRotation(p);setRot(rotation.y,rotation.x);yBodyRot=getYRot();yHeadRot=getYRot();}
 @Override protected Vec3 getRiddenInput(Player p,Vec3 input){throttle=hasBatteryPower()?p.zza:Math.min(0,p.zza);return new Vec3(0,0,throttle);}
 @Override public void aiStep(){
  // LivingEntity applies an extra 0.98 remote-AI decay even to a locally ridden client vehicle.
  // Cancel it here; travel owns drag on this dedicated vehicle.
  if(level().isClientSide&&isControlledByLocalInstance())setDeltaMovement(getDeltaMovement().scale(1/.98));
  super.aiStep();
 }
 @Override public void travel(Vec3 input){
  if(!isControlledByLocalInstance())return;
  if(!hasBatteryPower())input=new Vec3(0,0,Math.min(0,input.z));
  if(!ScooterEasterEggs.inverted(this)&&(isInWater()||isInLava())){super.travel(input);if(isInWater()&&ScooterEnchants.level(this,"depth_strider")>0){var v=getDeltaMovement();double a=.012*ScooterEnchants.level(this,"depth_strider")*input.z;setDeltaMovement(v.add(-Math.sin(Math.toRadians(getYRot()))*a,0,Math.cos(Math.toRadians(getYRot()))*a));}return;}
  if(ScooterRental.isRental(this)&&getFirstPassenger() instanceof net.minecraft.world.entity.monster.Zombie)input=new Vec3(0,0,.6);
  getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.STEP_HEIGHT).setBaseValue(stepHeight());
  Vec3 old=getDeltaMovement();
  drift=ScooterHandling.driftNext(drift,old.horizontalDistance(),steering(1),onGround(),driftHeld(),ScooterTuning.get(level().isClientSide).recovery());drift*=1-.08f*ScooterEnchants.level(this,"density");
  var motion=ScooterHandling.step(old.x,old.z,getYRot(),input.z,onGround(),isVehicle(),drift,normalCruiseSpeed(),.045*(1+.15*ScooterEnchants.level(this,"density")),1+.12*ScooterEnchants.level(this,"quick_charge"),ScooterTuning.get(level().isClientSide).normalGrip(),ScooterTuning.get(level().isClientSide).driftGrip());
  if(onGround()&&ScooterEnchants.level(this,"soul_speed")>0&&getBlockStateOn().is(net.minecraft.tags.BlockTags.SOUL_SPEED_BLOCKS)){double factor=1+.015*ScooterEnchants.level(this,"soul_speed");motion=new ScooterHandling.Motion(motion.x()*factor,motion.z()*factor);}
  if(!onGround()&&isVehicle())motion=ScooterHandling.airSteer(motion,steering(1)*(flightUpgraded()?2.8:1));
  motion=ScooterFlight.accelerate(motion,getYRot(),input.z,old.horizontalDistance(),normalCruiseSpeed(),flightUpgraded()&&hasBatteryPower(),rocketActive()&&hasBatteryPower(),onGround());
  double gravity=isNoGravity()?0:ScooterHandling.fallingGravity(old.y,getGravity(),isVehicle());
  double y=ScooterEasterEggs.inverted(this)?Math.min(.8,old.y+getGravity()):ScooterFlight.vertical(old.y,gravity,flightUpgraded()&&isVehicle(),rocketActive(),entityData.get(THRUST_UP));
  if(ScooterRental.isRental(this)&&motion.speed()>RentalPolicy.MAX_BLOCKS_PER_TICK){double f=RentalPolicy.MAX_BLOCKS_PER_TICK/motion.speed();motion=new ScooterHandling.Motion(motion.x()*f,motion.z()*f);}
  if(overheated()&&motion.speed()>20/72d){double f=(20/72d)/motion.speed();motion=new ScooterHandling.Motion(motion.x()*f,motion.z()*f);}
  setDeltaMovement(motion.x(),y,motion.z());
  move(MoverType.SELF,getDeltaMovement());
  Vec3 clipped=getDeltaMovement();
  var slide=ScooterHandling.wallSlide(motion,new ScooterHandling.Motion(clipped.x,clipped.z),horizontalCollision);setDeltaMovement(slide.x(),verticalCollision?0:clipped.y*.98,slide.z());
  if(level().isClientSide)wallKickMomentum.record(motion,slide,horizontalCollision,level().getGameTime());
  calculateEntityAnimation(false);
 }
 public double stepHeight(){return flightUpgraded()&&Math.max(turnSpeed,getDeltaMovement().horizontalDistance())>100/72d?3:1.3;}
 public double cruiseSpeed(){if(overheated())return 20/72d;return rocketActive()?ScooterFlight.ROCKET_LIMIT:flightUpgraded()?ScooterFlight.FLIGHT_LIMIT:normalCruiseSpeed();}
 public double normalCruiseSpeed(){if(ScooterRental.isRental(this))return RentalPolicy.MAX_BLOCKS_PER_TICK;return ScooterHandling.TOP_SPEED*(1+.08*ScooterEnchants.level(this,"efficiency"));}
 public float speed(float partial){return net.minecraft.util.Mth.lerp(partial,previousSpeed,observedSpeed);}
 public float throttle(){return throttle;}
 public boolean windBoost(net.minecraft.server.level.ServerPlayer p){
  if(level().isClientSide||getControllingPassenger()!=p)return false;
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
  if(level().isClientSide||getControllingPassenger()!=p||onGround()||level().getGameTime()<nextWallKick)return false;
  var normal=touchingWallNormal();if(normal==null)return false;
  nextWallKick=level().getGameTime()+8;nextBoostTick=level().getGameTime()+8;
  entityData.set(WALL_X,(float)normal.x);entityData.set(WALL_Z,(float)normal.z);entityData.set(WALL_KICK,entityData.get(WALL_KICK)+1);
  ((net.minecraft.server.level.ServerLevel)level()).sendParticles(net.minecraft.core.particles.ParticleTypes.GUST,getX(),getY()+.35,getZ(),4,.15,.1,.15,.01);
  level().playSound(null,blockPosition(),net.minecraft.sounds.SoundEvents.WIND_CHARGE_BURST.value(),net.minecraft.sounds.SoundSource.PLAYERS,.35f,1.4f);return true;
 }
 @Override public void tick(){
  if(ScooterEasterEggs.tick(this))return;if(RentalSnow.tick(this))return;ScooterThermal.tick(this);ScooterBattery.tick(this);ScooterBreach.tick(this);if(RentalParking.tick(this))return;ScooterRental.tick(this);if(isRemoved())return;
  if(ScooterEasterEggs.inverted(this))fallDistance=0;
  if(!level().isClientSide)entityData.set(HEADLIGHTS,headlightsRequired());
  if(!level().isClientSide&&ScooterEnchants.bound(getItemBySlot(EquipmentSlot.FEET))){var owner=ScooterEnchants.owner(getItemBySlot(EquipmentSlot.FEET));var rider=getFirstPassenger();if(rider!=null&&!rider.getUUID().equals(owner))rider.stopRiding();}
  ScooterFrostWalker.tick(this);
  if(!level().isClientSide&&discPlaying()&&level().getGameTime()>discUntil){if(ScooterMusic.looping(this))startDisc();else stopDisc();}
  if(!level().isClientSide&&ScooterEnchants.bound(getItemBySlot(EquipmentSlot.FEET))){var data=getPersistentData();if(getY()<level().getMinBuildHeight()-16){var safe=data.contains("ScooterSafePos")?net.minecraft.core.BlockPos.of(data.getLong("ScooterSafePos")):level().getSharedSpawnPos();teleportTo(safe.getX()+.5,safe.getY()+1,safe.getZ()+.5);setDeltaMovement(Vec3.ZERO);}else if(onGround())data.putLong("ScooterSafePos",blockPosition().asLong());}

  int breach=entityData.get(BREACH_SERIAL);if(breach!=lastBreach){lastBreach=breach;if(isControlledByLocalInstance()&&isVehicle())setDeltaMovement(entityData.get(BREACH_X),getDeltaMovement().y,entityData.get(BREACH_Z));}
  int kick=entityData.get(WALL_KICK);if(kick!=lastWallKick){lastWallKick=kick;if(isControlledByLocalInstance()&&isVehicle()){
   var v=getDeltaMovement();var momentum=new ScooterHandling.Motion(v.x,v.z);if(level().isClientSide)momentum=wallKickMomentum.take(momentum,entityData.get(WALL_X),entityData.get(WALL_Z),level().getGameTime());var m=ScooterHandling.wallKick(momentum.x(),momentum.z(),getYRot(),steering(1),entityData.get(WALL_X),entityData.get(WALL_Z),cruiseSpeed()*1.5);
   setDeltaMovement(m.x(),Math.max(.38,v.y),m.z());float yaw=(float)Math.toDegrees(Math.atan2(-m.x(),m.z()));setYRot(yaw);yBodyRot=yaw;yHeadRot=yaw;localSteer=0;
   if(getControllingPassenger() instanceof Player rider){wallTurnTarget=yaw;wallTurnTicks=4;}
  }}
  if(wallTurnTicks>0){if(getControllingPassenger() instanceof Player rider&&isControlledByLocalInstance()){float delta=net.minecraft.util.Mth.wrapDegrees(wallTurnTarget-rider.getYRot());rider.setYRot(rider.getYRot()+delta/wallTurnTicks);rider.yHeadRot=rider.getYRot();}wallTurnTicks--;}
  int lunge=entityData.get(LUNGE);if(lunge!=lastLunge){lastLunge=lunge;if(isControlledByLocalInstance()&&isVehicle()){double angle=Math.toRadians(getYRot()),power=entityData.get(LUNGE_POWER);var v=getDeltaMovement().add(-Math.sin(angle)*power,0,Math.cos(angle)*power);double speed=v.horizontalDistance();double limit=cruiseSpeed()*1.5;if(speed>limit)v=new Vec3(v.x*limit/speed,v.y,v.z*limit/speed);setDeltaMovement(v);}}
  int boost=entityData.get(BOOST);if(boost!=lastBoost){lastBoost=boost;if(isControlledByLocalInstance()&&isVehicle())setDeltaMovement(getDeltaMovement().x,Math.max(getDeltaMovement().y,entityData.get(BOOST_POWER)),getDeltaMovement().z);}
if(!level().isClientSide&&ScooterEnchants.bound(getItemBySlot(EquipmentSlot.FEET)))ScooterIndex.get(getServer()).track(this);
if(!level().isClientSide&&rocketActive()&&tickCount%2==0){double a=Math.toRadians(getYRot());for(int side:new int[]{-1,1})((net.minecraft.server.level.ServerLevel)level()).sendParticles(net.minecraft.core.particles.ParticleTypes.FIREWORK,getX()+Math.sin(a)*.6+Math.cos(a)*side*.27,getY()+.25,getZ()-Math.cos(a)*.6+Math.sin(a)*side*.27,2,.04,.04,.04,.025);}
 getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.STEP_HEIGHT).setBaseValue(stepHeight());
 previousSteer=steering(1);previousLean=lean;previousWheel=wheel;previousSpeed=observedSpeed;previousSuspension=suspension;previousStepOffset=stepOffset;stepOffset*=.55f;double beforePositionY=getY();double beforeY=getDeltaMovement().y;super.tick();double climbed=getY()-beforePositionY;if((onGround()||climbed>Math.max(0,beforeY)+.05)&&climbed>.08&&climbed<=stepHeight()+.05)stepOffset-=climbed;stepOffset=net.minecraft.util.Mth.clamp(stepOffset,-(float)(stepHeight()+.05),0);double impact=beforeY-getDeltaMovement().y;if(onGround()&&Math.abs(impact)>.08)suspensionVelocity-=Math.min(.035,Math.abs(impact)*.025);suspensionVelocity+=-suspension*.32;suspensionVelocity*=.62;suspension=net.minecraft.util.Mth.clamp(suspension+suspensionVelocity,-.055f,.025f);if(visualReady){double dx=getX()-lastX,dz=getZ()-lastZ,travel=Math.sqrt(dx*dx+dz*dz);observedSpeed=travel<4?(float)travel:0;if(travel<4){double forward=dx*-Math.sin(Math.toRadians(getYRot()))+dz*Math.cos(Math.toRadians(getYRot()));wheel+=Math.copySign(travel,forward)/(10.5*1.4/111)*180/Math.PI;}}turnSpeed=Math.max((float)getDeltaMovement().horizontalDistance(),Math.max(observedSpeed,turnSpeed*.92f));visualReady=true;lastX=getX();lastZ=getZ();// Derive visible slip from velocity relative to the chassis for both local and remote riders.
 var slipVelocity=getDeltaMovement();double heading=Math.toRadians(getYRot()),sideways=slipVelocity.x*Math.cos(heading)+slipVelocity.z*Math.sin(heading);float slip=driftHeld()&&onGround()&&isVehicle()&&observedSpeed>.16f?(float)Math.clamp(Math.abs(sideways)/Math.max(.1,slipVelocity.horizontalDistance())*2.8,0,1):0;driftEffect=net.minecraft.util.Mth.lerp(slip>driftEffect?.25f:.12f,driftEffect,slip);
 lean=net.minecraft.util.Mth.lerp(.2f,lean,ScooterRental.isRental(this)&&!isVehicle()?78:-steering(1)*Math.min(1,observedSpeed*2)*(.25f+.26f*driftEffect));if(!isVehicle())throttle=0;if(!level().isClientSide&&isVehicle()){int pierce=ScooterEnchants.level(this,"piercing"),knockback=ScooterEnchants.level(this,"knockback");if((pierce>0||knockback>0)&&observedSpeed>.22&&getFirstPassenger() instanceof LivingEntity rider){for(var target:level().getEntitiesOfClass(LivingEntity.class,getBoundingBox().inflate(.2),t->t!=this&&t!=rider&&t.getVehicle()!=this)){if(target instanceof Player player&&rider instanceof Player driver&&!driver.canHarmPlayer(player))continue;if(level().getGameTime()<impactCooldown.getOrDefault(target.getUUID(),0L))continue;impactCooldown.put(target.getUUID(),level().getGameTime()+20);if(pierce>0)target.hurt(rider instanceof Player driver?damageSources().playerAttack(driver):damageSources().mobAttack(rider),ScooterImpact.piercingDamage(pierce,observedSpeed));double shove=.4+.45*knockback;target.push(getDeltaMovement().x*shove,.12+.04*knockback,getDeltaMovement().z*shove);if(knockback>0)RentalTrip.start(target,this);}}impactCooldown.entrySet().removeIf(e->e.getValue()<level().getGameTime());}}
 public float stepOffset(float partial){return net.minecraft.util.Mth.lerp(partial,previousStepOffset,stepOffset);}
 public float drift(){return drift;}
 public float driftEffect(){return driftEffect;}
 public float suspension(float partial){return net.minecraft.util.Mth.lerp(partial,previousSuspension,suspension);}
 public boolean headlights(){return isVehicle()&&(entityData.get(HEADLIGHTS)||(level().isClientSide&&headlightsRequired()));}
 private boolean headlightsRequired(){long time=Math.floorMod(level().getDayTime(),24000);boolean night=level().dimensionType().hasSkyLight()&&time>12500&&time<23500;return isVehicle()&&(night||level().getMaxLocalRawBrightness(blockPosition())<8);}
 public float steering(float partial){return net.minecraft.util.Mth.lerp(partial,previousSteer,level().isClientSide&&isControlledByLocalInstance()?localSteer:entityData.get(STEER));}
 public float lean(float partial){return net.minecraft.util.Mth.lerp(partial,previousLean,lean);}
 public float wheelRotation(float partial){return (float)((previousWheel+(wheel-previousWheel)*partial)%360);}
 @Override public boolean causeFallDamage(float distance,float multiplier,net.minecraft.world.damagesource.DamageSource source){return false;}
 @Override protected float getRiddenSpeed(Player p){return .35f;}
 @Override protected Vec3 getPassengerAttachmentPoint(Entity e,EntityDimensions d,float scale){return new Vec3(0,.23,getFirstPassenger()==e?-.20:-.70).yRot(-getYRot()*(float)Math.PI/180).add(e.getVehicleAttachmentPoint(this));}
 @Override protected net.minecraft.sounds.SoundEvent getAmbientSound(){return null;}
 @Override protected net.minecraft.sounds.SoundEvent getHurtSound(net.minecraft.world.damagesource.DamageSource s){return net.minecraft.sounds.SoundEvents.IRON_GOLEM_HURT;}
 @Override protected net.minecraft.sounds.SoundEvent getDeathSound(){return net.minecraft.sounds.SoundEvents.IRON_GOLEM_DEATH;}
 @Override protected void playStepSound(net.minecraft.core.BlockPos p,net.minecraft.world.level.block.state.BlockState s){}
 private net.minecraft.world.item.ItemStack scooterItem(){var stored=getItemBySlot(EquipmentSlot.FEET);var result=stored.is(Kukirin.ITEM.get())?stored.copyWithCount(1):new net.minecraft.world.item.ItemStack(Kukirin.ITEM.get());if(hasCustomName())result.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,getCustomName());return result;}
 @Override public InteractionResult mobInteract(Player p,InteractionHand hand){if(ScooterRental.isRental(this))return ScooterRental.interact(this,p);if(ScooterEnchants.bound(getItemBySlot(EquipmentSlot.FEET))&&!p.getUUID().equals(ScooterEnchants.owner(getItemBySlot(EquipmentSlot.FEET)))&&(!isVehicle()||!canAddPassenger(p)||p.isShiftKeyDown()))return InteractionResult.FAIL;if(p.getItemInHand(hand).is(net.minecraft.world.item.Items.NAME_TAG))return p.getItemInHand(hand).interactLivingEntity(p,this,hand);if(p.getItemInHand(hand).is(net.minecraft.world.item.Items.COAL))return ScooterBattery.insertCoal(this,p,hand);if(p.getItemInHand(hand).has(net.minecraft.core.component.DataComponents.JUKEBOX_PLAYABLE)&&ScooterUpgradeRecipe.has(getItemBySlot(EquipmentSlot.FEET),"jukebox")){if(!level().isClientSide&&p instanceof net.minecraft.server.level.ServerPlayer sp)ScooterMusic.insertDisc(this,sp,hand);return InteractionResult.sidedSuccess(level().isClientSide);}if(p.isShiftKeyDown()&&!isVehicle()){if(!level().isClientSide){ScooterMusic.stop(this);spawnAtLocation(scooterItem());ScooterIndex.get(getServer()).remove(getUUID());discard();}return InteractionResult.sidedSuccess(level().isClientSide);}if(canAddPassenger(p)){if(!level().isClientSide)p.startRiding(this);return InteractionResult.sidedSuccess(level().isClientSide);}return InteractionResult.PASS;}
 @Override protected net.minecraft.resources.ResourceKey<net.minecraft.world.level.storage.loot.LootTable> getDefaultLootTable(){return net.minecraft.world.level.storage.loot.BuiltInLootTables.EMPTY;}
 @Override protected void dropCustomDeathLoot(net.minecraft.server.level.ServerLevel level,net.minecraft.world.damagesource.DamageSource s,boolean b){super.dropCustomDeathLoot(level,s,b);if(!ScooterRental.isRental(this))spawnAtLocation(scooterItem());}
}
