package pl.aridlin.kukirin;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerChangedDimensionEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerRespawnEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import pl.aridlin.psychiatrykroles.jukebox.WearableJukebox;

@EventBusSubscriber(
   modid = "goplanska_kukirin"
)
public final class WearableJukeboxMusic {
   private static final Map<UUID, WearableJukeboxMusic.Open> opened = new HashMap<>();

   public static int open(ServerPlayer var0) {
      ScooterJukeboxMusic.forget(var0);
      forget(var0);
      if (var0.isAlive() && !WearableJukebox.equipped(var0).isEmpty()) {
         ScooterMusic.Source var1 = new ScooterMusic.Source(var0);
         if (!var1.valid()) {
            return 0;
         } else {
            opened.put(var0.getUUID(), new WearableJukeboxMusic.Open(var1, var1.level.getGameTime() + 6000L));
            ScooterMusicMenu.sendCatalog(var0, new ScooterMusicMenu.Catalog(ScooterMusic.songs(), true, false, var1.looping()), false);
            ScooterMusic.options(var0, var1);
            return 1;
         }
      } else {
         var0.displayClientMessage(Component.literal("Equip a jukebox in your back slot to open its music."), false);
         return 0;
      }
   }

   static boolean handle(ServerPlayer var0, String var1, String var2) {
      WearableJukeboxMusic.Open var3 = opened.get(var0.getUUID());
      if (var3 == null) {
         return false;
      } else {
         ScooterMusic.Source var4 = var3.source;
         if (var4.valid() && var4.level.getGameTime() <= var3.expires) {
            switch (var1) {
               case "play":
                  ScooterMusic.play(var0, var4, var2);
                  break;
               case "loop":
                  ScooterMusic.wearableLoop(var4, var2.equals("true"));
                  break;
               case "autoplay":
               case "shuffle":
                  ScooterMusic.mode(var0, var4, var1, var2);
                  break;
               case "stop":
                  ScooterMusic.stop(var4.id);
            }

            return true;
         } else {
            return true;
         }
      }
   }

   static boolean hasContext(ServerPlayer var0) {
      return opened.containsKey(var0.getUUID());
   }

   static ScooterMusic.Source importSource(ServerPlayer var0) {
      WearableJukeboxMusic.Open var1 = opened.get(var0.getUUID());
      return var1 != null && var1.source.valid() && var1.source.level.getGameTime() <= var1.expires ? var1.source : null;
   }

   public static void forget(ServerPlayer var0) {
      opened.remove(var0.getUUID());
   }

   static void clear() {
      opened.clear();
   }

   private static int loop(ServerPlayer var0, boolean var1) {
      if (var0.isAlive() && !WearableJukebox.equipped(var0).isEmpty()) {
         return ScooterMusic.wearableLoop(new ScooterMusic.Source(var0), var1) ? 1 : 0;
      } else {
         return 0;
      }
   }

   @SubscribeEvent
   public static void commands(RegisterCommandsEvent var0) {
      var0.getDispatcher()
         .register(
            (LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("jukebox")
                     .executes(var0x -> open(((CommandSourceStack)var0x.getSource()).getPlayerOrException())))
                  .then(Commands.literal("stop").executes(var0x -> {
                     ScooterMusic.stop(((CommandSourceStack)var0x.getSource()).getPlayerOrException().getUUID());
                     return 1;
                  })))
               .then(
                  Commands.literal("loop")
                     .then(
                        Commands.argument("enabled", BoolArgumentType.bool())
                           .executes(var0x -> loop(((CommandSourceStack)var0x.getSource()).getPlayerOrException(), BoolArgumentType.getBool(var0x, "enabled")))
                     )
               )
         );
   }

   @SubscribeEvent
   public static void death(LivingDeathEvent var0) {
      if (var0.getEntity() instanceof ServerPlayer var1) {
         ScooterMusic.stop(var1.getUUID());
      }
   }

   @SubscribeEvent
   public static void dimension(PlayerChangedDimensionEvent var0) {
      if (var0.getEntity() instanceof ServerPlayer var1) {
         ScooterMusic.stop(var1.getUUID());
      }
   }

   @SubscribeEvent
   public static void respawn(PlayerRespawnEvent var0) {
      if (var0.getEntity() instanceof ServerPlayer var1) {
         ScooterMusic.stop(var1.getUUID());
      }
   }

   @SubscribeEvent
   public static void logout(PlayerLoggedOutEvent var0) {
      if (var0.getEntity() instanceof ServerPlayer var1) {
         ScooterMusic.stop(var1.getUUID());
         forget(var1);
      }
   }

   private WearableJukeboxMusic() {
   }

   private static record Open(ScooterMusic.Source source, long expires) {
   }
}
