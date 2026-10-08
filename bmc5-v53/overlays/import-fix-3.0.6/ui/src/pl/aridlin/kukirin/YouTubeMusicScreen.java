package pl.aridlin.kukirin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.PacketDistributor;

public final class YouTubeMusicScreen extends Screen {
   private final Screen back;
   private EditBox link;
   private Button song;
   private Button playlist;
   private boolean busy;
   private String saved = "";
   private String message = "Paste a YouTube Music song or public playlist link.";

   public YouTubeMusicScreen(Screen var1) {
      super(Component.literal("YouTube Music"));
      this.back = var1;
      if (var1 instanceof ScooterMusicScreen picker && !picker.importMessage.isBlank()) {
         this.message = picker.importMessage;
         this.busy = picker.importBusy;
      }
   }

   static ScooterMusicScreen library(Screen screen) {
      if (screen instanceof ScooterMusicScreen picker) return picker;
      if (screen instanceof YouTubeMusicScreen importing && importing.back instanceof ScooterMusicScreen picker) return picker;
      return null;
   }

   public static void receive(MusicImports.Status value) {
      Screen screen = Minecraft.getInstance().screen;
      if (screen instanceof YouTubeMusicScreen importing) {
         importing.message = value.message();
         importing.busy = value.busy();
         importing.enable();
      }
      ScooterMusicScreen picker = library(screen);
      if (picker != null) picker.importStatus(value);
   }

   public static void completed(ScooterMusicMenu.Catalog value) {
      catalog(value, true);
   }

   /** Catalog packets may arrive after the user has closed the import screen. */
   static void catalog(ScooterMusicMenu.Catalog value, boolean imported) {
      catalog(value, imported, "");
   }

   static void catalog(ScooterMusicMenu.Catalog value, boolean imported, String focusSong) {
      Minecraft minecraft = Minecraft.getInstance();
      Screen screen = minecraft.screen;
      ScooterMusicScreen picker = library(screen);
      if (picker != null) {
         if (imported && screen instanceof YouTubeMusicScreen importing) {
            importing.busy = false;
            minecraft.setScreen(picker);
         }
         picker.updateCatalog(value, imported, focusSong);
      } else if (imported && screen instanceof YouTubeMusicScreen) {
         minecraft.setScreen(new ScooterMusicScreen(value));
      }
      // Unrelated screens and gameplay are never replaced by a late import result.
   }

   protected void init() {
      int var1 = this.width / 2 - 160;
      int var2 = this.height / 2 - 70;
      this.link = new EditBox(this.font, var1, var2, 320, 20, Component.literal("Song or playlist URL"));
      this.link.setMaxLength(2048);
      this.link.setHint(Component.literal("https://music.youtube.com/…"));
      this.link.setValue(this.saved);
      this.link.setResponder(var1x -> {
         this.saved = var1x;
         this.enable();
      });
      this.addRenderableWidget(this.link);
      this.setInitialFocus(this.link);
      this.song = (Button)this.addRenderableWidget(
         Button.builder(Component.literal("Play song"), var1x -> this.submit(false)).bounds(var1, var2 + 30, 154, 20).build()
      );
      this.playlist = (Button)this.addRenderableWidget(
         Button.builder(Component.literal("Play playlist"), var1x -> this.submit(true))
            .bounds(var1 + 166, var2 + 30, 154, 20)
            .tooltip(Tooltip.create(Component.literal("Stream a public playlist in order. Turn Loop OFF to advance to the next track.")))
            .build()
      );
      this.addRenderableWidget(Button.builder(Component.literal("Back"), var1x -> this.onClose()).bounds(var1, var2 + 110, 320, 20).build());
      this.enable();
   }

   private void enable() {
      if (this.song != null) {
         this.song.active = this.playlist.active = !this.busy && !this.saved.isBlank();
      }
   }

   private void submit(boolean var1) {
      try {
         MusicImports.canonical(this.saved, var1);
      } catch (Exception var3) {
         this.message = var1 ? "Use a public playlist URL containing list=…" : "Use a song URL containing v=… or a youtu.be link.";
         return;
      }

      this.busy = true;
      this.message = "Preparing audio on ProLiant; playback starts when the stream is ready.";
      this.enable();
      PacketDistributor.sendToServer(new MusicImports.Request(this.saved, var1), new CustomPacketPayload[0]);
   }

   public void render(GuiGraphics var1, int var2, int var3, float var4) {
      this.renderBackground(var1, var2, var3, var4);
      super.render(var1, var2, var3, var4);
      var1.drawCenteredString(this.font, this.title, this.width / 2, this.height / 2 - 98, -23241);
      var1.drawWordWrap(this.font, Component.literal(this.message), this.width / 2 - 160, this.height / 2 - 8, 320, -2959136);
      var1.drawWordWrap(
         this.font,
         Component.literal("Streams from ProLiant · Imported songs appear in the server library as they become ready."),
         this.width / 2 - 160,
         this.height / 2 + 24,
         320,
         -7496787
      );
   }

   public void onClose() {
      Minecraft.getInstance().setScreen(this.back);
   }

   public boolean isPauseScreen() {
      return false;
   }
}
