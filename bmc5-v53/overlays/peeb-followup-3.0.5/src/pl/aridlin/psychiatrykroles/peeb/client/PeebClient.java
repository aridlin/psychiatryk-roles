package pl.aridlin.psychiatrykroles.peeb.client;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut;
import net.neoforged.neoforge.client.event.ClientTickEvent.Post;
import net.neoforged.neoforge.client.event.ClientTickEvent.Pre;
import net.neoforged.neoforge.client.event.InputEvent.InteractionKeyMappingTriggered;
import pl.aridlin.psychiatrykroles.peeb.PeebAttachment;
import pl.aridlin.psychiatrykroles.peeb.PeebConfig;
import pl.aridlin.psychiatrykroles.peeb.PeebConfigPayload;
import pl.aridlin.psychiatrykroles.peeb.PeebGrapple;
import pl.aridlin.psychiatrykroles.peeb.PeebMode;
import pl.aridlin.psychiatrykroles.peeb.PeebPackets;
import pl.aridlin.psychiatrykroles.peeb.PeebStatePayload;

@EventBusSubscriber(
   modid = "psychiatryk_peeb",
   value = {Dist.CLIENT}
)
public final class PeebClient {
   private static final Map<UUID, PeebClient.Received> STATES = new HashMap<>();
   // Keep the aim line just above Peeb's head instead of through his torso.
   private static PeebClient.CameraProfile profile = new PeebClient.CameraProfile(6.0F, 2.1F, -90.0F, 90.0F);
   private static ClientLevel level;
   private static LocalPlayer owner;
   private static CameraType savedCamera;
   private static boolean owned;
   private static boolean grappleHeld;
   private static boolean requestOutstanding;
   private static boolean releaseSent;
   private static long ticks;
   private static long lastPredictionTick = Long.MIN_VALUE;
   private static long lastAttachTick = -100L;
   private static PeebClient.Target target;
   private static float freeYaw;
   private static float freePitch;
   private static float freeYawO;
   private static float freePitchO;
   private static PeebConfig.Values settings = PeebConfig.DEFAULT;

   private PeebClient() {
   }

   public static void cameraProfile(PeebClient.CameraProfile var0) {
      profile = var0;
   }

   public static PeebConfig.Values settings() {
      return settings;
   }

   public static void receiveConfig(PeebConfigPayload var0) {
      if (var0.values().valid()) {
         settings = var0.values();
         if (var0.open()) {
            PeebSettingsScreen.receive(settings);
         }
      }
   }

   public static float cameraDistance() {
      return profile.distance();
   }

   public static void cameraDistance(float var0) {
      cameraProfile(new PeebClient.CameraProfile(Mth.clamp(var0, 2.0F, 10.0F), profile.height(), profile.minimumPitch(), profile.maximumPitch()));
   }

   public static Optional<PeebStatePayload> state(UUID var0) {
      PeebClient.Received var1 = STATES.get(var0);
      return var1 == null ? Optional.empty() : Optional.of(var1.packet());
   }

   public static boolean active(Player var0) {
      return var0 != null
         && var0.isAlive()
         && !var0.isRemoved()
         && !var0.isSpectator()
         && (!var0.isPassenger() || PeebGrapple.isScooterRider(var0))
         && !var0.isSleeping()
         && !var0.isFallFlying()
         && !var0.getAbilities().flying
         && PeebMode.holding(var0);
   }

   public static boolean localActive() {
      Minecraft var0 = Minecraft.getInstance();
      return owned && owner == var0.player && level == var0.level && active(owner) && var0.getCameraEntity() == owner;
   }

   public static Optional<PeebClient.Target> target() {
      return localActive() ? Optional.ofNullable(target) : Optional.empty();
   }

   public static Optional<PeebClient.Target> target(float var0) {
      if (localActive() && Minecraft.getInstance().screen == null && !grapple(owner).isPresent()) {
         target = findTarget(Minecraft.getInstance(), var0);
         return Optional.ofNullable(target);
      } else {
         return Optional.empty();
      }
   }

   public static boolean freeLookTurn(double var0, double var2) {
      Minecraft var4 = Minecraft.getInstance();
      if (localActive() && PeebBindings.freelookDown(var4)) {
         freeYaw = Mth.wrapDegrees(freeYaw + (float)var0 * 0.15F);
         freePitch = Mth.clamp(owner.getXRot() + freePitch + (float)var2 * 0.15F, profile.minimumPitch(), profile.maximumPitch()) - owner.getXRot();
         return true;
      } else {
         return false;
      }
   }

