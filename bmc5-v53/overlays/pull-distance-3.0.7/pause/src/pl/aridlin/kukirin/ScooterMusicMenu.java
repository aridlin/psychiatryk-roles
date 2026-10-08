package pl.aridlin.kukirin;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class ScooterMusicMenu {
   public static void register(RegisterPayloadHandlersEvent var0) {
      PayloadRegistrar var1 = var0.registrar("3");
      var1.playToServer(
         ScooterMusicMenu.Request.TYPE,
         ScooterMusicMenu.Request.CODEC,
         (var0x, var1x) -> var1x.enqueueWork(
               () -> {
                  if (var1x.player() instanceof ServerPlayer var2) {
                     if (var0x.action.equals("started") || var0x.action.equals("ended")) {
                        ScooterMusic.timing(var2, var0x.action, var0x.song);
                     } else if (var0x.action.equals("wear_open")) {
                        WearableJukeboxMusic.open(var2);
                     } else {
                        if (var0x.action.equals("open")) {
                           ScooterJukeboxMusic.forget(var2);
                           WearableJukeboxMusic.forget(var2);
                        } else if (WearableJukeboxMusic.handle(var2, var0x.action, var0x.song) || ScooterJukeboxMusic.handle(var2, var0x.action, var0x.song)) {
                           return;
                        }

                        Scooter var8 = ScooterStorage.target(var2);
                        if (ScooterMusic.permitted(var8, var2)) {
                           ItemStack var4 = var8.getItemBySlot(EquipmentSlot.FEET);
                           ScooterMusic.Source var5 = ScooterMusic.source(var8);
                           String var6 = var0x.action;
                           switch (var6) {
                              case "open":
                                 sendCatalog(var2, new ScooterMusicMenu.Catalog(
                                    ScooterMusic.songs().stream().filter(var0xxx -> var0xxx.length() <= 256).toList(),
                                    ScooterUpgradeRecipe.has(var4, "noteblock"),
                                    ScooterUpgradeRecipe.has(var4, "jukebox"),
                                    ScooterMusic.looping(var8)), false);
                                 ScooterMusic.options(var2, var5);
                                 break;
                              case "play":
                                 ScooterMusic.wav(var2, var0x.song);
                                 break;
                              case "loop":
                                 ScooterMusic.looping(var8, var0x.song.equals("true"));
                                 break;
                              case "autoplay":
                              case "shuffle":
                                 if (ScooterUpgradeRecipe.has(var4, "noteblock")) {
                                    ScooterMusic.mode(var2, var5, var0x.action, var0x.song);
                                 }
                                 break;
                              case "volume":
                                 ScooterMusic.volumeRequest(var2, var5, var0x.song);
                                 break;
                              case "pause":
                              case "resume":
                                 ScooterMusic.pause(var2, var5, var0x.action.equals("pause"));
                                 break;
                              case "previous":
                                 ScooterMusic.previous(var2, var5);
                                 break;
                              case "skip":
                                 ScooterMusic.skip(var2, var5);
                                 break;
                              case "stop":
                                 ScooterMusic.stop(var8);
                                 break;
                              case "disc":
                                 if (ScooterUpgradeRecipe.has(var4, "jukebox")) {
                                    ScooterMusic.stop(var8);
                                    var8.startDisc();
                                 }
                           }
                        }
                     }
                  }
               }
            )
      );
      var1.playToClient(ScooterMusicMenu.Catalog.TYPE, ScooterMusicMenu.Catalog.CODEC, ScooterMusicClient::receive);
      var1.playToClient(ScooterMusicMenu.Options.TYPE, ScooterMusicMenu.Options.CODEC, ScooterMusicClient::receive);
      var1.playToClient(ScooterMusicMenu.CatalogPart.TYPE, ScooterMusicMenu.CatalogPart.CODEC, ScooterMusicClient::receive);
   }

   /** Bound each network payload rather than truncating the server library. */
   public static void sendCatalog(ServerPlayer player, Catalog catalog, boolean imported) {
      sendCatalog(player, catalog, imported, "");
   }

   public static void sendCatalog(ServerPlayer player, Catalog catalog, boolean imported, String focusSong) {
      catalog = new Catalog(catalog.songs(), catalog.wav(), catalog.disc(), catalog.loop(), ScooterMusic.paused(MusicImports.selected(player)), ScooterMusic.volume(MusicImports.selected(player)), ScooterMusic.currentSong(MusicImports.selected(player)), sourceId(MusicImports.selected(player)));
      if (catalog.songs().isEmpty() || catalog.songs().size() <= 64 && (!imported || focusSong.isEmpty())) {
         CustomPacketPayload payload = imported ? new MusicImports.Completed(catalog) : catalog;
         PacketDistributor.sendToPlayer(player, payload, new CustomPacketPayload[0]);
         return;
      }
      UUID transfer = UUID.randomUUID();
      for (int offset = 0; offset < catalog.songs().size(); offset += 64) {
         int end = Math.min(offset + 64, catalog.songs().size());
         PacketDistributor.sendToPlayer(player, new CatalogPart(transfer, offset, end == catalog.songs().size(), imported,
            catalog.wav(), catalog.disc(), catalog.loop(), catalog.paused(), catalog.volume(), catalog.currentSong(), catalog.source(), focusSong, List.copyOf(catalog.songs().subList(offset, end))), new CustomPacketPayload[0]);
      }
   }

   public static record CatalogPart(UUID transfer, int offset, boolean last, boolean imported,
      boolean wav, boolean disc, boolean loop, boolean paused, int volume, String currentSong, UUID source, String focusSong, List<String> songs) implements CustomPacketPayload {
      public CatalogPart(UUID transfer, int offset, boolean last, boolean imported, boolean wav, boolean disc, boolean loop, boolean paused, int volume, String currentSong, String focusSong, List<String> songs) {
         this(transfer, offset, last, imported, wav, disc, loop, paused, volume, currentSong, null, focusSong, songs);
      }
      public CatalogPart(UUID transfer, int offset, boolean last, boolean imported, boolean wav, boolean disc, boolean loop, boolean paused, int volume, String focusSong, List<String> songs) {
         this(transfer, offset, last, imported, wav, disc, loop, paused, volume, "", focusSong, songs);
      }
      public CatalogPart(UUID transfer, int offset, boolean last, boolean imported, boolean wav, boolean disc, boolean loop, boolean paused, String focusSong, List<String> songs) {
         this(transfer, offset, last, imported, wav, disc, loop, paused, 100, focusSong, songs);
      }
      public CatalogPart { if (volume < 0 || volume > 100) throw new IllegalArgumentException("Invalid volume"); }
      public CatalogPart(UUID transfer, int offset, boolean last, boolean imported, boolean wav, boolean disc, boolean loop, String focusSong, List<String> songs) {
         this(transfer, offset, last, imported, wav, disc, loop, false, focusSong, songs);
      }
      public CatalogPart(UUID transfer, int offset, boolean last, boolean imported, boolean wav, boolean disc, boolean loop, List<String> songs) {
         this(transfer, offset, last, imported, wav, disc, loop, false, "", songs);
      }
      public static final Type<CatalogPart> TYPE = new Type<>(ResourceLocation.parse("goplanska_kukirin:music_catalog_part"));
      public static final StreamCodec<RegistryFriendlyByteBuf, CatalogPart> CODEC = new StreamCodec<>() {
         public CatalogPart decode(RegistryFriendlyByteBuf buffer) {
            UUID transfer = buffer.readUUID();
            int offset = buffer.readVarInt();
            boolean last = buffer.readBoolean(), imported = buffer.readBoolean();
            boolean wav = buffer.readBoolean(), disc = buffer.readBoolean(), loop = buffer.readBoolean(), paused = buffer.readBoolean();
            int volume = buffer.readVarInt();
            String currentSong = buffer.readUtf(256);
            UUID source = readSource(buffer);
            String focusSong = buffer.readUtf(256);
            int count = buffer.readVarInt();
            if (offset < 0 || count < 0 || count > 64 || count > buffer.readableBytes()) throw new IllegalArgumentException("Malformed song catalog part");
            List<String> songs = new ArrayList<>();
            for (int i = 0; i < count; i++) songs.add(buffer.readUtf(256));
            return new CatalogPart(transfer, offset, last, imported, wav, disc, loop, paused, volume, currentSong, source, focusSong, List.copyOf(songs));
         }
         public void encode(RegistryFriendlyByteBuf buffer, CatalogPart part) {
            if (part.offset() < 0 || part.songs().size() > 64) throw new IllegalArgumentException("Malformed song catalog part");
            buffer.writeUUID(part.transfer());
            buffer.writeVarInt(part.offset());
            buffer.writeBoolean(part.last()); buffer.writeBoolean(part.imported());
            buffer.writeBoolean(part.wav()); buffer.writeBoolean(part.disc()); buffer.writeBoolean(part.loop()); buffer.writeBoolean(part.paused()); buffer.writeVarInt(part.volume());
            buffer.writeUtf(part.currentSong(), 256);
            writeSource(buffer, part.source());
            buffer.writeUtf(part.focusSong(), 256);
            buffer.writeVarInt(part.songs().size());
            for (String song : part.songs()) buffer.writeUtf(song, 256);
         }
      };
      public Type<CatalogPart> type() { return TYPE; }
   }

   private static UUID sourceId(ScooterMusic.Source source) { return source == null ? null : source.id; }
   private static UUID readSource(RegistryFriendlyByteBuf buffer) { return buffer.readBoolean() ? buffer.readUUID() : null; }
   private static void writeSource(RegistryFriendlyByteBuf buffer, UUID source) { buffer.writeBoolean(source != null); if (source != null) buffer.writeUUID(source); }

   private ScooterMusicMenu() {
   }

   public static record Catalog(List<String> songs, boolean wav, boolean disc, boolean loop, boolean paused, int volume, String currentSong, UUID source) implements CustomPacketPayload {
      public Catalog(List<String> songs, boolean wav, boolean disc, boolean loop, boolean paused, int volume, String currentSong) { this(songs, wav, disc, loop, paused, volume, currentSong, null); }
      public Catalog(List<String> songs, boolean wav, boolean disc, boolean loop, boolean paused, int volume) { this(songs, wav, disc, loop, paused, volume, ""); }
      public Catalog(List<String> songs, boolean wav, boolean disc, boolean loop, boolean paused) { this(songs, wav, disc, loop, paused, 100); }
      public Catalog { if (volume < 0 || volume > 100) throw new IllegalArgumentException("Invalid volume"); }
      public Catalog(List<String> songs, boolean wav, boolean disc, boolean loop) { this(songs, wav, disc, loop, false); }
      public static final Type<ScooterMusicMenu.Catalog> TYPE = new Type(ResourceLocation.parse("goplanska_kukirin:music_catalog"));
      public static final StreamCodec<RegistryFriendlyByteBuf, ScooterMusicMenu.Catalog> CODEC = new StreamCodec<RegistryFriendlyByteBuf, ScooterMusicMenu.Catalog>() {
         public ScooterMusicMenu.Catalog decode(RegistryFriendlyByteBuf var1) {
            int var2 = var1.readVarInt();
            if (var2 >= 0 && var2 <= Math.max(0, var1.readableBytes() - 7)) {
               ArrayList var3 = new ArrayList();

               for (int var4 = 0; var4 < var2; var4++) {
                  var3.add(var1.readUtf(256));
               }

               return new ScooterMusicMenu.Catalog(List.copyOf(var3), var1.readBoolean(), var1.readBoolean(), var1.readBoolean(), var1.readBoolean(), var1.readVarInt(), var1.readUtf(256), readSource(var1));
            } else {
               throw new IllegalArgumentException("Malformed song catalog");
            }
         }

         public void encode(RegistryFriendlyByteBuf var1, ScooterMusicMenu.Catalog var2) {
            var1.writeVarInt(var2.songs.size());

            for (String var4 : var2.songs) {
               var1.writeUtf(var4, 256);
            }

            var1.writeBoolean(var2.wav);
            var1.writeBoolean(var2.disc);
            var1.writeBoolean(var2.loop);
            var1.writeBoolean(var2.paused);
            var1.writeVarInt(var2.volume);
            var1.writeUtf(var2.currentSong, 256);
            writeSource(var1, var2.source);
         }
      };

      public Type<ScooterMusicMenu.Catalog> type() {
         return TYPE;
      }
   }

   public static record Options(boolean autoplay, boolean shuffle, boolean paused, int volume, String currentSong, UUID source) implements CustomPacketPayload {
      public Options(boolean autoplay, boolean shuffle, boolean paused, int volume, String currentSong) { this(autoplay, shuffle, paused, volume, currentSong, null); }
      public Options(boolean autoplay, boolean shuffle, boolean paused, int volume) { this(autoplay, shuffle, paused, volume, ""); }
      public Options(boolean autoplay, boolean shuffle, boolean paused) { this(autoplay, shuffle, paused, 100); }
      public Options { if (volume < 0 || volume > 100) throw new IllegalArgumentException("Invalid volume"); }
      public Options(boolean autoplay, boolean shuffle) { this(autoplay, shuffle, false); }
      public static final Type<ScooterMusicMenu.Options> TYPE = new Type(ResourceLocation.parse("goplanska_kukirin:music_options"));
      public static final StreamCodec<RegistryFriendlyByteBuf, ScooterMusicMenu.Options> CODEC = new StreamCodec<RegistryFriendlyByteBuf, ScooterMusicMenu.Options>() {
         public ScooterMusicMenu.Options decode(RegistryFriendlyByteBuf var1) {
            return new ScooterMusicMenu.Options(var1.readBoolean(), var1.readBoolean(), var1.readBoolean(), var1.readVarInt(), var1.readUtf(256), readSource(var1));
         }

         public void encode(RegistryFriendlyByteBuf var1, ScooterMusicMenu.Options var2) {
            var1.writeBoolean(var2.autoplay);
            var1.writeBoolean(var2.shuffle);
            var1.writeBoolean(var2.paused);
            var1.writeVarInt(var2.volume);
            var1.writeUtf(var2.currentSong, 256);
            writeSource(var1, var2.source);
         }
      };

      public Type<ScooterMusicMenu.Options> type() {
         return TYPE;
      }
   }

   public static record Request(String action, String song) implements CustomPacketPayload {
      public static final Type<ScooterMusicMenu.Request> TYPE = new Type(ResourceLocation.parse("goplanska_kukirin:music_menu"));
      public static final StreamCodec<RegistryFriendlyByteBuf, ScooterMusicMenu.Request> CODEC = new StreamCodec<RegistryFriendlyByteBuf, ScooterMusicMenu.Request>() {
         public ScooterMusicMenu.Request decode(RegistryFriendlyByteBuf var1) {
            return new ScooterMusicMenu.Request(var1.readUtf(16), var1.readUtf(256));
         }

         public void encode(RegistryFriendlyByteBuf var1, ScooterMusicMenu.Request var2) {
            var1.writeUtf(var2.action, 16);
            var1.writeUtf(var2.song, 256);
         }
      };

      public Type<ScooterMusicMenu.Request> type() {
         return TYPE;
      }
   }
}
