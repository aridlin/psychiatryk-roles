package pl.aridlin.kukirin;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.function.Consumer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent.Post;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.slf4j.LoggerFactory;

public final class MusicCategoryConfig {
   public static final String FILE_NAME = "psychiatryk-music-category.json";
   private static SoundSource current = SoundSource.BLOCKS;
   private static SoundSource sent;
   private static volatile SoundSource client = SoundSource.BLOCKS;
   private static Consumer<MusicCategoryConfig.Settings> receiver = var0 -> {
   };
   private static long stamp = -1L;
   private static long size = -1L;
   private static long nextPoll;

   public static Path file() {
      return FMLPaths.CONFIGDIR.get().resolve("psychiatryk-music-category.json");
   }

   public static SoundSource server() {
      return current;
   }

   public static SoundSource client() {
      return client;
   }

   public static void clientHandler(Consumer<MusicCategoryConfig.Settings> var0) {
      receiver = Objects.requireNonNull(var0);
   }

   private static SoundSource parse(String var0) {
      return switch (var0) {
         case "block", "blocks" -> SoundSource.BLOCKS;
         case "master" -> SoundSource.MASTER;
         case "music" -> SoundSource.MUSIC;
         default -> throw new IllegalArgumentException("Music category");
      };
   }

   static SoundSource read(Path var0) throws IOException {
      if (!Files.exists(var0, LinkOption.NOFOLLOW_LINKS)) {
         return SoundSource.BLOCKS;
      } else if (!Files.isSymbolicLink(var0) && Files.isRegularFile(var0, LinkOption.NOFOLLOW_LINKS) && Files.size(var0) <= 1024L) {
         SoundSource var3;
         try (BufferedReader var1 = Files.newBufferedReader(var0)) {
            JsonObject var2 = JsonParser.parseReader(var1).getAsJsonObject();
            if (var2.get("version").getAsInt() != 1) {
               throw new IOException("Music settings version");
            }

            var3 = parse(var2.get("category").getAsString());
         }

         return var3;
      } else {
         throw new IOException("Music settings bounds");
      }
   }

