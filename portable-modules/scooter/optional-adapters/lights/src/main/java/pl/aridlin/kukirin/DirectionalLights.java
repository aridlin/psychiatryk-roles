package pl.aridlin.kukirin;
import dev.lambdaurora.lambdynlights.api.behavior.DynamicLightBehavior;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
/** Directional client light sources, updated at 4 Hz, without changing world blocks. */
public final class DirectionalLights implements DynamicLightBehavior {
 private static final java.util.Map<Integer,DirectionalLights> lights=new java.util.HashMap<>();
 private final Scooter scooter;private final dev.lambdaurora.lambdynlights.engine.source.DeferredDynamicLightSource source;private Vec3 origin=Vec3.ZERO,forward=Vec3.ZERO;private boolean active,changed;private int tick;
 private final double[] reach=new double[15];
 private DirectionalLights(Scooter s){scooter=s;source=new dev.lambdaurora.lambdynlights.engine.source.DeferredDynamicLightSource(this);}
 public static void tick(){
  var mc=Minecraft.getInstance();lights.entrySet().removeIf(entry->entry.getValue().isRemoved());
  if(mc.level==null||mc.player==null)return;
  for(var entity:mc.level.entitiesForRendering())if(entity instanceof Scooter s&&s.isVehicle()&&s.distanceToSqr(mc.player)<40*40&&!lights.containsKey(s.getId())){
   var light=new DirectionalLights(s);light.tick=4;light.update();lights.put(s.getId(),light);
  }
  // Resource/world changes may clear Lamb's registry while our scooter cache survives.
  // Keep the same wrapper and repair registration, rather than accumulating duplicates.
  var engine=dev.lambdaurora.lambdynlights.LambDynLights.get();
  for(var light:lights.values()){light.update();if(!engine.containsLightSource(light.source)){light.changed=true;engine.addLightSource(light.source);}}
 }
 private void update(){if(++tick%5!=0)return;
  boolean enabled=scooter.headlights()&&ScooterClientOptions.get().headlightsEnabled;
  Vec3 pos=scooter.position().add(0,.75,0);double yaw=Math.toRadians(scooter.getYRot());Vec3 dir=new Vec3(-Math.sin(yaw),-.12,Math.cos(yaw)).normalize();
  changed|=active!=enabled||pos.distanceToSqr(origin)>.04||dir.distanceToSqr(forward)>.001;active=enabled;origin=pos;forward=dir;
  if(active)for(int row=0;row<3;row++)for(int col=0;col<5;col++){
   Vec3 ray=new Vec3(-Math.sin(yaw+(col-2)*.2),(row-1)*.18-.12,Math.cos(yaw+(col-2)*.2)).normalize();
   var hit=scooter.level().clip(new net.minecraft.world.level.ClipContext(origin,origin.add(ray.scale(11)),net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,scooter));double distance=origin.distanceTo(hit.getLocation())+.7;changed|=Math.abs(reach[row*5+col]-distance)>.1;reach[row*5+col]=distance;
  }
 }
 @Override public double lightAtPos(BlockPos pos,double falloffRatio){if(!active)return 0;// Terrain queries address solid blocks, so test the exposed top rather than an underground block centre.
 Vec3 target=pos.getY()<origin.y-.5?new Vec3(pos.getX()+.5,pos.getY()+1.01,pos.getZ()+.5):Vec3.atCenterOf(pos);Vec3 delta=target.subtract(origin);double distance=delta.length();if(distance>.01&&distance<11){Vec3 d=delta.scale(1/distance);double alignment=d.dot(forward);if(alignment>.87){double yaw=Math.atan2(-d.x,d.z),base=Math.atan2(-forward.x,forward.z);double angle=Math.atan2(Math.sin(yaw-base),Math.cos(yaw-base));int col=Mth.clamp((int)Math.round(angle/.2)+2,0,4),row=Mth.clamp((int)Math.round((d.y+.12)/.18)+1,0,2);if(distance<=reach[row*5+col])return Math.max(0,(15-distance*1.1)*Math.min(1,(alignment-.87)/.08));}}return 0;}
 @Override public BoundingBox getBoundingBox(){var end=origin.add(forward.scale(11));return new BoundingBox((int)Math.floor(Math.min(origin.x,end.x)-6),(int)Math.floor(Math.min(origin.y,end.y)-5),(int)Math.floor(Math.min(origin.z,end.z)-6),(int)Math.ceil(Math.max(origin.x,end.x)+6),(int)Math.ceil(Math.max(origin.y,end.y)+5),(int)Math.ceil(Math.max(origin.z,end.z)+6));}
 @Override public boolean hasChanged(){boolean result=changed;changed=false;return result;}
 @Override public boolean isRemoved(){var mc=Minecraft.getInstance();return scooter.isRemoved()||(mc.player!=null&&mc.player.level()!=scooter.level())||mc.player==null||scooter.distanceToSqr(mc.player)>48*48;}
}
