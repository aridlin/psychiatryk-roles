package pl.aridlin.kukirin;
/** Scheduled ticks survive saves, so temporary lava paths restore after a server restart too. */
public final class ScooterLavaCrust extends net.minecraft.world.level.block.Block {
 public ScooterLavaCrust(){super(net.minecraft.world.level.block.state.BlockBehaviour.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.OBSIDIAN).strength(-1,3600000).noLootTable());}
 @Override protected void tick(net.minecraft.world.level.block.state.BlockState state,net.minecraft.server.level.ServerLevel world,net.minecraft.core.BlockPos pos,net.minecraft.util.RandomSource random){
  for(var scooter:world.getEntitiesOfClass(Scooter.class,new net.minecraft.world.phys.AABB(pos).inflate(5),s->s.isVehicle()&&ScooterEnchants.level(s,"frost_walker")>0)){world.scheduleTick(pos,this,20);return;}
  world.setBlockAndUpdate(pos,net.minecraft.world.level.block.Blocks.LAVA.defaultBlockState());
 }
}
