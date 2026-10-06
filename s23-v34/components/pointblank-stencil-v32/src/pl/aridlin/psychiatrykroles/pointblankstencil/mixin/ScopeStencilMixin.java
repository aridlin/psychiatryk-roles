package pl.aridlin.psychiatrykroles.pointblankstencil.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.opengl.GL11C;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keep Point Blank's scoped passes correct alongside raw portal stencil calls. */
@Mixin(targets = "com.vicmatskiv.pointblank.client.render.RenderTypeProvider", remap = false)
public interface ScopeStencilMixin {
    @Inject(method = {"lambda$static$5", "lambda$static$7"}, at = @At("TAIL"))
    private static void roles$finishScopePass(CallbackInfo ci) {
        // Immersive Portals enables stencil for the next outer world pass before
        // choosing a new function. An EQUAL scope function must not leak there.
        RenderSystem.stencilFunc(GL11C.GL_ALWAYS, 0, 255);
        GL11C.glStencilFunc(GL11C.GL_ALWAYS, 0, 255);
        RenderSystem.stencilOp(GL11C.GL_KEEP, GL11C.GL_KEEP, GL11C.GL_KEEP);
        GL11C.glStencilOp(GL11C.GL_KEEP, GL11C.GL_KEEP, GL11C.GL_KEEP);
        RenderSystem.stencilMask(255);
        GL11C.glStencilMask(255);
    }

    @Redirect(method = {"lambda$static$3", "lambda$static$4", "lambda$static$5"},
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;stencilMask(I)V"))
    private static void roles$writeMask(int mask) {
        // Unscoped mask-only drawing must not leave stencil clears disabled for
        // the following portal world pass. The subsequent lens pass uses KEEP.
        int restored = mask == 0 ? 255 : mask;
        RenderSystem.stencilMask(restored);
        GL11C.glStencilMask(restored);
    }

    @Redirect(method = {"lambda$static$3", "lambda$static$4"},
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;stencilFunc(III)V"))
    private static void roles$maskFunction(int function, int reference, int mask) {
        RenderSystem.stencilFunc(function, reference, mask);
        GL11C.glStencilFunc(function, reference, mask);
    }

    @Redirect(method = {"lambda$static$3", "lambda$static$4", "lambda$static$6"},
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;stencilOp(III)V"))
    private static void roles$maskOperation(int fail, int depthFail, int pass) {
        RenderSystem.stencilOp(fail, depthFail, pass);
        GL11C.glStencilOp(fail, depthFail, pass);
    }

    @Redirect(method = "lambda$static$6", at = @At(value = "INVOKE",
            target = "Lorg/lwjgl/opengl/GL11;glStencilFunc(III)V"))
    private static void roles$lensFunction(int function, int reference, int mask) {
        RenderSystem.stencilFunc(function, reference, mask);
        GL11C.glStencilFunc(function, reference, mask);
    }

    @Redirect(method = "lambda$static$7", at = @At(value = "INVOKE",
            target = "Lorg/lwjgl/opengl/GL11;glStencilMask(I)V"))
    private static void roles$lensWriteMask(int mask) {
        RenderSystem.stencilMask(mask);
        GL11C.glStencilMask(mask);
    }
}
