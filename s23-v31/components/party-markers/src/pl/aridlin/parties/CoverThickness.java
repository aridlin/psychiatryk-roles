package pl.aridlin.parties;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.*;
import java.util.*;
public final class CoverThickness {
 private CoverThickness(){}
 public static double interval(AABB box,Vec3 from,Vec3 to){var d=to.subtract(from);double enter=0,exit=1;double[] a={from.x,from.y,from.z},v={d.x,d.y,d.z},lo={box.minX,box.minY,box.minZ},hi={box.maxX,box.maxY,box.maxZ};for(int i=0;i<3;i++){if(Math.abs(v[i])<1e-9){if(a[i]<lo[i]||a[i]>hi[i])return 0;continue;}double t1=(lo[i]-a[i])/v[i],t2=(hi[i]-a[i])/v[i];enter=Math.max(enter,Math.min(t1,t2));exit=Math.min(exit,Math.max(t1,t2));if(exit<=enter)return 0;}return (exit-enter)*d.length();}
 public static float between(ServerLevel level,Vec3 from,Vec3 to){if(from.distanceToSqr(to)>128d*128d)return 16;double[] sum={0};int[] visited={0};BlockGetter.traverseBlocks(from,to,level,(world,pos)->{if(++visited[0]>4096||sum[0]>=16)return Boolean.TRUE;if(!world.hasChunkAt(pos))return null;var state=world.getBlockState(pos);if(state.isAir())return null;var shape=state.getCollisionShape(world,pos);double local=0;for(var box:shape.toAabbs())local+=interval(box.move(pos),from,to);sum[0]+=Math.min(local,interval(new AABB(pos),from,to));return null;},world->null);return (float)Math.min(16,sum[0]);}
}
