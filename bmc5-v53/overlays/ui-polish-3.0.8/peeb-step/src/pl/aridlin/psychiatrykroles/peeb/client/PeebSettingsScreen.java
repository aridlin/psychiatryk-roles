package pl.aridlin.psychiatrykroles.peeb.client;

import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import pl.aridlin.psychiatrykroles.peeb.PeebConfig;
import pl.aridlin.psychiatrykroles.peeb.PeebPackets;

public final class PeebSettingsScreen extends Screen {
   private static Screen requestedFrom;
   private final Screen back;
   private final double[] values = new double[5];
   private final double[] low = new double[]{1.0, 0.1, 0.1, 0.05, PeebConfig.MIN_STOP_DISTANCE};
   private final double[] high = new double[]{32.0, 0.7, 1.2, 1.0, PeebConfig.MAX_STOP_DISTANCE};
   private final String[] names = new String[]{"Grapple range (blocks)", "Horizontal cap (blocks/tick)", "Total speed cap (blocks/tick)", "Tether strength", "Pull stop distance (blocks)"};
   private boolean fallImmunity;
   private boolean grappleStep;
   private EditBox stopDistanceInput;
   private Button applyButton;
   private ValueSlider stopDistanceSlider;

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
      this.values[4] = var1.stopDistance();
      this.fallImmunity = var1.fallImmunity();
      this.grappleStep = var1.grappleStep();
   }

   protected void init() {
      int var1 = Math.min(380, this.width - 32);
      int var2 = (this.width - var1) / 2;
      int var3 = Math.max(28, (this.height - 272) / 2);

      this.stopDistanceInput = null;
      this.applyButton = null;
      for (int index = 0; index < this.values.length; index++) {
         ValueSlider slider = new ValueSlider(var2, var3 + index * 24, index == 4 ? var1 - 84 : var1, index);
         this.addRenderableWidget(slider);
         if (index == 4) {
            this.stopDistanceSlider = slider;
         }
      }

      this.stopDistanceInput = new EditBox(this.font, var2 + var1 - 80, var3 + 96, 80, 22, Component.literal("Exact pull stop distance in blocks"));
      this.stopDistanceInput.setMaxLength(24);
      this.stopDistanceInput.setValue(Double.toString(this.values[4]));
      this.stopDistanceInput.setResponder(text -> {
         double distance = parseStopDistance(text);
         if (Double.isFinite(distance)) {
            this.values[4] = distance;
            this.stopDistanceSlider.refreshValue();
         }
         if (this.applyButton != null) {
            this.applyButton.active = Double.isFinite(distance);
         }
      });
      this.addRenderableWidget(this.stopDistanceInput);

      this.addRenderableWidget(Button.builder(this.fallLabel(), var1x -> {
         this.fallImmunity = !this.fallImmunity;
         var1x.setMessage(this.fallLabel());
      }).bounds(var2, var3 + 124, var1, 22).build());
      this.addRenderableWidget(Button.builder(this.stepLabel(), button -> {
         this.grappleStep = !this.grappleStep;
         button.setMessage(this.stepLabel());
      }).bounds(var2, var3 + 148, var1, 22).build());
      this.applyButton = this.addRenderableWidget(Button.builder(Component.literal("Apply live & save"), var1x -> {
         PeebConfig.Values var2x = new PeebConfig.Values(this.values[0], this.values[1], this.values[2], this.values[3], this.fallImmunity, parseStopDistance(this.stopDistanceInput.getValue()), this.grappleStep);
         if (var2x.valid()) {
            PeebPackets.saveConfig(var2x);
         }
      }).bounds(var2, var3 + 172, var1 / 2 - 4, 22).build());
      this.addRenderableWidget(
         Button.builder(Component.literal("Reload server file"), var0 -> PeebPackets.reloadConfig())
            .bounds(var2 + var1 / 2 + 4, var3 + 172, var1 / 2 - 4, 22)
            .build()
      );
      this.addRenderableWidget(Button.builder(Component.literal("Defaults"), var1x -> {
         this.set(PeebConfig.DEFAULT);
         this.rebuildWidgets();
      }).bounds(var2, var3 + 196, var1, 22).build());
      this.addRenderableWidget(
         Button.builder(Component.literal("Back to scooter settings"), var1x -> this.onClose()).bounds(var2, var3 + 220, var1, 22).build()
      );
   }

   private final class ValueSlider extends AbstractSliderButton {
      private final int index;

      private ValueSlider(int x, int y, int width, int index) {
         super(x, y, width, 22, Component.empty(), (PeebSettingsScreen.this.values[index] - PeebSettingsScreen.this.low[index]) / (PeebSettingsScreen.this.high[index] - PeebSettingsScreen.this.low[index]));
         this.index = index;
         this.updateMessage();
      }

      private void refreshValue() {
         this.value = (PeebSettingsScreen.this.values[this.index] - PeebSettingsScreen.this.low[this.index]) / (PeebSettingsScreen.this.high[this.index] - PeebSettingsScreen.this.low[this.index]);
         this.updateMessage();
      }

      protected void updateMessage() {
         this.setMessage(Component.literal(PeebSettingsScreen.this.names[this.index] + ": " + String.format(Locale.ROOT, this.index == 0 ? "%.1f" : "%.2f", PeebSettingsScreen.this.values[this.index])));
      }

      protected void applyValue() {
         PeebSettingsScreen.this.values[this.index] = PeebSettingsScreen.this.low[this.index] + this.value * (PeebSettingsScreen.this.high[this.index] - PeebSettingsScreen.this.low[this.index]);
         if (this.index == 4 && PeebSettingsScreen.this.stopDistanceInput != null) {
            PeebSettingsScreen.this.stopDistanceInput.setValue(Double.toString(PeebSettingsScreen.this.values[4]));
         }
         this.updateMessage();
      }
   }

   /** A zero value pulls all the way to the target; collision prevents entry into solid blocks. */
   static double parseStopDistance(String text) {
      try {
         double value = Double.parseDouble(text.trim());
         return Double.isFinite(value) && value >= PeebConfig.MIN_STOP_DISTANCE && value <= PeebConfig.MAX_STOP_DISTANCE ? value : Double.NaN;
      } catch (NumberFormatException exception) {
         return Double.NaN;
      }
   }

   private Component stepLabel() {
      return Component.literal("Step while grappling (1.3 blocks): " + (this.grappleStep ? "ON" : "OFF"));
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
