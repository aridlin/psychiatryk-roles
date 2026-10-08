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
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
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
   private static final Map<UUID, PeebGrapple.Session> SESSIONS = new HashMap<>();

   private PeebGrapple() {
   }

   private static boolean modeActive(ServerPlayer var0) {
      return PeebMode.holding(var0) && var0.isAlive() && !var0.isRemoved() && !var0.hasDisconnected();
   }

   private static boolean canGrapple(ServerPlayer var0) {
      return modeActive(var0)
         && !var0.isPassenger()
         && !var0.isSpectator()
         && !var0.isChangingDimension()
         && !var0.isSleeping()
         && !var0.isFallFlying()
         && !var0.getAbilities().flying
         && var0.getServer() != null
         && var0.getServer().getPlayerList().getPlayer(var0.getUUID()) == var0;
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
                           var2.ropeLength = initialRopeLength(var12, var5);
                           var2.attachmentYaw = var7;
                           var2.previousPosition = var0.position();
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
         var1.ropeLength = 0.0;
         var1.previousPosition = var0.position();
         broadcast(var0);
      }
   }

   public static boolean validTether(ServerPlayer var0) {
      PeebGrapple.Session var1 = SESSIONS.get(var0.getUUID());
      if (canGrapple(var0) && current(var0, var1) && var1.anchor != null) {
         double var2 = var0.getEyePosition().distanceTo(var1.anchor);
         Vec3 var4 = PeebAttachment.pivot(var0, var1.attachmentYaw);
         double var5 = var4.distanceTo(var1.anchor);
         double var7 = PeebConfig.server().range();
         if (Double.isFinite(var2)
            && !(var2 > var7 + TETHER_RANGE_EPSILON)
            && Double.isFinite(var5)
            && !(var5 > var7 + TETHER_RANGE_EPSILON)
            && var0.level().hasChunkAt(var1.block)) {
            BlockHitResult var9 = trace(var0, var1.anchor);
            if (var9.getType() == Type.BLOCK
               && !var9.isInside()
               && var9.getBlockPos().equals(var1.block)
               && !(var9.getLocation().distanceTo(var1.anchor) >= 0.06)) {
               BlockHitResult var10 = trace(var0, var4, var1.anchor);
               return var10.getType() == Type.BLOCK
                  && !var10.isInside()
                  && var10.getBlockPos().equals(var1.block)
                  && var10.getLocation().distanceTo(var1.anchor) < 0.06;
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
            Vec3 var4 = var1.position().subtract(var6.previousPosition);
            var6.previousPosition = var1.position();
            if (var6.anchor != null) {
               if (validTether(var1) && finite(var4) && !(var4.lengthSqr() > 16.0)) {
                  double previousLength = var6.ropeLength;
                  var6.ropeLength = reelLength(previousLength, PeebConfig.server());
                  if (previousLength != var6.ropeLength && (physicsTick - var6.lastRopeSyncTick >= ROPE_SYNC_INTERVAL_TICKS
                     || var6.ropeLength == Math.min(RESTING_ROPE_LENGTH, PeebConfig.server().range()))) {
                     var6.lastRopeSyncTick = physicsTick;
                     broadcast(var1);
                  }
                  // Owning-client movement already contains winch pull. Only
                  // reject illegal outward/speed travel here, never add force.
                  Vec3 var5 = constrainVelocity(var3, var4, var6.anchor, var6.ropeLength, PeebConfig.server(), var1.onGround());
                  if (var5.distanceToSqr(var4) > 1.0E-6) {
                     var1.setDeltaMovement(var5);
                     var1.connection.send(new ClientboundSetEntityMotionPacket(var1));
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
   }

   private static final class Session {
      final ServerPlayer player;
      final ResourceKey<Level> dimension;
      Vec3 anchor;
      BlockPos block;
      double ropeLength;
      float attachmentYaw;
      Vec3 previousPosition;
      long lastAttach = -4611686018427387904L;
      long lastPhysicsTick = Long.MIN_VALUE;
      long lastRopeSyncTick;

      Session(ServerPlayer var1) {
         this.player = var1;
         this.dimension = var1.level().dimension();
         this.previousPosition = var1.position();
      }
   }
}
