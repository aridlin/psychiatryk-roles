package pl.aridlin.portablechams.client;
import java.util.*;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.*;
/** Gather real collision-shape boxes along the entire entity's viewing cone. */
final class PixelCover {
 static final int LIMIT=2048;
 record Data(float[] boxes,int count,boolean complete,java.util.List<BlockPos> textured){}
 static Data gather(ClientLevel level,Vec3 camera,AABB body){return gather(level,camera,body,java.util.Set.of());}
 static Data gather(ClientLevel level,Vec3 camera,AABB body,java.util.Set<BlockPos> excluded){
  var cells=new HashSet<BlockPos>();boolean[] complete={true};
  for(int i=0;i<8;i++){var end=new Vec3((i&1)==0?body.minX:body.maxX,(i&2)==0?body.minY:body.maxY,(i&4)==0?body.minZ:body.maxZ);
   if(camera.distanceTo(end)>192){complete[0]=false;continue;}
   BlockGetter.traverseBlocks(camera,end,level,(world,pos)->{for(int x=-1;x<=1;x++)for(int y=-1;y<=1;y++)for(int z=-1;z<=1;z++)cells.add(pos.offset(x,y,z).immutable());return null;},world->null);
  }
  var boxes=new ArrayList<AABB>();var textured=new ArrayList<BlockPos>();for(var pos:cells){if(excluded.contains(pos))continue;if(!level.hasChunkAt(pos)){complete[0]=false;continue;}var state=level.getBlockState(pos);if(state.isAir())continue;var layers=net.minecraft.client.renderer.ItemBlockRenderTypes.getRenderLayers(state);if(layers.contains(net.minecraft.client.renderer.RenderType.cutout())||layers.contains(net.minecraft.client.renderer.RenderType.cutoutMipped())||layers.contains(net.minecraft.client.renderer.RenderType.translucent())){textured.add(pos);continue;}for(var box:state.getCollisionShape(level,pos).toAabbs())boxes.add(box.move(pos));}
  boxes.sort(Comparator.comparingDouble(b->b.getCenter().distanceToSqr(camera)));if(boxes.size()>LIMIT){complete[0]=false;boxes.subList(LIMIT,boxes.size()).clear();}
  float[] data=new float[Math.max(8,boxes.size()*8)];int n=0;for(var b:boxes){data[n++]=(float)(b.minX-camera.x);data[n++]=(float)(b.minY-camera.y);data[n++]=(float)(b.minZ-camera.z);data[n++]=0;data[n++]=(float)(b.maxX-camera.x);data[n++]=(float)(b.maxY-camera.y);data[n++]=(float)(b.maxZ-camera.z);data[n++]=0;}
  textured.sort(Comparator.comparingDouble(p->p.getCenter().distanceToSqr(camera)));if(textured.size()>512){textured.subList(512,textured.size()).clear();complete[0]=false;}return new Data(data,boxes.size(),complete[0],java.util.List.copyOf(textured));
 }
}
