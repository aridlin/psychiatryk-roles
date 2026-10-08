package pl.aridlin.kukirin;

import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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
   boolean pausePending;
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
   String currentSong = "";
   UUID currentSource;
   IconButton likeButton;
   IconButton previousPageButton;
   IconButton nextPageButton;
   Layout layout;
   private static final ItemStack FALLBACK_ART = new ItemStack(Items.JUKEBOX);

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
      this.currentSong = var1.currentSong();
      this.currentSource = var1.source();
      this.filtered = var1.songs();
      ScooterMusicFavorites.select(Minecraft.getInstance().player == null ? null : Minecraft.getInstance().player.getUUID());
   }

   protected void init() {
      this.layout = Layout.forScreen(this.width, this.height);
      this.pageSize = this.layout.rows();
      this.rows.clear(); this.stars.clear();
      int x = this.layout.listX(), y = this.layout.searchY(), width = this.layout.listWidth();
      this.favoritesButton = icon(Icon.HEART, "Show favorites only", x + width - 24, y, button -> {
         this.favoritesOnly = !this.favoritesOnly; this.page = 0; this.filter(); this.modeLabels();
      });
      this.search = new EditBox(this.font, x, y, width - 28, 20, Component.literal("Search songs"));
      this.search.setMaxLength(128); this.search.setHint(Component.literal("Search songs…"));
      this.search.setResponder(value -> { this.searchTerm = value; this.page = 0; this.filter(); });
      this.search.setValue(this.searchTerm); this.addRenderableWidget(this.search);
      for (int row = 0; row < this.pageSize; row++) {
         int index = row, rowY = this.layout.rowY() + row * 22;
         this.stars.add(icon(Icon.HEART, "Like song", x, rowY, button -> {
            int selected = this.page * this.pageSize + index;
            if (selected < this.filtered.size() && ScooterMusicFavorites.toggle(this.filtered.get(selected))) {
               this.filter(); this.modeLabels();
            }
         }));
         this.rows.add(this.addRenderableWidget(Button.builder(Component.empty(), button -> {
            int selected = this.page * this.pageSize + index;
            if (selected < this.filtered.size()) send("play", this.filtered.get(selected));
         }).bounds(x + 28, rowY, width - 28, 20).build()));
      }
      this.previousPageButton = icon(Icon.LEFT, "Previous page", x, this.layout.pagerY(), button -> {
         this.page = Math.max(0, this.page - 1); this.refresh();
      });
      this.nextPageButton = icon(Icon.RIGHT, "Next page", x + width - 24, this.layout.pagerY(), button -> {
         this.page = Math.min(Math.max(0, (this.filtered.size() - 1) / this.pageSize), this.page + 1); this.refresh();
      });
      int controlsX = this.layout.controlsX(), controlsY = this.layout.controlsY();
      this.previousSongButton = icon(Icon.PREVIOUS, "Previous song", controlsX, controlsY, button -> send("previous", ""));
      this.pauseButton = icon(this.paused ? Icon.PLAY : Icon.PAUSE, this.paused ? "Resume" : "Pause", controlsX + 28, controlsY,
         button -> { this.pausePending = true; button.active = false; send(this.paused ? "resume" : "pause", ""); });
      this.skipButton = icon(Icon.NEXT, "Next song / skip", controlsX + 56, controlsY, button -> send("skip", ""));
      icon(Icon.STOP, "Stop playback", controlsX + 84, controlsY, button -> send("stop", ""));
      int modeX = this.layout.wide() ? controlsX : controlsX + 112;
      int modeY = this.layout.wide() ? controlsY + 24 : controlsY;
      this.loopButton = icon(Icon.LOOP, "Repeat current song", modeX, modeY, button -> {
         this.loop = !this.loop; send("loop", Boolean.toString(this.loop)); this.modeLabels();
      });
      this.autoplayButton = icon(Icon.AUTOPLAY, "Autoplay next song", modeX + 28, modeY, button -> {
         this.optionsReady = false; send("autoplay", Boolean.toString(!this.autoplay)); this.modeLabels();
      });
      this.shuffleButton = icon(Icon.SHUFFLE, "Shuffle next songs", modeX + 56, modeY, button -> {
         this.optionsReady = false; send("shuffle", Boolean.toString(!this.shuffle)); this.modeLabels();
      });
      this.discButton = icon(Icon.DISC, "Play inserted disc", modeX + 84, modeY, button -> send("disc", ""));
      this.volumeSlider = this.addRenderableWidget(new VolumeSlider(this.layout.volumeX(), this.layout.volumeY(), this.layout.volumeWidth(), 18, this.volume));
      this.likeButton = icon(Icon.HEART, "Like the current song", this.layout.likeX(), this.layout.likeY(), button -> {
         if (!this.currentSong.isEmpty() && ScooterMusicFavorites.toggle(this.currentSong)) { this.filter(); this.modeLabels(); }
      });
      this.importButton = this.addRenderableWidget(Button.builder(Component.literal("YouTube Music / playlist"),
         button -> this.minecraft.setScreen(new YouTubeMusicScreen(this)))
         .bounds(x, this.layout.footerY(), width - 28, 20).build());
      icon(Icon.BACK, "Back", x + width - 24, this.layout.footerY(), button -> this.onClose());
      this.filter(); this.modeLabels();
   }

   private IconButton icon(Icon glyph, String label, int x, int y, Button.OnPress action) {
      IconButton button = new IconButton(x, y, glyph, label, action);
      this.addRenderableWidget(button); return button;
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
      this.pausePending = false;
      this.volume = value.volume();
      this.currentSong = value.currentSong(); this.currentSource = value.source();
      if (imported) {
         this.favoritesOnly = false;
         this.searchTerm = "";
         this.page = 0;
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
      this.pausePending = false;
      this.volume = var1.volume();
      this.currentSong = var1.currentSong(); this.currentSource = var1.source();
      this.optionsReady = true;
      this.modeLabels();
   }

   private void modeLabels() {
      if (this.volumeSlider != null) { this.volumeSlider.serverValue(this.volume); this.volumeSlider.active = this.catalog.wav(); }
      if (this.pauseButton instanceof IconButton button) {
         button.glyph(this.paused ? Icon.PLAY : Icon.PAUSE, this.paused ? "Resume for everyone" : "Pause for everyone");
         button.active = this.catalog.wav() && !this.pausePending;
      }
      if (this.loopButton instanceof IconButton button) { button.selected = this.loop; button.active = this.catalog.wav(); }
      if (this.autoplayButton instanceof IconButton button) { button.selected = this.autoplay; button.active = this.optionsReady && this.catalog.wav(); }
      if (this.shuffleButton instanceof IconButton button) { button.selected = this.shuffle; button.active = this.optionsReady && this.catalog.wav(); }
      if (this.favoritesButton instanceof IconButton button) { button.selected = this.favoritesOnly; button.glyph(Icon.HEART, this.favoritesOnly ? "Show all songs" : "Show favorites only"); }
      if (this.likeButton != null) {
         this.likeButton.active = !this.currentSong.isEmpty(); this.likeButton.selected = ScooterMusicFavorites.contains(this.currentSong);
         this.likeButton.glyph(Icon.HEART, this.likeButton.selected ? "Remove current song from favorites" : "Like current song");
      }
      if (this.importButton != null) this.importButton.active = this.catalog.wav();
      if (this.discButton != null) this.discButton.active = this.catalog.disc();
      if (this.skipButton != null) this.skipButton.active = this.catalog.wav();
      if (this.previousSongButton != null) this.previousSongButton.active = this.catalog.wav();
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
      if (this.previousPageButton != null) this.previousPageButton.active = this.page > 0;
      if (this.nextPageButton != null) this.nextPageButton.active = (this.page + 1) * this.pageSize < this.filtered.size();
      for (int var1 = 0; var1 < this.rows.size(); var1++) {
         int var2 = this.page * this.pageSize + var1;
         Button var3 = this.rows.get(var1);
         Button var4 = this.stars.get(var1);
         var3.visible = var4.visible = var2 < this.filtered.size();
         var3.active = this.catalog.wav() && var3.visible;
         if (var3.visible) {
            String var5 = this.filtered.get(var2);
            boolean var6 = ScooterMusicFavorites.contains(var5);
            var3.setMessage(Component.literal(this.font.plainSubstrByWidth(MusicNowPlaying.fallbackTitle(var5), Math.max(1, var3.getWidth() - 12))));
            var3.setTooltip(Tooltip.create(Component.literal(var5)));
            if (var4 instanceof IconButton button) button.selected = var6;
            var4.setTooltip(Tooltip.create(Component.literal(var6 ? "Remove favorite" : "Save favorite")));
         }
      }
   }

   static void send(String var0, String var1) {
      PacketDistributor.sendToServer(new ScooterMusicMenu.Request(var0, var1), new CustomPacketPayload[0]);
   }

   /** Screen.render calls this once before widgets. Paint our panel after its
    * native background blur, so Now Playing artwork/text stay sharp. */
   @Override public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
      super.renderBackground(graphics, mouseX, mouseY, partialTick);
      if (this.layout == null) return;
      MusicNowPlayingClient.minecraftPanel(graphics, this.layout.x(), this.layout.y(), this.layout.width(), this.layout.footerY() + 22 - this.layout.y());
      this.renderNowPlaying(graphics);
   }

   public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
      super.render(graphics, mouseX, mouseY, partialTick);
      if (this.layout == null) return;
      graphics.drawCenteredString(this.font, this.title, this.width / 2, this.layout.y() - 14, 0xffffffff);
      int pages = Math.max(1, (this.filtered.size() + this.pageSize - 1) / this.pageSize);
      graphics.drawCenteredString(this.font, Component.literal((this.page + 1) + " / " + pages),
         this.layout.listX() + this.layout.listWidth() / 2, this.layout.pagerY() + 6, 0xff404040);
      if (!this.importMessage.isBlank()) graphics.drawCenteredString(this.font,
         Component.literal(this.font.plainSubstrByWidth(this.importMessage, Math.max(1, this.width - 20))), this.width / 2, this.height - 12, 0xffffd478);
      else if (!this.catalog.wav()) graphics.drawCenteredString(this.font,
         Component.literal("Add a note block at a smithing table to play server music."), this.width / 2, this.height - 12, 0xffcccccc);
   }

   private void renderNowPlaying(GuiGraphics graphics) {
      MusicNowPlaying.Info info = this.currentSource == null || this.currentSong.isEmpty() ? null : MusicNowPlayingClient.menuMetadata(this.currentSource);
      ResourceLocation artwork = info == null ? null : MusicNowPlayingClient.menuArt(this.currentSource);
      int artX = this.layout.artX(), artY = this.layout.artY(), artSize = this.layout.artSize();
      graphics.fill(artX - 1, artY - 1, artX + artSize + 1, artY + artSize + 1, 0xff181818);
      if (artwork != null) graphics.blit(artwork, artX, artY, artSize, artSize, 0f, 0f, 64, 64, 64, 64);
      else {
         graphics.fill(artX, artY, artX + artSize, artY + artSize, 0xff777777);
         graphics.pose().pushPose(); graphics.pose().translate(artX + (artSize - 32) / 2f, artY + (artSize - 32) / 2f, 0);
         graphics.pose().scale(2, 2, 1); graphics.renderItem(FALLBACK_ART, 0, 0); graphics.pose().popPose();
      }
      int textX = this.layout.wide() ? this.layout.x() + 12 : artX + artSize + 8;
      int textY = this.layout.wide() ? artY + artSize + 7 : artY + 1;
      int textWidth = this.layout.wide() ? 162 : this.layout.likeX() - textX - 6;
      String title = this.currentSong.isEmpty() ? "No song playing" : info != null ? info.title() : MusicNowPlaying.fallbackTitle(this.currentSong);
      graphics.drawString(this.font, this.paused ? "PAUSED" : "NOW PLAYING", textX, textY, 0xff404040, false);
      graphics.drawString(this.font, fit(title, textWidth), textX, textY + 11, 0xff171717, false);
      String artist = info != null && !info.artist().isEmpty() ? info.artist() : this.currentSong.isEmpty() ? "Choose a song below" : "Server library";
      graphics.drawString(this.font, fit(artist, textWidth), textX, textY + 22, 0xff404040, false);
      if (!this.layout.wide() && info != null && !info.album().isEmpty()) graphics.drawString(this.font, fit(info.album(), textWidth), textX, textY + 33, 0xff555555, false);
   }

   private String fit(String text, int width) {
      if (this.font.width(text) <= width) return text;
      return this.font.plainSubstrByWidth(text, Math.max(0, width - this.font.width("…"))) + "…";
   }

   /** GUI-coordinate layout shared by rendering and the focused overlap checks. */
   static record Layout(boolean wide, int rows, int x, int y, int width, int listX, int listWidth,
      int searchY, int rowY, int pagerY, int controlsX, int controlsY, int volumeX, int volumeY,
      int volumeWidth, int footerY, int likeX, int likeY, int artX, int artY, int artSize) {
      static Layout forScreen(int screenWidth, int screenHeight) {
         boolean wide = screenWidth >= 520;
         int width = Math.min(680, screenWidth - 24), x = (screenWidth - width) / 2;
         if (wide) {
            int rows = Math.clamp((screenHeight - 116L) / 22, 1, 11);
            int y = Math.max(20, (screenHeight - (108 + rows * 22)) / 2);
            int listX = x + 200, listWidth = width - 212;
            int artSize = Math.clamp(screenHeight - 170L, 52, 118), artX = x + (186 - artSize) / 2, artY = y + 8;
            int controlsX = x + 37, controlsY = artY + artSize + 35;
            int pagerY = y + 32 + rows * 22, footerY = Math.max(pagerY + 24, controlsY + 73);
            return new Layout(true, rows, x, y, width, listX, listWidth, y + 8, y + 32, pagerY,
               controlsX, controlsY, x + 12, controlsY + 48, 162, footerY, x + 158, y + 8, artX, artY, artSize);
         }
         int rows = Math.clamp((screenHeight - 216L) / 22, 1, 11), y = 20;
         int pagerY = y + 96 + rows * 22, controlsY = pagerY + 22;
         return new Layout(false, rows, x, y, width, x + 8, width - 16, y + 72, y + 96, pagerY,
            x + (width - 220) / 2, controlsY, x + 8, controlsY + 24, width - 16, controlsY + 46,
            x + width - 32, y + 8, x + 8, y + 8, 54);
      }
   }

   enum Icon {
      PLAY(new String[]{"1000000","1110000","1111100","1111111","1111100","1110000","1000000"}),
      PAUSE(new String[]{"1100011","1100011","1100011","1100011","1100011","1100011","1100011"}),
      PREVIOUS(new String[]{"1000001","1000111","1011111","1111111","1011111","1000111","1000001"}),
      NEXT(new String[]{"1000001","1110001","1111101","1111111","1111101","1110001","1000001"}),
      STOP(new String[]{"1111111","1111111","1111111","1111111","1111111","1111111","1111111"}),
      LOOP(new String[]{"0011110","0110001","1100001","1110001","1000111","1000011","0111110"}),
      AUTOPLAY(new String[]{"1000011","1100011","1110011","1111011","1110011","1100011","1000011"}),
      SHUFFLE(new String[]{"1100011","0010101","0001000","0001000","0010100","1100011","0000001"}),
      DISC(new String[]{"0011100","0111110","1110111","1100011","1110111","0111110","0011100"}),
      BACK(new String[]{"0001000","0011000","0111111","1111111","0111111","0011000","0001000"}),
      LEFT(new String[]{"0001100","0011000","0110000","1100000","0110000","0011000","0001100"}),
      RIGHT(new String[]{"0011000","0001100","0000110","0000011","0000110","0001100","0011000"}),
      HEART(new String[]{"0110110","1111111","1111111","1111111","0111110","0011100","0001000"});
      final String[] pixels;
      Icon(String[] pixels) { this.pixels = pixels; }
   }

   static final class IconButton extends Button {
      Icon icon;
      boolean selected;
      IconButton(int x, int y, Icon icon, String label, OnPress action) {
         super(x, y, 24, 20, Component.literal(label), action, DEFAULT_NARRATION);
         glyph(icon, label);
      }
      void glyph(Icon icon, String label) {
         this.icon = icon; this.setMessage(Component.literal(label)); this.setTooltip(Tooltip.create(Component.literal(label)));
      }
      @Override public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
         int x = this.getX(), y = this.getY(), bg = this.active ? (this.isHoveredOrFocused() ? 0xff999999 : 0xff777777) : 0xff484848;
         graphics.fill(x, y, x + 24, y + 20, 0xff191919); graphics.fill(x + 1, y + 1, x + 23, y + 19, bg);
         graphics.fill(x + 1, y + 1, x + 23, y + 2, 0xffc7c7c7); graphics.fill(x + 1, y + 1, x + 2, y + 19, 0xffaaaaaa);
         graphics.fill(x + 2, y + 18, x + 23, y + 19, 0xff3e3e3e); graphics.fill(x + 22, y + 2, x + 23, y + 18, 0xff3e3e3e);
         int color = !this.active ? 0xff808080 : this.selected ? 0xffffe04a : 0xffeeeeee;
         for (int row = 0; row < this.icon.pixels.length; row++) for (int col = 0; col < this.icon.pixels[row].length(); col++)
            if (this.icon.pixels[row].charAt(col) == '1') graphics.fill(x + 5 + col * 2, y + 3 + row * 2, x + 7 + col * 2, y + 5 + row * 2, color);
         if (this.isFocused()) graphics.renderOutline(x, y, 24, 20, 0xffffffff);
      }
   }

   public void onClose() {
      this.back.run();
   }

   public boolean isPauseScreen() {
      return false;
   }
}
