package pl.aridlin.psychiatrykroles.peeb;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.HitResult.Type;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerChangedDimensionEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerRespawnEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.StartTracking;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent.Post;
import net.neoforged.neoforge.network.PacketDistributor;
import pl.aridlin.kukirin.Scooter;

public final class PeebGrapple {
   public static final double TARGET_TOLERANCE = 0.2;
   public static final double MIN_ROPE_LENGTH = 0.35;
   public static final double RESTING_ROPE_LENGTH = 2.0;
   public static final double MAX_STRETCH = 0.75;
   public static final double MAX_HORIZONTAL_SPEED = 0.4;
   public static final double MAX_TETHER_SPEED = 0.8;
   public static final double MAX_RADIAL_REPAIR = 0.2;
   public static final double RADIAL_REPAIR_FACTOR = 0.3;
   public static final int ATTACH_INTERVAL_TICKS = 4;
   /** Quick hook take-up, followed by a short, forgiving finish near the anchor. */
   public static final double REEL_EASING_PER_TICK = 0.25;
   public static final double MAX_REEL_PER_TICK = 0.60;
   public static final double DEFAULT_PULL_ACCELERATION = 0.24;
   public static final double MAX_PULL_ACCELERATION = 0.45;
   public static final double WINCH_APPROACH_GAIN = 2.0;
   public static final double TETHER_RANGE_EPSILON = 0.15;
   public static final int ROPE_SYNC_INTERVAL_TICKS = 2;
   public static final float CONTACT_DAMAGE = 4.0F;
   public static final double CONTACT_RANGE = 1.8;
   public static final int CONTACT_COOLDOWN_TICKS = 20;
   private static final Map<UUID, PeebGrapple.Session> SESSIONS = new HashMap<>();
   private static final Map<UUID, Long> NEXT_CONTACT_ATTACK = new HashMap<>();

   private PeebGrapple() {
   }

   private static boolean modeActive(ServerPlayer var0) {
      return PeebMode.holding(var0) && var0.isAlive() && !var0.isRemoved() && !var0.hasDisconnected();
   }

   private static boolean canGrapple(ServerPlayer var0) {
      return modeActive(var0)
         && (!var0.isPassenger() || isScooterRider(var0))
         && !var0.isSpectator()
         && !var0.isChangingDimension()
         && !var0.isSleeping()
         && !var0.isFallFlying()
         && !var0.getAbilities().flying
         && var0.getServer() != null
         && var0.getServer().getPlayerList().getPlayer(var0.getUUID()) == var0;
   }

   /** Only the controlling rider of our scooter receives mounted grapple movement. */
   public static boolean isScooterRider(Player player) {
      return player != null && player.isPassenger() && player.getRootVehicle() instanceof Scooter scooter
         && scooter.isAlive() && !scooter.isRemoved() && scooter.getControllingPassenger() == player;
   }

   private static Entity movementEntity(ServerPlayer player) {
      return isScooterRider(player) ? player.getRootVehicle() : player;
   }

   private static boolean allowedTarget(ServerPlayer player, LivingEntity target) {
      if (target == player || !target.isAlive() || target.isRemoved() || !target.isPickable() || target.isSpectator()
         || target.level() != player.level() || target.getRootVehicle() == player.getRootVehicle()
         || target.isAlliedTo(player) || player.isAlliedTo(target)) {
         return false;
      }
      return !(target instanceof Player other)
         || (player.getServer().isPvpAllowed() && player.canHarmPlayer(other) && !other.getAbilities().invulnerable);
   }

   private static boolean visiblePoint(ServerPlayer player, Vec3 origin, Vec3 point) {
      BlockHitResult hit = trace(player, origin, point);
      return hit.getType() == Type.MISS || origin.distanceToSqr(hit.getLocation()) + 0.0025 >= origin.distanceToSqr(point);
   }

