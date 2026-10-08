package pl.aridlin.psychiatrykroles.peeb.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.util.Optional;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.MultiBufferSource.BufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent.Stage;
import net.neoforged.neoforge.client.event.RenderPlayerEvent.Pre;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import pl.aridlin.psychiatrykroles.peeb.PeebAttachment;
import pl.aridlin.psychiatrykroles.peeb.client.mixin.PeebNameAccess;

@EventBusSubscriber(
   modid = "psychiatryk_peeb",
   value = {Dist.CLIENT}
)
public final class PeebRenderer {
   private static final ResourceLocation WHITE = ResourceLocation.parse("psychiatryk_peeb:textures/entity/white.png");
   private static final ResourceLocation INNER = ResourceLocation.parse("psychiatryk_peeb:textures/gui/grapple-inner.png");
   private static final ResourceLocation OUTER = ResourceLocation.parse("psychiatryk_peeb:textures/gui/grapple-outer.png");
   private static final WeakHashMap<Player, PeebRenderer.Frame> POSES = new WeakHashMap<>();
   public static long renderedPlayers;
   public static long renderedReticles;
   public static long renderedLocalDitherBatches;
   public static long renderedRopes;
   public static double lastRopeSag;
   public static double lastRopeArcLength;

   public static PeebMesh.Pose renderedPose(Player var0) {
      PeebRenderer.Frame var1 = POSES.get(var0);
      return var1 == null ? null : var1.pose;
   }

   public static Vec3 renderedTuskTip(Player var0) {
      PeebRenderer.Frame var1 = POSES.get(var0);
      return var1 == null ? null : var1.tuskTip;
   }

   public static PeebAttachment.Tilt renderedTilt(Player var0) {
      PeebRenderer.Frame var1 = POSES.get(var0);
      return var1 == null ? PeebAttachment.Tilt.ZERO : var1.tilt;
   }

   @SubscribeEvent(
      priority = EventPriority.LOWEST
   )
   public static void player(Pre var0) {
      Player var1 = var0.getEntity();
      if (PeebClient.active(var1) && !var0.isCanceled()) {
         Minecraft var2 = Minecraft.getInstance();
         if (!var1.isInvisibleTo(var2.player)) {
            float var3 = var0.getPartialTick();
            float var4 = Mth.rotLerp(var3, var1.yBodyRotO, var1.yBodyRot);
            PeebMesh var5 = PeebMesh.get();
            PeebRenderer.Frame var6 = POSES.get(var1);
            if (var6 == null || var6.mesh != var5) {
               var6 = new PeebRenderer.Frame(var5);
               POSES.put(var1, var6);
            }

            PeebClient.Motion var7 = PeebClient.motion(var1, var3);
            float var8 = Float.isFinite(var6.age) ? Math.max(0.0F, (var7.ageTicks() - var6.age) / 20.0F) : 0.0F;
            float var9 = var8 > 1.0E-4F ? Mth.wrapDegrees(var4 - var6.yaw) / var8 : 0.0F;
            var6.yaw = var4;
            var6.age = var7.ageTicks();
            var5.animate(var6.pose, var7, Mth.wrapDegrees(Mth.rotLerp(var3, var1.yHeadRotO, var1.yHeadRot) - var4), var1.getViewXRot(var3), var9);
            Optional<PeebClient.Grapple> var10 = var7.grapple();
            boolean hanging = var7.airborne() && !pl.aridlin.psychiatrykroles.peeb.PeebGrapple.isScooterRider(var1);
            Vec3 var11 = var1.getPosition(var3);
            Vec3 var12 = var10.<Vec3>map(var1x -> PeebAttachment.pivot(var11, var1x.attachmentYaw())).orElse(var11);
            PeebAttachment.Tilt var13 = (hanging ? var10 : Optional.<PeebClient.Grapple>empty()).<PeebAttachment.Tilt>map(var3x -> PeebAttachment.bodyTilt(var12, var3x.anchor(), PeebClient.velocity(var1), var4))
               .orElse(PeebAttachment.Tilt.ZERO);
            var6.tilt = PeebAttachment.smooth(var6.tilt, var13, 1.0 - Math.exp((double)(-Math.min(0.1F, var8) * 8.0F)));
            Quaternionf var14 = Axis.YP
               .rotationDegrees(-var4)
               .rotateX((float)Math.toRadians((double)var6.tilt.pitchDegrees()))
               .rotateZ((float)Math.toRadians((double)var6.tilt.rollDegrees()));
            Vec3 aimOrigin = hanging ? var12 : var11.add(0.0, PeebAttachment.PIVOT_HEIGHT, 0.0);
            Vector3f var15 = var10.<Vector3f>map(var2x -> new Quaternionf(var14).invert().transform(var2x.anchor().subtract(aimOrigin).toVector3f())).orElse(null);
            var5.aimTusk(var6.pose, var15, var8);
            Vector3f var16 = var6.pose.tuskTip();
            PoseStack var17 = var0.getPoseStack();
            var17.pushPose();
            if (var10.isPresent() && hanging) {
               Vec3 var18 = var12.subtract(var11);
               var17.translate(var18.x, var18.y, var18.z);
               var17.mulPose(var14);
               var17.translate(-var16.x, -var16.y, -var16.z);
               var6.tuskTip = var12;
            } else {
               var17.mulPose(var14);
               if (var1.isCrouching()) {
                  var17.translate(0.0, -0.15, 0.0);
                  var17.mulPose(Axis.XP.rotationDegrees(8.0F));
               }

               Vector3f var20 = new Quaternionf(var14).transform(new Vector3f(var16));
               var6.tuskTip = var11.add((double)var20.x, (double)var20.y, (double)var20.z);
            }

            int var21 = LivingEntityRenderer.getOverlayCoords(var1, 0.0F);
            RenderType characterType = PeebStyle.type();
            if (var1 == var2.player && !var2.options.getCameraType().isFirstPerson()) {
               Vec3 cameraPosition = var2.gameRenderer.getMainCamera().getPosition();
               Optional<PeebClient.Target> aimingTarget = PeebClient.target(var3);
               PeebStyle.localTarget(aimingTarget.map(t -> t.point().subtract(cameraPosition)).orElse(Vec3.ZERO), aimingTarget.isPresent());
               characterType = PeebStyle.localType();
               renderedLocalDitherBatches++;
            }
            var5.draw(var6.pose, var17, var0.getMultiBufferSource().getBuffer(characterType), var0.getPackedLight(), var21);
            var17.popPose();
            var0.setCanceled(true);
            renderedPlayers++;
            PeebNameAccess var19 = (PeebNameAccess)var0.getRenderer();
            if (var19.peeb$shouldShowName(var1)) {
               var19.peeb$renderNameTag(var1, var1.getDisplayName(), var17, var0.getMultiBufferSource(), var0.getPackedLight(), var3);
            }
         }
      }
   }

