package pl.aridlin.psychiatrykroles.peeb;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Properties;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent.Post;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class PeebConfig {
   private static final Logger LOGGER = LoggerFactory.getLogger("Psychiatryk Peeb");
   public static final String FILE_NAME = "psychiatryk-peeb.properties";
   public static final double MIN_RANGE = 1.0;
   public static final double MAX_RANGE = 32.0;
   public static final double MIN_HORIZONTAL_SPEED = 0.1;
   public static final double MAX_HORIZONTAL_SPEED = 0.7;
   public static final double MIN_SPEED = 0.1;
   public static final double MAX_SPEED = 1.2;
   public static final double MIN_STRENGTH = 0.05;
   public static final double MAX_STRENGTH = 1.0;
   public static final double MIN_STOP_DISTANCE = 0.0;
   public static final double MAX_STOP_DISTANCE = 32.0;
   public static final PeebConfig.Values DEFAULT = new PeebConfig.Values(8.0, 0.4, 0.8, 0.3, true, 0.0, false);
   private static PeebConfig.Values current = DEFAULT;
   private static PeebConfig.Values sent;
   private static long nextPoll;
   private static long stamp = Long.MIN_VALUE;
   private static long size = Long.MIN_VALUE;

   private PeebConfig() {
   }

   public static Path file() {
      return FMLPaths.CONFIGDIR.get().resolve("psychiatryk-peeb.properties");
   }

   private static double clamp(double var0, double var2, double var4) {
      return Math.max(var2, Math.min(var4, var0));
   }

   public static synchronized PeebConfig.Values server() {
      long var0 = System.currentTimeMillis();
      if (var0 < nextPoll) {
         return current;
      } else {
         nextPoll = var0 + 1000L;

         try {
            Path var2 = file();
            long var3 = Files.exists(var2) ? Files.getLastModifiedTime(var2).toMillis() : 0L;
            long var5 = Files.exists(var2) ? Files.size(var2) : 0L;
            if (var3 != stamp || var5 != size) {
               try {
                  reload();
               } catch (Exception var8) {
                  stamp = var3;
                  size = var5;
                  LOGGER.warn("Invalid Peeb settings; retaining current values", var8);
               }
            }
         } catch (Exception var9) {
            LOGGER.warn("Cannot reload Peeb settings; retaining current values", var9);
         }

         return current;
      }
   }

   public static synchronized PeebConfig.Values reload() throws IOException {
      Path var0 = file();
      if (!Files.exists(var0)) {
         current = DEFAULT;
         stamp = 0L;
         size = 0L;
         return current;
      } else {
         Properties var1 = new Properties();

         try (InputStream var2 = Files.newInputStream(var0)) {
            var1.load(var2);
         }

         PeebConfig.Values var7 = new PeebConfig.Values(
               number(var1, "range", DEFAULT.range()),
               number(var1, "maxHorizontalSpeed", DEFAULT.maxHorizontalSpeed()),
               number(var1, "maxSpeed", DEFAULT.maxSpeed()),
               number(var1, "strength", DEFAULT.strength()),
               bool(var1, "fallImmunity", DEFAULT.fallImmunity()),
               number(var1, "stopDistance", DEFAULT.stopDistance()),
               bool(var1, "grappleStep", DEFAULT.grappleStep())
            )
            .clamped();
         current = var7;
         stamp = Files.getLastModifiedTime(var0).toMillis();
         size = Files.size(var0);
         return current;
      }
   }

   private static double number(Properties var0, String var1, double var2) {
      return Double.parseDouble(var0.getProperty(var1, Double.toString(var2)));
   }

   private static boolean bool(Properties var0, String var1, boolean var2) {
      String var3 = var0.getProperty(var1, Boolean.toString(var2)).trim();
      if (!var3.equalsIgnoreCase("true") && !var3.equalsIgnoreCase("false")) {
         throw new IllegalArgumentException("Invalid boolean " + var1);
      } else {
         return Boolean.parseBoolean(var3);
      }
   }

   public static synchronized void save(PeebConfig.Values var0) throws IOException {
      if (!var0.valid()) {
         throw new IllegalArgumentException("Invalid Peeb settings");
      } else {
         Path var1 = file();
         Files.createDirectories(var1.getParent());
         Path var2 = Files.createTempFile(var1.getParent(), "psychiatryk-peeb-", ".tmp");

         try {
            Files.writeString(
               var2,
               "# Held Peeb; /scooteradmin -> Peeb. Reloads live, no restart required.\n# Safe bounds: range 1..32, horizontal 0.10..0.70, speed 0.10..1.20, strength 0.05..1.0, stopDistance 0..32 blocks.\n# stopDistance=0 pulls to the attachment point; block collision still applies.\n# grappleStep enables collision-safe 1.3-block ground/midair steps only while hooked.\nrange="
                  + var0.range()
                  + "\nmaxHorizontalSpeed="
                  + var0.maxHorizontalSpeed()
                  + "\nmaxSpeed="
                  + var0.maxSpeed()
                  + "\nstrength="
                  + var0.strength()
                  + "\nfallImmunity="
                  + var0.fallImmunity()
                  + "\nstopDistance="
                  + var0.stopDistance()
                  + "\ngrappleStep="
                  + var0.grappleStep()
                  + "\n"
            );

            try {
               Files.move(var2, var1, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException var7) {
               Files.move(var2, var1, StandardCopyOption.REPLACE_EXISTING);
            }

            current = var0;
            stamp = Files.getLastModifiedTime(var1).toMillis();
            size = Files.size(var1);
         } finally {
            Files.deleteIfExists(var2);
         }
      }
   }

   public static void send(ServerPlayer var0, boolean var1) {
      PacketDistributor.sendToPlayer(var0, PeebConfigPayload.of(var1, server()), new CustomPacketPayload[0]);
   }

   public static void broadcast(MinecraftServer var0) {
      sent = server();
      PacketDistributor.sendToAllPlayers(PeebConfigPayload.of(false, sent), new CustomPacketPayload[0]);
   }

   static void started(ServerStartedEvent var0) {
      try {
         if (!Files.exists(file())) {
            save(DEFAULT);
         } else {
            reload();
         }
      } catch (Exception var2) {
         LOGGER.warn("Cannot initialize Peeb settings; using safe current values", var2);
      }

      broadcast(var0.getServer());
   }

   static void tick(Post var0) {
      PeebConfig.Values var1 = server();
      if (!var1.equals(sent)) {
         broadcast(var0.getServer());
      }
   }

   static void fall(LivingFallEvent var0) {
      if (var0.getEntity() instanceof ServerPlayer var1 && PeebMode.holding(var1) && server().fallImmunity()) {
         var0.setCanceled(true);
      }
   }

   public static record Values(double range, double maxHorizontalSpeed, double maxSpeed, double strength, boolean fallImmunity, double stopDistance, boolean grappleStep) {
      /** Preserve the constructor descriptor used by earlier compiled callers. */
      public Values(double range, double maxHorizontalSpeed, double maxSpeed, double strength, boolean fallImmunity) {
         this(range, maxHorizontalSpeed, maxSpeed, strength, fallImmunity, 0.0, false);
      }
      public Values(double range, double maxHorizontalSpeed, double maxSpeed, double strength, boolean fallImmunity, double stopDistance) {
         this(range, maxHorizontalSpeed, maxSpeed, strength, fallImmunity, stopDistance, false);
      }
      public boolean valid() {
         return bounded(this.range, 1.0, 32.0)
            && bounded(this.maxHorizontalSpeed, 0.1, 0.7)
            && bounded(this.maxSpeed, 0.1, 1.2)
            && bounded(this.strength, 0.05, 1.0)
            && bounded(this.stopDistance, MIN_STOP_DISTANCE, MAX_STOP_DISTANCE);
      }

      private static boolean bounded(double var0, double var2, double var4) {
         return Double.isFinite(var0) && var0 >= var2 && var0 <= var4;
      }

      public PeebConfig.Values clamped() {
         if (Double.isFinite(this.range) && Double.isFinite(this.maxHorizontalSpeed) && Double.isFinite(this.maxSpeed) && Double.isFinite(this.strength) && Double.isFinite(this.stopDistance)) {
            return new PeebConfig.Values(
               PeebConfig.clamp(this.range, 1.0, 32.0),
               PeebConfig.clamp(this.maxHorizontalSpeed, 0.1, 0.7),
               PeebConfig.clamp(this.maxSpeed, 0.1, 1.2),
               PeebConfig.clamp(this.strength, 0.05, 1.0),
               this.fallImmunity,
               PeebConfig.clamp(this.stopDistance, MIN_STOP_DISTANCE, MAX_STOP_DISTANCE),
               this.grappleStep
            );
         } else {
            throw new IllegalArgumentException("Peeb settings must be finite");
         }
      }
   }
}
