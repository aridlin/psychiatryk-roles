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
   // Separate the vinyl from the block surface, including shader depth bias.
   public static final float DISC_Y = 1.016F;
   public static final float ART_Y = 1.020F;
   public static final float WORN_Z = -0.004F;

   public static double rotationDegrees(double var0) {
      return Double.isFinite(var0) && !(var0 < 0.0) ? var0 % 1.8 * 200.0 : 0.0;
   }

   public static void vinylRim(PoseStack var0, VertexConsumer var1, int var2) {
      annulus(var0, var1, HOLE_RADIUS, DISC_RADIUS, DISC_Y, 0.0, 0xff090909, var2, false);
      for (int groove = 0; groove < 7; groove++) {
         float radius = 0.18F + groove * 0.031F;
         annulus(var0, var1, radius, radius + 0.0015F, DISC_Y + 0.001F, 0.0, 0xff303030, var2, false);
      }
      edge(var0, var1, var2);
   }

   public static void vinylArt(PoseStack var0, VertexConsumer var1, int var2, double var3) {
      annulus(var0, var1, HOLE_RADIUS, ART_RADIUS, ART_Y, Math.toRadians(rotationDegrees(var3)), -1, var2, true);
   }

   /** A real spinning record while a track has no cover, or its cover is loading. */
   public static void vinylFallback(PoseStack pose, VertexConsumer vinyl, int light, double seconds) {
      double angle = Math.toRadians(rotationDegrees(seconds));
      annulus(pose, vinyl, HOLE_RADIUS, 0.16F, ART_Y, 0.0, 0xff64a840, light, false);
      // Asymmetric paint makes rotation visible even with a circular plain label.
      index(pose, vinyl, light, angle, 0.23F, 0.345F, 0.055, 0xffefe5c2);
      index(pose, vinyl, light, angle + Math.PI * 0.5, 0.32F, 0.345F, 0.09, 0xffdf8b38);
   }

   public static void vinylFallbackArt(PoseStack pose, VertexConsumer label, int light, double seconds) {
      annulus(pose, label, HOLE_RADIUS, 0.15F, ART_Y + 0.001F, Math.toRadians(rotationDegrees(seconds)), -1, light, true);
   }

   private static void index(PoseStack pose, VertexConsumer buffer, int light, double angle,
                             float inner, float outer, double halfWidth, int color) {
      top(pose, buffer, inner, angle - halfWidth, ART_Y + 0.002F, 0.0, color, light, false);
      top(pose, buffer, inner, angle + halfWidth, ART_Y + 0.002F, 0.0, color, light, false);
      top(pose, buffer, outer, angle + halfWidth, ART_Y + 0.002F, 0.0, color, light, false);
      top(pose, buffer, outer, angle - halfWidth, ART_Y + 0.002F, 0.0, color, light, false);
   }

   private static void edge(PoseStack pose, VertexConsumer buffer, int light) {
      for (int sector = 0; sector < SECTORS; sector++) {
         double a = sector * Math.PI * 2.0 / SECTORS;
         double b = (sector + 1) * Math.PI * 2.0 / SECTORS;
         float x1 = 0.5F + DISC_RADIUS * (float)Math.cos(a);
         float z1 = 0.5F + DISC_RADIUS * (float)Math.sin(a);
         float x2 = 0.5F + DISC_RADIUS * (float)Math.cos(b);
         float z2 = 0.5F + DISC_RADIUS * (float)Math.sin(b);
         float nx = (float)Math.cos((a + b) * 0.5);
         float nz = (float)Math.sin((a + b) * 0.5);
         vertex(pose, buffer, x1, 1.002F, z1, 0.5F, 0.5F, 0xff090909, light, nx, 0.0F, nz);
         vertex(pose, buffer, x1, DISC_Y, z1, 0.5F, 0.5F, 0xff090909, light, nx, 0.0F, nz);
         vertex(pose, buffer, x2, DISC_Y, z2, 0.5F, 0.5F, 0xff090909, light, nx, 0.0F, nz);
         vertex(pose, buffer, x2, 1.002F, z2, 0.5F, 0.5F, 0xff090909, light, nx, 0.0F, nz);
      }
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
