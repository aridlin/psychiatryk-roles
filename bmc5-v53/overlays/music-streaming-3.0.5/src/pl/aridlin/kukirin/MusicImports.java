package pl.aridlin.kukirin;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpClient.Redirect;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpRequest.Builder;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadPoolExecutor.AbortPolicy;
import java.util.function.Consumer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.EventBusSubscriber.Bus;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@EventBusSubscriber(
   modid = "goplanska_kukirin",
   bus = Bus.MOD
)
public final class MusicImports {
   private static final Map<UUID, UUID> active = new HashMap<>();
   private static final Map<UUID, Long> lastRequest = new HashMap<>();
   private static final ThreadPoolExecutor io = new ThreadPoolExecutor(2, 2, 30L, TimeUnit.SECONDS, new ArrayBlockingQueue<>(6), var0 -> {
      Thread var1 = new Thread(var0, "YouTube music import");
      var1.setDaemon(true);
      return var1;
   }, new AbortPolicy());
   private static final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10L)).followRedirects(Redirect.NEVER).build();
   private static final Object catalogLock = new Object();
   private static Consumer<MusicImports.Status> clientReceiver = var0 -> {
   };
   private static Consumer<ScooterMusicMenu.Catalog> completedReceiver = var0 -> {
   };

   static Path catalogFile() {
      return FMLPaths.CONFIGDIR.get().resolve("goplanska-scooter/music-sources.json");
   }

   static Path artDirectory() {
      return catalogFile().getParent().resolve("cover-art");
   }

   public static void clientHandler(Consumer<MusicImports.Status> var0) {
      clientReceiver = Objects.requireNonNull(var0);
   }

   public static void completedHandler(Consumer<ScooterMusicMenu.Catalog> var0) {
      completedReceiver = Objects.requireNonNull(var0);
   }

   @SubscribeEvent
   public static void register(RegisterPayloadHandlersEvent var0) {
      PayloadRegistrar var1 = var0.registrar("1");
      var1.playToServer(MusicImports.Request.TYPE, MusicImports.Request.CODEC, (var0x, var1x) -> var1x.enqueueWork(() -> {
            if (var1x.player() instanceof ServerPlayer var2) {
               start(var2, var0x.url, var0x.playlist);
            }
         }));
      var1.playToClient(MusicImports.Status.TYPE, MusicImports.Status.CODEC, (var0x, var1x) -> var1x.enqueueWork(() -> clientReceiver.accept(var0x)));
      var1.playToClient(
         MusicImports.Completed.TYPE, MusicImports.Completed.CODEC, (var0x, var1x) -> var1x.enqueueWork(() -> completedReceiver.accept(var0x.catalog))
      );
   }

   static String canonical(String var0, boolean var1) {
      URI var2 = URI.create(var0.strip());
      String var3 = var2.getHost();
      if ("https".equals(var2.getScheme()) && var3 != null && var2.getUserInfo() == null && var2.getFragment() == null && var2.getPort() == -1) {
         var3 = var3.toLowerCase(Locale.ROOT);
         if (!Set.of("youtube.com", "www.youtube.com", "music.youtube.com", "youtu.be").contains(var3)) {
            throw new IllegalArgumentException();
         } else {
            HashMap var4 = new HashMap();
            if (var2.getRawQuery() != null) {
               for (String var8 : var2.getRawQuery().split("&")) {
                  String[] var9 = var8.split("=", 2);
                  var4.put(URLDecoder.decode(var9[0], StandardCharsets.UTF_8), var9.length == 2 ? URLDecoder.decode(var9[1], StandardCharsets.UTF_8) : "");
               }
            }

            if (var1) {
               String var12 = (String)var4.get("list");
               if (var12 != null && var12.matches("[A-Za-z0-9_-]{10,150}")) {
                  return "https://music.youtube.com/playlist?list=" + var12;
               } else {
                  throw new IllegalArgumentException();
               }
            } else {
               String var11 = var3.equals("youtu.be") ? var2.getPath().substring(1) : (String)var4.get("v");
               if (var11 != null && var11.matches("[A-Za-z0-9_-]{11}")) {
                  return "https://music.youtube.com/watch?v=" + var11;
               } else {
                  throw new IllegalArgumentException();
               }
            }
         }
      } else {
         throw new IllegalArgumentException();
      }
   }

   static ScooterMusic.Source selected(ServerPlayer var0) {
      if (WearableJukeboxMusic.hasContext(var0)) {
         return WearableJukeboxMusic.importSource(var0);
      } else if (ScooterJukeboxMusic.hasContext(var0)) {
         return ScooterJukeboxMusic.importSource(var0);
      } else {
         Scooter var1 = ScooterStorage.target(var0);
         return ScooterMusic.permitted(var1, var0) && ScooterUpgradeRecipe.has(var1.getItemBySlot(EquipmentSlot.FEET), "noteblock")
            ? ScooterMusic.source(var1)
            : null;
      }
   }

   private static boolean same(ScooterMusic.Source var0, ScooterMusic.Source var1) {
      return var0 != null
         && var1 != null
         && var0.id.equals(var1.id)
         && var0.level == var1.level
         && var0.scooter == var1.scooter
         && var0.wearer == var1.wearer
         && Objects.equals(var0.wornToken, var1.wornToken)
         && Objects.equals(var0.block, var1.block);
   }

   private static void status(ServerPlayer var0, String var1, boolean var2) {
      PacketDistributor.sendToPlayer(var0, new MusicImports.Status(var1, var2), new CustomPacketPayload[0]);
      var0.displayClientMessage(Component.literal(var1), false);
   }

   static void start(ServerPlayer var0, String var1, boolean var2) {
      ScooterMusic.Source var3 = selected(var0);
      if (var3 != null && var3.valid()) {
         String var4;
         try {
            var4 = canonical(var1, var2);
         } catch (Exception var12) {
            status(var0, "Paste an HTTPS YouTube Music song or public playlist URL.", false);
            return;
         }

         if (active.containsKey(var0.getUUID())) {
            status(var0, "Your music import is already running.", true);
         } else {
            long var5 = System.currentTimeMillis();
            if (var5 - lastRequest.getOrDefault(var0.getUUID(), 0L) < 60000L) {
               status(var0, "Please wait a minute between music imports.", false);
            } else {
               if (lastRequest.size() >= 256) {
                  lastRequest.entrySet().removeIf(var2x -> var5 - var2x.getValue() > 60000L);
               }

               UUID var7 = UUID.randomUUID();
               active.put(var0.getUUID(), var7);
               lastRequest.put(var0.getUUID(), var5);
               MinecraftServer var8 = var0.getServer();
               UUID var9 = var0.getUUID();
               status(var0, var2 ? "Importing playlist (up to 25 songs); you can keep playing." : "Preparing YouTube Music audio; you can keep playing.", true);

               try {
                  io.execute(
                     () -> {
                        List var6 = null;
                        Throwable var7x = null;

                        try {
                           MusicImports.Configuration var8x = configuration();
                           var6 = publish(importJob(var8x, var4, var2));
                        } catch (Throwable var10) {
                           var7x = var10;
                        }

                        List var11x = var6;
                        Throwable var9x = var7x;
                        var8.execute(
                           () -> {
                              if (var7.equals(active.get(var9))) {
                                 active.remove(var9);
                                 ServerPlayer var7xx = var8.getPlayerList().getPlayer(var9);
                                 if (var7xx != null) {
                                    if (var9x != null) {
                                       status(var7xx, "Music import failed. Try again later.", false);
                                    } else {
                                       boolean var8xx = same(var3, selected(var7xx)) && var3.valid();
                                       if (var8xx) {
                                          if (var2) {
                                             ScooterMusic.playPlaylist(var7xx, var3, var11x);
                                          } else {
                                             ScooterMusic.play(var7xx, var3, (String)var11x.getFirst());
                                          }
                                       }

                                       status(
                                          var7xx,
                                          "Added "
                                             + var11x.size()
                                             + " song(s) to the server library"
                                             + (var8xx ? "; playback started." : ". Open the picker to play them."),
                                          false
                                       );
                                       if (var8xx) {
                                          PacketDistributor.sendToPlayer(
                                             var7xx,
                                             new MusicImports.Completed(
                                                new ScooterMusicMenu.Catalog(
                                                   ScooterMusic.songs(),
                                                   true,
                                                   var3.scooter != null && ScooterUpgradeRecipe.has(var3.scooter.getItemBySlot(EquipmentSlot.FEET), "jukebox"),
                                                   var3.looping()
                                                )
                                             ),
                                             new CustomPacketPayload[0]
                                          );
                                          ScooterMusic.options(var7xx, var3);
                                       }
                                    }
                                 }
                              }
                           }
                        );
                     }
                  );
               } catch (RejectedExecutionException var11) {
                  active.remove(var9);
                  status(var0, "The music import queue is full. Please try later.", false);
               }
            }
         }
      } else {
         status(var0, "Open an equipped jukebox, nearby placed jukebox or note-block scooter first.", false);
      }
   }

   private static JsonObject readCatalog() throws IOException {
      Path var0 = catalogFile();
      if (!Files.isSymbolicLink(var0) && Files.isRegularFile(var0, LinkOption.NOFOLLOW_LINKS) && Files.size(var0) <= 1048576L) {
         JsonObject var2;
         try (BufferedReader var1 = Files.newBufferedReader(var0)) {
            var2 = JsonParser.parseReader(var1).getAsJsonObject();
         }

         return var2;
      } else {
         throw new IOException("Music catalogue bounds");
      }
   }

   private static MusicImports.Configuration configuration() throws IOException {
      JsonObject var0 = readCatalog();
      String var1 = var0.has("apiUrl") ? var0.get("apiUrl").getAsString() : "https://prol.aridlin.pl/scooter-music-api/v1";
      URI var2 = URI.create(var1.replaceAll("/+$", ""));
      ScooterMusicHttpCache.validateUri(var2);
      if (var2.getQuery() == null && var2.getFragment() == null) {
         String var3 = var0.get("bearerToken").getAsString();
         if (!var3.isBlank() && var3.length() <= 512 && !var3.contains("\r") && !var3.contains("\n")) {
            return new MusicImports.Configuration(var2, var3);
         } else {
            throw new IOException("Authorization bounds");
         }
      } else {
         throw new IOException("Music API URL");
      }
   }

   private static byte[] request(MusicImports.Configuration var0, URI var1, String var2, int var3) throws Exception {
      if (Objects.equals(var1.getHost(), var0.api.getHost()) && var1.getPort() == var0.api.getPort() && var1.getScheme().equals(var0.api.getScheme())) {
         Builder var4 = HttpRequest.newBuilder(var1).timeout(Duration.ofSeconds(60L)).header("Authorization", "Bearer " + var0.bearer);
         if (var2 == null) {
            var4.GET();
         } else {
            var4.header("Content-Type", "application/json").POST(BodyPublishers.ofString(var2));
         }

         HttpResponse var5 = http.send(var4.build(), BodyHandlers.ofInputStream());

         byte[] var8;
         try (InputStream var6 = (InputStream)var5.body()) {
            if (var5.statusCode() != 200 && var5.statusCode() != 202) {
               throw new IOException("Music API unavailable");
            }

            byte[] var7 = var6.readNBytes(var3 + 1);
            if (var7.length > var3) {
               throw new IOException("Music response bounds");
            }

            var8 = var7;
         }

         return var8;
      } else {
         throw new IOException("Music API origin");
      }
   }

   private static JsonObject json(MusicImports.Configuration var0, URI var1, String var2) throws Exception {
      return JsonParser.parseString(new String(request(var0, var1, var2, 2097152), StandardCharsets.UTF_8)).getAsJsonObject();
   }

   private static List<MusicImports.Imported> importJob(MusicImports.Configuration var0, String var1, boolean var2) throws Exception {
      JsonObject var3 = new JsonObject();
      var3.addProperty("url", var1);
      var3.addProperty("mode", "import");
      var3.addProperty("maxEntries", var2 ? 0 : 1);
      JsonObject var4 = json(var0, URI.create(var0.api + "/jobs"), var3.toString());
      String var5 = UUID.fromString(var4.get("jobId").getAsString()).toString();
      long var6 = System.nanoTime() + 900000000000L;

      JsonObject var8;
      for (var8 = var4; ; var8 = json(var0, URI.create(var0.api + "/jobs/" + var5), null)) {
         String var9 = var8.get("status").getAsString();
         if (Set.of("done", "ready", "completed", "complete").contains(var9)) {
            break;
         }

         if (Set.of("failed", "error", "cancelled").contains(var9)) {
            throw new IOException("Import failed");
         }

         Thread.sleep(2000L);
      }

      if (!Set.of("done", "ready", "completed", "complete").contains(var8.get("status").getAsString())) {
         throw new IOException("Import timeout");
      } else {
         JsonArray var25 = var8.getAsJsonArray("entries");
         if (var25 != null && !var25.isEmpty() && (var2 || var25.size() == 1)) {
            ArrayList var10 = new ArrayList();
            HashSet var11 = new HashSet();

            for (JsonElement var13 : var25) {
               JsonObject var14 = var13.getAsJsonObject();
               String var15 = var14.get("videoId").getAsString();
               if (!var15.matches("[A-Za-z0-9_-]{11}")) {
                  throw new IOException("Video identifier");
               }

               String var16 = "youtube_" + var15 + ".wav";
               String var17 = var14.get("sha256").getAsString();
               int var18 = var14.get("bytes").getAsInt();
               int var19 = var14.get("durationTicks").getAsInt();
               if (!var17.matches("[a-f0-9]{64}") || var18 < 44 || var19 < 1) {
                  throw new IOException("Track bounds");
               }

               if (var11.add(var16)) {
                  JsonObject var20 = new JsonObject();
                  var20.addProperty("id", var16);
                  var20.addProperty("title", text(var14, "title", 160));
                  var20.addProperty("artist", text(var14, "artist", 120));
                  var20.addProperty("album", text(var14, "album", 120));
                  var20.addProperty("sha256", var17);
                  var20.addProperty("bytes", var18);
                  var20.addProperty("durationTicks", var19);
                  var20.addProperty("videoId", var15);
                  var20.addProperty("sourceUrl", "https://music.youtube.com/watch?v=" + var15);
                  byte[] var21 = new byte[0];
                  if (var14.has("artwork") && !var14.get("artwork").isJsonNull()) {
                     JsonObject var22 = var14.getAsJsonObject("artwork");
                     String var23 = var22.get("sha256").getAsString();
                     if (!var23.matches("[a-f0-9]{64}") || var22.get("bytes").getAsInt() > 16384) {
                        throw new IOException("Artwork bounds");
                     }

                     URI var24 = var0.api.resolve(var22.get("url").getAsString());
                     if (!var24.getPath().endsWith("/art/" + var23 + ".png")) {
                        throw new IOException("Artwork path");
                     }

                     var21 = request(var0, var24, null, 16384);
                     if (!HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(var21)).equals(var23) || !MusicTrackDetails.png64(var21)) {
                        throw new IOException("Artwork content");
                     }

                     var20.addProperty("artworkSha256", var23);
                  }

                  var10.add(new MusicImports.Imported(var20, var21));
               }
            }

            if (var10.isEmpty()) {
               throw new IOException("No imported songs");
            } else {
               return var10;
            }
         } else {
            throw new IOException("Playlist bounds");
         }
      }
   }

   private static String text(JsonObject var0, String var1, int var2) {
      return var0.has(var1) && !var0.get(var1).isJsonNull() ? MusicNowPlaying.text(var0.get(var1).getAsString(), var2) : "";
   }

   private static void writeAtomic(Path var0, byte[] var1) throws IOException {
      if (Files.isSymbolicLink(var0)) {
         throw new IOException("Output symlink");
      } else {
         Files.createDirectories(var0.getParent());
         Path var2 = Files.createTempFile(var0.getParent(), ".music-import-", ".tmp");

         try {
            Files.write(var2, var1);
            Files.move(var2, var0, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
         } finally {
            Files.deleteIfExists(var2);
         }
      }
   }

   private static List<String> publish(List<MusicImports.Imported> var0) throws IOException {
      synchronized (catalogLock) {
         JsonObject var2 = readCatalog();
         JsonArray var3 = var2.getAsJsonArray("songs");
         HashMap var4 = new HashMap();

         for (int var5 = 0; var5 < var3.size(); var5++) {
            var4.put(var3.get(var5).getAsJsonObject().get("id").getAsString(), var5);
         }

         ArrayList var16 = new ArrayList();

         for (MusicImports.Imported var7 : var0) {
            String var8 = var7.row.get("id").getAsString();
            if (var4.containsKey(var8)) {
               var3.set((Integer)var4.get(var8), var7.row);
            } else {
               if (var3.size() >= 1022) {
                  throw new IOException("Catalogue is full");
               }

               var4.put(var8, var3.size());
               var3.add(var7.row);
            }

            var16.add(var8);
         }

         byte[] var17 = (new GsonBuilder().setPrettyPrinting().create().toJson(var2) + "\n").getBytes(StandardCharsets.UTF_8);
         if (var17.length > 1048576) {
            throw new IOException("Catalogue bounds");
         } else {
            ArrayList<Path> var18 = new ArrayList<>();

            try {
               for (MusicImports.Imported var21 : var0) {
                  if (var21.artwork.length > 0) {
                     Path var22 = artDirectory().resolve(var21.row.get("artworkSha256").getAsString() + ".png");
                     if (Files.exists(var22, LinkOption.NOFOLLOW_LINKS)) {
                        if (!Files.isRegularFile(var22, LinkOption.NOFOLLOW_LINKS)
                           || Files.size(var22) > 16384L
                           || !Arrays.equals(Files.readAllBytes(var22), var21.artwork)) {
                           throw new IOException("Existing cover integrity");
                        }
                     } else {
                        writeAtomic(var22, var21.artwork);
                        var18.add(var22);
                     }
                  }
               }

               writeAtomic(catalogFile(), var17);
            } catch (IOException var14) {
               IOException var19 = var14;

               for (Path var10 : var18) {
                  try {
                     Files.deleteIfExists(var10);
                  } catch (IOException var13) {
                     var19.addSuppressed(var13);
                  }
               }

               throw var19;
            }

            ScooterMusicSources.reload();
            MusicTrackDetails.reload();
            return List.copyOf(var16);
         }
      }
   }

   static void clear() {
      active.clear();
      lastRequest.clear();
   }

   private MusicImports() {
   }

   public static record Completed(ScooterMusicMenu.Catalog catalog) implements CustomPacketPayload {
      public static final Type<MusicImports.Completed> TYPE = new Type(ResourceLocation.parse("psychiatryk_peeb:music_import_completed"));
      public static final StreamCodec<RegistryFriendlyByteBuf, MusicImports.Completed> CODEC = StreamCodec.of(
         (var0, var1) -> ScooterMusicMenu.Catalog.CODEC.encode(var0, var1.catalog),
         var0 -> new MusicImports.Completed((ScooterMusicMenu.Catalog)ScooterMusicMenu.Catalog.CODEC.decode(var0))
      );

      public Type<MusicImports.Completed> type() {
         return TYPE;
      }
   }

   private static record Configuration(URI api, String bearer) {
      @Override
      public String toString() {
         return "Music API configuration";
      }
   }

   private static record Imported(JsonObject row, byte[] artwork) {
   }

   public static record Request(String url, boolean playlist) implements CustomPacketPayload {
      public static final Type<MusicImports.Request> TYPE = new Type(ResourceLocation.parse("psychiatryk_peeb:music_import"));
      public static final StreamCodec<RegistryFriendlyByteBuf, MusicImports.Request> CODEC = new StreamCodec<RegistryFriendlyByteBuf, MusicImports.Request>() {
         public MusicImports.Request decode(RegistryFriendlyByteBuf var1) {
            return new MusicImports.Request(var1.readUtf(2048), var1.readBoolean());
         }

         public void encode(RegistryFriendlyByteBuf var1, MusicImports.Request var2) {
            var1.writeUtf(var2.url, 2048);
            var1.writeBoolean(var2.playlist);
         }
      };

      public Type<MusicImports.Request> type() {
         return TYPE;
      }
   }

   public static record Status(String message, boolean busy) implements CustomPacketPayload {
      public static final Type<MusicImports.Status> TYPE = new Type(ResourceLocation.parse("psychiatryk_peeb:music_import_status"));
      public static final StreamCodec<RegistryFriendlyByteBuf, MusicImports.Status> CODEC = new StreamCodec<RegistryFriendlyByteBuf, MusicImports.Status>() {
         public MusicImports.Status decode(RegistryFriendlyByteBuf var1) {
            return new MusicImports.Status(var1.readUtf(256), var1.readBoolean());
         }

         public void encode(RegistryFriendlyByteBuf var1, MusicImports.Status var2) {
            var1.writeUtf(var2.message, 256);
            var1.writeBoolean(var2.busy);
         }
      };

      public Type<MusicImports.Status> type() {
         return TYPE;
      }
   }
}
