package pl.aridlin.kukirin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.entity.JukeboxBlockEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(
   modid = "goplanska_kukirin"
)
public final class ScooterJukeboxMusic {
   private static final Map<UUID, ScooterJukeboxMusic.Open> opened = new HashMap<>();

   @SubscribeEvent
   public static void use(RightClickBlock var0) {
      if (var0.getEntity().isShiftKeyDown()
         && var0.getHand() == InteractionHand.MAIN_HAND
         && var0.getLevel().getBlockEntity(var0.getPos()) instanceof JukeboxBlockEntity) {
         var0.setCancellationResult(InteractionResult.SUCCESS);
         var0.setCanceled(true);
         if (var0.getEntity() instanceof ServerPlayer var1) {
            WearableJukeboxMusic.forget(var1);
            ScooterMusic.Source var3 = ScooterMusic.openJukebox(var1, var0.getPos());
            opened.put(var1.getUUID(), new ScooterJukeboxMusic.Open(var3, var1.serverLevel().getGameTime() + 6000L));
            ScooterMusicMenu.sendCatalog(var1, new ScooterMusicMenu.Catalog(ScooterMusic.songs(), true, false, var3.looping()), false);
            ScooterMusic.options(var1, var3);
         }
      }
   }

   static boolean handle(ServerPlayer var0, String var1, String var2) {
      ScooterJukeboxMusic.Open var3 = opened.get(var0.getUUID());
      if (var3 == null) {
         return false;
      } else {
         ScooterMusic.Source var4 = var3.source;
         if (var0.level() == var4.level && !(var4.distance(var0) > 64.0) && var4.valid() && var4.level.getGameTime() <= var3.expires) {
            switch (var1) {
               case "play":
                  if (var4.level.getBlockEntity(var4.block) instanceof JukeboxBlockEntity var7) {
                     var7.getSongPlayer().stop(var4.level, var4.level.getBlockState(var4.block));
                  }

                  ScooterMusic.play(var0, var4, var2);
                  break;
               case "loop":
                  ScooterMusic.jukeboxLoop(var4, var2.equals("true"));
                  break;
               case "autoplay":
               case "shuffle":
                  ScooterMusic.mode(var0, var4, var1, var2);
                  break;
               case "previous":
                  ScooterMusic.previous(var0, var4);
                  break;
               case "skip":
                  ScooterMusic.skip(var0, var4);
                  break;
               case "stop":
                  ScooterMusic.stop(var4.id);
            }

            return true;
         } else {
            opened.remove(var0.getUUID());
            return true;
         }
      }
   }

   static boolean hasContext(ServerPlayer var0) {
      return opened.containsKey(var0.getUUID());
   }

   static ScooterMusic.Source importSource(ServerPlayer var0) {
      ScooterJukeboxMusic.Open var1 = opened.get(var0.getUUID());
      if (var1 == null) {
         return null;
      } else {
         ScooterMusic.Source var2 = var1.source;
         return var0.level() == var2.level && var2.distance(var0) <= 64.0 && var2.valid() && var2.level.getGameTime() <= var1.expires ? var2 : null;
      }
   }

   static void forget(ServerPlayer var0) {
      opened.remove(var0.getUUID());
   }

   static void clear() {
      opened.clear();
   }

   @SubscribeEvent
   public static void logout(PlayerLoggedOutEvent var0) {
      opened.remove(var0.getEntity().getUUID());
   }

   private ScooterJukeboxMusic() {
   }

   private static record Open(ScooterMusic.Source source, long expires) {
   }
}
