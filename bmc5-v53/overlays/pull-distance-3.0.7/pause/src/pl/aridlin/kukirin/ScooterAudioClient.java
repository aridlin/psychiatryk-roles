package pl.aridlin.kukirin;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.BooleanSupplier;
import javax.sound.sampled.AudioFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.JukeboxPlayable;
import net.minecraft.world.item.JukeboxSong;
import net.minecraft.world.level.block.entity.JukeboxBlockEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.ClientTickEvent.Post;
import org.slf4j.LoggerFactory;
import pl.aridlin.kukirin.ScooterWav.Pcm;
import pl.aridlin.psychiatrykroles.jukebox.WearableJukebox;

@EventBusSubscriber(
   modid = "goplanska_kukirin",
   value = {Dist.CLIENT}
)
public final class ScooterAudioClient {
   private static final Map<UUID, ScooterAudioClient.Transfer> transfers = new HashMap<>();
   private static final Map<UUID, ScooterAudioClient.Moving> sounds = new HashMap<>();
   private static final Map<UUID, UUID> decoding = new HashMap<>();
   private static final Map<UUID, Integer> discs = new HashMap<>();
   private static final Map<UUID, ScooterAudioUrlPacket> urls = new HashMap<>();
   private static final Map<UUID, ScooterAudioClient.Ready> ready = new HashMap<>();
   private static final Map<UUID, ScooterAudioClient.Cached> cached = new LinkedHashMap<>();
   private static final Map<UUID, UUID> finished = new LinkedHashMap<UUID, UUID>() {
      @Override
      protected boolean removeEldestEntry(Entry<UUID, UUID> var1) {
         return this.size() > 256;
      }
   };
   private static final Map<UUID, UUID> preparationGenerations = new java.util.concurrent.ConcurrentHashMap<>();
   private static final Map<UUID, Timeline> timelines = new java.util.concurrent.ConcurrentHashMap<>();
   private record Timeline(ScooterAudioUrlPacket packet,long receivedNanos) {}
   private static double positionFor(UUID source) {
      Timeline timeline = timelines.get(source);
      return timeline == null ? 0 : MusicTimeline.position(timeline.packet.elapsedMillis(), timeline.receivedNanos,
         System.nanoTime(), timeline.packet.durationTicks(), timeline.packet.loop(), timeline.packet.paused());
   }
   private static boolean audioAllowed(UUID source, UUID session) {
      Timeline timeline = timelines.get(source);
      return timeline == null || session == null || timeline.packet.session().equals(session) && !timeline.packet.paused() && timeline.packet.volume() > 0;
   }
   private static float sourceGain(UUID source) {
      Timeline timeline = timelines.get(source);
      return timeline == null ? 1.0F : timeline.packet.volume() / 100.0F;
   }
   private static void pauseAudio(UUID source, ScooterAudioUrlPacket packet) {
      resync(source); // Stops the channel without removing its song overlay or shared timeline.
      preparationGenerations.remove(source); urls.remove(source); decoding.remove(source); transfers.remove(source); discardReady(source);
      Cached retained = cached.get(source);
      if (retained != null && retained.session.equals(packet.session())) retained.packet = packet;
   }
   private static Entity find(ScooterAudioUrlPacket packet) {
      ClientLevel level = Minecraft.getInstance().level;
      Entity entity = level == null || packet.entityId() < 0 ? null : level.getEntity(packet.entityId());
      return entity != null ? entity : find(packet.source());
   }
   private static void resync(UUID source) {
      Moving moving = sounds.remove(source);
      if (moving != null) { moving.cancel(); Minecraft.getInstance().getSoundManager().stop(moving); }
   }
   private static ClientLevel lastLevel;
   private static long clientTicks;

   private static void cache(UUID var0, ScooterAudioClient.Cached var1) {
      if (!cached.containsKey(var0) && cached.size() >= 4) {
         UUID var2 = cached.keySet().stream().filter(var0x -> !sounds.containsKey(var0x)).findFirst().orElse(null);
         if (var2 == null) {
            return;
         }

         cached.remove(var2);
      }

      cached.put(var0, var1);
   }

