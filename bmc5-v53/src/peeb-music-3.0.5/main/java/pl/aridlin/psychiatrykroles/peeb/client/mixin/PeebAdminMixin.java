package pl.aridlin.psychiatrykroles.peeb.client.mixin;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pl.aridlin.kukirin.ScooterAdminScreen;
import pl.aridlin.psychiatrykroles.peeb.client.PeebSettingsScreen;

@Mixin({ScooterAdminScreen.class})
public abstract class PeebAdminMixin extends Screen {
   protected PeebAdminMixin(Component var1) {
      super(var1);
   }

   @Inject(
      method = {"init"},
      at = {@At("TAIL")}
   )
   private void peeb$settings(CallbackInfo var1) {
      Button var2 = null;

      for (GuiEventListener var4 : this.children()) {
         if (var4 instanceof Button var5 && var5.getMessage().getString().equals("Done")) {
            var2 = var5;
            break;
         }
      }

      if (var2 != null) {
         int var6 = var2.getWidth();
         int var7 = var6 / 2 - 4;
         var2.setWidth(var7);
         this.addRenderableWidget(
            Button.builder(Component.literal("Peeb settings"), var1x -> PeebSettingsScreen.request(this))
               .bounds(var2.getX() + var6 / 2 + 4, var2.getY(), var7, var2.getHeight())
               .build()
         );
      }
   }
}