   public static Optional<PeebClient.Grapple> grapple(Player var0) {
      if (!active(var0)) {
         return Optional.empty();
      } else {
         PeebClient.Received var1 = STATES.get(var0.getUUID());
         return var1 != null && var1.packet().entityId() == var0.getId() && var1.packet().active()
            ? var1.packet().anchor().map(var1x -> new PeebClient.Grapple(var1x,
               PeebGrapple.predictedRestLength(var1.packet().ropeLength(), ticks - var1.receivedTick(), settings),
               var1.packet().attachmentYaw()))
            : Optional.empty();
      }
   }

   public static PeebClient.Motion motion(Player var0, float var1) {
      Vec3 var2 = velocity(var0);
      return new PeebClient.Motion(
         (float)var0.tickCount + var1,
         Mth.lerp(var1, var0.walkDistO, var0.walkDist),
         (float)var2.horizontalDistance(),
         airborne(var0, var2),
         (float)var2.y,
         PeebAudio.landingAge(var0, var1),
         grapple(var0)
      );
   }

   public static Vec3 velocity(Player var0) {
      Vec3 var1 = var0.getDeltaMovement();
      return var0 != Minecraft.getInstance().player && !(var1.lengthSqr() > 1.0E-8)
         ? new Vec3(var0.getX() - var0.xo, var0.getY() - var0.yo, var0.getZ() - var0.zo)
         : var1;
   }

   public static boolean airborne(Player var0, Vec3 var1) {
      if (PeebGrapple.isScooterRider(var0)) return false;
      if (!var0.onGround()) {
         // A tether can leave onGround unset for a tick while feet still rest
         // against a surface. Do not switch to the hanging pose in that case.
         return var1.y > 0.05 || var0.isInWater()
            || !var0.level().getBlockCollisions(var0, var0.getBoundingBox().deflate(0.04).move(0.0, -0.12, 0.0)).iterator().hasNext();
      } else {
         return var0 != Minecraft.getInstance().player && !(Math.abs(var1.y) < 0.015) && !var0.isInWater()
            ? !var0.level().getBlockCollisions(var0, var0.getBoundingBox().deflate(0.04).move(0.0, -0.09, 0.0)).iterator().hasNext()
            : false;
      }
   }

   public static void receive(PeebStatePayload var0) {
      Minecraft var1 = Minecraft.getInstance();
      synchronizeWorld(var1);
      if (var1.level != null
         && Float.isFinite(var0.attachmentYaw())
         && Double.isFinite(var0.ropeLength())
         && !(var0.ropeLength() < 0.0)
         && !(var0.ropeLength() > 32.0)) {
         if (var0.anchor().isPresent()) {
            Vec3 var2 = var0.anchor().get();
            if (!Double.isFinite(var2.x + var2.y + var2.z)) {
               return;
            }
         }

         if (!var0.active()) {
            STATES.remove(var0.playerUuid());
         } else {
            if (STATES.size() >= 64 && !STATES.containsKey(var0.playerUuid())) {
               STATES.entrySet().removeIf(var0x -> ticks - var0x.getValue().receivedTick() > 200L);
            }

            if (STATES.size() >= 64 && !STATES.containsKey(var0.playerUuid())) {
               return;
            }

            STATES.put(var0.playerUuid(), new PeebClient.Received(var0, ticks));
         }

         if (var1.player != null && var0.playerUuid().equals(var1.player.getUUID())) {
            requestOutstanding = false;
            if (var0.anchor().isEmpty()) {
               releaseSent = false;
            }
         }
      }
   }

