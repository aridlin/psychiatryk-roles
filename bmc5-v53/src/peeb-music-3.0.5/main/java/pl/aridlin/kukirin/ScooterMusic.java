package pl.aridlin.kukirin;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.random.RandomGenerator;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.entity.JukeboxBlockEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent.Post;
import net.neoforged.neoforge.network.PacketDistributor;
import pl.aridlin.kukirin.ScooterMusicSources.Song;
import pl.aridlin.kukirin.ScooterWav.Pcm;
import pl.aridlin.psychiatrykroles.jukebox.WearableJukebox;

@EventBusSubscriber(
   modid = "goplanska_kukirin"
)
public final class ScooterMusic {
   public static final int MAX_BYTES = 16777216;
   private static final Map<UUID, ScooterMusic.Playback> playing = new HashMap<>();
   private static final Map<UUID, UUID> loading = new HashMap<>();
   private static final Map<UUID, ScooterMusic.Source> jukeboxes = new HashMap<>();
   private static final Map<UUID, ScooterMusic.Modes> modes = new HashMap<>();
   private static final Map<UUID, ScooterMusic.Dormant> dormant = new LinkedHashMap<>();

   private static void retire(ScooterMusic.Playback var0) {
      stop(var0.source.id);
      if (dormant.size() >= 256) {
         dormant.remove(dormant.keySet().iterator().next());
      }

      dormant.put(var0.source.id, new ScooterMusic.Dormant(var0.source, var0.name, var0.controller));
   }

   private static boolean sameSource(ScooterMusic.Source var0, ScooterMusic.Source var1) {
      return var0.level == var1.level
         && var0.scooter == var1.scooter
         && var0.wearer == var1.wearer
         && Objects.equals(var0.wornToken, var1.wornToken)
         && Objects.equals(var0.block, var1.block);
   }

   private static ScooterMusic.Modes modes(ScooterMusic.Source var0, boolean var1) {
      ScooterMusic.Modes var2 = modes.get(var0.id);
      if (var2 != null && !sameSource(var2.source, var0)) {
         modes.remove(var0.id);
         var2 = null;
      }

      if (var2 == null && var1 && modes.size() < 256) {
         var2 = new ScooterMusic.Modes(var0);
         modes.put(var0.id, var2);
      }

      return var2;
   }

   static boolean autoplay(ScooterMusic.Source var0) {
      ScooterMusic.Modes var1 = modes(var0, false);
      return var1 != null && var1.autoplay;
   }

   static boolean shuffle(ScooterMusic.Source var0) {
      ScooterMusic.Modes var1 = modes(var0, false);
      return var1 != null && var1.shuffle;
   }

   static ScooterMusic.Source source(Scooter var0) {
      ScooterMusic.Playback var1 = playing.get(var0.getUUID());
      return var1 != null && var1.source.scooter == var0 ? var1.source : new ScooterMusic.Source(var0);
   }

   static void options(ServerPlayer var0, ScooterMusic.Source var1) {
      PacketDistributor.sendToPlayer(var0, new ScooterMusicMenu.Options(autoplay(var1), shuffle(var1)), new CustomPacketPayload[0]);
   }

   static boolean mode(ServerPlayer var0, ScooterMusic.Source var1, String var2, String var3) {
      if (var1.valid() && (var3.equals("true") || var3.equals("false"))) {
         ScooterMusic.Modes var4 = modes(var1, true);
         if (var4 == null) {
            var0.displayClientMessage(Component.literal("Too many music settings are active; retry after unused sources close."), false);
            return false;
         } else {
            if (var2.equals("autoplay")) {
               var4.autoplay = Boolean.parseBoolean(var3);
            } else {
               if (!var2.equals("shuffle")) {
                  return false;
               }

               var4.shuffle = Boolean.parseBoolean(var3);
            }

            options(var0, var1);
            return true;
         }
      } else {
         return false;
      }
   }

