package pl.aridlin.kukirin;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.System.Logger.Level;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public final class RentalConfig {
   private final Path file;
   private long lastModified = -1L;
   private RentalConfig.Settings settings = new RentalConfig.Settings(true, 4, 40, 10L, 600, 0.02, 0.02);

   public RentalConfig(Path var1) {
      this.file = var1;
   }

   public void setSpawning(boolean var1) {
      this.get();

      try {
         String var2 = Files.readString(this.file).replaceAll("(?m)^spawning=.*$", "spawning=" + var1);
         Files.writeString(this.file, var2);
         this.lastModified = -1L;
      } catch (IOException var3) {
         throw new UncheckedIOException(var3);
      }
   }

   public RentalConfig.Settings get() {
      try {
         if (!Files.exists(this.file)) {
            Files.createDirectories(this.file.getParent());
            Files.writeString(
               this.file,
               "# Rental scooters; rare zombie jockeys with bounded loaded-chunk spawning.\nspawning=true\nperPlayer=4\nmaxTotal=40\npriceChips=10\nspawnIntervalTicks=600\nzombieChance=0.02\nfrontDoorChance=0.02\n"
            );
         }

         long var1 = Files.getLastModifiedTime(this.file).toMillis();
         if (var1 != this.lastModified) {
            Properties var3 = new Properties();

            try (BufferedReader var4 = Files.newBufferedReader(this.file)) {
               var3.load(var4);
            }

            String var10 = var3.getProperty("spawning", "true");
            if (!var10.equals("true") && !var10.equals("false")) {
               throw new IllegalArgumentException("spawning must be true or false");
            }

            RentalConfig.Settings var5 = new RentalConfig.Settings(
               Boolean.parseBoolean(var10),
               range(var3, "perPlayer", 4, 0, 12),
               range(var3, "maxTotal", 40, 0, 200),
               (long)range(var3, "priceChips", 10, 1, 1000000),
               range(var3, "spawnIntervalTicks", 600, 100, 72000),
               chance(var3, "zombieChance", 0.02),
               chance(var3, "frontDoorChance", 0.02)
            );
            this.settings = var5;
            this.lastModified = var1;
         }
      } catch (Exception var9) {
         System.getLogger("RentalConfig").log(Level.WARNING, "Keeping previous rental configuration: " + var9.getMessage());
      }

      return this.settings;
   }

   private static int range(Properties var0, String var1, int var2, int var3, int var4) {
      int var5 = Integer.parseInt(var0.getProperty(var1, var2 + ""));
      if (var5 >= var3 && var5 <= var4) {
         return var5;
      } else {
         throw new IllegalArgumentException(var1 + " outside range");
      }
   }

   private static double chance(Properties var0, String var1, double var2) {
      double var4 = Double.parseDouble(var0.getProperty(var1, var2 + ""));
      if (Double.isFinite(var4) && !(var4 < 0.0) && !(var4 > 1.0)) {
         return var4;
      } else {
         throw new IllegalArgumentException(var1 + " outside 0..1");
      }
   }

   public static record Settings(boolean spawning, int perPlayer, int total, long price, int interval, double zombieChance, double doorChance) {
   }
}