   public static void receive(ScooterAudioPacket var0) {
      Minecraft var1 = Minecraft.getInstance();
      syncLevel(var1);
      UUID var2 = var0.scooter();
      if (var0.total() == 0) {
         preparationGenerations.remove(var2);
         Timeline timeline = timelines.get(var2);
         if (timeline != null && timeline.packet.session().equals(var0.session())) timelines.remove(var2);
         ScooterAudioClient.Cached var8 = cached.get(var2);
         if (var8 != null && var8.session.equals(var0.session())) {
            cached.remove(var2);
         }

         if (var0.session().equals(finished.get(var2))) {
            finished.remove(var2);
         }

         ScooterAudioUrlPacket var9 = urls.get(var2);
         if (var9 != null && var9.session().equals(var0.session())) {
            urls.remove(var2);
         }

         ScooterAudioClient.Transfer var10 = transfers.get(var2);
         if (var10 != null && var10.session.equals(var0.session())) {
            transfers.remove(var2);
         }

         if (var0.session().equals(decoding.get(var2))) {
            decoding.remove(var2);
         }

         ScooterAudioClient.Ready var6 = ready.get(var2);
         if (var6 != null && var6.session.equals(var0.session())) {
            discardReady(var2);
         }

         ScooterAudioClient.Moving var7 = sounds.get(var2);
         if (var7 != null && var0.session().equals(var7.session)) {
            remove(var2);
         }
      } else if (var0.total() >= 44
         && var0.total() <= 16777216
         && var0.offset() >= 0
         && var0.bytes().length != 0
         && var0.bytes().length <= 32768
         && (long)var0.offset() + (long)var0.bytes().length <= (long)var0.total()) {
         ScooterAudioClient.Transfer var3 = transfers.get(var2);
         if (var0.offset() == 0) {
            cached.remove(var2);
            urls.remove(var2);
            discardReady(var2);
            if (transfers.size() + decoding.size() >= 4 && !transfers.containsKey(var2)) {
               return;
            }

            var3 = new ScooterAudioClient.Transfer(var0);
            transfers.put(var2, var3);
         }

         if (var3 != null && var3.session.equals(var0.session()) && var3.offset == var0.offset() && var3.bytes.length == var0.total()) {
            System.arraycopy(var0.bytes(), 0, var3.bytes, var3.offset, var0.bytes().length);
            var3.offset = var3.offset + var0.bytes().length;
            var3.touched = System.nanoTime();
            if (var3.offset == var3.bytes.length) {
               transfers.remove(var2);
               decoding.put(var2, var0.session());
               byte[] var4 = var3.bytes;
               ClientLevel var5 = var1.level;
               CompletableFuture.<Pcm>supplyAsync(() -> {
                  try {
                     return ScooterWav.decode(var4);
                  } catch (Exception var2x) {
                     throw new CompletionException(var2x);
                  }
               }).whenComplete((var4x, var5x) -> var1.execute(() -> {
                     if (var0.session().equals(decoding.get(var2)) && var1.level == var5) {
                        decoding.remove(var2);
                        if (var5x != null) {
                           LoggerFactory.getLogger("ScooterAudio").warn("WAV decoding failed ({}).", var5x.getClass().getSimpleName());
                           if (var1.player != null) {
                              var1.player.displayClientMessage(Component.literal("Scooter WAV could not be decoded."), false);
                           }
                        } else {
                           if (find(var2) instanceof Scooter var7x) {
                              remove(var2);
                              ScooterAudioClient.Moving var8x = new ScooterAudioClient.Moving(var7x, var0.session(), var4x);
                              sounds.put(var2, var8x);
                              var1.getSoundManager().play(var8x);
                           }
                        }
                     }
                  }));
            }
         }
      }
   }

   private static Entity find(UUID var0) {
      ClientLevel var1 = Minecraft.getInstance().level;
      if (var1 == null) {
         return null;
      } else {
         Player var2 = var1.getPlayerByUUID(var0);
         if (var2 != null) {
            return var2;
         } else {
            for (Entity var4 : var1.entitiesForRendering()) {
               if (var4 instanceof Scooter && var4.getUUID().equals(var0)) {
                  return var4;
               }
            }

            return null;
         }
      }
   }

   private static void discardReady(UUID source) {
      Ready waiting = ready.remove(source);
      if (waiting != null && waiting.stream != null) waiting.stream.closeAsync();
   }

   private static void remove(UUID var0) {
      ScooterAudioClient.Moving var1 = sounds.remove(var0);
      if (var1 != null) {
         var1.cancel();
         Minecraft.getInstance().getSoundManager().stop(var1);
      }

      MusicNowPlayingClient.removed(var0, var1 == null ? null : var1.session);
   }

