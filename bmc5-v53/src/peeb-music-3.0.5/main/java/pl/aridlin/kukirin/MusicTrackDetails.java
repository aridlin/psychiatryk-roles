package pl.aridlin.kukirin;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedReader;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class MusicTrackDetails {
   private static volatile Map<String, MusicTrackDetails.Row> rows = Map.of();
   private static final Map<String, byte[]> covers = new LinkedHashMap<>(8, 0.75F, true);
   private static final Set<String> loading = ConcurrentHashMap.newKeySet();
   private static final AtomicBoolean started = new AtomicBoolean();
   private static final ScheduledThreadPoolExecutor io = new ScheduledThreadPoolExecutor(1, var0 -> {
      Thread var1 = new Thread(var0, "Music metadata");
      var1.setDaemon(true);
      return var1;
   });
   private static long stamp = -1L;
   private static long size = -1L;

   public static boolean png64(byte[] var0) {
      return MusicNowPlaying.validArtwork(var0);
   }

   private static String text(JsonObject var0, String var1, int var2) {
      return var0.has(var1) && !var0.get(var1).isJsonNull() ? MusicNowPlaying.text(var0.get(var1).getAsString(), var2) : "";
   }

   public static MusicTrackDetails.Details get(String var0, String var1) {
      if (started.compareAndSet(false, true)) {
         io.scheduleWithFixedDelay(MusicTrackDetails::reload, 0L, 5L, TimeUnit.SECONDS);
      }

      MusicTrackDetails.Row var2 = rows.get(var0);
      if (var2 == null) {
         return new MusicTrackDetails.Details(MusicNowPlaying.fallbackTitle(var1), "", "", "", new byte[0]);
      } else {
         byte[] var3 = new byte[0];
         if (!var2.art.isEmpty()) {
            synchronized (covers) {
               byte[] var5 = covers.get(var2.art);
               if (var5 != null) {
                  var3 = var5;
               }
            }

            if (var3.length == 0 && loading.size() < 8 && loading.add(var2.art)) {
               io.execute(() -> loadCover(var2.art));
            }
         }

         return new MusicTrackDetails.Details(
            var2.title.isEmpty() ? MusicNowPlaying.fallbackTitle(var1) : var2.title, var2.artist, var2.album, var3.length == 0 ? "" : var2.art, var3
         );
      }
   }

   private static void loadCover(String var0) {
      try {
         Path var1 = MusicImports.artDirectory().resolve(var0 + ".png");
         if (!Files.isRegularFile(var1, LinkOption.NOFOLLOW_LINKS) || Files.size(var1) > 16384L) {
            return;
         }

         byte[] var2 = Files.readAllBytes(var1);
         if (png64(var2) && HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(var2)).equals(var0)) {
            synchronized (covers) {
               covers.put(var0, var2);

               while (covers.size() > 8) {
                  covers.remove(covers.keySet().iterator().next());
               }

               return;
            }
         }
      } catch (Exception var10) {
         return;
      } finally {
         loading.remove(var0);
      }
   }

   static synchronized void reload() {
      try {
         Path var0 = MusicImports.catalogFile();
         if (!Files.isRegularFile(var0, LinkOption.NOFOLLOW_LINKS)) {
            rows = Map.of();
            size = -1L;
            stamp = -1L;
            return;
         }

         long var1 = Files.getLastModifiedTime(var0, LinkOption.NOFOLLOW_LINKS).toMillis();
         long var3 = Files.size(var0);
         if (var1 == stamp && var3 == size) {
            return;
         }

         if (var3 > 1048576L) {
            return;
         }

         JsonObject var5;
         try (BufferedReader var6 = Files.newBufferedReader(var0)) {
            var5 = JsonParser.parseReader(var6).getAsJsonObject();
         }

         JsonArray var16 = var5.getAsJsonArray("songs");
         if (var16 == null || var16.size() > 1024) {
            return;
         }

         HashMap var7 = new HashMap();

         for (JsonElement var9 : var16) {
            JsonObject var10 = var9.getAsJsonObject();
            String var11 = text(var10, "id", 256);
            if (ScooterMusicSources.validName(var11)) {
               String var12 = text(var10, "artworkSha256", 64);
               if (!var12.matches("[a-f0-9]{64}")) {
                  var12 = "";
               }

               var7.put(var11, new MusicTrackDetails.Row(text(var10, "title", 160), text(var10, "artist", 120), text(var10, "album", 120), var12));
            }
         }

         rows = Map.copyOf(var7);
         stamp = var1;
         size = var3;
      } catch (Exception var15) {
      }
   }

   private MusicTrackDetails() {
   }

   public static record Details(String title, String artist, String album, String artworkSha, byte[] artwork) {
      public Details(String title, String artist, String album, String artworkSha, byte[] artwork) {
         artwork = (byte[])artwork.clone();
         this.title = title;
         this.artist = artist;
         this.album = album;
         this.artworkSha = artworkSha;
         this.artwork = artwork;
      }

      public byte[] artwork() {
         return (byte[])this.artwork.clone();
      }
   }

   private static record Row(String title, String artist, String album, String art) {
   }
}