   @SubscribeEvent(
      priority = EventPriority.HIGHEST
   )
   public static void tick(Pre var0) {
      Minecraft var1 = Minecraft.getInstance();
      ticks++;
      synchronizeWorld(var1);
      boolean var2 = level != null && active(owner) && var1.getCameraEntity() == owner;
      if (var2 && !owned) {
         savedCamera = var1.options.getCameraType();
         owned = true;
         lastAttachTick = -100L;
      } else if (!var2 && owned) {
         exit(true);
      }

      if (!owned) {
         while (PeebClientBootstrap.INTERACT.consumeClick()) {
         }
      } else {
         PeebBindings.audit(var1);
         var1.options.setCameraType(CameraType.THIRD_PERSON_BACK);
         freeYawO = freeYaw;
         freePitchO = freePitch;
         PeebBindings.reserveFreelook(var1);
         if (!PeebBindings.freelookDown(var1)) {
            freeYaw *= 0.65F;
            freePitch *= 0.65F;
            if (Math.abs(freeYaw) < 0.05F) {
               freeYaw = 0.0F;
            }

            if (Math.abs(freePitch) < 0.05F) {
               freePitch = 0.0F;
            }
         }

         owner.setXRot(Mth.clamp(owner.getXRot(), profile.minimumPitch(), profile.maximumPitch()));
         var1.options.keyAttack.setDown(false);

         while (var1.options.keyAttack.consumeClick()) {
         }

         if (var1.screen == null && !var1.isPaused()) {
            target = grapple(owner).isPresent() ? null : findTarget(var1, 1.0F);

            while (PeebClientBootstrap.INTERACT.consumeClick()) {
               interact(var1);
            }

            if (grappleHeld
               && grapple(owner).isEmpty()
               && target != null
               && target.eligible()
               && ticks - lastAttachTick >= 4L
               && (!requestOutstanding || ticks - lastAttachTick >= 20L)) {
               PeebPackets.requestAttach(target.point());
               lastAttachTick = ticks;
               requestOutstanding = true;
               releaseSent = false;
            }
         } else {
            release();
            target = null;
         }

         STATES.entrySet().removeIf(var0x -> {
            Player var1x = level.getPlayerByUUID(var0x.getKey());
            return var1x == null && ticks - var0x.getValue().receivedTick() > 200L;
         });
      }
   }

   @SubscribeEvent(
      priority = EventPriority.HIGHEST
   )
   public static void mouse(net.neoforged.neoforge.client.event.InputEvent.MouseButton.Pre var0) {
      Minecraft var1 = Minecraft.getInstance();
      if (active(var1.player) && var1.getCameraEntity() == var1.player && var1.screen == null && var0.getButton() == 0) {
         synchronizeWorld(var1);
         if (var0.getAction() == 1) {
            grappleHeld = true;
            releaseSent = false;
         } else if (var0.getAction() == 0) {
            release();
         }

         var0.setCanceled(true);
      }
   }

   @SubscribeEvent(
      priority = EventPriority.HIGHEST
   )
   public static void attack(InteractionKeyMappingTriggered var0) {
      Minecraft var1 = Minecraft.getInstance();
      if (active(var1.player) && var1.getCameraEntity() == var1.player && var1.screen == null && var0.isAttack()) {
         var0.setSwingHand(false);
         var0.setCanceled(true);
      }
   }

   @SubscribeEvent
   public static void logout(LoggingOut var0) {
      reset();
      settings = PeebConfig.DEFAULT;
      PeebSettingsScreen.forget();
   }

   @SubscribeEvent
   public static void predict(Post var0) {
      if (localActive() && !PeebGrapple.isScooterRider(owner) && !Minecraft.getInstance().isPaused() && lastPredictionTick != ticks) {
         lastPredictionTick = ticks;
         grapple(owner)
            .ifPresent(
               var0x -> {
                  Vec3 var1 = PeebAttachment.pivot(owner, var0x.attachmentYaw());
                  double range = settings.range() + PeebGrapple.TETHER_RANGE_EPSILON;
                  if (owner.getEyePosition().distanceToSqr(var0x.anchor()) <= range * range
                     && var1.distanceToSqr(var0x.anchor()) <= range * range) {
                     owner.setDeltaMovement(
                        PeebGrapple.pullVelocity(var1, owner.getDeltaMovement(), var0x.anchor(), var0x.length(), settings, owner.onGround())
                     );
                  }
               }
            );
      }
   }

   private static void release() {
      if (!releaseSent && (grappleHeld || requestOutstanding || owner != null && grapple(owner).isPresent())) {
         PeebPackets.release();
         releaseSent = true;
      }

      grappleHeld = false;
      requestOutstanding = false;
   }

   private static void exit(boolean var0) {
      if (var0 && owner != null && owner == Minecraft.getInstance().player) {
         release();
      }

      if (savedCamera != null) {
         Minecraft.getInstance().options.setCameraType(savedCamera);
      }

      owned = false;
      savedCamera = null;
      releaseSent = false;
      requestOutstanding = false;
      grappleHeld = false;
      target = null;
      lastPredictionTick = Long.MIN_VALUE;
      freePitchO = 0.0F;
      freeYawO = 0.0F;
      freePitch = 0.0F;
      freeYaw = 0.0F;
   }

