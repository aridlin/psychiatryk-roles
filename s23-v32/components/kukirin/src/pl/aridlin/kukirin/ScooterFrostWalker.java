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
  // Four ticks of lead cover boosted travel; cap the vector, not each axis.
  double advanceX=velocity.x*4,advanceZ=velocity.z*4;
  double advanceLength=Math.hypot(advanceX,advanceZ);
  if(advanceLength>12){double scale=12/advanceLength;advanceX*=scale;advanceZ*=scale;advanceLength=12;}
  double aheadX=scooter.getX()+advanceX,aheadZ=scooter.getZ()+advanceZ;
  int y=net.minecraft.util.Mth.floor(scooter.getY()-.2);
  if(level<2){
   if(scooter.tickCount%2!=0||scooter.getY()<y+.99||scooter.getY()>y+1.75)return;
  }else{
   // Sweep two falling ticks before movement so a fast descent cannot skip the surface.
   int lowest=(int)Math.floor(ScooterFrostLanding.lowestSurface(scooter.getY(),velocity.y));
   boolean found=false;
   int samples=Math.max(1,(int)Math.ceil(advanceLength));
   for(int candidate=y;candidate>=lowest&&!found;candidate--){
    if(scooter.getY()<candidate+1-.05)continue;
    for(int sample=0;sample<=samples;sample++){
     double progress=(double)sample/samples;
     var pos=BlockPos.containing(scooter.getX()+advanceX*progress,candidate,scooter.getZ()+advanceZ*progress);if(!world.hasChunkAt(pos))continue;
     var surface=world.getBlockState(pos);
     boolean temporary=surface.is(Kukirin.LAVA_CRUST.get())||surface.is(Blocks.FROSTED_ICE);
     boolean source=(surface.is(Blocks.WATER)||surface.is(Blocks.LAVA))&&surface.getValue(LiquidBlock.LEVEL)==0;
     if((temporary||source)&&world.getBlockState(pos.above()).isAir()){y=candidate;found=true;break;}
    }
   }
   if(!found)return;
  }
  int radius=2+Math.min(2,level);
  for(int x=net.minecraft.util.Mth.floor(Math.min(scooter.getX(),aheadX))-radius;x<=net.minecraft.util.Mth.floor(Math.max(scooter.getX(),aheadX))+radius;x++)for(int z=net.minecraft.util.Mth.floor(Math.min(scooter.getZ(),aheadZ))-radius;z<=net.minecraft.util.Mth.floor(Math.max(scooter.getZ(),aheadZ))+radius;z++){
   if(pathDistanceSquared(x+.5,z+.5,scooter.getX(),scooter.getZ(),aheadX,aheadZ)>radius*radius)continue;
   var pos=new BlockPos(x,y,z);
   // Neighbor updates can ask adjacent fluids about their own neighbors. Leave
   // a loaded two-block halo so creating a path never requests a new chunk.
   if(!world.hasChunksAt(x-2,z-2,x+2,z+2))continue;
   var state=world.getBlockState(pos);
   boolean lava=state.is(Blocks.LAVA);if((!lava&&!state.is(Blocks.WATER))||state.getValue(LiquidBlock.LEVEL)!=0||!world.getBlockState(pos.above()).isAir())continue;
   var block=lava?Kukirin.LAVA_CRUST.get():Blocks.FROSTED_ICE;var ice=block.defaultBlockState();if(!ice.canSurvive(world,pos))continue;
   world.setBlockAndUpdate(pos,ice);world.scheduleTick(pos,block,net.minecraft.util.Mth.nextInt(world.random,60,120));
  }
 }
 /** Distance to the entire swept segment, including its two circular end caps. */
 static double pathDistanceSquared(double x,double z,double startX,double startZ,double endX,double endZ){
  double dx=endX-startX,dz=endZ-startZ,lengthSquared=dx*dx+dz*dz;
  double progress=lengthSquared==0?0:Math.clamp(((x-startX)*dx+(z-startZ)*dz)/lengthSquared,0,1);
  double offsetX=x-(startX+progress*dx),offsetZ=z-(startZ+progress*dz);
  return offsetX*offsetX+offsetZ*offsetZ;
 }
}