   /** A coordinate request is resolved to a real, nearest visible living target on the server. */
   private static LivingEntity findTarget(ServerPlayer player, Vec3 requested) {
      Vec3 eye = player.getEyePosition();
      Vec3 end = requested.add(requested.subtract(eye).normalize().scale(0.03));
      LivingEntity nearest = null;
      double distance = Double.POSITIVE_INFINITY;
      for (LivingEntity target : player.level().getEntitiesOfClass(LivingEntity.class, new AABB(eye, end).inflate(0.3),
         candidate -> allowedTarget(player, candidate))) {
         AABB box = target.getBoundingBox().inflate(0.1);
         Optional<Vec3> hit = box.clip(eye, end);
         if (!target.getBoundingBox().inflate(0.25).contains(requested) || hit.isEmpty()) {
            continue;
         }
         double next = eye.distanceToSqr(hit.get());
         if (next < distance && visiblePoint(player, eye, hit.get())) {
            nearest = target;
            distance = next;
         }
      }
      return nearest;
   }

   private static LivingEntity target(ServerPlayer player, Session session) {
      if (session.targetUuid == null || !(player.level() instanceof ServerLevel level)) {
         return null;
      }
      Entity entity = level.getEntity(session.targetUuid);
      return entity instanceof LivingEntity living && allowedTarget(player, living) ? living : null;
   }

   private static boolean refreshTarget(ServerPlayer player, Session session) {
      LivingEntity entity = target(player, session);
      if (entity == null || session.targetOffset == null) {
         return false;
      }
      session.anchor = entity.position().add(session.targetOffset);
      return finite(session.anchor) && player.level().hasChunkAt(BlockPos.containing(entity.position()));
   }

   private static boolean current(ServerPlayer var0, PeebGrapple.Session var1) {
      return var1 != null
         && var1.player == var0
         && var1.dimension.equals(var0.level().dimension())
         && var0.getServer() != null
         && var0.getServer().getPlayerList().getPlayer(var0.getUUID()) == var0;
   }

   private static PeebGrapple.Session session(ServerPlayer var0) {
      PeebGrapple.Session var1 = SESSIONS.get(var0.getUUID());
      if (!current(var0, var1)) {
         if (var1 != null) {
            clear(var1.player);
         }

         var1 = new PeebGrapple.Session(var0);
         SESSIONS.put(var0.getUUID(), var1);
         broadcast(var0);
      }

      return var1;
   }

   public static PeebStatePayload snapshot(ServerPlayer var0) {
      PeebGrapple.Session var1 = SESSIONS.get(var0.getUUID());
      boolean var2 = modeActive(var0);
      Optional var3 = var2 && current(var0, var1) && var1.anchor != null ? Optional.of(var1.anchor) : Optional.empty();
      return new PeebStatePayload(
         var0.getUUID(), var0.getId(), var2, var3, var3.isPresent() ? var1.ropeLength : 0.0, var3.isPresent() ? var1.attachmentYaw : PeebAttachment.yaw(var0)
      );
   }

   public static BlockHitResult raycast(ServerPlayer var0) {
      Vec3 var1 = var0.getEyePosition();
      return var0.level().clip(new ClipContext(var1, var1.add(var0.getLookAngle().scale(PeebConfig.server().range())), Block.COLLIDER, Fluid.NONE, var0));
   }

   private static boolean finite(Vec3 var0) {
      return Double.isFinite(var0.x) && Double.isFinite(var0.y) && Double.isFinite(var0.z);
   }

   private static BlockHitResult trace(ServerPlayer var0, Vec3 var1) {
      return trace(var0, var0.getEyePosition(), var1);
   }

   private static BlockHitResult trace(ServerPlayer var0, Vec3 var1, Vec3 var2) {
      Vec3 var3 = var2.subtract(var1);
      return var0.level().clip(new ClipContext(var1, var2.add(var3.normalize().scale(0.03)), Block.COLLIDER, Fluid.NONE, var0));
   }

