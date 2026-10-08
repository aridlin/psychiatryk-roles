package pl.aridlin.kukirin;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.LoggerFactory;

public final class ScooterMusicFavorites {
   private static final int LIMIT = 1024;
   private static final int MAX_BYTES = 262144;
   private static UUID owner;
   private static Path file;
   private static Set<String> favorites = new TreeSet<>();

   static synchronized void select(UUID var0) {
      if (!Objects.equals(owner, var0)) {
         owner = var0;
         favorites = new TreeSet<>();
         file = var0 == null ? null : FMLPaths.CONFIGDIR.get().resolve("goplanska-scooter/music-favorites-" + var0 + ".json");
         if (file != null) {
            try {
               favorites = read(file);
            } catch (Exception var2) {
               LoggerFactory.getLogger("ScooterMusic").warn("Local music favorites rejected ({}).", var2.getClass().getSimpleName());
            }
         }
      }
   }

   static Set<String> read(Path var0) throws IOException {
      if (!Files.exists(var0, LinkOption.NOFOLLOW_LINKS)) {
         return new TreeSet<>();
      } else if (!Files.isSymbolicLink(var0) && Files.isRegularFile(var0, LinkOption.NOFOLLOW_LINKS) && Files.size(var0) <= 262144L) {
         JsonObject var1;
         try (BufferedReader var2 = Files.newBufferedReader(var0)) {
            var1 = JsonParser.parseReader(var2).getAsJsonObject();
         }

         if (var1.get("version").getAsInt() != 1) {
            throw new IOException("Favorites version");
         } else {
            JsonArray var9 = var1.getAsJsonArray("songs");
            if (var9.size() > 1024) {
               throw new IOException("Favorites count");
            } else {
               TreeSet var3 = new TreeSet();

               for (JsonElement var5 : var9) {
                  String var6 = var5.getAsString();
                  if (!ScooterMusicSources.validName(var6) || !var3.add(var6)) {
                     throw new IOException("Favorites names");
                  }
               }

               return var3;
            }
         }
      } else {
         throw new IOException("Favorites file bounds");
      }
   }

   static void write(Path var0, Set<String> var1) throws IOException {
      if (var1.size() > 1024 || var1.stream().anyMatch(var0x -> !ScooterMusicSources.validName(var0x))) {
         throw new IOException("Favorites values");
      } else if (Files.isSymbolicLink(var0)) {
         throw new IOException("Favorites symlink");
      } else {
         JsonObject var2 = new JsonObject();
         var2.addProperty("version", 1);
         JsonArray var3 = new JsonArray();
         new TreeSet<String>(var1).forEach(var3::add);
         var2.add("songs", var3);
         String var4 = new GsonBuilder().setPrettyPrinting().create().toJson(var2) + "\n";
         if (var4.getBytes(StandardCharsets.UTF_8).length > 262144) {
            throw new IOException("Favorites byte limit");
         } else {
            Files.createDirectories(var0.getParent());
            Path var5 = Files.createTempFile(var0.getParent(), ".music-favorites-", ".tmp");

            try {
               Files.writeString(var5, var4);
               Files.move(var5, var0, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } finally {
               Files.deleteIfExists(var5);
            }
         }
      }
   }

   static synchronized boolean contains(String var0) {
      return favorites.contains(var0);
   }

   static synchronized boolean toggle(String var0) {
      if (file != null && ScooterMusicSources.validName(var0)) {
         TreeSet var1 = new TreeSet<>(favorites);
         if (!var1.remove(var0)) {
            if (var1.size() >= 1024) {
               return false;
            }

            var1.add(var0);
         }

         try {
            write(file, var1);
            favorites = var1;
            return true;
         } catch (Exception var3) {
            LoggerFactory.getLogger("ScooterMusic").warn("Local music favorites could not be saved ({}).", var3.getClass().getSimpleName());
            return false;
         }
      } else {
         return false;
      }
   }

   private ScooterMusicFavorites() {
   }
}
