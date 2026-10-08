package pl.aridlin.psychiatrykroles.runtime.mixin.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pl.aridlin.psychiatrykroles.runtime.gui.GuiBlurPolicy;

/** Keep each GUI's existing menu texture and artwork, but leave the world sharp. */
@Mixin(Screen.class)
abstract class ScreenBlurMixin {
    @Inject(method = "renderBlurredBackground", at = @At("HEAD"), cancellable = true)
    private void psychiatryk$keepWorldSharp(float partialTick, CallbackInfo ci) {
        if (GuiBlurPolicy.avoidsWorldBlur(getClass().getName(), Minecraft.getInstance().level != null)) {
            ci.cancel();
        }
    }
}
