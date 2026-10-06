package pl.aridlin.kukirin;
import net.minecraft.core.*;
import net.minecraft.server.level.*;
import net.minecraft.world.phys.Vec3;
/** Server-authoritative thin-wall breaking; all candidates and protection events pass before any block is removed. */
public final class ScooterBreach {
 static final class Motion {Vec3 pos;Vec3 velocity=Vec3.ZERO;long validUntil,next;Motion(Vec3 p){pos=p;}}
 static final java.util.Map<Scooter,Motion> MOTION=new java.util.WeakHashMap<>();
 static double cooldown=5;static long checked,modified=-1;
 public static double cooldownSeconds(){long now=System.nanoTime();if(now-checked<1_000_000_000L)return cooldown;checked=now;try{var path=net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get().resolve("goplanska-scooter/breach.properties");if(!java.nio.file.Files.exists(path)){java.nio.file.Files.createDirectories(path.getParent());java.nio.file.Files.writeString(path,"# Live reload; 0 disables cooldown. Minimum speed stays 60 km/h.\ncooldownSeconds=5\n");}long time=java.nio.file.Files.getLastModifiedTime(path).toMillis();if(time!=modified){var p=new java.util.Properties();try(var reader=java.nio.file.Files.newBufferedReader(path)){p.load(reader);}double value=Double.parseDouble(p.getProperty("cooldownSeconds","5"));if(!Double.isFinite(value)||value<0||value>3600)throw new IllegalArgumentException("cooldownSeconds must be 0..3600");cooldown=value;modified=time;}}catch(Exception e){System.getLogger("ScooterBreach").log(System.Logger.Level.WARNING,e.getMessage());}return cooldown;}
 public static void tick(Scooter s){if(!PortableOptions.get().blockBreaking())return;if(!(s.level() instanceof ServerLevel l))return;var state=MOTION.computeIfAbsent(s,k->new Motion(s.position()));var now=s.position();Vec3 motion=now.subtract(state.pos).multiply(1,0,1);state.pos=now;long time=l.getGameTime();double speed=motion.horizontalDistance();if(speed>=60/72d&&speed<=200/72d+.15){state.velocity=motion;state.validUntil=time+3;}
 if(!(s.getControllingPassenger() instanceof ServerPlayer p)||ScooterRental.isRental(s)||ScooterEnchants.level(s,"breach")==0||time<state.next||time>state.validUntil)return;
 var v=state.velocity;Direction forward=Math.abs(v.x)>Math.abs(v.z)?(v.x>0?Direction.EAST:Direction.WEST):(v.z>0?Direction.SOUTH:Direction.NORTH);
 var box=s.getBoundingBox().minmax(p.getBoundingBox()).expandTowards(v.scale(1.25)).inflate(-.04);var hits=new java.util.ArrayList<BlockPos>();
 for(var pos:BlockPos.betweenClosed(BlockPos.containing(box.minX,box.minY,box.minZ),BlockPos.containing(box.maxX,box.maxY,box.maxZ))){if(!l.hasChunkAt(pos))return;var block=l.getBlockState(pos);if(block.getCollisionShape(l,pos).isEmpty())continue;
  if(pos.getY()<net.minecraft.util.Mth.floor(s.getY()+.05))continue;if(block.getDestroySpeed(l,pos)<0||block.hasBlockEntity()||!p.mayInteract(l,pos))return;
  if(!l.getBlockState(pos.relative(forward)).getCollisionShape(l,pos.relative(forward)).isEmpty())return;hits.add(pos.immutable());}
 if(hits.isEmpty())return;int plane=forward.getAxis()==Direction.Axis.X?hits.getFirst().getX():hits.getFirst().getZ();for(var pos:hits)if((forward.getAxis()==Direction.Axis.X?pos.getX():pos.getZ())!=plane)return;
 for(var pos:hits)if(net.neoforged.neoforge.common.CommonHooks.fireBlockBreak(l,p.gameMode.getGameModeForPlayer(),p,pos,l.getBlockState(pos)).isCanceled())return;
 for(var pos:hits)l.destroyBlock(pos,!p.isCreative(),p);state.next=time+(long)Math.ceil(cooldownSeconds()*20);s.breachMomentum(v);}
 private ScooterBreach(){}
}
