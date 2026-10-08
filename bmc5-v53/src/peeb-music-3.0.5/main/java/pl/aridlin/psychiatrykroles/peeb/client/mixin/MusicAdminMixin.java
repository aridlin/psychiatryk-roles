package pl.aridlin.psychiatrykroles.peeb.client.mixin;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pl.aridlin.kukirin.MusicCategoryScreen;
import pl.aridlin.kukirin.ScooterAdminScreen;

@Mixin({ScooterAdminScreen.class})
public abstract class MusicAdminMixin extends Screen {
   protected MusicAdminMixin(Component var1) {
      super(var1);
   }

   @Inject(
      method = {"init"},
      at = {@At("TAIL")}
   )
   private void music$category(CallbackInfo var1) {
      int var2 = Math.min(380, this.width - 32);
      int var3 = (this.width - var2) / 2;
      this.addRenderableWidget(
         Button.builder(Component.literal("Music category"), var1x -> MusicCategoryScreen.request(this)).bounds(var3, this.height - 46, var2, 22).build()
      );
   }
}
