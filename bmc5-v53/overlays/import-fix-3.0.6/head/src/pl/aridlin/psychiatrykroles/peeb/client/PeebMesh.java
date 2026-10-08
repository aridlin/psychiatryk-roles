package pl.aridlin.psychiatrykroles.peeb.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.Mth;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class PeebMesh {
   private static final ResourceLocation MODEL = ResourceLocation.parse("psychiatryk_peeb:models/peeb-model.json");
   private static final float SCALE = 1.4851409F;
   private static final float FLOOR = 0.5587755F;
   private static Object manager;
   private static PeebMesh mesh;
   private final Vector3f[] vertices;
   private final Vector3f[] normals;
   private final int[][] bones;
   private final float[][] weights;
   private final int[] parents;
   private final int[] boneNodes;
   private final float[][] defaults;
   private final Matrix4f[] bind;
   private final int[][] triangles;
   private final int[] colors;
   private final Map<String, PeebMesh.Clip> clips = new HashMap<>();
   private final int[] noseNodes = new int[3];
   private final int headNode;

   public static PeebMesh get() {
      ResourceManager var0 = Minecraft.getInstance().getResourceManager();
      if (manager != var0 || mesh == null) {
         try {
            mesh = new PeebMesh(read(MODEL));
            manager = var0;
         } catch (IOException var2) {
            throw new IllegalStateException("Original Peeb rig unavailable", var2);
         }
      }

      return mesh;
   }

   public PeebMesh.Pose pose() {
      return new PeebMesh.Pose(this);
   }

   public int vertexCount() {
      return this.vertices.length;
   }

   public int triangleCount() {
      return Arrays.stream(this.triangles).mapToInt(var0 -> var0.length / 3).sum();
   }

   private static JsonObject read(ResourceLocation var0) throws IOException {
      JsonObject var3;
      try (
         InputStream var1 = Minecraft.getInstance().getResourceManager().getResourceOrThrow(var0).open();
         InputStreamReader var2 = new InputStreamReader(var1, StandardCharsets.UTF_8);
      ) {
         var3 = JsonParser.parseReader(var2).getAsJsonObject();
      }

      return var3;
   }

   private PeebMesh(JsonObject var1) throws IOException {
      this.vertices = vectors(var1.getAsJsonArray("vertices"));
      this.normals = vectors(var1.getAsJsonArray("normals"));
      if (this.vertices.length == 500 && this.normals.length == this.vertices.length) {
         this.bones = integers(var1.getAsJsonArray("bone_indices"));
         this.weights = rows(var1.getAsJsonArray("bone_weights"));
         JsonArray var2 = var1.getAsJsonArray("nodes");
         this.parents = new int[var2.size()];
         this.defaults = new float[var2.size()][10];
         int var3 = -1;

         for (int var4 = 0; var4 < var2.size(); var4++) {
            JsonObject var5 = var2.get(var4).getAsJsonObject();
            this.parents[var4] = var5.get("parent").getAsInt();
            if (this.parents[var4] >= var4 || this.parents[var4] < -1) {
               throw new IOException("Invalid original rig hierarchy");
            }

            float[] var6 = array(var5.getAsJsonArray("position"));
            float[] var7 = array(var5.getAsJsonArray("rotation"));
            float[] var8 = array(var5.getAsJsonArray("scale"));
            System.arraycopy(var6, 0, this.defaults[var4], 0, 3);
            System.arraycopy(var7, 0, this.defaults[var4], 3, 4);
            System.arraycopy(var8, 0, this.defaults[var4], 7, 3);
            String var9 = var5.get("name").getAsString();
            if (var9.equals("Head")) {
               var3 = var4;
            }

            for (int var10 = 0; var10 < 3; var10++) {
               if (var9.equals("Nose_" + var10)) {
                  this.noseNodes[var10] = var4;
               }
            }
         }

         this.headNode = var3;
         this.boneNodes = flatInts(var1.getAsJsonArray("bone_node_indices"));
         JsonArray var14 = var1.getAsJsonArray("bind_matrices");
         this.bind = new Matrix4f[var14.size()];

         for (int var15 = 0; var15 < this.bind.length; var15++) {
            float[] var17 = array(var14.get(var15).getAsJsonArray());
            float[] var19 = new float[16];

            for (int var22 = 0; var22 < 4; var22++) {
               for (int var25 = 0; var25 < 4; var25++) {
                  var19[var25 * 4 + var22] = var17[var22 * 4 + var25];
               }
            }

            this.bind[var15] = new Matrix4f().set(var19);
         }

         JsonArray var16 = var1.getAsJsonArray("materials");
         JsonArray var18 = var1.getAsJsonArray("submeshes");
         this.triangles = new int[var18.size()][];
         this.colors = new int[var18.size()];

         for (int var20 = 0; var20 < var18.size(); var20++) {
            JsonObject var23 = var18.get(var20).getAsJsonObject();
            this.triangles[var20] = flatInts(var23.getAsJsonArray("triangles"));
            float[] var26 = array(var16.get(var23.get("material").getAsInt()).getAsJsonObject().getAsJsonArray("rgba"));
            this.colors[var20] = Math.round(var26[3] * 255.0F) << 24
               | Math.round(var26[0] * 255.0F) << 16
               | Math.round(var26[1] * 255.0F) << 8
               | Math.round(var26[2] * 255.0F);

            for (int var13 : this.triangles[var20]) {
               if (var13 < 0 || var13 >= this.vertices.length) {
                  throw new IOException("Invalid Peeb triangle");
               }
            }
         }

         for (String var24 : List.of("idle", "run", "midair")) {
            JsonObject var27 = read(ResourceLocation.parse("psychiatryk_peeb:models/" + var24 + "-animation-30fps.json"));
            HashMap var29 = new HashMap();

            for (JsonElement var31 : var27.getAsJsonArray("tracks")) {
               JsonObject var32 = var31.getAsJsonObject();
               var29.put(var32.get("node").getAsInt(), rows(var32.getAsJsonArray("samples")));
            }

            this.clips.put(var24, new PeebMesh.Clip(var27.get("duration").getAsFloat(), var27.get("fps").getAsFloat(), var29));
         }
      } else {
         throw new IOException("Unexpected original Peeb mesh");
      }
   }

   private static Vector3f[] vectors(JsonArray var0) {
      Vector3f[] var1 = new Vector3f[var0.size()];

      for (int var2 = 0; var2 < var1.length; var2++) {
         JsonArray var3 = var0.get(var2).getAsJsonArray();
         var1[var2] = new Vector3f(var3.get(0).getAsFloat(), var3.get(1).getAsFloat(), var3.get(2).getAsFloat());
      }

      return var1;
   }

   private static float[] array(JsonArray var0) {
      float[] var1 = new float[var0.size()];

      for (int var2 = 0; var2 < var1.length; var2++) {
         var1[var2] = var0.get(var2).getAsFloat();
         if (!Float.isFinite(var1[var2])) {
            throw new IllegalArgumentException("Nonfinite Peeb data");
         }
      }

      return var1;
   }

   private static float[][] rows(JsonArray var0) {
      float[][] var1 = new float[var0.size()][];

      for (int var2 = 0; var2 < var1.length; var2++) {
         var1[var2] = array(var0.get(var2).getAsJsonArray());
      }

      return var1;
   }

   private static int[] flatInts(JsonArray var0) {
      int[] var1 = new int[var0.size()];

      for (int var2 = 0; var2 < var1.length; var2++) {
         var1[var2] = var0.get(var2).getAsInt();
      }

      return var1;
   }

   private static int[][] integers(JsonArray var0) {
      int[][] var1 = new int[var0.size()][];

      for (int var2 = 0; var2 < var1.length; var2++) {
         var1[var2] = flatInts(var0.get(var2).getAsJsonArray());
      }

      return var1;
   }

   public void animate(PeebMesh.Pose var1, PeebClient.Motion var2, float var3, float var4, float var5) {
      float var6 = Float.isFinite(var1.age) ? Mth.clamp((var2.ageTicks() - var1.age) / 20.0F, 0.0F, 0.1F) : 0.0F;
      var1.age = var2.ageTicks();
      String var7 = var2.airborne() ? "midair" : (var2.speed() > 0.025F ? "run" : "idle");
      if (!var7.equals(var1.clip)) {
         var1.clip = var7;
         var1.blend = 0.0F;
      }

      var1.blend = Math.min(1.0F, var1.blend + var6 * 8.0F);

      for (int var8 = 0; var8 < this.defaults.length; var8++) {
         System.arraycopy(this.defaults[var8], 0, var1.local[var8], 0, 10);
      }

      PeebMesh.Clip var23 = this.clips.get(var1.clip);
      float var9 = var2.ageTicks() / 20.0F;
      if (var1.clip.equals("run")) {
         var9 = var2.walkPhase() * 0.32F;
      }

      var9 %= var23.duration();
      var1.animationSeconds = var9;

      for (Entry var11 : var23.tracks().entrySet()) {
         int var12 = (Integer)var11.getKey();
         float[][] var13 = (float[][])var11.getValue();
         float var14 = var9 * var23.fps();
         int var15 = Math.min(var13.length - 1, (int)var14);
         int var16 = Math.min(var13.length - 1, var15 + 1);
         float var17 = var14 - (float)var15;
         float[] var18 = var13[var15];
         float[] var19 = var13[var16];
         float[] var20 = var1.local[var12];

         for (int var21 = 0; var21 < 3; var21++) {
            var20[var21] = Mth.lerp(var1.blend, this.defaults[var12][var21], Mth.lerp(var17, var18[var21], var19[var21]));
         }

         Quaternionf var39 = new Quaternionf(var18[3], var18[4], var18[5], var18[6]).slerp(new Quaternionf(var19[3], var19[4], var19[5], var19[6]), var17);
         var39 = new Quaternionf(this.defaults[var12][3], this.defaults[var12][4], this.defaults[var12][5], this.defaults[var12][6]).slerp(var39, var1.blend);
         var20[3] = var39.x;
         var20[4] = var39.y;
         var20[5] = var39.z;
         var20[6] = var39.w;

         for (int var22 = 7; var22 < 10; var22++) {
            var20[var22] = Mth.lerp(var1.blend, this.defaults[var12][var22], Mth.lerp(var17, var18[var22], var19[var22]));
         }
      }

      if (this.headNode >= 0) {
         float[] var25 = var1.local[this.headNode];
         Quaternionf var27 = new Quaternionf(var25[3], var25[4], var25[5], var25[6]);
         // Head axes belong to the imported rig: Spine already rotates them by 90 degrees.
         // Convert a model-space look delta into the currently animated parent frame.
         Quaternionf parentWorld = new Quaternionf();
         for (int ancestor = this.parents[this.headNode]; ancestor >= 0; ancestor = this.parents[ancestor]) {
            float[] local = var1.local[ancestor];
            parentWorld.premul(new Quaternionf(local[3], local[4], local[5], local[6]));
         }
         parentWorld.normalize();
         Quaternionf modelLook = new Quaternionf().rotationYXZ(
            (float)Math.toRadians((double)Mth.clamp(var3, -65.0F, 65.0F)),
            (float)Math.toRadians((double)(Mth.clamp(var4, -40.0F, 45.0F) * 0.6F)), 0.0F);
         var27 = new Quaternionf(parentWorld).conjugate().mul(modelLook).mul(parentWorld).mul(var27).normalize();
         var25[3] = var27.x;
         var25[4] = var27.y;
         var25[5] = var27.z;
         var25[6] = var27.w;
      }

      for (int var26 = 0; var26 < 3; var26++) {
         float var28 = 20.0F / (float)(1 << var26);
         float var29 = var6;
         Vector3f var30 = var1.spring[var26];

         for (Vector3f var31 = var1.velocity[var26]; var29 > 0.0F; var30.z = Mth.clamp(var30.z, -90.0F, 40.0F)) {
            float var32 = Math.min(var29, 0.008333334F);
            var29 -= var32;
            float var34 = 1.0F - (float)Math.pow(0.6, (double)(var32 * 60.0F));
            float var36 = 1.0F - (float)Math.pow(0.9, (double)(var32 * 60.0F));
            float var37 = Mth.clamp(-var5 * var28 * 0.08F, -30.0F, 30.0F);
            float var38 = Mth.clamp((var2.verticalSpeed() * 20.0F / 1.4851409F + 4.0F) * 2.0F * var28, -70.0F, 20.0F);
            var31.y = var31.y + (var37 - var30.y) * var34;
            var31.z = var31.z + (var38 - var30.z) * var34;
            var30.y = var30.y + (var31.y - var30.y) * var36;
            var30.z = var30.z + (var31.z - var30.z) * var36;
            var30.y = Mth.clamp(var30.y, -55.0F, 55.0F);
         }

         float[] var33 = var1.local[this.noseNodes[var26]];
         Quaternionf var35 = new Quaternionf(var33[3], var33[4], var33[5], var33[6]);
         var35.mul(new Quaternionf().rotationYXZ((float)Math.toRadians((double)var30.y), 0.0F, (float)Math.toRadians((double)var30.z)));
         var33[3] = var35.x;
         var33[4] = var35.y;
         var33[5] = var35.z;
         var33[6] = var35.w;
      }

      this.skin(var1);
   }

   private void skin(PeebMesh.Pose var1) {
      for (int var2 = 0; var2 < var1.world.length; var2++) {
         float[] var3 = var1.local[var2];
         Matrix4f var4 = var1.world[var2].identity();
         if (this.parents[var2] >= 0) {
            var4.set(var1.world[this.parents[var2]]);
         }

         var4.translate(var3[0], var3[1], var3[2]).rotate(new Quaternionf(var3[3], var3[4], var3[5], var3[6])).scale(var3[7], var3[8], var3[9]);
      }

      for (int var7 = 0; var7 < this.bind.length; var7++) {
         var1.world[this.boneNodes[var7]].mul(this.bind[var7], var1.skin[var7]);
         var1.skin[var7].normal(var1.normal[var7]);
      }

      for (int var8 = 0; var8 < this.vertices.length; var8++) {
         Vector3f var9 = var1.skinned[var8].zero();
         Vector3f var10 = var1.skinnedNormal[var8].zero();

         for (int var5 = 0; var5 < this.weights[var8].length; var5++) {
            if (this.weights[var8][var5] > 0.0F) {
               int var6 = this.bones[var8][var5];
               var1.skin[var6].transformPosition(this.vertices[var8], var1.scratch);
               var9.fma(this.weights[var8][var5], var1.scratch);
               var1.normal[var6].transform(this.normals[var8], var1.normalScratch);
               var10.fma(this.weights[var8][var5], var1.normalScratch);
            }
         }

         var9.set(-var9.x * 1.4851409F, (var9.y + 0.5587755F) * 1.4851409F, var9.z * 1.4851409F);
         var10.set(-var10.x, var10.y, var10.z).normalize();
      }
   }

   public void aimTusk(PeebMesh.Pose var1, Vector3f var2, float var3) {
      Quaternionf var4 = new Quaternionf();
      if (var2 != null && var2.lengthSquared() > 1.0E-7F) {
         Vector3f var5 = var1.tuskTip();
         var5.set(-var5.x / 1.4851409F, var5.y / 1.4851409F - 0.5587755F, var5.z / 1.4851409F);
         Vector3f var6 = var1.world[this.noseNodes[0]].getTranslation(new Vector3f());
         Vector3f var7 = var5.sub(var6).normalize();
         Vector3f var8 = new Vector3f(-var2.x, var2.y, var2.z).normalize();
         var4.rotationTo(var7, var8);
      }

      var1.tuskAim.slerp(var4, Math.min(1.0F, 1.0F - (float)Math.exp((double)(-Math.max(0.0F, var3) * 10.0F)))).normalize();
      Quaternionf var9 = var1.world[this.parents[this.noseNodes[0]]].getUnnormalizedRotation(new Quaternionf()).normalize();
      Quaternionf var10 = new Quaternionf(var9).invert().mul(var1.tuskAim).mul(var9);
      float[] var11 = var1.local[this.noseNodes[0]];
      Quaternionf var12 = var10.mul(new Quaternionf(var11[3], var11[4], var11[5], var11[6]));
      var11[3] = var12.x;
      var11[4] = var12.y;
      var11[5] = var12.z;
      var11[6] = var12.w;
      this.skin(var1);
   }

   public void draw(PeebMesh.Pose var1, PoseStack var2, VertexConsumer var3, int var4, int var5) {
      var1.submittedVertices = 0L;

      for (int var6 = 0; var6 < this.triangles.length; var6++) {
         int var7 = this.colors[var6];
         int[] var8 = this.triangles[var6];

         for (byte var9 = 0; var9 < var8.length; var9 += 3) {
            for (int var10 = 0; var10 < 4; var10++) {
               int var11 = var8[var9 + Math.min(var10, 2)];
               Vector3f var12 = var1.skinned[var11];
               Vector3f var13 = var1.skinnedNormal[var11];
               var3.addVertex(var2.last(), var12)
                  .setColor(var7)
                  .setUv((float)var6 / 2.0F, 0.5F)
                  .setOverlay(var5)
                  .setLight(var4)
                  .setNormal(var2.last(), var13.x, var13.y, var13.z);
               var1.submittedVertices++;
            }
         }
      }
   }

   private static record Clip(float duration, float fps, Map<Integer, float[][]> tracks) {
   }

   public static final class Pose {
      private final Matrix4f[] world;
      private final Matrix4f[] skin;
      private final Matrix3f[] normal;
      private final float[][] local;
      private final Vector3f[] skinned;
      private final Vector3f[] skinnedNormal;
      private final Vector3f[] spring = new Vector3f[]{new Vector3f(), new Vector3f(), new Vector3f()};
      private final Vector3f[] velocity = new Vector3f[]{new Vector3f(), new Vector3f(), new Vector3f()};
      private float age = Float.NaN;
      private float blend;
      private String clip = "idle";
      private final Vector3f scratch = new Vector3f();
      private final Vector3f normalScratch = new Vector3f();
      private final Quaternionf tuskAim = new Quaternionf();
      public long submittedVertices;
      public float animationSeconds;

      private Pose(PeebMesh var1) {
         this.world = matrices(var1.parents.length);
         this.skin = matrices(var1.bind.length);
         this.normal = new Matrix3f[var1.bind.length];

         for (int var2 = 0; var2 < this.normal.length; var2++) {
            this.normal[var2] = new Matrix3f();
         }

         this.local = new float[var1.parents.length][10];
         this.skinned = vectors(var1.vertices.length);
         this.skinnedNormal = vectors(var1.vertices.length);
      }

      private static Matrix4f[] matrices(int var0) {
         Matrix4f[] var1 = new Matrix4f[var0];

         for (int var2 = 0; var2 < var0; var2++) {
            var1[var2] = new Matrix4f();
         }

         return var1;
      }

      private static Vector3f[] vectors(int var0) {
         Vector3f[] var1 = new Vector3f[var0];

         for (int var2 = 0; var2 < var0; var2++) {
            var1[var2] = new Vector3f();
         }

         return var1;
      }

      public float tuskDeflection() {
         return this.spring[0].length();
      }

      public Vector3f tuskTip() {
         return new Vector3f(this.skinned[234]).add(this.skinned[237]).mul(0.5F);
      }
   }
}
