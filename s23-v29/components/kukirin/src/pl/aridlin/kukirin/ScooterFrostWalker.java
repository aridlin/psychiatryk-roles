package pl.aridlin.kukirin;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
/** Freeze ahead of a ridden vehicle close to the water surface; never freeze deep submerged water. */
public final class ScooterFrostWalker {
 public static void tick(Scooter scooter){
  if(!(scooter.level() instanceof ServerLevel world)||!scooter.isVehicle())return;
  int level=ScooterEnchants.level(scooter,"frost_walker");if(level==0)return;
  var velocity=scooter.getDeltaMovement();
  if(velocity.y>.15)return;
  int y=net.minecraft.util.Mth.floor(scooter.getY()-.2);
  if(level<2){
   if(scooter.tickCount%2!=0||scooter.getY()<y+.99||scooter.getY()>y+1.75)return;
  }else{
   // Sweep two falling ticks before movement so a fast descent cannot skip the surface.
   int lowest=(int)Math.floor(ScooterFrostLanding.lowestSurface(scooter.getY(),velocity.y));
   boolean found=false;
   int cx=net.minecraft.util.Mth.floor(scooter.getX()),cz=net.minecraft.util.Mth.floor(scooter.getZ());
   int ax=net.minecraft.util.Mth.floor(scooter.getX()+Math.clamp(velocity.x*2,-2.4,2.4)),az=net.minecraft.util.Mth.floor(scooter.getZ()+Math.clamp(velocity.z*2,-2.4,2.4));
   for(int candidate=y;candidate>=lowest&&!found;candidate--){
    if(scooter.getY()<candidate+1-.05)continue;
    for(int[] centre:new int[][]{{cx,cz},{ax,az}}){
     var pos=new BlockPos(centre[0],candidate,centre[1]);if(!world.hasChunkAt(pos))continue;
     var liquid=world.getBlockState(pos);
     if((liquid.is(Blocks.WATER)||liquid.is(Blocks.LAVA))&&liquid.getValue(LiquidBlock.LEVEL)==0&&world.getBlockState(pos.above()).isAir()){y=candidate;found=true;break;}
    }
   }
   if(!found)return;
  }
  double aheadX=scooter.getX()+Math.clamp(velocity.x*3,-2.4,2.4),aheadZ=scooter.getZ()+Math.clamp(velocity.z*3,-2.4,2.4);int radius=2+Math.min(2,level);
  for(int x=net.minecraft.util.Mth.floor(Math.min(scooter.getX(),aheadX))-radius;x<=net.minecraft.util.Mth.floor(Math.max(scooter.getX(),aheadX))+radius;x++)for(int z=net.minecraft.util.Mth.floor(Math.min(scooter.getZ(),aheadZ))-radius;z<=net.minecraft.util.Mth.floor(Math.max(scooter.getZ(),aheadZ))+radius;z++){
   if(Math.min(Math.hypot(x+.5-scooter.getX(),z+.5-scooter.getZ()),Math.hypot(x+.5-aheadX,z+.5-aheadZ))>radius)continue;
   var pos=new BlockPos(x,y,z);if(!world.hasChunkAt(pos))continue;var state=world.getBlockState(pos);
   boolean lava=state.is(Blocks.LAVA);if((!lava&&!state.is(Blocks.WATER))||state.getValue(LiquidBlock.LEVEL)!=0||!world.getBlockState(pos.above()).isAir())continue;
   var block=lava?Kukirin.LAVA_CRUST.get():Blocks.FROSTED_ICE;var ice=block.defaultBlockState();if(!ice.canSurvive(world,pos))continue;
   world.setBlockAndUpdate(pos,ice);world.scheduleTick(pos,block,net.minecraft.util.Mth.nextInt(world.random,60,120));
  }
 }
}
