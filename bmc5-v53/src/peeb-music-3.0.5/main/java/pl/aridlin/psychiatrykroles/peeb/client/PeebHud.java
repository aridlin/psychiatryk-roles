package pl.aridlin.psychiatrykroles.peeb.client;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.util.Locale;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent.Pre;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import pl.aridlin.psychiatrykroles.peeb.PeebConfig;
import pl.aridlin.psychiatrykroles.peeb.PeebMode;

@EventBusSubscriber(
   modid = "psychiatryk_peeb",
   value = {Dist.CLIENT}
)
public final class PeebHud {
   @SubscribeEvent
   public static void crosshair(Pre var0) {
      if (PeebClient.localActive() && var0.getName().equals(VanillaGuiLayers.CROSSHAIR)) {
         var0.setCanceled(true);
      }
   }

   @SubscribeEvent
   public static void hand(RenderHandEvent var0) {
      if (PeebClient.localActive()) {
         var0.setCanceled(true);
      }
   }

   @SubscribeEvent
   public static void tooltip(ItemTooltipEvent var0) {
      if (var0.getItemStack().is((Item)PeebMode.PEEB.get())) {
         var0.getToolTip().add(Component.literal("Hold or wear to become Peeb. Remove to restore your previous camera."));
         var0.getToolTip().add(Component.literal("LMB: hold to grapple, release to drop. Space: jump. RMB: interact. E: inventory."));
         var0.getToolTip().add(Component.literal("Freelook: ").append(PeebClientBootstrap.FREELOOK.getTranslatedKeyMessage()));
         PeebConfig.Values var1 = PeebClient.settings();
         var0.getToolTip()
            .add(
               Component.literal(
                  String.format(Locale.ROOT, "Server grapple range: %.1f blocks. Fall damage while Peeb is active: %s.", var1.range(), var1.fallImmunity() ? "OFF" : "ON")
               )
            );
         var0.getToolTip().add(Component.literal("/scooteradmin → Peeb settings; /peebcamera 2..10 changes local camera distance."));
      }
   }

   @SubscribeEvent
   public static void cameraCommand(RegisterClientCommandsEvent var0) {
      var0.getDispatcher()
         .register(
            (LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("peebcamera")
                  .executes(
                     var0x -> {
                        ((CommandSourceStack)var0x.getSource())
                           .sendSuccess(
                              () -> Component.translatable("command.psychiatryk_peeb.camera.current", new Object[]{PeebClient.cameraDistance()}), false
                           );
                        return 1;
                     }
                  ))
               .then(
                  Commands.argument("distance", FloatArgumentType.floatArg(2.0F, 10.0F))
                     .executes(
                        var0x -> {
                           PeebClient.cameraDistance(FloatArgumentType.getFloat(var0x, "distance"));
                           ((CommandSourceStack)var0x.getSource())
                              .sendSuccess(
                                 () -> Component.translatable("command.psychiatryk_peeb.camera.current", new Object[]{PeebClient.cameraDistance()}), false
                              );
                           return 1;
                        }
                     )
               )
         );
   }

   private PeebHud() {
   }
}
