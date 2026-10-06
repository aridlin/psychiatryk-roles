package pl.aridlin.psychiatrykroles.pointblankstencil.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.opengl.GL11C;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Scope-dependent glow shares the same cached-versus-raw stencil boundary. */
@Mixin(targets = "com.vicmatskiv.pointblank.client.render.DefaultRenderTypeProvider", remap = false)
public abstract class ScopeGlowStencilMixin {
    @Inject(method = "lambda$createGlowRenderType$21", at = @At("TAIL"))
    private static void roles$finishGlowPass(CallbackInfo ci) {
        RenderSystem.stencilFunc(GL11C.GL_ALWAYS, 0, 255);
        GL11C.glStencilFunc(GL11C.GL_ALWAYS, 0, 255);
        RenderSystem.stencilOp(GL11C.GL_KEEP, GL11C.GL_KEEP, GL11C.GL_KEEP);
        GL11C.glStencilOp(GL11C.GL_KEEP, GL11C.GL_KEEP, GL11C.GL_KEEP);
        RenderSystem.stencilMask(255);
        GL11C.glStencilMask(255);
    }

    @Redirect(method = "lambda$createGlowRenderType$20", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/systems/RenderSystem;stencilFunc(III)V"))
    private static void roles$glowFunction(int function, int reference, int mask) {
        RenderSystem.stencilFunc(function, reference, mask);
        GL11C.glStencilFunc(function, reference, mask);
    }

    @Redirect(method = "lambda$createGlowRenderType$20", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/systems/RenderSystem;stencilOp(III)V"))
    private static void roles$glowOperation(int fail, int depthFail, int pass) {
        RenderSystem.stencilOp(fail, depthFail, pass);
        GL11C.glStencilOp(fail, depthFail, pass);
    }
}