   @SubscribeEvent
   public static void tick(Post var0) {
      Minecraft var1 = Minecraft.getInstance();
      syncLevel(var1);
      if (var1.level != null) {
         long var2 = System.nanoTime();
         clientTicks++;
         transfers.entrySet().removeIf(var2x -> var2 - var2x.getValue().touched > 30000000000L);

         for (UUID var5 : new ArrayList<>(sounds.keySet())) {
            ScooterAudioClient.Moving var6 = sounds.get(var5);
            boolean var7 = (var6.pcm != null || var6.stream != null || var6.session != null) && clientTicks - var6.createdClientTick >= 20L && !var1.getSoundManager().isActive(var6);
            if (var6.isStopped() || var7) {
               remove(var5);
               ScooterAudioClient.Cached var8 = cached.get(var5);
               if (var6.session != null && !var6.repeatAudio && var6.streamEnded) {
                  finished.put(var5, var6.session);
                  ScooterMusicScreen.send("ended", var5 + ":" + var6.session);
               }
            } else if (var6.session != null && var6.age >= 1 && !var6.startNotified && var6.streamPrepared && var1.getSoundManager().isActive(var6)) {
               var6.startNotified = true;
               var6.audioBeganNanos = System.nanoTime();
               ScooterMusicScreen.send("started", var5 + ":" + var6.session);
            }
         }

         for (UUID var14 : new ArrayList<>(cached.keySet())) {
            resumeCached(var14);
         }

         for (UUID var15 : new ArrayList<>(ready.keySet())) {
            startReady(var15);
         }

         for (Entity var16 : var1.level.entitiesForRendering()) {
            if (var16 instanceof Scooter) {
               Scooter var17 = (Scooter)var16;
               int var18 = var17.discSerial();
               if (discs.getOrDefault(var17.getUUID(), 0) != var18) {
                  discs.put(var17.getUUID(), var18);
                  ScooterAudioClient.Moving var19 = sounds.get(var17.getUUID());
                  if (var19 != null && var19.pcm == null && var19.stream == null) {
                     remove(var17.getUUID());
                  }

                  if (var17.discPlaying()) {
                     urls.remove(var17.getUUID());
                     cached.remove(var17.getUUID());
                     discardReady(var17.getUUID());
                     decoding.remove(var17.getUUID());
                     remove(var17.getUUID());
                     ItemStack var9 = ScooterMusic.disc(var17.getItemBySlot(EquipmentSlot.FEET), var17);
                     JukeboxPlayable var10 = (JukeboxPlayable)var9.get(DataComponents.JUKEBOX_PLAYABLE);
                     if (var10 != null) {
                        var10.song()
                           .unwrap(var17.registryAccess())
                           .ifPresent(
                              var2x -> {
                                 ScooterAudioClient.Moving var3 = new ScooterAudioClient.Moving(
                                    var17, (SoundEvent)((JukeboxSong)var2x.value()).soundEvent().value(), ((JukeboxSong)var2x.value()).lengthInTicks()
                                 );
                                 sounds.put(var17.getUUID(), var3);
                                 var1.getSoundManager().play(var3);
                              }
                           );
                     }
                  }
               }
            }
         }

         MusicNowPlayingClient.observe(sounds.values());
      }
   }

   private static void syncLevel(Minecraft var0) {
      if (var0.level != lastLevel) {
         for (UUID var2 : new ArrayList<>(sounds.keySet())) {
            remove(var2);
         }

         transfers.clear();
         decoding.clear();
         discs.clear();
         urls.clear();
         for (Ready waiting : ready.values()) if (waiting.stream != null) waiting.stream.closeAsync();
         ready.clear();
         finished.clear();
         cached.clear();
         timelines.clear();
         preparationGenerations.clear();
         lastLevel = var0.level;
      }
   }

