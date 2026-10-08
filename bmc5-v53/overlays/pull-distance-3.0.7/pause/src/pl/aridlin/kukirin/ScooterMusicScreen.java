package pl.aridlin.kukirin;

import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.PacketDistributor;

public final class ScooterMusicScreen extends Screen {
   final Runnable back;
   ScooterMusicMenu.Catalog catalog;
   List<String> filtered;
   final List<Button> rows = new ArrayList<>();
   final List<Button> stars = new ArrayList<>();
   int page;
   int pageSize;
   boolean loop;
   boolean paused;
   int volume;
   boolean autoplay;
   boolean shuffle;
   boolean optionsReady;
   boolean favoritesOnly;
   String searchTerm = "";
   Button favoritesButton;
   Button autoplayButton;
   Button shuffleButton;
   Button loopButton;
   Button discButton;
   Button importButton;
   Button skipButton;
   Button pauseButton;
   VolumeSlider volumeSlider;
   Button previousSongButton;
   EditBox search;
   String importMessage = "";
   boolean importBusy;

   public ScooterMusicScreen(ScooterMusicMenu.Catalog var1) {
      this(var1, ScooterMenus::backToScooter);
   }

   public ScooterMusicScreen(ScooterMusicMenu.Catalog var1, Runnable var2) {
      super(Component.translatable("scooter.menu.music"));
      this.back = var2;
      this.catalog = var1;
      this.loop = var1.loop();
      this.paused = var1.paused();
      this.volume = var1.volume();
      this.filtered = var1.songs();
      ScooterMusicFavorites.select(Minecraft.getInstance().player == null ? null : Minecraft.getInstance().player.getUUID());
   }

