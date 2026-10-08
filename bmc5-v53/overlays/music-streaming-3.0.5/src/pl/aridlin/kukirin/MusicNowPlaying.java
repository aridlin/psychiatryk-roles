package pl.aridlin.kukirin;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.EventBusSubscriber.Bus;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

@EventBusSubscriber(
   modid = "goplanska_kukirin",
   bus = Bus.MOD
)
public final class MusicNowPlaying {
   public static final int MAX_ARTWORK_BYTES = 16384;
   private static Consumer<MusicNowPlaying.Info> receiver = var0 -> {
   };

   public static void clientHandler(Consumer<MusicNowPlaying.Info> var0) {
      receiver = Objects.requireNonNull(var0);
   }

   public static void send(ServerPlayer var0, UUID var1, UUID var2, String var3, String var4, String var5, int var6, byte[] var7) {
      PacketDistributor.sendToPlayer(var0, new MusicNowPlaying.Info(var1, var2, var3, var4, var5, var6, var7), new CustomPacketPayload[0]);
   }

   public static String text(String var0, int var1) {
      if (var0 == null) {
         return "";
      } else {
         StringBuilder var2 = new StringBuilder();
         var0 = var0.replaceAll("(?i)§[0-9a-fk-or]", "");
         var0.codePoints().filter(var0x -> !Character.isISOControl(var0x) && var0x != 167).limit((long)var1).forEach(var2::appendCodePoint);
         int var3 = Math.min(var2.length(), var1);
         if (var3 > 0 && Character.isHighSurrogate(var2.charAt(var3 - 1))) {
            var3--;
         }

         return var2.substring(0, var3).strip();
      }
   }

   public static String fallbackTitle(String var0) {
      String var1 = text(var0, 160);
      int var2 = Math.max(var1.lastIndexOf(47), var1.lastIndexOf(92));
      if (var2 >= 0) {
         var1 = var1.substring(var2 + 1);
      }

      if (var1.endsWith(".wav") || var1.endsWith(".ogg")) {
         var1 = var1.substring(0, var1.length() - 4);
      }

      var1 = var1.replace("music_disc.", "").replace('_', ' ').strip();
      return var1.isEmpty() ? "Music" : Character.toUpperCase(var1.charAt(0)) + var1.substring(1);
   }

   public static boolean validArtwork(byte[] var0) {
      if (var0 != null && var0.length >= 33 && var0.length <= 16384) {
         int[] var1 = new int[]{137, 80, 78, 71, 13, 10, 26, 10};

         for (int var2 = 0; var2 < 8; var2++) {
            if ((var0[var2] & 255) != var1[var2]) {
               return false;
            }
         }

         return var0[8] == 0
            && var0[9] == 0
            && var0[10] == 0
            && var0[11] == 13
            && var0[12] == 73
            && var0[13] == 72
            && var0[14] == 68
            && var0[15] == 82
            && var0[16] == 0
            && var0[17] == 0
            && var0[18] == 0
            && var0[19] == 64
            && var0[20] == 0
            && var0[21] == 0
            && var0[22] == 0
            && var0[23] == 64;
      } else {
         return false;
      }
   }

   @SubscribeEvent
   public static void register(RegisterPayloadHandlersEvent var0) {
      var0.registrar("1").playToClient(MusicNowPlaying.Info.TYPE, MusicNowPlaying.Info.CODEC, (var0x, var1) -> var1.enqueueWork(() -> receiver.accept(var0x)));
   }

   private MusicNowPlaying() {
   }

   public static record Info(UUID source, UUID session, String title, String artist, String album, int durationTicks, byte[] artwork)
      implements CustomPacketPayload {
      public static final Type<MusicNowPlaying.Info> TYPE = new Type(ResourceLocation.parse("goplanska_kukirin:now_playing"));
      public static final StreamCodec<RegistryFriendlyByteBuf, MusicNowPlaying.Info> CODEC = StreamCodec.of(
         (var0, var1) -> {
            var0.writeUUID(var1.source);
            var0.writeUUID(var1.session);
            var0.writeUtf(var1.title, 160);
            var0.writeUtf(var1.artist, 120);
            var0.writeUtf(var1.album, 120);
            var0.writeVarInt(var1.durationTicks);
            var0.writeByteArray(var1.artwork);
         },
         var0 -> new MusicNowPlaying.Info(
               var0.readUUID(), var0.readUUID(), var0.readUtf(160), var0.readUtf(120), var0.readUtf(120), var0.readVarInt(), var0.readByteArray(16384)
            )
      );

      public Info(UUID source, UUID session, String title, String artist, String album, int durationTicks, byte[] artwork) {
         Objects.requireNonNull(source);
         Objects.requireNonNull(session);
         title = MusicNowPlaying.text(title, 160);
         artist = MusicNowPlaying.text(artist, 120);
         album = MusicNowPlaying.text(album, 120);
         durationTicks = Math.max(0, durationTicks);
         artwork = MusicNowPlaying.validArtwork(artwork) ? (byte[])artwork.clone() : new byte[0];
         this.source = source;
         this.session = session;
         this.title = title;
         this.artist = artist;
         this.album = album;
         this.durationTicks = durationTicks;
         this.artwork = artwork;
      }

      public byte[] artwork() {
         return (byte[])this.artwork.clone();
      }

      public Type<MusicNowPlaying.Info> type() {
         return TYPE;
      }

      @Override
      public String toString() {
         return "MusicNowPlaying.Info[source=" + this.source + ",session=" + this.session + ",artworkBytes=" + this.artwork.length + "]";
      }
   }
}