   public static boolean attach(ServerPlayer var0, Vec3 var1) {
      if (canGrapple(var0) && finite(var1)) {
         PeebGrapple.Session var2 = session(var0);
         if (var2.anchor != null) {
            return false;
         } else {
            long var3 = var0.level().getGameTime();
            if (var3 - var2.lastAttach < 4L) {
               return false;
            } else {
               var2.lastAttach = var3;
               double var5 = PeebConfig.server().range();
               float var7 = PeebAttachment.yaw(var0);
               Vec3 var8 = PeebAttachment.pivot(var0, var7);
               double var9 = var0.getEyePosition().distanceTo(var1);
               if (!(var9 < 0.35) && !(var9 > var5) && !(var8.distanceTo(var1) > var5) && var0.level().hasChunkAt(BlockPos.containing(var1))) {
                  LivingEntity target = findTarget(var0, var1);
                  if (target != null) {
                     Vec3 hit = target.getBoundingBox().inflate(0.1).clip(var0.getEyePosition(),
                        var1.add(var1.subtract(var0.getEyePosition()).normalize().scale(0.03))).orElse(null);
                     if (hit != null && var8.distanceTo(hit) >= MIN_ROPE_LENGTH && var8.distanceTo(hit) <= var5
                        && var0.getEyePosition().distanceTo(hit) <= var5 && visiblePoint(var0, var8, hit)) {
                        var2.anchor = hit;
                        var2.block = null;
                        var2.targetUuid = target.getUUID();
                        var2.targetOffset = hit.subtract(target.position());
                        var2.contactAttempted = false;
                        var2.ropeLength = initialRopeLength(var8.distanceTo(hit), var5);
                        var2.attachmentYaw = var7;
                        Entity movement = movementEntity(var0);
                        var2.movementUuid = movement.getUUID();
                        var2.previousPosition = movement.position();
                        var2.lastPhysicsTick = Long.MIN_VALUE;
                        var2.lastRopeSyncTick = var3;
                        broadcast(var0);
                        return true;
                     }
                     return false;
                  }
                  BlockHitResult var11 = trace(var0, var1);
                  if (var11.getType() == Type.BLOCK && !var11.isInside() && !(var11.getLocation().distanceTo(var1) > 0.2)) {
                     double var12 = var8.distanceTo(var11.getLocation());
                     if (!(var12 > var5) && !(var12 < 0.35) && !(var0.getEyePosition().distanceTo(var11.getLocation()) > var5)) {
                        BlockHitResult var14 = trace(var0, var8, var11.getLocation());
                        if (var14.getType() == Type.BLOCK
                           && !var14.isInside()
                           && var14.getBlockPos().equals(var11.getBlockPos())
                           && !(var14.getLocation().distanceTo(var11.getLocation()) > 0.06)) {
                           var2.anchor = var11.getLocation();
                           var2.block = var11.getBlockPos().immutable();
                           var2.targetUuid = null;
                           var2.targetOffset = null;
                           var2.contactAttempted = false;
                           var2.ropeLength = initialRopeLength(var12, var5);
                           var2.attachmentYaw = var7;
                           Entity movement = movementEntity(var0);
                           var2.movementUuid = movement.getUUID();
                           var2.previousPosition = movement.position();
                           var2.lastPhysicsTick = Long.MIN_VALUE;
                           var2.lastRopeSyncTick = var3;
                           broadcast(var0);
                           return true;
                        } else {
                           return false;
                        }
                     } else {
                        return false;
                     }
                  } else {
                     return false;
                  }
               } else {
                  return false;
               }
            }
         }
      } else {
         return false;
      }
   }

   public static void release(ServerPlayer var0) {
      PeebGrapple.Session var1 = SESSIONS.get(var0.getUUID());
      if (current(var0, var1) && var1.anchor != null) {
         var1.anchor = null;
         var1.block = null;
         var1.targetUuid = null;
         var1.targetOffset = null;
         var1.ropeLength = 0.0;
         var1.previousPosition = movementEntity(var0).position();
         broadcast(var0);
      }
   }