   public static void receive(ScooterAudioUrlPacket var0) {
      Minecraft var1 = Minecraft.getInstance();
      syncLevel(var1);
      if (var1.level != null) {
         UUID var2 = var0.source();
         timelines.put(var2, new Timeline(var0, System.nanoTime()));
         if (var0.paused() || var0.volume() == 0) { pauseAudio(var2, var0); return; }
         Moving existing = sounds.get(var2);
         if (existing != null && var0.session().equals(existing.session)
            && (existing.entity != (var0.block() ? null : find(var0))
               || existing.startNotified && MusicTimeline.drift(existing.estimatedPosition(), positionFor(var2), var0.durationTicks(), var0.loop()) > 0.75)) resync(var2);
         if (!var0.session().equals(finished.get(var2)) || var0.loop()) {
            finished.remove(var2);
            ScooterAudioClient.Cached var3 = cached.get(var2);
            if (var3 != null
               && var3.session.equals(var0.session())
               && var3.level == var1.level
               && var3.packet.sha256().equals(var0.sha256())
               && var3.packet.bytes() == var0.bytes()) {
               var3.packet = var0;
            } else if (var3 != null) {
               cached.remove(var2);
               var3 = null;
            }

            ScooterAudioClient.Moving var4 = sounds.get(var2);
            if (var4 != null && var0.session().equals(var4.session) && !var4.isStopped()) {
               var4.loopOverride = var0.loop();
               var4.repeatAudio = var0.loop();
            } else if (var3 != null) {
               resumeCached(var2);
            } else {
               ScooterAudioUrlPacket var5 = urls.get(var2);
               if (var5 != null && var5.session().equals(var0.session())) {
                  urls.put(var2, var0);
                  startReady(var2);
               } else {
                  HashSet<UUID> var6 = new HashSet<>();
                  for (Map.Entry<UUID, Moving> entry : sounds.entrySet()) if (entry.getValue().pcm != null) var6.add(entry.getKey());
                  var6.addAll(transfers.keySet());
                  for (Map.Entry<UUID, ScooterAudioUrlPacket> entry : urls.entrySet()) if (entry.getValue().url().startsWith("bundled:")) var6.add(entry.getKey());
                  for (Map.Entry<UUID, Ready> entry : ready.entrySet()) if (entry.getValue().pcm != null) var6.add(entry.getKey());
                  if (!var0.url().startsWith("bundled:") || var6.size() < 4 || var6.contains(var2)) {
                     if (!var0.block() || !var1.level.hasChunkAt(var0.position()) || var1.level.getBlockEntity(var0.position()) instanceof JukeboxBlockEntity) {
                        remove(var2);
                        transfers.remove(var2);
                        discardReady(var2);
                        decoding.put(var2, var0.session());
                        urls.put(var2, var0);
                        UUID preparation = UUID.randomUUID(); preparationGenerations.put(var2, preparation);
                        ClientLevel var7 = var1.level;
                        Path var8 = FMLPaths.GAMEDIR.get().resolve("cache/goplanska-scooter/music");
                        if (var0.url().startsWith("minecraftsound:")) {
                           nativeReady(var0, var1, var7);
                           return;
                        }
                        if (!var0.url().startsWith("bundled:")) {
                           streamRemote(var0, var1, var7);
                           return;
                        }
                        (var0.url().startsWith("bundled:")
                              ? bundled(var0)
                              : ScooterMusicHttpCache.fetch(var8, var0.url(), var0.bearer(), var0.sha256(), var0.bytes()))
                           .<Pcm>thenApplyAsync(var0x -> {
                              try {
                                 return ScooterWav.decode(var0x);
                              } catch (Exception var2x) {
                                 throw new CompletionException(var2x);
                              }
                           })
                           .whenComplete((var4x, var5x) -> var1.execute(() -> {
                                 ScooterAudioUrlPacket var6x = urls.get(var2);
                                 if (var6x != null && preparation.equals(preparationGenerations.get(var2)) && var6x.session().equals(var0.session()) && var0.session().equals(decoding.get(var2)) && var1.level == var7
                                    )
                                  {
                                    decoding.remove(var2);
                                    if (var5x != null) {
                                       urls.remove(var2);
                                       finished.put(var2, var0.session());
                                       if (var1.player != null) {
                                          var1.player.displayClientMessage(Component.literal("Song download failed; reopen Music to retry."), false);
                                       }
                                    } else {
                                       ready.put(var2, new ScooterAudioClient.Ready(var0.session(), var4x, var7, System.nanoTime() + 30000000000L));
                                       cache(var2, new ScooterAudioClient.Cached(var0.session(), var4x, var7, var6x));
                                       startReady(var2);
                                    }
                                 }
                              }));
                     }
                  }
               }
            }
         }
      }
   }

