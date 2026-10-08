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
   public static final double RESTING_ROPE_LENGTH = PeebAdventuresPhysics.REST_LENGTH;
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
   public static final float CONTACT_DAMAGE = 6.0F;
   public static final float HOOK_DAMAGE = 2.0F;
   public static final double CONTACT_RANGE = 1.8;
   public static final int CONTACT_COOLDOWN_TICKS = 20;
   private static final Map<UUID, PeebGrapple.Session> SESSIONS = new HashMap<>();
   private static final Map<UUID, Long> NEXT_CONTACT_ATTACK = new HashMap<>();
   private static final Map<UUID, Long> NEXT_HOOK_ATTACK = new HashMap<>();

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
                        var2.previousLook = var0.getLookAngle();
                        var2.lastPhysicsTick = Long.MIN_VALUE;
                        var2.lastRopeSyncTick = var3;
                        hookAttack(var0, target);
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
                        var2.previousLook = var0.getLookAngle();
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
         var1.previousLook = null;
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
            && !(var2 > var7 + Math.max(3.0, var7))
            && Double.isFinite(var5)
            && !(var5 > var7 + Math.max(3.0, var7))
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
      return Double.isFinite(var0) && Double.isFinite(var2) && !(var0 < 0.35) && !(var2 < 0.35) ? Math.min(var2, Math.max(MIN_ROPE_LENGTH, var0)) : 0.0;
   }

   private static boolean validPhysics(Vec3 pivot, Vec3 velocity, Vec3 anchor, double restLength, PeebConfig.Values settings) {
      return pivot != null && velocity != null && anchor != null
         && finite(pivot) && finite(velocity) && finite(anchor)
         && Double.isFinite(restLength) && settings != null && settings.valid()
         && restLength >= 0.0 && restLength <= settings.range();
   }

   /** Frame-independent take-up toward the server's chosen stopping distance. */
   public static double reelLength(double restLength, PeebConfig.Values settings) {
      if (!Double.isFinite(restLength) || settings == null || !settings.valid()) return 0.0;
      return PeebAdventuresPhysics.reel(restLength, settings.range(), settings.stopDistance(), PeebAdventuresPhysics.TICK_SECONDS);
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

   /** Server validation never projects or truncates an existing launch. */
   public static Vec3 constrainVelocity(Vec3 pivot, Vec3 velocity, Vec3 anchor, double restLength, PeebConfig.Values settings, boolean grounded) {
      return validPhysics(pivot, velocity, anchor, restLength, settings) ? velocity : Vec3.ZERO;
   }

   /** Peeb's original elastic force, with an acceleration budget only. */
   public static Vec3 pullVelocity(Vec3 pivot, Vec3 velocity, Vec3 anchor, double restLength, PeebConfig.Values settings, boolean grounded) {
      if (!validPhysics(pivot, velocity, anchor, restLength, settings)) return Vec3.ZERO;
      Vec3 pulled = PeebAdventuresPhysics.pull(pivot, velocity, anchor, restLength,
         settings.strength() / PeebConfig.DEFAULT.strength(), 1.0, PeebAdventuresPhysics.TICK_SECONDS);
      return PeebAdventuresPhysics.addWithinBudget(velocity, pulled, settings.maxHorizontalSpeed() * 0.25, settings.maxSpeed() * 0.25);
   }

   /** Client-owned vehicles report actual travel, not their server-side motion field.
    * Add the hook force to that observed momentum; never replace it with the
    * small server correction from the preceding tick.
    */
   public static Vec3 scooterPullVelocity(Vec3 velocity, Vec3 pivot, Vec3 anchor, double restLength,
                                         double cruiseSpeed, PeebConfig.Values settings) {
      if (!validPhysics(pivot, velocity, anchor, restLength, settings)) return velocity;
      double horizontalCap = Math.max(1.2, Math.min(3.0, cruiseSpeed * 1.2));
      double totalCap = Math.max(1.8, horizontalCap + 0.8);
      Vec3 pulled = PeebAdventuresPhysics.pull(pivot, velocity, anchor, restLength,
         settings.strength() / PeebConfig.DEFAULT.strength(), 0.5, PeebAdventuresPhysics.TICK_SECONDS);
      return PeebAdventuresPhysics.addWithinBudget(velocity, pulled, horizontalCap * 0.15, totalCap * 0.15);
   }

   /** Actual body bounds, rather than proximity to the tusk, trigger the impact. */
   public static boolean bodyContact(AABB playerBounds, AABB targetBounds) {
      return playerBounds != null && targetBounds != null && playerBounds.inflate(0.1).intersects(targetBounds);
   }

   /** Hook and body keep independent player cooldowns across reattachments. */
   private static boolean claimAttack(Map<UUID, Long> cooldowns, UUID player, long now) {
      if (now < cooldowns.getOrDefault(player, Long.MIN_VALUE)) return false;
      cooldowns.put(player, now + CONTACT_COOLDOWN_TICKS);
      return true;
   }

   private static void hookAttack(ServerPlayer player, LivingEntity enemy) {
      if (allowedTarget(player, enemy) && claimAttack(NEXT_HOOK_ATTACK, player.getUUID(), player.level().getGameTime())) {
         enemy.hurt(player.damageSources().playerAttack(player), HOOK_DAMAGE);
      }
   }

   private static void contactAttack(ServerPlayer player, Session session) {
      LivingEntity enemy = target(player, session);
      if (session.contactAttempted || enemy == null
          || !bodyContact(player.getBoundingBox(), enemy.getBoundingBox())) return;
      // A collision consumes this attachment's one body strike even when the
      // independent player cooldown suppresses it. Reattach cannot bypass it.
      session.contactAttempted = true;
      if (claimAttack(NEXT_CONTACT_ATTACK, player.getUUID(), player.level().getGameTime())) {
         enemy.hurt(player.damageSources().playerAttack(player), CONTACT_DAMAGE);
      }
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
               var6.previousLook = var1.getLookAngle();
            }
            Vec3 var4 = movement.position().subtract(var6.previousPosition);
            var6.previousPosition = movement.position();
            if (var6.anchor != null) {
               if (validTether(var1) && finite(var4) && !(var4.lengthSqr() > 16.0)) {
                  double previousLength = var6.ropeLength;
                  var6.ropeLength = reelLength(previousLength, PeebConfig.server());
                  if ((previousLength != var6.ropeLength || var6.lastSyncedAnchor == null || var6.lastSyncedAnchor.distanceToSqr(var6.anchor) > 1.0E-6)
                     && (physicsTick - var6.lastRopeSyncTick >= ROPE_SYNC_INTERVAL_TICKS
                     || var6.ropeLength == PeebAdventuresPhysics.targetLength(PeebConfig.server().range(), PeebConfig.server().stopDistance()))) {
                     var6.lastRopeSyncTick = physicsTick;
                     broadcast(var1);
                  }
                  contactAttack(var1, var6);
                  if (movement instanceof Scooter scooter) {
                     // The rider client skips the additive Peeb winch. The root
                     // scooter receives one authoritative server impulse instead.
                     Vec3 pulled = scooterPullVelocity(var4, var3, var6.anchor, var6.ropeLength, scooter.cruiseSpeed(), PeebConfig.server());
                     pulled = pulled.add(PeebAdventuresPhysics.cameraTug(var3, var6.previousLook, var1.getLookAngle(), var6.anchor, var6.ropeLength));
                     scooter.breachMomentum(pulled);
                     scooter.setDeltaMovement(pulled);
                     scooter.hasImpulse = true;
                     var1.connection.send(new ClientboundSetEntityMotionPacket(scooter));
                     ((ServerLevel)scooter.level()).getChunkSource().broadcast(scooter, new ClientboundSetEntityMotionPacket(scooter));
                  } else {
                     // Walking client's movement already contains its winch.
                     // Preserve observed travel; the client applied its one elastic force.
                     Vec3 var5 = constrainVelocity(var3, var4, var6.anchor, var6.ropeLength, PeebConfig.server(), var1.onGround());
                     if (var5.distanceToSqr(var4) > 1.0E-6) {
                        var1.setDeltaMovement(var5);
                        var1.connection.send(new ClientboundSetEntityMotionPacket(var1));
                     }
                  }
               } else {
                  release(var1);
               }
               var6.previousLook = var1.getLookAngle();
            } else {
               var6.previousLook = null;
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
      NEXT_HOOK_ATTACK.clear();
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
      Vec3 previousLook;
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
