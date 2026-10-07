package pl.aridlin.portablechams.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import org.lwjgl.opengl.*;

/** Keeps the offscreen masks and their samplers inside one render transaction. */
final class ChamsRenderState implements AutoCloseable {
    // The masks use units 0..2 and the composite uses units 0..3. Also preserve
    // the caller's active unit, since RenderTarget allocation binds on that unit.
    private final int activeTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
    private final int[] textures = new int[4];
    private final int activeBinding;
    private final int[] shaderTextures = new int[GlStateManager.TEXTURE_COUNT];
    private final ShaderInstance shader = RenderSystem.getShader();
    private final float[] shaderColor = RenderSystem.getShaderColor().clone();
    private final int program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
    private final int drawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
    private final int readFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
    private final int[] viewport = new int[4];
    private final boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
    private final boolean depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
    private final int depthFunc = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
    private final boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
    private final int srcRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB);
    private final int dstRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
    private final int srcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA);
    private final int dstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
    private final int equationRgb = GL11.glGetInteger(GL20.GL_BLEND_EQUATION_RGB);
    private final int equationAlpha = GL11.glGetInteger(GL20.GL_BLEND_EQUATION_ALPHA);
    private final boolean cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
    private final boolean[] colorMask = new boolean[4];
    private final float[] clearColor = new float[4];
    private final double clearDepth = GL11.glGetDouble(GL11.GL_DEPTH_CLEAR_VALUE);

    static ChamsRenderState capture() {
        // Flush pending world geometry before switching its destination framebuffer.
        Minecraft.getInstance().renderBuffers().bufferSource().endBatch();
        return new ChamsRenderState();
    }

    private ChamsRenderState() {
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
        GL11.glGetFloatv(GL11.GL_COLOR_CLEAR_VALUE, clearColor);
        try (var memory = org.lwjgl.system.MemoryStack.stackPush()) {
            var mask = memory.malloc(4);
            GL11.glGetBooleanv(GL11.GL_COLOR_WRITEMASK, mask);
            for (int i = 0; i < 4; i++) colorMask[i] = mask.get(i) != 0;
        }
        activeBinding = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        for (int i = 0; i < shaderTextures.length; i++) shaderTextures[i] = RenderSystem.getShaderTexture(i);
        for (int i = 0; i < textures.length; i++) {
            GL13.glActiveTexture(GL13.GL_TEXTURE0 + i);
            textures[i] = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        }
        // Raw GL queries do not trust another renderer's cached active unit.
        // Synchronize our touched bindings with Minecraft before any mask draw.
        for (int i = 0; i < textures.length; i++) restoreTexture(GL13.GL_TEXTURE0 + i, textures[i]);
        restoreTexture(activeTexture, activeBinding);
    }

    private static void restoreTexture(int unit, int texture) {
        // The direct bind also covers renderers which bypass GlStateManager;
        // the public calls keep its active-unit and binding caches coherent.
        GL13.glActiveTexture(unit);
        RenderSystem.activeTexture(unit);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        RenderSystem.bindTexture(texture);
    }

    @Override
    public void close() {
        // An interrupted mask renderer must not leave vertices for another draw.
        ChamsPass.discardPendingBuffers();
        // clear() invalidates ShaderInstance's program cache. A raw glUseProgram
        // alone leaves that cache pointing at the last mask/composite program.
        // Use our four-sampler shader even when a mask draw failed early;
        // clearing the caller's shader could unbind unrelated higher units.
        ChamsPass.composite.clear();
        RenderSystem.setShader(() -> shader);
        GlStateManager._glUseProgram(program);
        for (int i = 0; i < shaderTextures.length; i++) RenderSystem.setShaderTexture(i, shaderTextures[i]);
        for (int i = 0; i < textures.length; i++) {
            restoreTexture(GL13.GL_TEXTURE0 + i, textures[i]);
        }
        restoreTexture(activeTexture, activeBinding);
        RenderSystem.setShaderColor(shaderColor[0], shaderColor[1], shaderColor[2], shaderColor[3]);
        RenderSystem.depthFunc(depthFunc);
        RenderSystem.depthMask(depthMask);
        if (depth) RenderSystem.enableDepthTest(); else RenderSystem.disableDepthTest();
        RenderSystem.blendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha);
        GL20.glBlendEquationSeparate(equationRgb, equationAlpha);
        if (blend) RenderSystem.enableBlend(); else RenderSystem.disableBlend();
        if (cull) RenderSystem.enableCull(); else RenderSystem.disableCull();
        RenderSystem.colorMask(colorMask[0], colorMask[1], colorMask[2], colorMask[3]);
        RenderSystem.clearColor(clearColor[0], clearColor[1], clearColor[2], clearColor[3]);
        RenderSystem.clearDepth(clearDepth);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawFramebuffer);
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFramebuffer);
        RenderSystem.viewport(viewport[0], viewport[1], viewport[2], viewport[3]);
        // The fullscreen quad changed the uploader's current vertex format.
        BufferUploader.invalidate();
    }
}