   private static void nativeReady(ScooterAudioUrlPacket packet, Minecraft minecraft, ClientLevel level) {
      if (packet.paused() || !audioAllowed(packet.source(), packet.session())) return;
      ScooterMusicSources.Song song = VanillaMusicCatalog.forUrl(packet.url());
      if (song == null) { urls.remove(packet.source()); decoding.remove(packet.source()); return; }
      UUID source = packet.source();
      Entity entity = packet.block() ? null : find(packet);
      if (!packet.block() && entity == null) { urls.remove(source); decoding.remove(source); return; }
      decoding.remove(source); urls.remove(source);
      SoundEvent event = SoundEvent.createVariableRangeEvent(net.minecraft.resources.ResourceLocation.parse(packet.url().substring(15)));
      Moving sound = new Moving(source, entity, level, packet.block() ? packet.position() : null,
         packet.session(), event, song.durationTicks(), packet.loop());
      sounds.put(source, sound); minecraft.getSoundManager().play(sound);
   }

   private static void streamRemote(ScooterAudioUrlPacket packet, Minecraft minecraft, ClientLevel level) {
      UUID preparation = preparationGenerations.get(packet.source());
      ScooterHttpAudioStream.open(packet.url(), packet.bearer(), packet.sha256()).whenComplete((stream, failure) ->
         minecraft.execute(() -> {
            UUID source = packet.source();
            ScooterAudioUrlPacket current = urls.get(source);
            if (current == null || preparation == null || !preparation.equals(preparationGenerations.get(source)) || !current.session().equals(packet.session())
                || !packet.session().equals(decoding.get(source)) || minecraft.level != level) {
               if (stream != null) stream.closeAsync();
               return;
            }
            decoding.remove(source);
            if (failure != null) {
               urls.remove(source); finished.put(source, packet.session());
               if (minecraft.player != null) minecraft.player.displayClientMessage(Component.literal("Song stream unavailable; reopen Music to retry."), false);
               return;
            }
            ready.put(source, new Ready(packet.session(), null, level, System.nanoTime() + 30000000000L, stream));
            startReady(source);
         }));
   }

   private static void resumeCached(UUID var0) {
      ScooterAudioClient.Cached var1 = cached.get(var0);
      if (var1 != null
         && !var1.packet.paused() && var1.packet.volume() > 0
         && (var1.packet.loop() || !var1.session.equals(finished.get(var0)))
         && !sounds.containsKey(var0)
         && !decoding.containsKey(var0)
         && !transfers.containsKey(var0)
         && !ready.containsKey(var0)
         && clientTicks >= var1.retryAfterTick) {
         Minecraft var2 = Minecraft.getInstance();
         if (var2.level != var1.level) {
            cached.remove(var0);
         } else {
            ScooterAudioUrlPacket var3 = var1.packet;
            if (var3.block()) {
               if (!var2.level.hasChunkAt(var3.position())) {
                  return;
               }

               if (!(var2.level.getBlockEntity(var3.position()) instanceof JukeboxBlockEntity)) {
                  cached.remove(var0);
                  return;
               }
            } else {
               Entity var4 = find(var3);
               if (var4 == null || var4.isRemoved()) {
                  return;
               }

               if (var4 instanceof Player var5 && WearableJukebox.equipped(var5).isEmpty()) {
                  cached.remove(var0);
                  return;
               }
            }

            var1.retryAfterTick = clientTicks + 100L;
            urls.put(var0, var3);
            ready.put(var0, new ScooterAudioClient.Ready(var1.session, var1.pcm, var1.level, System.nanoTime() + 30000000000L));
            startReady(var0);
         }
      }
   }

