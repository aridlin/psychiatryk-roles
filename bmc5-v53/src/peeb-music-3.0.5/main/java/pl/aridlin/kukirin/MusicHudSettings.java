package pl.aridlin.kukirin;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import net.neoforged.fml.loading.FMLPaths;

public final class MusicHudSettings {
   public static final MusicHudSettings.Values DEFAULT = new MusicHudSettings.Values(
      true, true, true, MusicHudSettings.Corner.TOP_RIGHT, MusicHudSettings.Corner.BOTTOM_RIGHT, 5
   );
   private static MusicHudSettings.Values value;

   public static Path file() {
      return FMLPaths.CONFIGDIR.get().resolve("psychiatryk-now-playing.json");
   }

   public static MusicHudSettings.Values get() {
      if (value == null) {
         reload();
      }

      return value;
   }

   public static void reload() {
      value = DEFAULT;
      Path var0 = file();

      try {
         if (!Files.exists(var0)) {
            return;
         }

         if (Files.isSymbolicLink(var0) || Files.size(var0) > 4096L) {
            return;
         }

         JsonObject var1 = JsonParser.parseString(Files.readString(var0)).getAsJsonObject();
         if (var1.get("version").getAsInt() != 1) {
            return;
         }

         value = new MusicHudSettings.Values(
            flag(var1, "hud", true),
            flag(var1, "popup", true),
            flag(var1, "artwork", true),
            corner(var1, "hudCorner", MusicHudSettings.Corner.TOP_RIGHT),
            corner(var1, "popupCorner", MusicHudSettings.Corner.BOTTOM_RIGHT),
            var1.has("popupSeconds") ? var1.get("popupSeconds").getAsInt() : 5
         );
      } catch (Exception var2) {
         value = DEFAULT;
      }
   }

   private static boolean flag(JsonObject var0, String var1, boolean var2) {
      return var0.has(var1) ? var0.get(var1).getAsBoolean() : var2;
   }

   private static MusicHudSettings.Corner corner(JsonObject var0, String var1, MusicHudSettings.Corner var2) {
      return var0.has(var1) ? MusicHudSettings.Corner.valueOf(var0.get(var1).getAsString().toUpperCase(Locale.ROOT)) : var2;
   }

   public static void save(MusicHudSettings.Values var0) throws IOException {
      Path var1 = file();
      if (Files.isSymbolicLink(var1)) {
         throw new IOException("Music HUD settings symlink");
      } else {
         Files.createDirectories(var1.getParent());
         JsonObject var2 = new JsonObject();
         var2.addProperty("version", 1);
         var2.addProperty("hud", var0.hud());
         var2.addProperty("popup", var0.popup());
         var2.addProperty("artwork", var0.artwork());
         var2.addProperty("hudCorner", var0.hudCorner().name().toLowerCase(Locale.ROOT));
         var2.addProperty("popupCorner", var0.popupCorner().name().toLowerCase(Locale.ROOT));
         var2.addProperty("popupSeconds", var0.popupSeconds());
         Path var3 = Files.createTempFile(var1.getParent(), ".now-playing-", ".tmp");

         try {
            Files.writeString(var3, new GsonBuilder().setPrettyPrinting().create().toJson(var2) + "\n");
            Files.move(var3, var1, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            value = var0;
         } finally {
            Files.deleteIfExists(var3);
         }
      }
   }

   private MusicHudSettings() {
   }

   public static enum Corner {
      TOP_RIGHT,
      TOP_LEFT,
      BOTTOM_RIGHT,
      BOTTOM_LEFT;
   }

   public static record Values(
      boolean hud, boolean popup, boolean artwork, MusicHudSettings.Corner hudCorner, MusicHudSettings.Corner popupCorner, int popupSeconds
   ) {
      public Values(boolean hud, boolean popup, boolean artwork, MusicHudSettings.Corner hudCorner, MusicHudSettings.Corner popupCorner, int popupSeconds) {
         popupSeconds = Math.clamp((long)popupSeconds, 3, 10);
         this.hud = hud;
         this.popup = popup;
         this.artwork = artwork;
         this.hudCorner = hudCorner;
         this.popupCorner = popupCorner;
         this.popupSeconds = popupSeconds;
      }
   }
}