   static void write(Path var0, SoundSource var1) throws IOException {
      if (var1 != SoundSource.MASTER && var1 != SoundSource.MUSIC) {
         throw new IllegalArgumentException("Choose MASTER or MUSIC");
      } else if (Files.isSymbolicLink(var0)) {
         throw new IOException("Music settings symlink");
      } else {
         Files.createDirectories(var0.getParent());
         Path var2 = Files.createTempFile(var0.getParent(), ".music-category-", ".tmp");

         try {
            Files.writeString(var2, "{\"version\":1,\"category\":\"" + var1.getName() + "\"}\n");
            Files.move(var2, var0, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
         } finally {
            Files.deleteIfExists(var2);
         }
      }
   }

   public static void reload() throws IOException {
      SoundSource var0 = read(file());
      current = var0;
      stamp = Files.exists(file()) ? Files.getLastModifiedTime(file()).toMillis() : 0L;
      size = Files.exists(file()) ? Files.size(file()) : 0L;
   }

   public static void save(SoundSource var0) throws IOException {
      write(file(), var0);
      reload();
   }

   public static void send(ServerPlayer var0, boolean var1) {
      PacketDistributor.sendToPlayer(var0, new MusicCategoryConfig.Settings(var1, current.getName()), new CustomPacketPayload[0]);
   }

   public static void broadcast(MinecraftServer var0) {
      sent = current;

      for (ServerPlayer var2 : var0.getPlayerList().getPlayers()) {
         send(var2, false);
      }
   }

   public static void started(ServerStartedEvent var0) {
      current = SoundSource.BLOCKS;
      sent = null;
      size = -1L;
      stamp = -1L;
      nextPoll = 0L;

      try {
         reload();
      } catch (Exception var2) {
         LoggerFactory.getLogger("ScooterMusic").warn("Music category settings rejected; preserving original category ({}).", var2.getClass().getSimpleName());
      }

      broadcast(var0.getServer());
   }

   public static void login(PlayerLoggedInEvent var0) {
      if (var0.getEntity() instanceof ServerPlayer var1) {
         send(var1, false);
      }
   }

   public static void tick(Post var0) {
      if (System.currentTimeMillis() >= nextPoll) {
         nextPoll = System.currentTimeMillis() + 1000L;

         try {
            Path var1 = file();
            long var2 = Files.exists(var1, LinkOption.NOFOLLOW_LINKS) ? Files.getLastModifiedTime(var1, LinkOption.NOFOLLOW_LINKS).toMillis() : 0L;
            long var4 = Files.exists(var1, LinkOption.NOFOLLOW_LINKS) ? Files.size(var1) : 0L;
            if (var2 != stamp || var4 != size) {
               try {
                  reload();
               } catch (Exception var7) {
                  stamp = var2;
                  size = var4;
                  LoggerFactory.getLogger("ScooterMusic")
                     .warn("Music category settings rejected; retaining current choice ({}).", var7.getClass().getSimpleName());
               }
            }
         } catch (Exception var8) {
            LoggerFactory.getLogger("ScooterMusic").warn("Music category settings cannot be read ({}).", var8.getClass().getSimpleName());
         }
      }

      if (current != sent) {
         broadcast(var0.getServer());
      }
   }

   public static void register(RegisterPayloadHandlersEvent var0) {
      PayloadRegistrar var1 = var0.registrar("1");
      var1.playToClient(MusicCategoryConfig.Settings.TYPE, MusicCategoryConfig.Settings.CODEC, (var0x, var1x) -> var1x.enqueueWork(() -> {
            try {
               client = parse(var0x.category);
               receiver.accept(var0x);
            } catch (IllegalArgumentException var2) {
            }
         }));
      var1.playToServer(MusicCategoryConfig.Edit.TYPE, MusicCategoryConfig.Edit.CODEC, (var0x, var1x) -> var1x.enqueueWork(() -> {
            if (var1x.player() instanceof ServerPlayer var2 && var2.hasPermissions(2)) {
               try {
                  String var7 = var0x.action;
                  switch (var7) {
                     case "query":
                        send(var2, true);
                        break;
                     case "save":
                        SoundSource var5 = parse(var0x.category);
                        if (var5 != SoundSource.MASTER && var5 != SoundSource.MUSIC) {
                           return;
                        }

                        save(var5);
                        broadcast(var2.getServer());
                        send(var2, true);
                        var2.displayClientMessage(Component.literal("Jukebox and scooter music category saved; applies live to everyone."), false);
                        break;
                     case "reload":
                        reload();
                        broadcast(var2.getServer());
                        send(var2, true);
                  }
               } catch (Exception var6) {
                  var2.displayClientMessage(Component.literal("Music category could not be saved; current choice retained."), false);
               }

               return;
            }
         }));
   }

   private MusicCategoryConfig() {
   }

   public static record Edit(String action, String category) implements CustomPacketPayload {
      public static final Type<MusicCategoryConfig.Edit> TYPE = new Type(ResourceLocation.parse("psychiatryk_peeb:music_category_edit"));
      public static final StreamCodec<RegistryFriendlyByteBuf, MusicCategoryConfig.Edit> CODEC = new StreamCodec<RegistryFriendlyByteBuf, MusicCategoryConfig.Edit>() {
         public MusicCategoryConfig.Edit decode(RegistryFriendlyByteBuf var1) {
            return new MusicCategoryConfig.Edit(var1.readUtf(8), var1.readUtf(8));
         }

         public void encode(RegistryFriendlyByteBuf var1, MusicCategoryConfig.Edit var2) {
            var1.writeUtf(var2.action, 8);
            var1.writeUtf(var2.category, 8);
         }
      };

      public Type<MusicCategoryConfig.Edit> type() {
         return TYPE;
      }
   }

   public static record Settings(boolean open, String category) implements CustomPacketPayload {
      public static final Type<MusicCategoryConfig.Settings> TYPE = new Type(ResourceLocation.parse("psychiatryk_peeb:music_category"));
      public static final StreamCodec<RegistryFriendlyByteBuf, MusicCategoryConfig.Settings> CODEC = new StreamCodec<RegistryFriendlyByteBuf, MusicCategoryConfig.Settings>() {
         public MusicCategoryConfig.Settings decode(RegistryFriendlyByteBuf var1) {
            return new MusicCategoryConfig.Settings(var1.readBoolean(), var1.readUtf(8));
         }

         public void encode(RegistryFriendlyByteBuf var1, MusicCategoryConfig.Settings var2) {
            var1.writeBoolean(var2.open);
            var1.writeUtf(var2.category, 8);
         }
      };

      public Type<MusicCategoryConfig.Settings> type() {
         return TYPE;
      }
   }
}