   private static void startReady(UUID var0) {
      ScooterAudioClient.Ready var1 = ready.get(var0);
      if (var1 != null) {
         Minecraft var2 = Minecraft.getInstance();
         ScooterAudioUrlPacket var3 = urls.get(var0);
         if (var3 != null && var1.session.equals(var3.session()) && !var3.paused() && audioAllowed(var0, var1.session) && var2.level == var1.level && System.nanoTime() <= var1.expires) {
            Entity var4 = var3.block() ? null : find(var3);
            if (var3.block() || var4 != null && (!(var4 instanceof Player var5) || !WearableJukebox.equipped(var5).isEmpty())) {
               if (!var3.block() || var1.level.hasChunkAt(var3.position())) {
                  if (var3.block() && !(var1.level.getBlockEntity(var3.position()) instanceof JukeboxBlockEntity)) {
                     discardReady(var0);
                     urls.remove(var0);
                     cached.remove(var0);
                  } else {
                     ready.remove(var0);
                     urls.remove(var0);
                     ScooterAudioClient.Moving var6 = var1.stream != null
                        ? new ScooterAudioClient.Moving(var0, var4, var1.level, var3.block() ? var3.position() : null, var1.session, var1.stream, var3.loop())
                        : new ScooterAudioClient.Moving(var0, var4, var1.level, var3.block() ? var3.position() : null, var1.session, var1.pcm, var3.loop());
                     sounds.put(var0, var6);
                     var2.getSoundManager().play(var6);
                  }
               }
            }
         } else {
            discardReady(var0);
            if (var3 != null && var1.session.equals(var3.session())) {
               urls.remove(var0);
            }
         }
      }
   }

   private static CompletableFuture<byte[]> bundled(ScooterAudioUrlPacket var0) {
      return CompletableFuture.supplyAsync(() -> {
         try {
            String var1 = var0.url().substring(8);
            if (!var1.equals("chiki_ride.wav") && !var1.equals("night_motor.wav")) {
               throw new IOException();
            } else {
               byte[] var2;
               try (InputStream var3 = ScooterAudioClient.class.getResourceAsStream("/data/goplanska_kukirin/music/" + var1)) {
                  if (var3 == null) {
                     throw new IOException();
                  }

                  var2 = var3.readNBytes(16777217);
               }

               if (!ScooterMusicHttpCache.valid(var2, var0.sha256(), var0.bytes())) {
                  throw new IOException();
               } else {
                  return var2;
               }
            }
         } catch (Exception var8) {
            throw new CompletionException(new IOException("Bundled song unavailable"));
         }
      });
   }

   private ScooterAudioClient() {
   }

   private static final class Cached {
      final UUID session;
      final Pcm pcm;
      final ClientLevel level;
      ScooterAudioUrlPacket packet;
      long retryAfterTick;

      Cached(UUID var1, Pcm var2, ClientLevel var3, ScooterAudioUrlPacket var4) {
         this.session = var1;
         this.pcm = var2;
         this.level = var3;
         this.packet = var4;
      }
   }

   public static final class Moving extends AbstractTickableSoundInstance {
      final UUID source;
      final Entity entity;
      final Scooter scooter;
      final ClientLevel level;
      final BlockPos block;
      final UUID session;
      final Pcm pcm;
      final ScooterHttpAudioStream stream;
      final int duration;
      volatile Boolean loopOverride;
      private volatile boolean repeatAudio;
      private volatile boolean streamEnded;
      int age;
      private final long createdClientTick = ScooterAudioClient.clientTicks;
      private boolean startNotified;
      private volatile boolean streamPrepared;
      private volatile long audioBeganNanos = System.nanoTime();
      private volatile double initialSeconds;
      double estimatedPosition() {
         double elapsed = initialSeconds + Math.max(0,System.nanoTime() - audioBeganNanos) / 1_000_000_000.0;
         Timeline timeline = timelines.get(source);
         double length = timeline == null ? duration / 20.0 : timeline.packet.durationTicks() / 20.0;
         return repeatAudio && length > 0 ? elapsed % length : elapsed;
      }
      private volatile boolean active = true;

      Moving(Scooter var1, UUID var2, Pcm var3) {
         this(var1.getUUID(), var1, (ClientLevel)var1.level(), null, var2, var3, null);
      }

      Moving(UUID var1, Entity var2, ClientLevel var3, BlockPos var4, UUID var5, Pcm var6, Boolean var7) {
         super((SoundEvent)Kukirin.WAV.get(), SoundSource.BLOCKS, RandomSource.create());
         this.source = var1;
         this.entity = var2;
         this.scooter = var2 instanceof Scooter var8 ? var8 : null;
         this.level = var3;
         this.block = var4;
         this.session = var5;
         this.pcm = var6;
         this.stream = null;
         this.loopOverride = var7;
         this.repeatAudio = Boolean.TRUE.equals(var7) || var7 == null && this.scooter != null && ScooterMusic.looping(this.scooter);
         this.duration = (int)Math.ceil((double)((float)var6.samples().length / var6.format().getFrameRate() / (float)var6.format().getFrameSize() * 20.0F));
         this.relative = false;
         this.looping = false;
         this.position();
         this.volume = ScooterClientOptions.get().musicVolume * sourceGain(this.source);
      }

