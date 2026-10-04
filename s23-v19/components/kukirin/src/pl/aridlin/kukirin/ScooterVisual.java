package pl.aridlin.kukirin;
import com.wf.gemrender.asset.*;
import com.wf.gemrender.entity.GemRenderEntityVisual;
import com.wf.gemrender.gltf.*;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
/** Retained GPU model: shared mesh and two small animation parameters per scooter. */
public final class ScooterVisual extends GemRenderEntityVisual<Scooter> {
 static final ModelCache.Handle<GemRenderGltfModel> MODEL=GemRenderModels.handle(ResourceLocation.parse("goplanska_kukirin:models/kukirin_g2.glb"));
 public ScooterVisual(VisualizationContext ctx,Scooter entity,float partial){super(ctx,entity,partial,MODEL);}
 @Override protected int layers(){return 2;}
 @Override protected void animate(float partial,GltfAnimation[] clips,float[] times){clips[0]=model().animation("steering");times[0]=(entity.steering(partial)+28)/56;clips[1]=model().animation("wheels");times[1]=((entity.wheelRotation(partial)%360)+360)%360/360f;}
 @Override protected void transform(Matrix4f pose,float partial){super.transform(pose,partial);pose.rotateY((float)Math.PI).rotateZ(entity.lean(partial)*(float)Math.PI/180);}
}