   private static void reset() {
      exit(false);
      STATES.clear();
      PeebAudio.reset();
      owner = null;
      level = null;
   }

   private static void synchronizeWorld(Minecraft var0) {
      if (var0.level != level || var0.player != owner) {
         reset();
      }

      level = var0.level;
      owner = var0.player;
   }

   private static void interact(Minecraft var0) {
      if (var0.gameMode != null && owner != null) {
         HitResult var1 = var0.hitResult;
         InteractionHand var2 = !PeebMode.worn(owner) && !owner.getMainHandItem().is((Item)PeebMode.PEEB.get())
            ? InteractionHand.OFF_HAND
            : InteractionHand.MAIN_HAND;
         if (var1 instanceof EntityHitResult var3 && owner.canInteractWithEntity(var3.getEntity(), 0.0)) {
            InteractionResult var4 = var0.gameMode.interactAt(owner, var3.getEntity(), var3, var2);
            if (!var4.consumesAction()) {
               var0.gameMode.interact(owner, var3.getEntity(), var2);
            }

            return;
         }

         var1 = owner.pick(owner.blockInteractionRange(), 1.0F, false);
         if (var1 instanceof BlockHitResult var6 && var1.getType() == Type.BLOCK) {
            var0.gameMode.useItemOn(owner, var2, var6);
         }
      }
   }

   private static PeebClient.Target findTarget(Minecraft var0, float var1) {
      PeebClient.CameraFrame var2 = cameraFrame(var0.gameRenderer.getMainCamera(), var1);
      if (var2 == null) {
         return null;
      } else {
         Vec3 var3 = Vec3.directionFromRotation(var2.pitch(), var2.yaw());
         Vec3 var4 = Vec3.directionFromRotation(0.0F, var2.yaw() + 90.0F);
         Vec3 var5 = var4.cross(var3).normalize();
         double var6 = settings.range() + (double)profile.distance() + 2.0;
         double[][] var8 = new double[][]{
            {0.0, 0.0}, {0.25, 0.0}, {-0.25, 0.0}, {0.0, 0.25}, {0.0, -0.25}, {0.25, 0.25}, {-0.25, 0.25}, {0.25, -0.25}, {-0.25, -0.25}
         };

         for (double[] var12 : var8) {
            Vec3 var13 = var2.position().add(var4.scale(var12[0])).add(var5.scale(var12[1]));
            BlockHitResult var14 = level.clip(new ClipContext(var13, var13.add(var3.scale(var6)), Block.COLLIDER, Fluid.NONE, owner));
            PeebClient.Target enemy = entityTarget(var13, var14.getLocation());
            if (enemy != null) return enemy;
            PeebClient.Target var15 = validateTarget(var14);
            if (var15 != null) {
               BlockHitResult var16 = trace(var2.position(), var15.point());
               if (sameHit(var16, var14)) {
                  return var15;
               }
            }
         }

         return null;
      }
   }

   private static BlockHitResult trace(Vec3 var0, Vec3 var1) {
      Vec3 var2 = var1.subtract(var0).normalize();
      return level.clip(new ClipContext(var0, var1.add(var2.scale(0.03)), Block.COLLIDER, Fluid.NONE, owner));
   }

   private static boolean clearEntityRay(Vec3 start, Vec3 end) {
      BlockHitResult hit = trace(start, end);
      return hit.getType() != Type.BLOCK || hit.getLocation().distanceToSqr(start) + 1.0E-4 >= end.distanceToSqr(start);
   }

