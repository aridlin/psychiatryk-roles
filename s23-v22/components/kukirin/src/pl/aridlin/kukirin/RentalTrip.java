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
  p.setForcedPose(Pose.SWIMMING);
  Vec3 direction=p.getDeltaMovement().multiply(1,0,1);if(direction.lengthSqr()<.0025)direction=p.position().subtract(scooter.position()).multiply(1,0,1);if(direction.lengthSqr()<.001)direction=p.getLookAngle().multiply(1,0,1);
  direction=direction.normalize().scale(.38);p.setDeltaMovement(direction.x,.12,direction.z);p.hurtMarked=true;
  PacketDistributor.sendToPlayersTrackingEntityAndSelf(p,new RentalTripPacket(p.getUUID(),LENGTH));
 }
 @SubscribeEvent public static void tick(net.neoforged.neoforge.event.tick.PlayerTickEvent.Post e){if(!(e.getEntity() instanceof ServerPlayer p))return;var data=p.getPersistentData();if(!data.contains("RentalTripEnd"))return;
  if(p.level().getGameTime()>=data.getLong("RentalTripEnd")||!p.isAlive()||p.isPassenger()||p.isSpectator()){if(p.getForcedPose()==Pose.SWIMMING)p.setForcedPose(null);data.remove("RentalTripEnd");return;}
  // Fallen players skid briefly, then settle instead of bouncing or getting trapped in a trip loop.
  if(p.onGround()){p.setDeltaMovement(p.getDeltaMovement().multiply(.78,1,.78));p.hurtMarked=true;}
 }
 private RentalTrip(){}
}
