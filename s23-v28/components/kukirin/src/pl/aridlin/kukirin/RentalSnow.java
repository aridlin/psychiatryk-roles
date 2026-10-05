package pl.aridlin.kukirin;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
public final class RentalSnow {
 public static boolean sinks(Level level,BlockPos pos){var block=level.getBlockState(pos);return block.is(Blocks.SNOW_BLOCK)||(block.is(Blocks.SNOW)&&level.getBlockState(pos.below()).is(Blocks.SNOW_BLOCK));}
 public static boolean tick(Scooter s){if(s.level().isClientSide||!ScooterRental.isRental(s))return false;var data=s.getPersistentData();long now=s.level().getGameTime();
  if(!data.contains("RentalSnowEnd")){var here=BlockPos.containing(s.getX(),s.getY()+.05,s.getZ());if(!sinks(s.level(),here)&&!sinks(s.level(),here.below()))return false;data.putLong("RentalSnowEnd",now+20);for(var rider:s.getPassengers())if(rider instanceof net.minecraft.world.entity.monster.Zombie zombie)zombie.setNoAi(false);s.ejectPassengers();ScooterMusic.stop(s);s.setNoGravity(true);s.noPhysics=true;}
  s.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);s.setPos(s.getX(),s.getY()-.045,s.getZ());if(now>=data.getLong("RentalSnowEnd")){((net.minecraft.server.level.ServerLevel)s.level()).sendParticles(net.minecraft.core.particles.ParticleTypes.POOF,s.getX(),s.getY()+.4,s.getZ(),55,.5,.3,.5,.08);((net.minecraft.server.level.ServerLevel)s.level()).sendParticles(new net.minecraft.core.particles.BlockParticleOption(net.minecraft.core.particles.ParticleTypes.BLOCK,Blocks.SNOW_BLOCK.defaultBlockState()),s.getX(),s.getY()+.6,s.getZ(),35,.4,.2,.4,.08);s.discard();}return true;
 }
 private RentalSnow(){}
}