      Moving(UUID source, Entity entity, ClientLevel level, BlockPos block, UUID session,
             ScooterHttpAudioStream stream, Boolean loop) {
         super((SoundEvent)Kukirin.WAV.get(), SoundSource.BLOCKS, RandomSource.create());
         this.source = source; this.entity = entity; this.scooter = entity instanceof Scooter scooter ? scooter : null;
         this.level = level; this.block = block; this.session = session; this.pcm = null; this.stream = stream;
         this.duration = 0; this.loopOverride = loop; this.repeatAudio = Boolean.TRUE.equals(loop);
         this.relative = false; this.looping = false; this.position(); this.volume = ScooterClientOptions.get().musicVolume * sourceGain(this.source);
      }

      Moving(UUID source, Entity entity, ClientLevel level, BlockPos block, UUID session,
             SoundEvent sound, int duration, Boolean loop) {
         super(sound, SoundSource.BLOCKS, RandomSource.create());
         this.source = source; this.entity = entity; this.scooter = entity instanceof Scooter scooter ? scooter : null;
         this.level = level; this.block = block; this.session = session; this.pcm = null; this.stream = null;
         this.duration = duration; this.loopOverride = loop; this.repeatAudio = Boolean.TRUE.equals(loop);
         this.relative = false; this.looping = this.repeatAudio; this.position(); this.volume = ScooterClientOptions.get().musicVolume * sourceGain(this.source);
      }

      Moving(Scooter var1, SoundEvent var2, int var3) {
         super(var2, SoundSource.BLOCKS, RandomSource.create());
         this.source = var1.getUUID();
         this.entity = var1;
         this.scooter = var1;
         this.level = (ClientLevel)var1.level();
         this.block = null;
         this.session = null;
         this.pcm = null;
         this.stream = null;
         this.duration = var3;
         this.relative = false;
         this.looping = false;
         this.position();
         this.volume = ScooterClientOptions.get().musicVolume * sourceGain(this.source);
      }

      private void position() {
         if (this.entity instanceof Player var1) {
            double var4 = Math.toRadians((double)var1.yBodyRot);
            this.x = var1.getX() + Math.sin(var4) * 0.24;
            this.y = var1.getY() + (double)var1.getBbHeight() * 0.62;
            this.z = var1.getZ() - Math.cos(var4) * 0.24;
         } else if (this.entity != null) {
            this.x = this.entity.getX();
            this.y = this.entity.getY() + 0.7;
            this.z = this.entity.getZ();
         } else {
            this.x = (double)this.block.getX() + 0.5;
            this.y = (double)this.block.getY() + 0.5;
            this.z = (double)this.block.getZ() + 0.5;
         }
      }

      void cancel() {
         this.active = false;
         if (this.stream != null) this.stream.closeAsync();
         this.stop();
      }

      public SoundSource getSource() {
         return MusicCategoryConfig.client();
      }

      public boolean isEntitySource() {
         return this.entity != null;
      }

      public boolean physicsActive() {
         return this.active;
      }

      public void tick() {
         if (!this.isStopped() && ScooterAudioClient.sounds.get(this.source) == this) {
            boolean var1 = this.entity != null
               ? !this.entity.isRemoved()
                  && this.entity.level() == this.level
                  && (!(this.entity instanceof Player var2) || !WearableJukebox.equipped(var2).isEmpty())
               : this.level.getBlockEntity(this.block) instanceof JukeboxBlockEntity;
            if (Minecraft.getInstance().level == this.level && var1) {
               this.repeatAudio = this.loopOverride != null ? this.loopOverride : this.scooter != null && ScooterMusic.looping(this.scooter);
               if (++this.age >= this.duration && this.pcm == null && this.stream == null && !this.repeatAudio) {
                  this.streamEnded = true;
                  this.cancel();
               } else {
                  this.position();
                  this.volume = ScooterClientOptions.get().audioEnabled ? ScooterClientOptions.get().musicVolume * sourceGain(this.source) : 0.0F;
               }
            } else {
               if (Minecraft.getInstance().level != this.level
                  || this.entity instanceof Player var4 && WearableJukebox.equipped(var4).isEmpty()
                  || this.block != null && this.level.hasChunkAt(this.block)) {
                  ScooterAudioClient.cached.remove(this.source);
               }

               this.cancel();
            }
         } else {
            this.cancel();
         }
      }