   @SubscribeEvent
   public static void world(RenderLevelStageEvent var0) {
      if (var0.getStage() == Stage.AFTER_ENTITIES) {
         Minecraft var1 = Minecraft.getInstance();
         if (var1.level != null && var1.player != null) {
            BufferSource var2 = var1.renderBuffers().bufferSource();
            PoseStack var3 = var0.getPoseStack();
            Vec3 var4 = var0.getCamera().getPosition();
            float var5 = var0.getPartialTick().getGameTimeDeltaPartialTick(false);
            var3.pushPose();
            var3.translate(-var4.x, -var4.y, -var4.z);
            Optional var6 = PeebClient.target(var5);
            if (var6.isPresent()) {
               PeebClient.Target var7 = (PeebClient.Target)var6.get();
               Vec3 var8 = var7.point().add(var7.normal().scale(0.012));
               var3.pushPose();
               var3.translate(var8.x, var8.y, var8.z);
               var3.mulPose(new Quaternionf().rotationTo(new Vector3f(0.0F, 0.0F, 1.0F), var7.normal().toVector3f()));
               float var9 = var7.eligible() ? 0.512F : 0.256F;
               ring(var3, var2, OUTER, var9, ((float)var1.player.tickCount + var5) / 20.0F * 117.64705F, 0.0F);
               ring(var3, var2, INNER, var9, 0.0F, 0.003F);
               var3.popPose();
               renderedReticles++;
            }

            for (Player var13 : var1.level.players()) {
               if (PeebClient.active(var13)) {
                  Optional<PeebClient.Grapple> var14 = PeebClient.grapple(var13);
                  if (!var14.isEmpty()) {
                     Vec3 var10 = renderedTuskTip(var13);
                     if (var10 == null) {
                        var10 = PeebAttachment.pivot(var13.getPosition(var5), ((PeebClient.Grapple)var14.get()).attachmentYaw());
                     }

                     Vec3 var11 = ((PeebClient.Grapple)var14.get()).anchor();
                     rope(var3, var2, var10, var11, var4, var14.get().length());
                  }
               }
            }

            var3.popPose();
            var2.endBatch(PeebStyle.type());
            // Ordered discard leaves genuine depth holes before the reticle draws.
            // It does not bypass walls or depth-test unrelated players.
            var2.endBatch(PeebStyle.localType());
            var2.endBatch(RenderType.entityCutoutNoCull(WHITE));
            var2.endBatch(RenderType.entityCutoutNoCull(INNER));
            var2.endBatch(RenderType.entityCutoutNoCull(OUTER));
         }
      }
   }

