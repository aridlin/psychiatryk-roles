package pl.aridlin.kukirin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.sounds.SoundSource;
import net.neoforged.neoforge.network.PacketDistributor;

public final class MusicCategoryScreen extends Screen {
   private static Screen back;
   private final SoundSource category;

   private MusicCategoryScreen(SoundSource var1) {
      super(Component.literal("Server music category"));
      this.category = var1;
   }

   public static void request(Screen var0) {
      back = var0;
      MusicCategoryConfig.clientHandler(var0x -> {
         if (var0x.open()) {
            Minecraft.getInstance().setScreen(new MusicCategoryScreen(MusicCategoryConfig.client()));
         }
      });
      PacketDistributor.sendToServer(new MusicCategoryConfig.Edit("query", ""), new CustomPacketPayload[0]);
   }

   protected void init() {
      int var1 = this.width / 2 - 150;
      int var2 = this.height / 2 - 40;
      this.addRenderableWidget(
         Button.builder(Component.literal("MASTER"), var1x -> this.choose(SoundSource.MASTER))
            .bounds(var1, var2, 146, 24)
            .tooltip(Tooltip.create(Component.literal("Use Master volume. The Music slider will not mute custom jukebox and scooter music.")))
            .build()
      );
      this.addRenderableWidget(
         Button.builder(Component.literal("MUSIC"), var1x -> this.choose(SoundSource.MUSIC))
            .bounds(var1 + 154, var2, 146, 24)
            .tooltip(Tooltip.create(Component.literal("Use the Music slider together with Master volume.")))
            .build()
      );
      this.addRenderableWidget(
         Button.builder(
               Component.literal("Reload saved setting"),
               var0 -> PacketDistributor.sendToServer(new MusicCategoryConfig.Edit("reload", ""), new CustomPacketPayload[0])
            )
            .bounds(var1, var2 + 32, 300, 22)
            .build()
      );
      this.addRenderableWidget(Button.builder(Component.literal("Back"), var1x -> this.onClose()).bounds(var1, var2 + 62, 300, 22).build());
   }

   private void choose(SoundSource var1) {
      PacketDistributor.sendToServer(new MusicCategoryConfig.Edit("save", var1.getName()), new CustomPacketPayload[0]);
   }

   public void render(GuiGraphics var1, int var2, int var3, float var4) {
      this.renderBackground(var1, var2, var3, var4);
      super.render(var1, var2, var3, var4);
      var1.drawCenteredString(this.font, this.title, this.width / 2, this.height / 2 - 92, -23241);
      var1.drawCenteredString(
         this.font,
         Component.literal("Current: " + this.category.name() + (this.category == SoundSource.BLOCKS ? " (original)" : "")),
         this.width / 2,
         this.height / 2 - 70,
         -1
      );
      var1.drawCenteredString(
         this.font, Component.literal("Applies to everyone, including music already playing."), this.width / 2, this.height / 2 + 65, -3355444
      );
   }

   public void onClose() {
      Minecraft.getInstance().setScreen(back);
   }

   public boolean isPauseScreen() {
      return false;
   }
}
