package pl.aridlin.kukirin;
import com.wf.gemrender.asset.*;
import com.wf.gemrender.entity.GemRenderEntityVisual;
import com.wf.gemrender.gltf.*;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
/** Retained GPU model: shared mesh and two small animation parameters per scooter. */
public final class ScooterVisual extends GemRenderEntityVisual<Scooter> {
 static final java.util.Map<Scooter,ScooterVisual> ACTIVE=new java.util.IdentityHashMap<>();
 static final ModelCache.Handle<GemRenderGltfModel> MODEL=GemRenderModels.variants(ResourceLocation.parse("goplanska_kukirin:variants/scooter_dyes"),ResourceLocation.parse("goplanska_kukirin:models/kukirin_g2.glb"),skins());
 static java.util.List<java.util.Map<ResourceLocation,ResourceLocation>> skins(){var original=ResourceLocation.parse("goplanska_kukirin:textures/model/private_sketchfab_kukirin.png");var skins=new java.util.ArrayList<java.util.Map<ResourceLocation,ResourceLocation>>();skins.add(java.util.Map.of(original,ResourceLocation.parse("goplanska_kukirin:textures/model/dyes/default.png")));for(var color:net.minecraft.world.item.DyeColor.values())skins.add(java.util.Map.of(original,ResourceLocation.parse("goplanska_kukirin:textures/model/dyes/"+color.getName()+".png")));for(String brand:new String[]{"lime","bolt","city"})skins.add(java.util.Map.of(original,ResourceLocation.parse("goplanska_kukirin:textures/model/rentals/"+brand+".png")));return skins;}
 @Override public void beginFrame(dev.engine_room.flywheel.api.visual.DynamicVisual.Context context){super.beginFrame(context);var instance=((pl.aridlin.kukirin.mixin.ScooterVisualAccessor)(Object)this).goplanskaScooterInstance();if(instance!=null&&model()!=null){instance.variant(model().variant(ScooterEasterEggs.variant(entity,0)));instance.setChanged();}}
 public ScooterVisual(VisualizationContext ctx,Scooter entity,float partial){super(ctx,entity,partial,MODEL);ACTIVE.put(entity,this);}
 @Override protected void _delete(){ACTIVE.remove(entity,this);super._delete();}
 void drawTrim(com.mojang.blaze3d.vertex.PoseStack pose,net.minecraft.client.renderer.MultiBufferSource buffers,int light,net.minecraft.world.phys.Vec3 camera){
  var evaluated=pose();var instance=((pl.aridlin.kukirin.mixin.ScooterVisualAccessor)(Object)this).goplanskaScooterInstance();if(evaluated==null||instance==null)return;
  var origin=renderOrigin();pose.pushPose();pose.translate(origin.getX()-camera.x,origin.getY()-camera.y,origin.getZ()-camera.z);pose.mulPose(instance.pose);
  ScooterTrim.drawPosed(entity,pose,buffers,light,evaluated);ScooterRocketVisual.draw(entity,pose,buffers,light,evaluated);RentalBrandingVisual.draw(entity,pose,buffers,light,evaluated);pose.popPose();
 }
 @Override protected int layers(){return 2;}
 @Override protected void animate(float partial,GltfAnimation[] clips,float[] times){clips[0]=model().animation("steering");times[0]=ScooterSteeringRig.animationTime(entity.steering(partial));clips[1]=model().animation("wheels");times[1]=((entity.wheelRotation(partial)%360)+360)%360/360f;}
 @Override protected void transform(Matrix4f pose,float partial){super.transform(pose,partial);pose.translate(0,entity.suspension(partial)+entity.stepOffset(partial),0);if(ScooterEasterEggs.inverted(entity))pose.translate(0,entity.getBbHeight(),0).rotateZ((float)Math.PI);pose.scale(1.25f);pose.rotateY((float)Math.PI).rotateZ(entity.lean(partial)*(float)Math.PI/180);}
}
