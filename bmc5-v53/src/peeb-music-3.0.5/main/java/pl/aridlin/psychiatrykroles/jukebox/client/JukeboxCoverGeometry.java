package pl.aridlin.psychiatrykroles.jukebox.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.texture.OverlayTexture;

public final class JukeboxCoverGeometry {
   public static final int SECTORS = 96;
   public static final double DEGREES_PER_SECOND = 200.0;
   public static final float HOLE_RADIUS = 0.035F;
   public static final float ART_RADIUS = 0.365F;
   public static final float DISC_RADIUS = 0.395F;
   public static final float DISC_Y = 1.004F;
   public static final float ART_Y = 1.006F;
   public static final float WORN_Z = -0.004F;

   public static double rotationDegrees(double var0) {
      return Double.isFinite(var0) && !(var0 < 0.0) ? var0 % 1.8 * 200.0 : 0.0;
   }

   public static void vinylRim(PoseStack var0, VertexConsumer var1, int var2) {
      annulus(var0, var1, 0.035F, 0.395F, 1.004F, 0.0, -16185079, var2, false);
      annulus(var0, var1, 0.373F, 0.376F, 1.005F, 0.0, -14342875, var2, false);
      annulus(var0, var1, 0.384F, 0.386F, 1.005F, 0.0, -14671840, var2, false);
   }

   public static void vinylArt(PoseStack var0, VertexConsumer var1, int var2, double var3) {
      annulus(var0, var1, 0.035F, 0.365F, 1.006F, Math.toRadians(rotationDegrees(var3)), -1, var2, true);
   }

   private static void annulus(PoseStack var0, VertexConsumer var1, float var2, float var3, float var4, double var5, int var7, int var8, boolean var9) {
      for (int var10 = 0; var10 < 96; var10++) {
         double var11 = (double)var10 * Math.PI * 2.0 / 96.0;
         double var13 = (double)(var10 + 1) * Math.PI * 2.0 / 96.0;
         top(var0, var1, var2, var11, var4, var5, var7, var8, var9);
         top(var0, var1, var2, var13, var4, var5, var7, var8, var9);
         top(var0, var1, var3, var13, var4, var5, var7, var8, var9);
         top(var0, var1, var3, var11, var4, var5, var7, var8, var9);
      }
   }

   private static void top(PoseStack var0, VertexConsumer var1, float var2, double var3, float var5, double var6, int var8, int var9, boolean var10) {
      float var11 = 0.5F + var2 * (float)Math.cos(var3);
      float var12 = 0.5F + var2 * (float)Math.sin(var3);
      float var13 = 0.5F;
      float var14 = 0.5F;
      if (var10) {
         float var15 = var2 / 0.73F;
         var13 += var15 * (float)Math.cos(var3 - var6);
         var14 += var15 * (float)Math.sin(var3 - var6);
      }

      vertex(var0, var1, var11, var5, var12, var13, var14, var8, var9, 0.0F, 1.0F, 0.0F);
   }

   public static void worn(PoseStack var0, VertexConsumer var1, int var2) {
      vertex(var0, var1, 0.92F, 0.08F, -0.004F, 0.0F, 1.0F, -1, var2, 0.0F, 0.0F, -1.0F);
      vertex(var0, var1, 0.08F, 0.08F, -0.004F, 1.0F, 1.0F, -1, var2, 0.0F, 0.0F, -1.0F);
      vertex(var0, var1, 0.08F, 0.92F, -0.004F, 1.0F, 0.0F, -1, var2, 0.0F, 0.0F, -1.0F);
      vertex(var0, var1, 0.92F, 0.92F, -0.004F, 0.0F, 0.0F, -1, var2, 0.0F, 0.0F, -1.0F);
   }

   private static void vertex(
      PoseStack var0, VertexConsumer var1, float var2, float var3, float var4, float var5, float var6, int var7, int var8, float var9, float var10, float var11
   ) {
      var1.addVertex(var0.last(), var2, var3, var4)
         .setColor(var7)
         .setUv(var5, var6)
         .setOverlay(OverlayTexture.NO_OVERLAY)
         .setLight(var8)
         .setNormal(var0.last(), var9, var10, var11);
   }

   private JukeboxCoverGeometry() {
   }
}
