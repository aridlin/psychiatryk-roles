package pl.aridlin.kukirin;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
/** Server-owned knockdown. The real player collides with terrain throughout; no surrogate body. */
@EventBusSubscriber(modid="goplanska_kukirin")
public final class RentalTrip {
 static final int LENGTH=28;
 public static void start(ServerPlayer p,Scooter scooter){
  var state=p.getPersistentData();long now=p.level().getGameTime();
  if(now<state.getLong("RentalTripCooldown")||p.getForcedPose()!=null||p.isSpectator()||p.isPassenger())return;
  state.putLong("RentalTripCooldown",now+60);state.putLong("RentalTripEnd",now+LENGTH);
  p.setForcedPose(Pose.SWIMMING);p.fallDistance=0;state.putLong("RentalTripFallProtection",now+LENGTH+10);
  p.serverLevel().getChunkSource().broadcastAndSend(p,new net.minecraft.network.protocol.game.ClientboundHurtAnimationPacket(p.getId(),90f));
  Vec3 direction=p.getDeltaMovement().multiply(1,0,1);if(direction.lengthSqr()<.0025)direction=p.position().subtract(scooter.position()).multiply(1,0,1);if(direction.lengthSqr()<.001)direction=p.getLookAngle().multiply(1,0,1);
  direction=direction.normalize().scale(.38);p.setDeltaMovement(direction.x,.12,direction.z);p.hurtMarked=true;
  PacketDistributor.sendToPlayersTrackingEntityAndSelf(p,new RentalTripPacket(p.getUUID(),LENGTH));
 }
 public static void start(net.minecraft.world.entity.LivingEntity target,Scooter scooter){
  if(target instanceof ServerPlayer p){start(p,scooter);return;}
  if(target.level().isClientSide||!target.isAlive()||target.isPassenger())return;var data=target.getPersistentData();long now=target.level().getGameTime();if(now<data.getLong("RentalTripCooldown"))return;
  data.putLong("RentalTripCooldown",now+60);data.putLong("RentalMobTripEnd",now+LENGTH);data.putString("RentalMobTripPose",target.getPose().name());target.setPose(Pose.SWIMMING);target.fallDistance=0;
  if(target instanceof net.minecraft.world.entity.Mob mob){data.putBoolean("RentalMobTripNoAi",mob.isNoAi());mob.setNoAi(true);}
  var v=scooter.getDeltaMovement().multiply(1,0,1);if(v.lengthSqr()<.001)v=target.position().subtract(scooter.position()).multiply(1,0,1);v=v.normalize().scale(.38);target.setDeltaMovement(v.x,.12,v.z);target.hurtMarked=true;
  PacketDistributor.sendToPlayersTrackingEntity(target,new RentalTripPacket(target.getUUID(),LENGTH));
 }
 @SubscribeEvent public static void mobTick(net.neoforged.neoforge.event.tick.EntityTickEvent.Post e){if(!(e.getEntity() instanceof net.minecraft.world.entity.LivingEntity target)||target instanceof net.minecraft.world.entity.player.Player||target.level().isClientSide)return;var data=target.getPersistentData();if(!data.contains("RentalMobTripEnd"))return;
  if(target.level().getGameTime()>=data.getLong("RentalMobTripEnd")||!target.isAlive()||target.isPassenger()){if(target.getPose()==Pose.SWIMMING){try{target.setPose(Pose.valueOf(data.getString("RentalMobTripPose")));}catch(IllegalArgumentException ex){target.setPose(Pose.STANDING);}}if(target instanceof net.minecraft.world.entity.Mob mob)mob.setNoAi(data.getBoolean("RentalMobTripNoAi"));data.remove("RentalMobTripEnd");data.remove("RentalMobTripPose");data.remove("RentalMobTripNoAi");return;}
  target.fallDistance=0;if(target.onGround())target.setDeltaMovement(target.getDeltaMovement().multiply(.78,1,.78));
 }
 @SubscribeEvent public static void tick(net.neoforged.neoforge.event.tick.PlayerTickEvent.Post e){if(!(e.getEntity() instanceof ServerPlayer p))return;var data=p.getPersistentData();if(!data.contains("RentalTripEnd"))return;
  if(p.level().getGameTime()>=data.getLong("RentalTripEnd")||!p.isAlive()||p.isPassenger()||p.isSpectator()){if(p.getForcedPose()==Pose.SWIMMING)p.setForcedPose(null);data.remove("RentalTripEnd");return;}
  p.fallDistance=0;
  // Fallen players skid briefly, then settle instead of bouncing or getting trapped in a trip loop.
  if(p.onGround()){p.setDeltaMovement(p.getDeltaMovement().multiply(.78,1,.78));p.hurtMarked=true;}
 }
 @SubscribeEvent public static void fall(net.neoforged.neoforge.event.entity.living.LivingFallEvent e){if(e.getEntity() instanceof net.minecraft.world.entity.LivingEntity p&&(p.level().getGameTime()<p.getPersistentData().getLong("RentalTripFallProtection")||p.level().getGameTime()<p.getPersistentData().getLong("RentalMobTripEnd"))){e.setDamageMultiplier(0);p.fallDistance=0;}}
 private RentalTrip(){}
}