   static String nextSong(List<String> var0, String var1, boolean var2, RandomGenerator var3) {
      List var4 = var0.stream().filter(ScooterMusicSources::validName).distinct().sorted().toList();
      if (var4.isEmpty()) {
         return null;
      } else if (var4.size() == 1) {
         return (String)var4.getFirst();
      } else if (var2) {
         List var6 = var4.stream().filter(var1x -> !var1x.equals(var1)).toList();
         return (String)var6.get(var3.nextInt(var6.size()));
      } else {
         int var5 = var4.indexOf(var1);
         return (String)var4.get((var5 + 1) % var4.size());
      }
   }

   private static void message(ServerPlayer var0, String var1) {
      if (var0 != null) {
         var0.displayClientMessage(Component.literal(var1), false);
      }
   }

   static String nextPlaylist(List<String> var0, String var1, boolean var2, RandomGenerator var3) {
      List var4 = var0.stream().filter(ScooterMusicSources::validName).distinct().limit(25L).toList();
      if (var4.isEmpty()) {
         return null;
      } else if (var4.size() == 1) {
         return (String)var4.getFirst();
      } else if (var2) {
         List var5 = var4.stream().filter(var1x -> !var1x.equals(var1)).toList();
         return (String)var5.get(var3.nextInt(var5.size()));
      } else {
         return (String)var4.get((var4.indexOf(var1) + 1) % var4.size());
      }
   }

   private static void advance(ScooterMusic.Playback var0) {
      if (playing.get(var0.source.id) == var0 && !var0.source.looping() && autoplay(var0.source) && var0.source.valid()) {
         ScooterMusic.Modes var1 = modes(var0.source, false);
         String var2 = var1 != null && !var1.playlist.isEmpty()
            ? nextPlaylist(var1.playlist, var0.name, shuffle(var0.source), ThreadLocalRandom.current())
            : nextSong(songs(), var0.name, shuffle(var0.source), ThreadLocalRandom.current());
         if (var2 == null) {
            stop(var0.source.id);
         } else {
            ServerPlayer var3 = var0.source.level.getServer().getPlayerList().getPlayer(var0.controller);
            play(var3, var0.source, var2, var0.controller);
         }
      }
   }

   static void timing(ServerPlayer var0, String var1, String var2) {
      if (var2 != null && var2.length() == 73 && var2.charAt(36) == ':') {
         UUID var3;
         UUID var4;
         try {
            var3 = UUID.fromString(var2.substring(0, 36));
            var4 = UUID.fromString(var2.substring(37));
         } catch (IllegalArgumentException var8) {
            return;
         }

         ScooterMusic.Playback var5 = playing.get(var3);
         if (var5 != null
            && var5.session.equals(var4)
            && var0.getUUID().equals(var5.controller)
            && var5.sent.containsKey(var0.getUUID())
            && var0.level() == var5.source.level
            && !(var5.source.distance(var0) > 4096.0)
            && var5.source.valid()) {
            long var6 = var5.source.level.getGameTime();
            if (var1.equals("started")) {
               if (!var5.started) {
                  var5.started = true;
                  var5.began = var6;
                  var5.endAfter = var6 + (long)var5.song.durationTicks() + 100L;
                  var5.until = var6 + (long)var5.song.durationTicks() + 600L;
               }
            } else {
               if (var1.equals("ended") && !var5.source.looping() && var6 >= var5.began + (long)var5.song.durationTicks()) {
                  advance(var5);
               }
            }
         }
      }
   }

