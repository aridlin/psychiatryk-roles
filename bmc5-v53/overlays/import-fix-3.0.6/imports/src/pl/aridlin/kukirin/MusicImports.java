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

   static void start(ServerPlayer player, String url, boolean playlist) {
      ScooterMusic.Source source = selected(player);
      if (source == null || !source.valid()) {
         status(player, "Open an equipped jukebox, nearby placed jukebox or note-block scooter first.", false);
         return;
      }
      final String canonical;
      try { canonical = canonical(url, playlist); }
      catch (Exception failure) {
         status(player, "Paste an HTTPS YouTube Music song or public playlist URL.", false);
         return;
      }
      UUID requestId = UUID.randomUUID();
      UUID playerId = player.getUUID();
      UUID previousRequest = active.put(playerId, requestId);
      MinecraftServer server = player.getServer();
      status(player, playlist ? "Preparing playlist; songs enter the library and play as they become ready."
                              : "Preparing music; cached songs play immediately.", true);
      try {
         io.execute(() -> {
            List<String> added = new ArrayList<>();
            boolean[] started = {false}; // Accessed only by the server-thread callbacks.
            Throwable failure = null;
            try {
               Configuration config = configuration();
               importJob(config, canonical, playlist, ready -> {
                  List<String> ids = publish(ready);
                  for (String id : ids) if (!added.contains(id)) added.add(id);
                  List<String> snapshot = List.copyOf(added);
                  server.execute(() -> {
                     ServerPlayer current = server.getPlayerList().getPlayer(playerId);
                     if (current == null) return;
                     boolean ownsRequest = requestId.equals(active.get(playerId));
                     boolean canPlay = ownsRequest && source.valid() && (started[0] || same(source, selected(current)));
                     if (canPlay) {
                        int accepted = 1;
                        if (playlist) {
                           accepted = ScooterMusic.updateImportedPlaylist(current, source, snapshot, !started[0]);
                        } else if (!started[0]) {
                           accepted = ScooterMusic.play(current, source, snapshot.getFirst());
                        }
                        if (accepted > 0) started[0] = true;
                        ScooterMusic.options(current, source);
                     }
                     ScooterMusicMenu.sendCatalog(current, new ScooterMusicMenu.Catalog(
                        ScooterMusic.songs(), canPlay,
                        source.scooter != null && ScooterUpgradeRecipe.has(source.scooter.getItemBySlot(EquipmentSlot.FEET), "jukebox"),
                        source.valid() && source.looping()), true, snapshot.getFirst());
                     if (ownsRequest) status(current, "Added " + snapshot.size() + " song(s) to the library"
                        + (canPlay ? "; playing now." : ". Open the library to play them."), true);
                  });
               });
            } catch (Throwable error) { failure = error; }
            Throwable finalFailure = failure;
            List<String> completedIds = List.copyOf(added);
            server.execute(() -> {
               if (!requestId.equals(active.get(playerId))) return;
               active.remove(playerId);
               ServerPlayer current = server.getPlayerList().getPlayer(playerId);
               if (current == null) return;
               if (finalFailure != null) {
                  status(current, completedIds.isEmpty() ? "Music could not be prepared. Please try again."
                     : "The remaining songs could not be prepared; " + completedIds.size() + " ready song(s) remain in the library.", false);
               } else {
                  status(current, "Added " + completedIds.size() + " song(s) to the server library"
                     + (started[0] ? "; playback started." : ". Open the library to play them."), false);
               }
            });
         });
      } catch (RejectedExecutionException busy) {
         if (previousRequest == null) active.remove(playerId, requestId);
         else active.replace(playerId, requestId, previousRequest);
         status(player, "The music service is busy preparing other songs. Please try again shortly.", false);
      }
   }

   private static JsonObject readCatalog() throws IOException {
      Path var0 = catalogFile();
      if (!Files.isSymbolicLink(var0) && Files.isRegularFile(var0, LinkOption.NOFOLLOW_LINKS)) {
         JsonObject var2;
         try (BufferedReader var1 = Files.newBufferedReader(var0)) {
            var2 = JsonParser.parseReader(var1).getAsJsonObject();
         }

         return var2;
      } else {
         throw new IOException("Music catalogue file");
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

            byte[] var7 = var3 < 0 ? var6.readAllBytes() : var6.readNBytes(var3 + 1);
            if (var3 >= 0 && var7.length > var3) {
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
      return JsonParser.parseString(new String(request(var0, var1, var2, -1), StandardCharsets.UTF_8)).getAsJsonObject();
   }

   @FunctionalInterface
   private interface Progress { void ready(List<MusicImports.Imported> entries) throws Exception; }

   private static void importJob(MusicImports.Configuration var0, String var1, boolean var2, Progress progress) throws Exception {
      JsonObject request = new JsonObject();
      request.addProperty("url", var1);
      request.addProperty("mode", "import");
      request.addProperty("maxEntries", 0);
      JsonObject var8 = json(var0, URI.create(var0.api + "/jobs"), request.toString());
      String job = UUID.fromString(var8.get("jobId").getAsString()).toString();
      HashSet<String> var11 = new HashSet<>();
      for (;;) {
         JsonArray var25 = var8.has("entries") && !var8.get("entries").isJsonNull() ? var8.getAsJsonArray("entries") : null;
         if (var25 != null) {
            ArrayList<MusicImports.Imported> var10 = new ArrayList<>();
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

            if (!var10.isEmpty()) progress.ready(List.copyOf(var10));
         }
         String state = var8.get("status").getAsString();
         if (Set.of("done", "ready", "completed", "complete").contains(state)) {
            if (var11.isEmpty()) throw new IOException("No imported songs");
            return;
         }
         if (Set.of("failed", "error", "cancelled").contains(state)) throw new IOException("Import failed");
         Thread.sleep(250L);
         var8 = json(var0, URI.create(var0.api + "/jobs/" + job), null);
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

   private static List<String> publish(List<MusicImports.Imported> imported) throws IOException {
      synchronized (catalogLock) {
         JsonObject catalog = readCatalog();
         JsonArray songs = catalog.getAsJsonArray("songs");
         if (songs == null) { songs = new JsonArray(); catalog.add("songs", songs); }
         Map<String, Integer> index = new HashMap<>();
         for (int i = 0; i < songs.size(); ++i) index.put(songs.get(i).getAsJsonObject().get("id").getAsString(), i);
         List<String> ids = new ArrayList<>();
         for (Imported entry : imported) {
            String id = entry.row.get("id").getAsString();
            Integer position = index.get(id);
            if (position != null) songs.set(position, entry.row);
            else { index.put(id, songs.size()); songs.add(entry.row); }
            ids.add(id);
         }
         byte[] serialized = (new GsonBuilder().setPrettyPrinting().create().toJson(catalog) + "\n").getBytes(StandardCharsets.UTF_8);
         List<Path> createdCovers = new ArrayList<>();
         try {
            for (Imported entry : imported) {
               if (entry.artwork.length == 0) continue;
               Path cover = artDirectory().resolve(entry.row.get("artworkSha256").getAsString() + ".png");
               if (Files.exists(cover, LinkOption.NOFOLLOW_LINKS)) {
                  if (!Files.isRegularFile(cover, LinkOption.NOFOLLOW_LINKS) || Files.size(cover) > 16384L
                     || !Arrays.equals(Files.readAllBytes(cover), entry.artwork)) throw new IOException("Existing cover integrity");
               } else { writeAtomic(cover, entry.artwork); createdCovers.add(cover); }
            }
            writeAtomic(catalogFile(), serialized);
         } catch (IOException failure) {
            for (Path cover : createdCovers) {
               try { Files.deleteIfExists(cover); } catch (IOException cleanup) { failure.addSuppressed(cleanup); }
            }
            throw failure;
         }
         ScooterMusicSources.reload();
         MusicTrackDetails.reload();
         return List.copyOf(ids);
      }
   }

   static void clear() {
      active.clear();
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
