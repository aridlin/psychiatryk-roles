package pl.aridlin.steeringqa.mixin;
import com.wf.gemrender.direct.*;
import com.wf.gemrender.gltf.GemRenderGltfModel;
import com.wf.gemrender.texture.*;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(value=DirectRenderer.class,remap=false)
public abstract class DirectDrawObserver {
 @Inject(method="submit(Lcom/wf/gemrender/gltf/GemRenderGltfModel;[Lorg/joml/Matrix4f;[FLorg/joml/Matrix4f;IIILcom/wf/gemrender/direct/DirectPass;Lcom/wf/gemrender/texture/VariantUv;Lcom/wf/gemrender/texture/Paint;I[Lorg/joml/Matrix4f;)V",at=@At("HEAD"))
 private static void before(GemRenderGltfModel model,Matrix4f[] palette,float[] morphs,Matrix4f pose,int light,int overlay,int color,DirectPass pass,VariantUv variant,Paint paint,int damage,Matrix4f[] sockets,CallbackInfo callback){pl.aridlin.steeringqa.SteeringQA.submitted(model,palette,pose);}
}
