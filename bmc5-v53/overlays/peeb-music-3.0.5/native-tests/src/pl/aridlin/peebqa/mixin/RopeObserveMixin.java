package pl.aridlin.peebqa.mixin;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pl.aridlin.psychiatrykroles.peeb.client.PeebRenderer;
@Mixin(PeebRenderer.class)
public abstract class RopeObserveMixin {
 @Inject(method="rope",at=@At("HEAD"),remap=false) private static void peebqa$begin(PoseStack p,MultiBufferSource b,Vec3 tip,Vec3 anchor,Vec3 camera,double len,CallbackInfo ci){pl.aridlin.peebqa.NativeDraws.begin(tip,anchor,len);}
 @Inject(method="ropeVertex",at=@At("HEAD"),remap=false) private static void peebqa$vertex(PoseStack p,VertexConsumer b,Vec3 point,float u,float v,CallbackInfo ci){pl.aridlin.peebqa.NativeDraws.vertex(point);}
 @Inject(method="rope",at=@At("RETURN"),remap=false) private static void peebqa$end(PoseStack p,MultiBufferSource b,Vec3 tip,Vec3 anchor,Vec3 camera,double len,CallbackInfo ci){pl.aridlin.peebqa.NativeDraws.end();}
}
