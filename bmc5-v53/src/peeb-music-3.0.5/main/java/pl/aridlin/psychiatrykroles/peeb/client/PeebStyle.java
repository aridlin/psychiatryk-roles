package pl.aridlin.psychiatrykroles.peeb.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.lang.invoke.MethodHandle;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import net.minecraft.client.renderer.RenderStateShard.ShaderStateShard;
import net.minecraft.client.renderer.RenderStateShard.TextureStateShard;
import net.minecraft.client.renderer.RenderType.CompositeState;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.EventBusSubscriber.Bus;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;

@EventBusSubscriber(
   modid = "psychiatryk_peeb",
   value = {Dist.CLIENT},
   bus = Bus.MOD
)
public final class PeebStyle {
   public static ShaderInstance shader;
   public static boolean irisMaterialOptIn;
   private static Vec3 localTarget = Vec3.ZERO;
   private static boolean localTargetVisible;
   private static final ResourceLocation WHITE = ResourceLocation.parse("psychiatryk_peeb:textures/entity/white.png");
   private static final RenderType TYPE = RenderType.create(
      "peeb_ps1_character",
      DefaultVertexFormat.NEW_ENTITY,
      Mode.QUADS,
      4096,
      false,
      false,
      CompositeState.builder()
         .setShaderState(new ShaderStateShard(() -> configure(false)))
         .setTextureState(new TextureStateShard(WHITE, false, false))
         .setLightmapState(RenderStateShard.LIGHTMAP)
         .setOverlayState(RenderStateShard.OVERLAY)
         .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
         .setWriteMaskState(RenderStateShard.COLOR_DEPTH_WRITE)
         .setCullState(RenderStateShard.NO_CULL)
         .createCompositeState(false)
   );
   // Keep the local character in its own batch: remote Peebs remain opaque.
   private static final RenderType LOCAL_TYPE = RenderType.create(
      "peeb_ps1_local_character",
      DefaultVertexFormat.NEW_ENTITY,
      Mode.QUADS,
      4096,
      false,
      false,
      CompositeState.builder()
         .setShaderState(new ShaderStateShard(() -> configure(true)))
         .setTextureState(new TextureStateShard(WHITE, false, false))
         .setLightmapState(RenderStateShard.LIGHTMAP)
         .setOverlayState(RenderStateShard.OVERLAY)
         .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
         .setWriteMaskState(RenderStateShard.COLOR_DEPTH_WRITE)
         .setCullState(RenderStateShard.NO_CULL)
         .createCompositeState(false)
   );

   public static RenderType type() {
      return shader == null ? RenderType.entityCutoutNoCull(WHITE) : TYPE;
   }

   public static RenderType localType() {
      return shader == null ? RenderType.entityCutoutNoCull(WHITE) : LOCAL_TYPE;
   }

   public static void localTarget(Vec3 cameraRelativeTarget, boolean visible) {
      localTarget = cameraRelativeTarget;
      localTargetVisible = visible;
   }

   private static ShaderInstance configure(boolean local) {
      if (shader != null) {
         Minecraft mc = Minecraft.getInstance();
         var size = shader.getUniform("ScreenSize");
         if (size != null) size.set((float)mc.getWindow().getWidth(), (float)mc.getWindow().getHeight());
         var fade = shader.getUniform("PeebLocalFade");
         if (fade != null) fade.set(local ? 1.0F : 0.0F, localTargetVisible ? 1.0F : 0.0F, 0.64F, 0.14F);
         var target = shader.getUniform("PeebTarget");
         if (target != null) target.set((float)localTarget.x, (float)localTarget.y, (float)localTarget.z);
      }
      return shader;
   }

   @SubscribeEvent
   public static void shaders(RegisterShadersEvent var0) throws IOException {
      var0.registerShader(
         new ShaderInstance(var0.getResourceProvider(), ResourceLocation.parse("psychiatryk_peeb:peeb_ps1"), DefaultVertexFormat.NEW_ENTITY), var0x -> {
            irisMaterialOptIn = allowOwnIrisMaterial(var0x);
            shader = var0x;
         }
      );
   }

   private static boolean allowOwnIrisMaterial(ShaderInstance var0) {
      try {
         Class var1 = Class.forName("net.irisshaders.iris.mixinterface.ShaderInstanceInterface", false, PeebStyle.class.getClassLoader());
         if (!var1.isInstance(var0)) {
            return false;
         } else {
            Class var2 = Class.forName("net.irisshaders.iris.compat.SkipList", false, PeebStyle.class.getClassLoader());
            MethodHandle var3 = (MethodHandle)var2.getField("NONE_FORCE").get(null);
            var1.getMethod("setShouldSkip", MethodHandle.class).invoke(var0, var3);
            return true;
         }
      } catch (ClassNotFoundException var4) {
         return false;
      } catch (LinkageError | ReflectiveOperationException var5) {
         LogUtils.getLogger().warn("Peeb material could not register its optional Iris draw policy", var5);
         return false;
      }
   }

   private PeebStyle() {
   }
}