      public CompletableFuture<AudioStream> getStream(SoundBufferLibrary library, Sound sound, boolean looping) {
         if (!active || !audioAllowed(source, session)) return CompletableFuture.failedFuture(new IOException("Music is paused"));
         initialSeconds = positionFor(source);
         age = (int)Math.min(Integer.MAX_VALUE, initialSeconds * 20);
         audioBeganNanos = System.nanoTime();
         if (this.stream != null) return this.stream.prepare(() -> positionFor(source)).thenApply(prepared -> {
            if (!active || !audioAllowed(source, session)) { prepared.closeAsync(); throw new CompletionException(new IOException("Music paused while preparing")); }
            initialSeconds = prepared.positionSeconds(); audioBeganNanos = System.nanoTime(); streamPrepared = true;
            return prepared.configure(() -> this.active && this.repeatAudio, () -> this.streamEnded = true);
         });
         if (this.pcm != null) { streamPrepared = true; return CompletableFuture.completedFuture(
            new PcmStream(this.pcm, () -> this.active && this.repeatAudio, () -> this.streamEnded = true, initialSeconds)); }
         return super.getStream(library, sound, looping).thenApplyAsync(stream -> {
            try {
               if (!active || !audioAllowed(source, session)) throw new IOException("Music paused while opening");
               ScooterAudioSeekStream prepared = ScooterAudioSeekStream.at(stream, () -> positionFor(source));
               if (!active || !audioAllowed(source, session)) { prepared.close(); throw new IOException("Music paused while preparing"); }
               initialSeconds = prepared.positionSeconds(); audioBeganNanos = System.nanoTime(); streamPrepared = true; return prepared;
            } catch (IOException failure) { try { stream.close(); } catch (IOException ignored) {} throw new CompletionException(failure); }
         });
      }
   }

   static final class PcmStream implements AudioStream {
      private final Pcm pcm;
      private final BooleanSupplier repeat;
      private final Runnable ended;
      private int offset;
      private boolean closed;

      PcmStream(Pcm var1) {
         this(var1, () -> false, () -> {
         });
      }

      PcmStream(Pcm var1, BooleanSupplier var2, Runnable var3) { this(var1,var2,var3,0); }
      PcmStream(Pcm var1, BooleanSupplier var2, Runnable var3, double startSeconds) {
         this.pcm = var1;
         this.repeat = var2;
         this.ended = var3;
         if (var1.samples().length == 0 || var1.samples().length % var1.format().getFrameSize() != 0) {
            throw new IllegalArgumentException("PCM must contain complete frames");
         }
         long start = (long)(Math.max(0,startSeconds) * var1.format().getFrameRate()) * var1.format().getFrameSize();
         this.offset = (int)(var2.getAsBoolean() ? start % var1.samples().length : Math.min(start, var1.samples().length));
      }

      public AudioFormat getFormat() {
         return this.pcm.format();
      }

      public synchronized ByteBuffer read(int var1) {
         if (var1 >= 0 && var1 <= 1048576) {
            int var2 = var1 - var1 % this.pcm.format().getFrameSize();
            ByteBuffer var3 = ByteBuffer.allocateDirect(this.closed ? 0 : var2);

            while (!this.closed && var3.hasRemaining()) {
               if (this.offset == this.pcm.samples().length) {
                  if (!this.repeat.getAsBoolean()) {
                     this.ended.run();
                     break;
                  }

                  this.offset = 0;
               }

               int var4 = Math.min(var3.remaining(), this.pcm.samples().length - this.offset);
               var3.put(this.pcm.samples(), this.offset, var4);
               this.offset += var4;
            }

            return var3.flip();
         } else {
            throw new IllegalArgumentException("PCM buffer size out of bounds");
         }
      }

      public synchronized void close() {
         this.closed = true;
      }
   }

   private static record Ready(UUID session, Pcm pcm, ClientLevel level, long expires, ScooterHttpAudioStream stream) {
      Ready(UUID session, Pcm pcm, ClientLevel level, long expires) { this(session, pcm, level, expires, null); }
   }

   private static final class Transfer {
      final UUID session;
      final byte[] bytes;
      int offset;
      long touched;

      Transfer(ScooterAudioPacket var1) {
         this.session = var1.session();
         this.bytes = new byte[var1.total()];
      }
   }
}