   private static void ring(PoseStack var0, MultiBufferSource var1, ResourceLocation var2, float var3, float var4, float var5) {
      var0.pushPose();
      var0.mulPose(Axis.ZP.rotationDegrees(var4));
      VertexConsumer var6 = var1.getBuffer(RenderType.entityCutoutNoCull(var2));
      quad(var0, var6, -var3, -var3, var5, 0.0F, 1.0F);
      quad(var0, var6, var3, -var3, var5, 1.0F, 1.0F);
      quad(var0, var6, var3, var3, var5, 1.0F, 0.0F);
      quad(var0, var6, -var3, var3, var5, 0.0F, 0.0F);
      var0.popPose();
   }

   private static void quad(PoseStack var0, VertexConsumer var1, float var2, float var3, float var4, float var5, float var6) {
      var1.addVertex(var0.last(), var2, var3, var4)
         .setColor(-1)
         .setUv(var5, var6)
         .setOverlay(OverlayTexture.NO_OVERLAY)
         .setLight(15728880)
         .setNormal(var0.last(), 0.0F, 0.0F, 1.0F);
   }

   private static void rope(PoseStack pose, MultiBufferSource buffers, Vec3 tip, Vec3 anchor, Vec3 camera, double restLength) {
      PeebRopeGeometry.Curve curve = PeebRopeGeometry.curve(tip, anchor, restLength);
      Vec3[] points = curve.points();
      Vec3[] side = new Vec3[points.length];
      for (int i = 0; i < points.length; i++) {
         Vec3 tangent = points[Math.min(i + 1, points.length - 1)].subtract(points[Math.max(0, i - 1)]);
         Vec3 width = tangent.cross(camera.subtract(points[i]));
         if (width.lengthSqr() < 1.0E-9) width = tangent.cross(new Vec3(0.0, 1.0, 0.0));
         if (width.lengthSqr() < 1.0E-9) width = tangent.cross(new Vec3(1.0, 0.0, 0.0));
         width = width.normalize().scale(0.035);
         if (i > 0 && width.dot(side[i - 1]) < 0.0) width = width.scale(-1.0);
         side[i] = width;
      }
      VertexConsumer vertices = buffers.getBuffer(RenderType.entityCutoutNoCull(WHITE));
      for (int i = 0; i < points.length - 1; i++) {
         ropeVertex(pose, vertices, points[i].subtract(side[i]), 0.0F, (float)i / PeebRopeGeometry.SEGMENTS);
         ropeVertex(pose, vertices, points[i].add(side[i]), 1.0F, (float)i / PeebRopeGeometry.SEGMENTS);
         ropeVertex(pose, vertices, points[i + 1].add(side[i + 1]), 1.0F, (float)(i + 1) / PeebRopeGeometry.SEGMENTS);
         ropeVertex(pose, vertices, points[i + 1].subtract(side[i + 1]), 0.0F, (float)(i + 1) / PeebRopeGeometry.SEGMENTS);
      }
      renderedRopes++;
      lastRopeSag = curve.sag();
      lastRopeArcLength = curve.arcLength();
   }

   private static void ropeVertex(PoseStack pose, VertexConsumer vertices, Vec3 point, float u, float v) {
      vertices.addVertex(pose.last(), (float)point.x, (float)point.y, (float)point.z)
         .setColor(-1).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(15728880)
         .setNormal(pose.last(), 0.0F, 1.0F, 0.0F);
   }

   private PeebRenderer() {
   }

   private static final class Frame {
      final PeebMesh mesh;
      final PeebMesh.Pose pose;
      float yaw;
      float age = Float.NaN;
      PeebAttachment.Tilt tilt = PeebAttachment.Tilt.ZERO;
      Vec3 tuskTip;

      Frame(PeebMesh var1) {
         this.mesh = var1;
         this.pose = var1.pose();
      }
   }
}
