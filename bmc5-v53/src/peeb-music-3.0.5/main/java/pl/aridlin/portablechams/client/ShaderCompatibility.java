package pl.aridlin.portablechams.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexSorting;
import java.lang.reflect.Method;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent.Stage;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

final class ShaderCompatibility implements AutoCloseable {
   private static Object iris;
   private static Method active;
   private static Method shadow;
   private static boolean checked;
   private final Matrix4f projection;
   private final VertexSorting sorting;
   private final boolean matrices;
   private final boolean depthMask;

   static boolean shaders() {
      if (!checked) {
         checked = true;

         try {
            Class var0 = Class.forName("net.irisshaders.iris.api.v0.IrisApi", false, ShaderCompatibility.class.getClassLoader());
            iris = var0.getMethod("getInstance").invoke(null);
            active = var0.getMethod("isShaderPackInUse");
            shadow = var0.getMethod("isRenderingShadowPass");
         } catch (LinkageError | ReflectiveOperationException var2) {
            iris = null;
         }
      }

      if (iris == null) {
         return false;
      } else {
         try {
            return Boolean.TRUE.equals(active.invoke(iris));
         } catch (ReflectiveOperationException var1) {
            return false;
         }
      }
   }

   static boolean shadowPass() {
      if (iris == null) {
         return false;
      } else {
         try {
            return Boolean.TRUE.equals(shadow.invoke(iris));
         } catch (ReflectiveOperationException var1) {
            return false;
         }
      }
   }

   static boolean stage(RenderLevelStageEvent var0) {
      if (var0.getStage() != Stage.AFTER_TRANSLUCENT_BLOCKS && var0.getStage() != Stage.AFTER_LEVEL) {
         return false;
      } else {
         boolean var1 = shaders();
         return !shadowPass() && var0.getStage() == (var1 ? Stage.AFTER_LEVEL : Stage.AFTER_TRANSLUCENT_BLOCKS);
      }
   }

   static ShaderCompatibility matrices(RenderLevelStageEvent var0) {
      return new ShaderCompatibility(var0);
   }

   private ShaderCompatibility(RenderLevelStageEvent var1) {
      this.matrices = var1.getStage() == Stage.AFTER_LEVEL;
      if (this.matrices) {
         this.depthMask = GL11.glGetBoolean(2930);
         this.projection = new Matrix4f(RenderSystem.getProjectionMatrix());
         this.sorting = RenderSystem.getVertexSorting();
         RenderSystem.getModelViewStack().pushMatrix().set(var1.getModelViewMatrix());
         RenderSystem.applyModelViewMatrix();
         RenderSystem.setProjectionMatrix(new Matrix4f(var1.getProjectionMatrix()), VertexSorting.DISTANCE_TO_ORIGIN);
         RenderSystem.depthMask(true);
      } else {
         this.projection = null;
         this.sorting = null;
         this.depthMask = false;
      }
   }

   @Override
   public void close() {
      if (this.matrices) {
         RenderSystem.getModelViewStack().popMatrix();
         RenderSystem.applyModelViewMatrix();
         RenderSystem.setProjectionMatrix(this.projection, this.sorting);
         RenderSystem.depthMask(this.depthMask);
      }
   }
}
