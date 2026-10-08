package pl.aridlin.kukirin;

import java.util.ArrayList;
import java.util.List;
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
      PayloadRegistrar var1 = var0.registrar("2");
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
                                 PacketDistributor.sendToPlayer(
                                    var2,
                                    new ScooterMusicMenu.Catalog(
                                       ScooterMusic.songs().stream().filter(var0xxx -> var0xxx.length() <= 256).limit(1024L).toList(),
                                       ScooterUpgradeRecipe.has(var4, "noteblock"),
                                       ScooterUpgradeRecipe.has(var4, "jukebox"),
                                       ScooterMusic.looping(var8)
                                    ),
                                    new CustomPacketPayload[0]
                                 );
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
   }

   private ScooterMusicMenu() {
   }

   public static record Catalog(List<String> songs, boolean wav, boolean disc, boolean loop) implements CustomPacketPayload {
      public static final Type<ScooterMusicMenu.Catalog> TYPE = new Type(ResourceLocation.parse("goplanska_kukirin:music_catalog"));
      public static final StreamCodec<RegistryFriendlyByteBuf, ScooterMusicMenu.Catalog> CODEC = new StreamCodec<RegistryFriendlyByteBuf, ScooterMusicMenu.Catalog>() {
         public ScooterMusicMenu.Catalog decode(RegistryFriendlyByteBuf var1) {
            int var2 = var1.readVarInt();
            if (var2 >= 0 && var2 <= 1024) {
               ArrayList var3 = new ArrayList();

               for (int var4 = 0; var4 < var2; var4++) {
                  var3.add(var1.readUtf(256));
               }

               return new ScooterMusicMenu.Catalog(List.copyOf(var3), var1.readBoolean(), var1.readBoolean(), var1.readBoolean());
            } else {
               throw new IllegalArgumentException("Song catalog limit");
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
         }
      };

      public Type<ScooterMusicMenu.Catalog> type() {
         return TYPE;
      }
   }

   public static record Options(boolean autoplay, boolean shuffle) implements CustomPacketPayload {
      public static final Type<ScooterMusicMenu.Options> TYPE = new Type(ResourceLocation.parse("goplanska_kukirin:music_options"));
      public static final StreamCodec<RegistryFriendlyByteBuf, ScooterMusicMenu.Options> CODEC = new StreamCodec<RegistryFriendlyByteBuf, ScooterMusicMenu.Options>() {
         public ScooterMusicMenu.Options decode(RegistryFriendlyByteBuf var1) {
            return new ScooterMusicMenu.Options(var1.readBoolean(), var1.readBoolean());
         }

         public void encode(RegistryFriendlyByteBuf var1, ScooterMusicMenu.Options var2) {
            var1.writeBoolean(var2.autoplay);
            var1.writeBoolean(var2.shuffle);
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
