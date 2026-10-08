package pl.aridlin.psychiatrykroles.peeb.client;

import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import pl.aridlin.psychiatrykroles.peeb.PeebConfig;
import pl.aridlin.psychiatrykroles.peeb.PeebPackets;

public final class PeebSettingsScreen extends Screen {
   private static Screen requestedFrom;
   private final Screen back;
   private final double[] values = new double[4];
   private final double[] low = new double[]{1.0, 0.1, 0.1, 0.05};
   private final double[] high = new double[]{32.0, 0.7, 1.2, 1.0};
   private final String[] names = new String[]{"Grapple range (blocks)", "Horizontal cap (blocks/tick)", "Total speed cap (blocks/tick)", "Tether strength"};
   private boolean fallImmunity;

   public static void request(Screen var0) {
      requestedFrom = var0;
      PeebPackets.requestConfig();
   }

   public static void receive(PeebConfig.Values var0) {
      Minecraft var1 = Minecraft.getInstance();
      if (var1.screen instanceof PeebSettingsScreen var2) {
         var2.set(var0);
         var2.rebuildWidgets();
      } else if (requestedFrom != null && var1.screen == requestedFrom) {
         var1.setScreen(new PeebSettingsScreen(requestedFrom, var0));
      }

      requestedFrom = null;
   }

   public static void forget() {
      requestedFrom = null;
   }

   private PeebSettingsScreen(Screen var1, PeebConfig.Values var2) {
      super(Component.literal("Server Peeb tuning"));
      this.back = var1;
      this.set(var2);
   }

   private void set(PeebConfig.Values var1) {
      this.values[0] = var1.range();
      this.values[1] = var1.maxHorizontalSpeed();
      this.values[2] = var1.maxSpeed();
      this.values[3] = var1.strength();
      this.fallImmunity = var1.fallImmunity();
   }

   protected void init() {
      int var1 = Math.min(380, this.width - 32);
      int var2 = (this.width - var1) / 2;
      int var3 = Math.max(28, (this.height - 224) / 2);

      for (int recoveredIndex = 0; recoveredIndex < this.values.length; recoveredIndex++) {
         final int var4 = recoveredIndex;
         this.addRenderableWidget(
            new AbstractSliderButton(
               var2, var3 + var4 * 24, var1, 22, Component.empty(), (this.values[var4] - this.low[var4]) / (this.high[var4] - this.low[var4])
            ) {
               {
                  this.updateMessage();
               }

               protected void updateMessage() {
                  this.setMessage(
                     Component.literal(
                        PeebSettingsScreen.this.names[var4]
                           + ": "
                           + String.format(Locale.ROOT, var4 == 0 ? "%.1f" : "%.2f", PeebSettingsScreen.this.values[var4])
                     )
                  );
               }

               protected void applyValue() {
                  PeebSettingsScreen.this.values[var4] = PeebSettingsScreen.this.low[var4]
                     + this.value * (PeebSettingsScreen.this.high[var4] - PeebSettingsScreen.this.low[var4]);
                  this.updateMessage();
               }
            }
         );
      }

      this.addRenderableWidget(Button.builder(this.fallLabel(), var1x -> {
         this.fallImmunity = !this.fallImmunity;
         var1x.setMessage(this.fallLabel());
      }).bounds(var2, var3 + 100, var1, 22).build());
      this.addRenderableWidget(Button.builder(Component.literal("Apply live & save"), var1x -> {
         PeebConfig.Values var2x = new PeebConfig.Values(this.values[0], this.values[1], this.values[2], this.values[3], this.fallImmunity);
         if (var2x.valid()) {
            PeebPackets.saveConfig(var2x);
         }
      }).bounds(var2, var3 + 124, var1 / 2 - 4, 22).build());
      this.addRenderableWidget(
         Button.builder(Component.literal("Reload server file"), var0 -> PeebPackets.reloadConfig())
            .bounds(var2 + var1 / 2 + 4, var3 + 124, var1 / 2 - 4, 22)
            .build()
      );
      this.addRenderableWidget(Button.builder(Component.literal("Defaults"), var1x -> {
         this.set(PeebConfig.DEFAULT);
         this.rebuildWidgets();
      }).bounds(var2, var3 + 148, var1, 22).build());
      this.addRenderableWidget(
         Button.builder(Component.literal("Back to scooter settings"), var1x -> this.onClose()).bounds(var2, var3 + 172, var1, 22).build()
      );
   }

   private Component fallLabel() {
      return Component.literal("Fall damage while holding Peeb: " + (this.fallImmunity ? "OFF" : "ON"));
   }

   public void onClose() {
      requestedFrom = null;
      Minecraft.getInstance().setScreen(this.back);
   }

   public boolean isPauseScreen() {
      return false;
   }

   public void render(GuiGraphics var1, int var2, int var3, float var4) {
      this.renderBackground(var1, var2, var3, var4);
      super.render(var1, var2, var3, var4);
      var1.drawCenteredString(this.font, this.title, this.width / 2, 10, -23241);
      var1.drawCenteredString(
         this.font, Component.literal("Applies to everyone; server validates and syncs changes live."), this.width / 2, this.height - 14, -2236963
      );
   }
}