   protected void init() {
      this.pageSize = Math.clamp((long)((this.height - 268) / 22), 1, 7);
      int var1 = this.width / 2 - 150;
      int var2 = Math.max(25, (this.height - (52 + this.pageSize * 22 + 152)) / 2);
      this.rows.clear();
      this.stars.clear();
      this.favoritesButton = (Button)this.addRenderableWidget(Button.builder(Component.literal(this.favoritesOnly ? "Favorites only" : "All songs"), var1x -> {
         this.favoritesOnly = !this.favoritesOnly;
         var1x.setMessage(Component.literal(this.favoritesOnly ? "Favorites only" : "All songs"));
         this.page = 0;
         this.filter();
      }).bounds(var1, var2, 300, 20).tooltip(Tooltip.create(Component.literal("Click the star next to a song to save it for this player."))).build());
      EditBox var3 = this.search = new EditBox(this.font, var1, var2 + 26, 300, 20, Component.literal("Search songs"));
      var3.setMaxLength(128);
      var3.setHint(Component.literal("Search server songs…"));
      var3.setResponder(var1x -> {
         this.searchTerm = var1x;
         this.page = 0;
         this.filter();
      });
      var3.setValue(this.searchTerm);
      this.addRenderableWidget(var3);

      for (int var4 = 0; var4 < this.pageSize; var4++) {
         int var5 = var4;
         this.stars.add((Button)this.addRenderableWidget(Button.builder(Component.empty(), var2x -> {
            int var3x = this.page * this.pageSize + var5;
            if (var3x < this.filtered.size() && ScooterMusicFavorites.toggle(this.filtered.get(var3x))) {
               this.filter();
            }
         }).bounds(var1, var2 + 52 + var4 * 22, 24, 20).build()));
         this.rows.add((Button)this.addRenderableWidget(Button.builder(Component.empty(), var2x -> {
            int var3x = this.page * this.pageSize + var5;
            if (var3x < this.filtered.size()) {
               send("play", this.filtered.get(var3x));
            }
         }).bounds(var1 + 28, var2 + 52 + var4 * 22, 272, 20).build()));
      }

      int var7 = var2 + 52 + this.pageSize * 22;
      this.addRenderableWidget(Button.builder(Component.literal("Previous page"), var1x -> {
         this.page = Math.max(0, this.page - 1);
         this.refresh();
      }).bounds(var1, var7, 95, 20).build());
      this.loopButton = this.addRenderableWidget(
         Button.builder(Component.literal("Loop: " + (this.loop ? "ON" : "OFF")), var1x -> {
               this.loop = !this.loop;
               send("loop", Boolean.toString(this.loop));
               var1x.setMessage(Component.literal("Loop: " + (this.loop ? "ON" : "OFF")));
            })
            .bounds(var1 + 102, var7, 95, 20)
            .tooltip(Tooltip.create(Component.literal("ON repeats the current song. Autoplay and shuffle advance only with Loop OFF.")))
            .build()
      );
      this.addRenderableWidget(Button.builder(Component.literal("Next page"), var1x -> {
         this.page = Math.min(Math.max(0, (this.filtered.size() - 1) / this.pageSize), this.page + 1);
         this.refresh();
      }).bounds(var1 + 205, var7, 95, 20).build());
      this.autoplayButton = (Button)this.addRenderableWidget(
         Button.builder(Component.empty(), var1x -> {
               this.optionsReady = false;
               send("autoplay", Boolean.toString(!this.autoplay));
               this.modeLabels();
            })
            .bounds(var1, var7 + 26, 146, 20)
            .tooltip(Tooltip.create(Component.literal("Play the next server song when this song ends. Loop ON keeps repeating the current song.")))
            .build()
      );
      this.shuffleButton = (Button)this.addRenderableWidget(
         Button.builder(Component.empty(), var1x -> {
               this.optionsReady = false;
               send("shuffle", Boolean.toString(!this.shuffle));
               this.modeLabels();
            })
            .bounds(var1 + 154, var7 + 26, 146, 20)
            .tooltip(Tooltip.create(Component.literal("Choose a random next song during autoplay, avoiding the current song when another is available.")))
            .build()
      );
      this.volumeSlider = this.addRenderableWidget(new VolumeSlider(var1, var7 + 52, 300, 20, this.volume));
      this.volumeSlider.active = this.catalog.wav();
      this.previousSongButton = this.addRenderableWidget(Button.builder(Component.literal("Previous"), button -> send("previous", ""))
         .bounds(var1, var7 + 78, 95, 20)
         .tooltip(Tooltip.create(Component.literal("Return to the previous song now, including when Loop is ON."))).build());
      this.previousSongButton.active = this.catalog.wav();
      this.pauseButton = this.addRenderableWidget(Button.builder(Component.literal(this.paused ? "Resume" : "Pause"), button -> {
         button.active = false; send(this.paused ? "resume" : "pause", "");
      }).bounds(var1 + 102, var7 + 78, 95, 20).tooltip(Tooltip.create(Component.literal("Pause or resume this shared source for every listener."))).build());
      this.pauseButton.active = this.catalog.wav();
      this.skipButton = this.addRenderableWidget(Button.builder(Component.literal("Skip song"), button -> send("skip", ""))
         .bounds(var1 + 205, var7 + 78, 95, 20)
         .tooltip(Tooltip.create(Component.literal("Play the next playlist or library song now, including when Loop is ON."))).build());
      this.skipButton.active = this.catalog.wav();
      this.addRenderableWidget(Button.builder(Component.literal("Stop"), var0 -> send("stop", "")).bounds(var1, var7 + 104, 95, 20).build());
      Button var8 = this.discButton = (Button)this.addRenderableWidget(
         Button.builder(Component.literal("Play disc"), var0 -> send("disc", "")).bounds(var1 + 102, var7 + 104, 95, 20).build()
      );
      var8.active = this.catalog.disc();
      this.addRenderableWidget(Button.builder(Component.literal("Back"), var1x -> this.onClose()).bounds(var1 + 205, var7 + 104, 95, 20).build());
      Button var6 = this.importButton = (Button)this.addRenderableWidget(
         Button.builder(Component.literal("YouTube Music / playlist"), var1x -> this.minecraft.setScreen(new YouTubeMusicScreen(this)))
            .bounds(var1, var7 + 130, 300, 20)
            .build()
      );
      var6.active = this.catalog.wav();
      if (this.importBusy) var6.setMessage(Component.literal("Importing… / YouTube Music"));
      this.filter();
      this.modeLabels();
   }

   /** Replace a server snapshot without dropping the active picker or its context. */
   void updateCatalog(ScooterMusicMenu.Catalog value, boolean imported) {
      updateCatalog(value, imported, "");
   }

   void updateCatalog(ScooterMusicMenu.Catalog value, boolean imported, String focusSong) {
      HashSet<String> previous = new HashSet<>(this.catalog.songs());
      String firstAdded = imported && value.songs().contains(focusSong) ? focusSong :
         imported ? value.songs().stream().filter(name -> !previous.contains(name)).findFirst().orElse(null) : null;
      this.catalog = value;
      this.loop = value.loop();
      this.paused = value.paused();
      this.volume = value.volume();
      if (imported) {
         this.favoritesOnly = false;
         this.searchTerm = "";
         this.page = 0;
         if (this.favoritesButton != null) this.favoritesButton.setMessage(Component.literal("All songs"));
         if (this.search != null) this.search.setValue("");
      }
      this.filter();
      if (firstAdded != null) {
         int index = this.filtered.indexOf(firstAdded);
         if (index >= 0) this.page = index / Math.max(1, this.pageSize);
         this.refresh();
      }
      if (this.discButton != null) this.discButton.active = value.disc();
      if (this.importButton != null) this.importButton.active = value.wav();
      if (this.skipButton != null) this.skipButton.active = value.wav();
      if (this.previousSongButton != null) this.previousSongButton.active = value.wav();
      if (this.loopButton != null) this.loopButton.setMessage(Component.literal("Loop: " + (this.loop ? "ON" : "OFF")));
      this.modeLabels();
   }