   private static Target entityTarget(Vec3 start, Vec3 end) {
      LivingEntity nearest = null;
      Vec3 point = null;
      double distance = Double.POSITIVE_INFINITY;
      for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, new AABB(start, end).inflate(0.2),
         e -> e != owner && e.isAlive() && !e.isSpectator() && e.isPickable() && !owner.isAlliedTo(e)
            && e.getRootVehicle() != owner.getRootVehicle())) {
         Optional<Vec3> hit = entity.getBoundingBox().inflate(0.08).clip(start, end);
         if (hit.isPresent() && start.distanceToSqr(hit.get()) < distance) {
            double range = settings.range();
            Vec3 eye = owner.getEyePosition(), pivot = PeebAttachment.pivot(owner);
            Vec3 candidate = hit.get();
            if (eye.distanceToSqr(candidate) <= range * range && pivot.distanceToSqr(candidate) <= range * range
               && clearEntityRay(eye, candidate) && clearEntityRay(pivot, candidate)) {
               nearest = entity; point = candidate; distance = start.distanceToSqr(candidate);
            }
         }
      }
      if (nearest == null) return null;
      Vec3 normal = start.subtract(point).normalize();
      return new Target(point, normal, true);
   }

   private static boolean sameHit(BlockHitResult var0, BlockHitResult var1) {
      return var0.getType() == Type.BLOCK
         && !var0.isInside()
         && var0.getBlockPos().equals(var1.getBlockPos())
         && var0.getLocation().distanceTo(var1.getLocation()) <= 0.06;
   }

   private static PeebClient.Target validateTarget(BlockHitResult var0) {
      if (var0.getType() == Type.BLOCK && !var0.isInside()) {
         Vec3 var1 = owner.getEyePosition();
         Vec3 var2 = var0.getLocation();
         Vec3 var3 = PeebAttachment.pivot(owner);
         double var4 = settings.range();
         double var6 = var1.distanceTo(var2);
         double var8 = var3.distanceTo(var2);
         boolean var10 = var6 >= 0.35 && var8 >= 0.35 && var6 <= var4 && var8 <= var4;
         return var10 && sameHit(trace(var1, var2), var0) && sameHit(trace(var3, var2), var0)
            ? new PeebClient.Target(var2, Vec3.atLowerCornerOf(var0.getDirection().getNormal()), true)
            : null;
      } else {
         return null;
      }
   }

   public static PeebClient.CameraFrame cameraFrame(Camera var0, float var1) {
      if (localActive() && var0.getEntity() == owner) {
         float var2 = owner.getViewYRot(var1) + Mth.rotLerp(var1, freeYawO, freeYaw);
         float var3 = Mth.clamp(owner.getViewXRot(var1) + Mth.lerp(var1, freePitchO, freePitch), profile.minimumPitch(), profile.maximumPitch());
         Vec3 var4 = owner.getPosition(var1).add(0.0, (double)profile.height(), 0.0);
         Vec3 var5 = Vec3.directionFromRotation(var3, var2).scale((double)(-profile.distance()));
         double var6 = (double)profile.distance();
         Vec3 var8 = var5.normalize();

         for (int var9 = 0; var9 < 8; var9++) {
            Vec3 var10 = var4.add((var9 & 1) == 0 ? -0.15 : 0.15, (var9 & 2) == 0 ? -0.15 : 0.15, (var9 & 4) == 0 ? -0.15 : 0.15);
            BlockHitResult var11 = level.clip(new ClipContext(var10, var10.add(var5), Block.VISUAL, Fluid.NONE, owner));
            if (var11.getType() != Type.MISS) {
               var6 = Math.min(var6, Math.max(0.05, var10.distanceTo(var11.getLocation()) - 0.08));
            }
         }

         return new PeebClient.CameraFrame(var4.add(var8.scale(var6)), var2, var3);
      } else {
         return null;
      }
   }

   public static record CameraFrame(Vec3 position, float yaw, float pitch) {
   }

   public static record CameraProfile(float distance, float height, float minimumPitch, float maximumPitch) {
      public CameraProfile(float distance, float height, float minimumPitch, float maximumPitch) {
         if (Float.isFinite(distance + height + minimumPitch + maximumPitch) && !(distance < 0.5F) && !(distance > 10.0F) && !(minimumPitch >= maximumPitch)) {
            this.distance = distance;
            this.height = height;
            this.minimumPitch = minimumPitch;
            this.maximumPitch = maximumPitch;
         } else {
            throw new IllegalArgumentException("Invalid Peeb camera profile");
         }
      }
   }

   public static record Grapple(Vec3 anchor, double length, float attachmentYaw) {
      public Grapple(Vec3 var1, double var2) {
         this(var1, var2, 0.0F);
      }
   }

   public static record Motion(
      float ageTicks, float walkPhase, float speed, boolean airborne, float verticalSpeed, float landingAgeTicks, Optional<PeebClient.Grapple> grapple
   ) {
   }

   private static record Received(PeebStatePayload packet, long receivedTick) {
   }

   public static record Target(Vec3 point, Vec3 normal, boolean eligible) {
   }
}
