package pl.aridlin.kukirin;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
@EventBusSubscriber(modid="goplanska_kukirin")
public final class RentalParking {
 static final String RIDER="RentalParkingRider",DEBT="RentalParkingDebt";
 public static boolean tick(Scooter scooter){if(scooter.level().isClientSide||!ScooterRental.isRental(scooter))return false;
 var state=scooter.getPersistentData();boolean forbidden=RentalParkingConfig.forbidden(scooter.level(),scooter.blockPosition());scooter.rentalNoParking(forbidden);
 var rider=scooter.getFirstPassenger();java.util.UUID previous=state.hasUUID(RIDER)?state.getUUID(RIDER):null;
 if(rider instanceof ServerPlayer p){state.putUUID(RIDER,p.getUUID());return false;}
 if(previous==null)return false;state.remove(RIDER);var p=scooter.getServer().getPlayerList().getPlayer(previous);
 if(!forbidden||p==null||!p.isAlive()||p.isSpectator()||p.level()!=scooter.level())return false;
 long owing=p.getPersistentData().getLong(DEBT);p.getPersistentData().putLong(DEBT,Math.addExact(owing,100));settle(p);
 p.displayClientMessage(Component.translatable("scooter.parking.fine"),false);
 ((net.minecraft.server.level.ServerLevel)scooter.level()).sendParticles(net.minecraft.core.particles.ParticleTypes.POOF,scooter.getX(),scooter.getY()+.4,scooter.getZ(),60,.5,.35,.5,.08);
 ScooterMusic.stop(scooter);scooter.discard();p.hurt(p.damageSources().generic(),5);return true;
 }
 static void settle(ServerPlayer p){long debt=p.getPersistentData().getLong(DEBT);if(debt<=0)return;long paid=Math.min(debt,RentalPayments.balance(p));if(paid>0&&RentalPayments.charge(p,paid))p.getPersistentData().putLong(DEBT,debt-paid);}
 @SubscribeEvent public static void playerTick(net.neoforged.neoforge.event.tick.PlayerTickEvent.Post e){if(e.getEntity() instanceof ServerPlayer p&&p.tickCount%20==0)settle(p);}
 private RentalParking(){}
}