   public static boolean validTether(ServerPlayer var0) {
      PeebGrapple.Session var1 = SESSIONS.get(var0.getUUID());
      if (canGrapple(var0) && current(var0, var1) && var1.anchor != null) {
         if (var1.targetUuid != null && !refreshTarget(var0, var1)) {
            return false;
         }
         double var2 = var0.getEyePosition().distanceTo(var1.anchor);
         Vec3 var4 = PeebAttachment.pivot(var0, var1.attachmentYaw);
         double var5 = var4.distanceTo(var1.anchor);
         double var7 = PeebConfig.server().range();
         if (Double.isFinite(var2)
            && !(var2 > var7 + TETHER_RANGE_EPSILON)
            && Double.isFinite(var5)
            && !(var5 > var7 + TETHER_RANGE_EPSILON)
            && (var1.targetUuid != null || (var1.block != null && var0.level().hasChunkAt(var1.block)))) {
            if (var1.targetUuid != null) {
               return true;
            }
            // Line of sight is checked when attaching. A rope must stay hooked
            // when the player looks away or swings around the supporting block.
            // Releasing still follows removal of the actual anchor surface.
            for (var box : var0.level().getBlockState(var1.block)
               .getCollisionShape(var0.level(), var1.block, CollisionContext.of(var0)).toAabbs()) {
               if (box.move(var1.block).inflate(0.01).contains(var1.anchor)) {
                  return true;
               }
            }
            return false;
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   public static Vec3 constrainVelocity(Vec3 var0, Vec3 var1, Vec3 var2, double var3) {
      return constrainVelocity(var0, var1, var2, var3, PeebConfig.DEFAULT);
   }

   public static Vec3 constrainVelocity(Vec3 var0, Vec3 var1, Vec3 var2, double var3, PeebConfig.Values var5) {
      return constrainVelocity(var0, var1, var2, var3, var5, false);
   }

   public static double initialRopeLength(double var0, double var2) {
      return Double.isFinite(var0) && Double.isFinite(var2) && !(var0 < 0.35) && !(var2 < 0.35) ? Math.min(var2, Math.max(2.0, var0)) : 0.0;
   }

   private static boolean validPhysics(Vec3 pivot, Vec3 velocity, Vec3 anchor, double restLength, PeebConfig.Values settings) {
      return pivot != null && velocity != null && anchor != null
         && finite(pivot) && finite(velocity) && finite(anchor)
         && Double.isFinite(restLength) && settings != null && settings.valid()
         && restLength >= MIN_ROPE_LENGTH && restLength <= settings.range();
   }

   /** Authoritative rest length. The server advances it once per server tick. */
   public static double reelLength(double restLength, PeebConfig.Values settings) {
      if (!Double.isFinite(restLength) || settings == null || !settings.valid()) {
         return 0.0;
      }
      double minimum = Math.min(RESTING_ROPE_LENGTH, settings.range());
      double current = Math.max(minimum, Math.min(settings.range(), restLength));
      if (current - minimum < 1.0E-4) {
         return minimum;
      }
      double strengthScale = settings.strength() / PeebConfig.DEFAULT.strength();
      double amount = Math.min(MAX_REEL_PER_TICK * strengthScale, (current - minimum) * REEL_EASING_PER_TICK);
      return Math.max(minimum, current - amount);
   }

   /** At most one sync interval of local 20 Hz reel prediction, never render-frame based. */
   public static double predictedRestLength(double serverRestLength, long ageTicks, PeebConfig.Values settings) {
      double predicted = serverRestLength;
      long ticksToPredict = Math.max(0L, Math.min(ROPE_SYNC_INTERVAL_TICKS, ageTicks));
      for (long tick = 0; tick < ticksToPredict; tick++) {
         predicted = reelLength(predicted, settings);
      }
      return predicted;
   }

   /**
    * A unilateral rope only resists outward travel. This safety clamp does not
    * pull, repair position or cancel inward/tangential momentum. The server may
    * run it against observed player travel without applying a second winch pull.
    */
   public static Vec3 constrainVelocity(Vec3 pivot, Vec3 velocity, Vec3 anchor, double restLength, PeebConfig.Values settings, boolean grounded) {
      if (!validPhysics(pivot, velocity, anchor, restLength, settings)) {
         return Vec3.ZERO;
      }
      velocity = limitVelocity(velocity, settings);
      Vec3 offset = pivot.subtract(anchor);
      double maximumLength = Math.min(settings.range(), restLength + MAX_STRETCH);
      if (grounded && velocity.y <= 0.0 && offset.y > 0.0) {
         double horizontalLength = Math.sqrt(Math.max(0.0, maximumLength * maximumLength - offset.y * offset.y));
         Vec3 horizontal = constrainRadial(new Vec3(offset.x, 0.0, offset.z), new Vec3(velocity.x, 0.0, velocity.z), horizontalLength);
         return limitVelocity(new Vec3(horizontal.x, velocity.y, horizontal.z), settings);
      }
      return constrainRadial(offset, velocity, maximumLength);
   }

   private static Vec3 constrainRadial(Vec3 offset, Vec3 velocity, double maximumLength) {
      double distance = offset.length();
      if (distance < 1.0E-8) {
         return velocity;
      }
      Vec3 radial = offset.scale(1.0 / distance);
      double outwardSpeed = velocity.dot(radial);
      if (outwardSpeed <= 0.0) {
         return velocity;
      }
      double allowedOutward = Math.max(0.0, maximumLength - distance);
      return outwardSpeed > allowedOutward ? velocity.subtract(radial.scale(outwardSpeed - allowedOutward)) : velocity;
   }

   /** Positive winch correction only, with no elastic spring or outward push. */
   public static double tension(double distance, double radialSpeed, double restLength, PeebConfig.Values settings) {
      if (!Double.isFinite(distance) || !Double.isFinite(radialSpeed) || !Double.isFinite(restLength)
         || settings == null || !settings.valid() || distance <= restLength) {
         return 0.0;
      }
      double inwardSpeed = Math.min(settings.maxSpeed(), (distance - restLength) * WINCH_APPROACH_GAIN);
      return Math.max(0.0, Math.min(pullAcceleration(settings), radialSpeed + inwardSpeed));
   }

   private static double pullAcceleration(PeebConfig.Values settings) {
      return Math.min(MAX_PULL_ACCELERATION, DEFAULT_PULL_ACCELERATION * settings.strength() / PeebConfig.DEFAULT.strength());
   }

   /**
    * Keep a taut rope as a pendulum without projecting the player's position.
    * Only the inward radial component changes; tangent and excess inward speed
    * remain available for steering, swinging and release momentum.
    */
   private static double tautCorrection(Vec3 offset, Vec3 velocity, Vec3 radial, double restLength, PeebConfig.Values settings) {
      if (offset.add(velocity).lengthSqr() <= restLength * restLength) {
         return 0.0;
      }
      double radialSpeed = velocity.dot(radial);
      double tangentSpeedSquared = velocity.subtract(radial.scale(radialSpeed)).lengthSqr();
      double allowedRadial = Math.sqrt(Math.max(0.0, restLength * restLength - tangentSpeedSquared)) - offset.length();
      return Math.max(0.0, Math.min(pullAcceleration(settings), radialSpeed - allowedRadial));
   }

   /**
    * Called once by the owning client's ClientTick Post. Minecraft's normal
    * local movement consumes this velocity on the next tick; the server checks
    * that movement and never applies this additive winch a second time.
    */
   public static Vec3 pullVelocity(Vec3 pivot, Vec3 velocity, Vec3 anchor, double restLength, PeebConfig.Values settings, boolean grounded) {
      if (!validPhysics(pivot, velocity, anchor, restLength, settings)) {
         return Vec3.ZERO;
      }
      velocity = limitVelocity(velocity, settings);
      Vec3 offset = pivot.subtract(anchor);
      double effectiveRestLength = restLength;
      boolean floorContact = grounded && velocity.y <= 0.0 && offset.y > 0.0;
      Vec3 forceVelocity = velocity;
      if (floorContact) {
         // The ground supports the body: floor anchors must not drag it down.
         effectiveRestLength = Math.sqrt(Math.max(0.0, restLength * restLength - offset.y * offset.y));
         offset = new Vec3(offset.x, 0.0, offset.z);
         forceVelocity = new Vec3(velocity.x, 0.0, velocity.z);
      }
      double distance = offset.length();
      if (distance < 1.0E-8) {
         return velocity;
      }
      Vec3 radial = offset.scale(1.0 / distance);
      // Winch take-up and the taut-rope constraint share one acceleration budget:
      // they must not add two impulses or turn into a rubber-band spring.
      double pull = Math.max(tension(distance, forceVelocity.dot(radial), effectiveRestLength, settings),
         tautCorrection(offset, forceVelocity, radial, effectiveRestLength, settings));
      Vec3 pulled = velocity.subtract(radial.scale(pull));
      return constrainVelocity(pivot, pulled, anchor, restLength, settings, grounded);
   }

   private static Vec3 limitVelocity(Vec3 var0, PeebConfig.Values var1) {
      double var2 = Math.sqrt(var0.x * var0.x + var0.z * var0.z);
      if (var2 > var1.maxHorizontalSpeed()) {
         var0 = new Vec3(var0.x * var1.maxHorizontalSpeed() / var2, var0.y, var0.z * var1.maxHorizontalSpeed() / var2);
      }

      double var4 = var0.length();
      return var4 > var1.maxSpeed() ? var0.scale(var1.maxSpeed() / var4) : var0;
   }

   private static Vec3 limitScooterVelocity(Vec3 velocity, double horizontalCap, double totalCap) {
      double horizontal = velocity.horizontalDistance();
      if (horizontal > horizontalCap) {
         velocity = new Vec3(velocity.x * horizontalCap / horizontal, velocity.y, velocity.z * horizontalCap / horizontal);
      }
      double total = velocity.length();
      return total > totalCap ? velocity.scale(totalCap / total) : velocity;
   }

   /** The mounted winch keeps vehicle momentum and uses the vehicle's own ground contact. */
   private static Vec3 scooterPullVelocity(Scooter scooter, Vec3 pivot, Vec3 anchor, double restLength, PeebConfig.Values settings) {
      Vec3 velocity = scooter.getDeltaMovement();
      if (!validPhysics(pivot, velocity, anchor, restLength, settings)) {
         return Vec3.ZERO;
      }
      double horizontalCap = Math.max(1.2, Math.min(3.0, scooter.cruiseSpeed() * 1.2));
      double totalCap = Math.max(1.8, horizontalCap + 0.8);
      velocity = limitScooterVelocity(velocity, horizontalCap, totalCap);
      Vec3 offset = pivot.subtract(anchor);
      double effectiveRestLength = restLength;
      Vec3 forceVelocity = velocity;
      boolean floorContact = scooter.onGround() && velocity.y <= 0.0 && offset.y > 0.0;
      if (floorContact) {
         effectiveRestLength = Math.sqrt(Math.max(0.0, restLength * restLength - offset.y * offset.y));
         offset = new Vec3(offset.x, 0.0, offset.z);
         forceVelocity = new Vec3(velocity.x, 0.0, velocity.z);
      }
      double distance = offset.length();
      if (distance < 1.0E-8) {
         return velocity;
      }
      Vec3 radial = offset.scale(1.0 / distance);
      double pull = Math.max(tension(distance, forceVelocity.dot(radial), effectiveRestLength, settings),
         tautCorrection(offset, forceVelocity, radial, effectiveRestLength, settings));
      Vec3 pulled = velocity.subtract(radial.scale(pull));
      double maximumLength = Math.min(settings.range(), restLength + MAX_STRETCH);
      Vec3 fullOffset = pivot.subtract(anchor);
      if (floorContact) {
         double horizontalLength = Math.sqrt(Math.max(0.0, maximumLength * maximumLength - fullOffset.y * fullOffset.y));
         Vec3 horizontal = constrainRadial(new Vec3(fullOffset.x, 0.0, fullOffset.z), new Vec3(pulled.x, 0.0, pulled.z), horizontalLength);
         pulled = new Vec3(horizontal.x, pulled.y, horizontal.z);
      } else {
         pulled = constrainRadial(fullOffset, pulled, maximumLength);
      }
      return limitScooterVelocity(pulled, horizontalCap, totalCap);
   }

   private static void contactAttack(ServerPlayer player, Session session) {
      LivingEntity enemy = target(player, session);
      if (session.contactAttempted || enemy == null) {
         return;
      }
      Vec3 pivot = PeebAttachment.pivot(player, session.attachmentYaw);
      AABB bounds = enemy.getBoundingBox();
      Vec3 nearest = new Vec3(Math.clamp(pivot.x, bounds.minX, bounds.maxX),
         Math.clamp(pivot.y, bounds.minY, bounds.maxY), Math.clamp(pivot.z, bounds.minZ, bounds.maxZ));
      if (pivot.distanceToSqr(nearest) > CONTACT_RANGE * CONTACT_RANGE || !visiblePoint(player, pivot, nearest)) {
         return;
      }
      // A held hook never hits repeatedly, including one initially suppressed
      // by cooldown. Releasing and attaching again cannot bypass the player cap.
      session.contactAttempted = true;
      long now = player.level().getGameTime();
      if (now < NEXT_CONTACT_ATTACK.getOrDefault(player.getUUID(), Long.MIN_VALUE)) {
         return;
      }
      NEXT_CONTACT_ATTACK.put(player.getUUID(), now + CONTACT_COOLDOWN_TICKS);
      enemy.hurt(player.damageSources().playerAttack(player), CONTACT_DAMAGE);
   }

   static void tick(Post var0) {
      if (var0.getEntity() instanceof ServerPlayer var1) {
         if (!modeActive(var1)) {
            clear(var1);
         } else {
            PeebGrapple.Session var6 = session(var1);
            long physicsTick = var1.level().getGameTime();
            if (physicsTick == var6.lastPhysicsTick) {
               return;
            }
            var6.lastPhysicsTick = physicsTick;
            Vec3 var3 = PeebAttachment.pivot(var1, var6.attachmentYaw);
            Entity movement = movementEntity(var1);
            if (!movement.getUUID().equals(var6.movementUuid)) {
               var6.movementUuid = movement.getUUID();
               var6.previousPosition = movement.position();
            }
            Vec3 var4 = movement.position().subtract(var6.previousPosition);
            var6.previousPosition = movement.position();
            if (var6.anchor != null) {
               if (validTether(var1) && finite(var4) && !(var4.lengthSqr() > 16.0)) {
                  double previousLength = var6.ropeLength;
                  var6.ropeLength = reelLength(previousLength, PeebConfig.server());
                  if ((previousLength != var6.ropeLength || var6.lastSyncedAnchor == null || var6.lastSyncedAnchor.distanceToSqr(var6.anchor) > 1.0E-6)
                     && (physicsTick - var6.lastRopeSyncTick >= ROPE_SYNC_INTERVAL_TICKS
                     || var6.ropeLength == Math.min(RESTING_ROPE_LENGTH, PeebConfig.server().range()))) {
                     var6.lastRopeSyncTick = physicsTick;
                     broadcast(var1);
                  }
                  contactAttack(var1, var6);
                  if (movement instanceof Scooter scooter) {
                     // The rider client skips the additive Peeb winch. The root
                     // scooter receives one authoritative server impulse instead.
                     Vec3 pulled = scooterPullVelocity(scooter, var3, var6.anchor, var6.ropeLength, PeebConfig.server());
                     scooter.breachMomentum(pulled);
                     scooter.setDeltaMovement(pulled);
                     scooter.hasImpulse = true;
                     var1.connection.send(new ClientboundSetEntityMotionPacket(scooter));
                     ((ServerLevel)scooter.level()).getChunkSource().broadcast(scooter, new ClientboundSetEntityMotionPacket(scooter));
                  } else {
                     // Walking client's movement already contains its winch.
                     // Only reject illegal outward/speed travel on the server.
                     Vec3 var5 = constrainVelocity(var3, var4, var6.anchor, var6.ropeLength, PeebConfig.server(), var1.onGround());
                     if (var5.distanceToSqr(var4) > 1.0E-6) {
                        var1.setDeltaMovement(var5);
                        var1.connection.send(new ClientboundSetEntityMotionPacket(var1));
                     }
                  }
               } else {
                  release(var1);
               }
            }
         }
      }
   }

   private static void broadcast(ServerPlayer var0) {
      PacketDistributor.sendToPlayersTrackingEntityAndSelf(var0, snapshot(var0), new CustomPacketPayload[0]);
      Session session = SESSIONS.get(var0.getUUID());
      if (current(var0, session)) {
         session.lastSyncedAnchor = session.anchor;
      }
   }

   private static void clear(ServerPlayer var0) {
      PeebGrapple.Session var1 = SESSIONS.get(var0.getUUID());
      if (var1 != null && var1.player == var0) {
         SESSIONS.remove(var0.getUUID());
         PacketDistributor.sendToAllPlayers(new PeebStatePayload(var0.getUUID(), var0.getId(), false, Optional.empty(), 0.0), new CustomPacketPayload[0]);
      }
   }

   static void login(PlayerLoggedInEvent var0) {
      if (var0.getEntity() instanceof ServerPlayer var1) {
         PeebConfig.send(var1, false);

         for (PeebGrapple.Session var3 : SESSIONS.values()) {
            if (modeActive(var3.player) && current(var3.player, var3)) {
               PacketDistributor.sendToPlayer(var1, snapshot(var3.player), new CustomPacketPayload[0]);
            }
         }
      }
   }

   static void logout(PlayerLoggedOutEvent var0) {
      if (var0.getEntity() instanceof ServerPlayer var1) {
         clear(var1);
      }
   }

   static void dimension(PlayerChangedDimensionEvent var0) {
      if (var0.getEntity() instanceof ServerPlayer var1) {
         clear(var1);
      }
   }

   static void respawn(PlayerRespawnEvent var0) {
      if (var0.getEntity() instanceof ServerPlayer var1) {
         PeebGrapple.Session var3 = SESSIONS.get(var1.getUUID());
         if (var3 != null) {
            clear(var3.player);
         }
      }
   }

   static void tracking(StartTracking var0) {
      if (var0.getEntity() instanceof ServerPlayer var1 && var0.getTarget() instanceof ServerPlayer var2) {
         PacketDistributor.sendToPlayer(var1, snapshot(var2), new CustomPacketPayload[0]);
      }
   }

   static void death(LivingDeathEvent var0) {
      if (var0.getEntity() instanceof ServerPlayer var1) {
         clear(var1);
      }
   }

   static void stopped(ServerStoppedEvent var0) {
      SESSIONS.clear();
      NEXT_CONTACT_ATTACK.clear();
   }

   private static final class Session {
      final ServerPlayer player;
      final ResourceKey<Level> dimension;
      Vec3 anchor;
      BlockPos block;
      UUID targetUuid;
      Vec3 targetOffset;
      Vec3 lastSyncedAnchor;
      boolean contactAttempted;
      UUID movementUuid;
      double ropeLength;
      float attachmentYaw;
      Vec3 previousPosition;
      long lastAttach = -4611686018427387904L;
      long lastPhysicsTick = Long.MIN_VALUE;
      long lastRopeSyncTick;

      Session(ServerPlayer var1) {
         this.player = var1;
         this.dimension = var1.level().dimension();
         Entity movement = movementEntity(var1);
         this.movementUuid = movement.getUUID();
         this.previousPosition = movement.position();
      }
   }
}