   void importStatus(MusicImports.Status value) {
      this.importMessage = value.message();
      this.importBusy = value.busy();
      if (this.importButton != null) this.importButton.setMessage(Component.literal(value.busy() ? "Importing… / YouTube Music" : "YouTube Music / playlist"));
   }

   void options(ScooterMusicMenu.Options var1) {
      this.autoplay = var1.autoplay();
      this.shuffle = var1.shuffle();
      this.paused = var1.paused();
      this.volume = var1.volume();
      this.optionsReady = true;
      this.modeLabels();
   }

   private void modeLabels() {
      if (this.volumeSlider != null) { this.volumeSlider.serverValue(this.volume); this.volumeSlider.active = this.catalog.wav(); }
      if (this.pauseButton != null) {
         this.pauseButton.setMessage(Component.literal(this.paused ? "Resume" : "Pause"));
         this.pauseButton.active = this.catalog.wav();
      }
      if (this.autoplayButton != null) {
         this.autoplayButton.setMessage(Component.literal("Autoplay: " + (this.autoplay ? "ON" : "OFF")));
         this.shuffleButton.setMessage(Component.literal("Shuffle: " + (this.shuffle ? "ON" : "OFF")));
         this.autoplayButton.active = this.optionsReady && this.catalog.wav();
         this.shuffleButton.active = this.optionsReady && this.catalog.wav();
      }
   }

   final class VolumeSlider extends AbstractSliderButton {
      private boolean adjusting;
      VolumeSlider(int x, int y, int width, int height, int percent) {
         super(x, y, width, height, Component.empty(), percent / 100.0); updateMessage();
      }
      void serverValue(int percent) {
         if (!adjusting) { this.value = percent / 100.0; updateMessage(); }
      }
      protected void updateMessage() { setMessage(Component.literal("Volume: " + Math.round(this.value * 100) + "%")); }
      protected void applyValue() {
         if (!adjusting) send("volume", Long.toString(Math.round(this.value * 100)));
      }
      public void onClick(double x, double y) { adjusting = true; super.onClick(x, y); }
      public void onRelease(double x, double y) {
         super.onRelease(x, y); adjusting = false; applyValue();
      }
   }

   private void filter() {
      String var1 = this.searchTerm.toLowerCase(Locale.ROOT);
      this.filtered = this.catalog
         .songs()
         .stream()
         .filter(ScooterMusicSources::validName)
         .filter(var1x -> var1x.toLowerCase(Locale.ROOT).contains(var1))
         .filter(var1x -> !this.favoritesOnly || ScooterMusicFavorites.contains(var1x))
         .toList();
      this.page = Math.min(this.page, Math.max(0, (this.filtered.size() - 1) / Math.max(1, this.pageSize)));
      this.refresh();
   }

   void refresh() {
      for (int var1 = 0; var1 < this.rows.size(); var1++) {
         int var2 = this.page * this.pageSize + var1;
         Button var3 = this.rows.get(var1);
         Button var4 = this.stars.get(var1);
         var3.visible = var4.visible = var2 < this.filtered.size();
         var3.active = this.catalog.wav() && var3.visible;
         if (var3.visible) {
            String var5 = this.filtered.get(var2);
            boolean var6 = ScooterMusicFavorites.contains(var5);
            var3.setMessage(Component.literal(this.font.plainSubstrByWidth(var5, 252)));
            var3.setTooltip(Tooltip.create(Component.literal(var5)));
            var4.setMessage(Component.literal(var6 ? "★" : "☆"));
            var4.setTooltip(Tooltip.create(Component.literal(var6 ? "Remove favorite" : "Save favorite")));
         }
      }
   }

   static void send(String var0, String var1) {
      PacketDistributor.sendToServer(new ScooterMusicMenu.Request(var0, var1), new CustomPacketPayload[0]);
   }

   public void render(GuiGraphics var1, int var2, int var3, float var4) {
      this.renderBackground(var1, var2, var3, var4);
      super.render(var1, var2, var3, var4);
      var1.drawCenteredString(this.font, this.title, this.width / 2, Math.max(10, (this.height - (52 + this.pageSize * 22 + 152)) / 2 - 20), -23241);
      if (!this.importMessage.isBlank()) {
         var1.drawCenteredString(this.font, Component.literal(this.font.plainSubstrByWidth(this.importMessage, Math.max(1, this.width - 20))),
            this.width / 2, this.height - 30, -2959136);
      }
      if (!this.catalog.wav()) {
         var1.drawCenteredString(
            this.font, Component.literal("Add a note block at the smithing table to play WAVs."), this.width / 2, this.height - 18, -3355444
         );
      }
   }

   public void onClose() {
      this.back.run();
   }

   public boolean isPauseScreen() {
      return false;
   }
}