   public static boolean looping(Scooter var0) {
      return ((CustomData)var0.getItemBySlot(EquipmentSlot.FEET).getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY))
         .getUnsafe()
         .getBoolean("ScooterMusicLoop");
   }

   public static void looping(Scooter var0, boolean var1) {
      ItemStack var2 = var0.getItemBySlot(EquipmentSlot.FEET).copy();
      CustomData.update(DataComponents.CUSTOM_DATA, var2, var1x -> var1x.putBoolean("ScooterMusicLoop", var1));
      var0.setItemSlot(EquipmentSlot.FEET, var2);
      updateLoop(var0.getUUID());
   }

   public static boolean permitted(Scooter var0, ServerPlayer var1) {
      return var0 != null && (!ScooterEnchants.bound(var0.getItemBySlot(EquipmentSlot.FEET)) || ScooterEnchants.owned(var0, var1));
   }

   public static boolean insertDisc(Scooter var0, ServerPlayer var1, InteractionHand var2) {
      ItemStack var3 = var1.getItemInHand(var2);
      if (var3.has(DataComponents.JUKEBOX_PLAYABLE) && ScooterUpgradeRecipe.has(var0.getItemBySlot(EquipmentSlot.FEET), "jukebox")) {
         stop(var0);
         ItemStack var4 = var0.getItemBySlot(EquipmentSlot.FEET).copy();
         ItemStack var5 = disc(var4, var0);
         if (!var5.isEmpty()) {
            var1.getInventory().placeItemBackInInventory(var5);
         }

         ItemStack var6 = var3.copyWithCount(1);
         CustomData.update(DataComponents.CUSTOM_DATA, var4, var2x -> var2x.put("ScooterDisc", var6.save(var0.registryAccess())));
         var0.setItemSlot(EquipmentSlot.FEET, var4);
         if (!var1.isCreative()) {
            var3.shrink(1);
         }

         var0.startDisc();
         return true;
      } else {
         return false;
      }
   }

   public static ItemStack disc(ItemStack var0, Scooter var1) {
      CustomData var2 = (CustomData)var0.get(DataComponents.CUSTOM_DATA);
      return var2 == null ? ItemStack.EMPTY : ItemStack.parseOptional(var1.registryAccess(), var2.copyTag().getCompound("ScooterDisc"));
   }

   public static void stop(Scooter var0) {
      stop(var0.getUUID());
      var0.stopDisc();
   }

   static void stop(UUID var0) {
      dormant.remove(var0);
      loading.remove(var0);
      ScooterMusic.Playback var1 = playing.remove(var0);
      if (var1 != null) {
         for (ServerPlayer var3 : var1.source.level.getServer().getPlayerList().getPlayers()) {
            if (var1.sent.containsKey(var3.getUUID())) {
               PacketDistributor.sendToPlayer(var3, new ScooterAudioPacket(var0, var1.session, 0, 0, new byte[0]), new CustomPacketPayload[0]);
            }
         }
      }
   }

   public static List<String> songs() {
      TreeSet var0 = new TreeSet(ScooterMusicSources.catalog().keySet());
      var0.add("chiki_ride.wav");
      var0.add("night_motor.wav");
      return var0.stream().limit(1024L).toList();
   }

   public static int wav(ServerPlayer var0, String var1) {
      Scooter var2 = ScooterStorage.target(var0);
      if (permitted(var2, var0) && ScooterUpgradeRecipe.has(var2.getItemBySlot(EquipmentSlot.FEET), "noteblock")) {
         var2.stopDisc();
         return play(var0, source(var2), var1);
      } else {
         var0.displayClientMessage(Component.literal("Ride or aim at your scooter upgraded with a note block."), false);
         return 0;
      }
   }

   static int play(ServerPlayer var0, ScooterMusic.Source var1, String var2) {
      ScooterMusic.Modes var3 = modes(var1, false);
      if (var3 != null) {
         var3.playlist = List.of();
      }

      return play(var0, var1, var2, var0.getUUID());
   }

   static int playPlaylist(ServerPlayer var0, ScooterMusic.Source var1, List<String> var2) {
      List var3 = var2.stream().filter(var0x -> ScooterMusicSources.catalog().containsKey(var0x)).distinct().limit(25L).toList();
      if (!var3.isEmpty() && var1.valid()) {
         ScooterMusic.Modes var4 = modes(var1, true);
         if (var4 == null) {
            return 0;
         } else {
            var4.playlist = var3;
            var4.autoplay = true;
            var4.shuffle = false;
            options(var0, var1);
            return play(var0, var1, (String)var3.getFirst(), var0.getUUID());
         }
      } else {
         return 0;
      }
   }

   private static int play(ServerPlayer var0, ScooterMusic.Source var1, String var2, UUID var3) {
      if (!var1.valid()) {
         return 0;
      } else if (!ScooterMusicSources.validName(var2)) {
         message(var0, "Choose a WAV from the server song list.");
         return 0;
      } else if (loading.containsKey(var1.id)) {
         return 0;
      } else if (playing.size() + loading.size() >= 4 && !playing.containsKey(var1.id)) {
         message(var0, "Four music sources are already active.");
         return 0;
      } else {
         stop(var1.id);
         Song var4 = (Song)ScooterMusicSources.catalog().get(var2);
         if (var4 != null) {
            playing.put(var1.id, new ScooterMusic.Playback(var1, UUID.randomUUID(), null, var4, var2, var3, var1.level.getGameTime(), new HashMap<>()));
            message(var0, "Playing " + var4.title() + ".");
            return 1;
         } else if (!var2.equals("chiki_ride.wav") && !var2.equals("night_motor.wav")) {
            message(var0, "Song unavailable; choose one from the server song list.");
            return 0;
         } else {
            UUID var5 = UUID.randomUUID();
            loading.put(var1.id, var5);
            MinecraftServer var6 = var1.level.getServer();
            CompletableFuture.<byte[]>supplyAsync(
                  () -> {
                     try {
                        byte[] var1x;
                        try (InputStream var2x = ScooterMusic.class.getResourceAsStream("/data/goplanska_kukirin/music/" + var2)) {
                           if (var2x == null) {
                              throw new IOException();
                           }

                           var1x = var2x.readNBytes(16777217);
                        }

                        if (var1x.length <= 16777216
                           && var1x.length >= 44
                           && new String(var1x, 0, 4, StandardCharsets.US_ASCII).equals("RIFF")
                           && new String(var1x, 8, 4, StandardCharsets.US_ASCII).equals("WAVE")) {
                           return var1x;
                        } else {
                           throw new IOException();
                        }
                     } catch (Exception var7) {
                        throw new CompletionException(var7);
                     }
                  }
               )
               .whenComplete(
                  (var6x, var7) -> var6.execute(
                        () -> {
                           if (var5.equals(loading.get(var1.id))) {
                              loading.remove(var1.id);
                              if (var1.retained()) {
                                 if (var7 != null) {
                                    message(var0, "Bundled WAV could not be loaded.");
                                 } else {
                                    try {
                                       String var7x = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(var6x));
                                       Pcm var8 = ScooterWav.decode(var6x);
                                       int var9 = (int)Math.ceil(
                                          (double)((float)var8.samples().length / var8.format().getFrameRate() / (float)var8.format().getFrameSize() * 20.0F)
                                       );
                                       Song var10 = new Song(var2, var2, "bundled:" + var2, "", var7x, var6x.length, var9);
                                       playing.put(
                                          var1.id,
                                          new ScooterMusic.Playback(var1, UUID.randomUUID(), null, var10, var2, var3, var1.level.getGameTime(), new HashMap<>())
                                       );
                                    } catch (Exception var11) {
                                       message(var0, "Bundled WAV could not be decoded.");
                                       return;
                                    }

                                    message(var0, "Playing " + var2 + ".");
                                 }
                              }
                           }
                        }
                     )
               );
            return 1;
         }
      }
   }

   private static ScooterAudioUrlPacket metadata(ScooterMusic.Playback var0) {
      ScooterMusic.Source var1 = var0.source;
      Song var2 = var0.song;
      return new ScooterAudioUrlPacket(
         var1.id, var0.session, var1.block != null, var1.position(), var2.url(), var2.bearer(), var2.sha256(), var2.bytes(), var1.looping()
      );
   }

   private static void describe(ServerPlayer var0, ScooterMusic.Playback var1) {
      MusicTrackDetails.Details var2 = MusicTrackDetails.get(var1.name, var1.song.title());
      String var3 = var2.title() + "\n" + var2.artist() + "\n" + var2.album() + "\n" + var2.artworkSha();
      if (!var3.equals(var1.descriptions.get(var0.getUUID()))) {
         MusicNowPlaying.send(var0, var1.source.id, var1.session, var2.title(), var2.artist(), var2.album(), var1.song.durationTicks(), var2.artwork());
         var1.descriptions.put(var0.getUUID(), var3);
      }
   }

   private static void updateLoop(UUID var0) {
      ScooterMusic.Playback var1 = playing.get(var0);
      if (var1 == null) {
         ScooterMusic.Dormant var2 = dormant.get(var0);
         if (var2 != null && var2.source.valid() && var2.source.looping()) {
            ServerPlayer var3 = var2.source.level.getServer().getPlayerList().getPlayer(var2.controller);
            play(var3, var2.source, var2.name, var2.controller);
            var1 = playing.get(var0);
         }
      }

      if (var1 != null && !var1.source.looping()) {
         var1.endAfter = var1.source.level.getGameTime() + (long)var1.song.durationTicks() + 100L;
         var1.until = var1.source.level.getGameTime() + (long)var1.song.durationTicks() + 600L;
      }

      if (var1 != null && var1.song != null) {
         for (ServerPlayer var5 : var1.source.level.getServer().getPlayerList().getPlayers()) {
            if (var1.sent.containsKey(var5.getUUID())) {
               PacketDistributor.sendToPlayer(var5, metadata(var1), new CustomPacketPayload[0]);
            }
         }
      }
   }

   static ScooterMusic.Source openJukebox(ServerPlayer var0, BlockPos var1) {
      ScooterMusic.Source var2 = new ScooterMusic.Source(var0.serverLevel(), var1);
      ScooterMusic.Playback var3 = playing.get(var2.id);
      if (var3 != null) {
         return var3.source;
      } else {
         ScooterMusic.Source var4 = jukeboxes.get(var2.id);
         if (var4 != null) {
            return var4;
         } else {
            if (jukeboxes.size() < 256) {
               jukeboxes.put(var2.id, var2);
            }

            return var2;
         }
      }
   }

   static boolean jukeboxLoop(ScooterMusic.Source var0, boolean var1) {
      if (!var0.valid()) {
         return false;
      } else {
         var0.blockLoop = var1;
         updateLoop(var0.id);
         return true;
      }
   }

   static boolean wearableLoop(ScooterMusic.Source var0, boolean var1) {
      if (var0.wearer != null && var0.valid()) {
         WearableJukebox.looping(WearableJukebox.equipped(var0.wearer), var1);
         updateLoop(var0.id);
         return true;
      } else {
         return false;
      }
   }

   @SubscribeEvent
   public static void start(ServerStartedEvent var0) {
      ScooterMusicSources.catalog();
   }

   @SubscribeEvent
   public static void commands(RegisterCommandsEvent var0) {
      var0.getDispatcher()
         .register(
            (LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal(
                              "scootermusic"
                           )
                           .then(
                              Commands.literal("list")
                                 .executes(
                                    var0x -> {
                                       List var1 = songs();
                                       ((CommandSourceStack)var0x.getSource())
                                          .sendSuccess(
                                             () -> Component.literal(
                                                   var1.size()
                                                      + " songs. Open the scooter Music menu or shift-right-click a jukebox; use /scootermusic <name> with tab completion."
                                                ),
                                             false
                                          );
                                       return 1;
                                    }
                                 )
                           ))
                        .then(Commands.literal("loop").then(Commands.argument("enabled", BoolArgumentType.bool()).executes(var0x -> {
                           ServerPlayer var1 = ((CommandSourceStack)var0x.getSource()).getPlayerOrException();
                           Scooter var2 = ScooterStorage.target(var1);
                           if (!permitted(var2, var1)) {
                              return 0;
                           } else {
                              looping(var2, BoolArgumentType.getBool(var0x, "enabled"));
                              return 1;
                           }
                        }))))
                     .then(Commands.literal("stop").executes(var0x -> {
                        ServerPlayer var1 = ((CommandSourceStack)var0x.getSource()).getPlayerOrException();
                        Scooter var2 = ScooterStorage.target(var1);
                        if (!permitted(var2, var1)) {
                           return 0;
                        } else {
                           stop(var2);
                           return 1;
                        }
                     })))
                  .then(Commands.literal("disc").executes(var0x -> {
                     ServerPlayer var1 = ((CommandSourceStack)var0x.getSource()).getPlayerOrException();
                     Scooter var2 = ScooterStorage.target(var1);
                     if (permitted(var2, var1) && ScooterUpgradeRecipe.has(var2.getItemBySlot(EquipmentSlot.FEET), "jukebox")) {
                        stop(var2);
                        var2.startDisc();
                        return 1;
                     } else {
                        return 0;
                     }
                  })))
               .then(
                  Commands.argument("wav", StringArgumentType.greedyString())
                     .suggests((var0x, var1) -> SharedSuggestionProvider.suggest(songs(), var1))
                     .executes(var0x -> wav(((CommandSourceStack)var0x.getSource()).getPlayerOrException(), StringArgumentType.getString(var0x, "wav")))
               )
         );
   }

   @SubscribeEvent
   public static void tick(Post var0) {
      for (ScooterMusic.Playback var2 : new ArrayList<>(playing.values())) {
         ScooterMusic.Source var3 = var2.source;
         if (var3.suspended()) {
            if (var2.suspendedAt < 0L) {
               var2.suspendedAt = var3.level.getGameTime();
            }

            for (ServerPlayer var11 : var3.level.getServer().getPlayerList().getPlayers()) {
               if (var2.sent.containsKey(var11.getUUID())) {
                  PacketDistributor.sendToPlayer(var11, new ScooterAudioPacket(var3.id, var2.session, 0, 0, new byte[0]), new CustomPacketPayload[0]);
               }
            }

            var2.sent.clear();
            var2.descriptions.clear();
         } else {
            if (var2.suspendedAt >= 0L) {
               long var4 = var3.level.getGameTime() - var2.suspendedAt;
               var2.began += var4;
               var2.endAfter += var4;
               var2.until += var4;
               var2.suspendedAt = -1L;
            }

            if (!var3.valid()) {
               stop(var3.id);
            } else if (!var3.looping() && autoplay(var3) && var3.level.getGameTime() >= var2.endAfter) {
               advance(var2);
            } else if (!var3.looping() && !autoplay(var3) && var3.level.getGameTime() > var2.until) {
               retire(var2);
            } else {
               for (ServerPlayer var5 : var3.level.getServer().getPlayerList().getPlayers()) {
                  if (var5.level() == var3.level && !(var3.distance(var5) > 4096.0)) {
                     if (var2.song != null) {
                        if (!var2.sent.containsKey(var5.getUUID()) || var3.level.getGameTime() % 100L == 0L) {
                           PacketDistributor.sendToPlayer(var5, metadata(var2), new CustomPacketPayload[0]);
                           var2.sent.put(var5.getUUID(), var2.song.bytes());
                        }

                        if (!var2.descriptions.containsKey(var5.getUUID()) || var3.level.getGameTime() % 20L == 0L) {
                           describe(var5, var2);
                        }
                     } else {
                        int var6 = var2.sent.getOrDefault(var5.getUUID(), 0);

                        for (int var7 = 0; var7 < 2 && var6 < var2.data.length; var7++) {
                           int var8 = Math.min(var6 + 32768, var2.data.length);
                           PacketDistributor.sendToPlayer(
                              var5,
                              new ScooterAudioPacket(var3.id, var2.session, var2.data.length, var6, Arrays.copyOfRange(var2.data, var6, var8)),
                              new CustomPacketPayload[0]
                           );
                           var6 = var8;
                        }

                        var2.sent.put(var5.getUUID(), var6);
                     }
                  } else {
                     if (var2.sent.remove(var5.getUUID()) != null) {
                        PacketDistributor.sendToPlayer(var5, new ScooterAudioPacket(var3.id, var2.session, 0, 0, new byte[0]), new CustomPacketPayload[0]);
                     }

                     var2.descriptions.remove(var5.getUUID());
                  }
               }
            }
         }
      }

      jukeboxes.entrySet().removeIf(var0x -> !var0x.getValue().retained());
      modes.entrySet().removeIf(var0x -> !var0x.getValue().source.retained());
      dormant.entrySet().removeIf(var0x -> !var0x.getValue().source.retained());
   }

   @SubscribeEvent
   public static void shutdown(ServerStoppedEvent var0) {
      playing.clear();
      loading.clear();
      jukeboxes.clear();
      modes.clear();
      dormant.clear();
      ScooterJukeboxMusic.clear();
      WearableJukeboxMusic.clear();
      MusicImports.clear();
   }

   private static record Dormant(ScooterMusic.Source source, String name, UUID controller) {
   }

   private static final class Modes {
      final ScooterMusic.Source source;
      boolean autoplay;
      boolean shuffle;
      List<String> playlist = List.of();

      Modes(ScooterMusic.Source var1) {
         this.source = var1;
      }
   }

   private static final class Playback {
      final ScooterMusic.Source source;
      final UUID session;
      final byte[] data;
      final Song song;
      final String name;
      final UUID controller;
      final Map<UUID, Integer> sent;
      final Map<UUID, String> descriptions = new HashMap<>();
      long began;
      long until;
      long endAfter;
      long suspendedAt = -1L;
      boolean started;

      Playback(ScooterMusic.Source var1, UUID var2, byte[] var3, Song var4, String var5, UUID var6, long var7, Map<UUID, Integer> var9) {
         this.source = var1;
         this.session = var2;
         this.data = var3;
         this.song = var4;
         this.name = var5;
         this.controller = var6;
         this.began = var7;
         this.sent = var9;
         this.until = var7 + (long)var4.durationTicks() + 600L;
         this.endAfter = var7 + (long)var4.durationTicks() + 100L;
      }
   }

   static final class Source {
      final UUID id;
      final Scooter scooter;
      final ServerPlayer wearer;
      final UUID wornToken;
      final ServerLevel level;
      final BlockPos block;
      boolean blockLoop;

      Source(Scooter var1) {
         this.id = var1.getUUID();
         this.scooter = var1;
         this.wearer = null;
         this.wornToken = null;
         this.level = (ServerLevel)var1.level();
         this.block = null;
      }

      Source(ServerPlayer var1) {
         this.id = var1.getUUID();
         this.scooter = null;
         this.wearer = var1;
         this.level = var1.serverLevel();
         this.block = null;
         this.wornToken = WearableJukebox.token(WearableJukebox.equipped(var1));
      }

      Source(ServerLevel var1, BlockPos var2) {
         this.level = var1;
         this.block = var2.immutable();
         this.scooter = null;
         this.wearer = null;
         this.wornToken = null;
         this.id = UUID.nameUUIDFromBytes(("goplanska-jukebox:" + var1.dimension().location() + ":" + var2.asLong()).getBytes(StandardCharsets.UTF_8));
      }

      boolean valid() {
         if (this.wearer != null) {
            return this.wearer.isAlive()
               && !this.wearer.isRemoved()
               && this.wearer.level() == this.level
               && this.level.getServer().getPlayerList().getPlayer(this.id) == this.wearer
               && WearableJukebox.valid(this.wearer, this.wornToken);
         } else {
            return this.scooter != null
               ? !this.scooter.isRemoved() && this.scooter.level() == this.level
               : this.level.hasChunkAt(this.block) && this.level.getBlockEntity(this.block) instanceof JukeboxBlockEntity;
         }
      }

      boolean suspended() {
         return this.block != null && (this.blockLoop || ScooterMusic.autoplay(this)) && !this.level.hasChunkAt(this.block);
      }

      boolean retained() {
         return this.valid() || this.suspended();
      }

      BlockPos position() {
         return this.wearer != null ? this.wearer.blockPosition() : (this.scooter != null ? this.scooter.blockPosition() : this.block);
      }

      double distance(ServerPlayer var1) {
         return this.wearer != null
            ? var1.distanceToSqr(this.wearer)
            : (
               this.scooter != null
                  ? var1.distanceToSqr(this.scooter)
                  : var1.distanceToSqr((double)this.block.getX() + 0.5, (double)this.block.getY() + 0.5, (double)this.block.getZ() + 0.5)
            );
      }

      boolean looping() {
         return this.wearer != null
            ? WearableJukebox.looping(WearableJukebox.equipped(this.wearer))
            : (this.scooter != null ? ScooterMusic.looping(this.scooter) : this.blockLoop);
      }
   }
}
