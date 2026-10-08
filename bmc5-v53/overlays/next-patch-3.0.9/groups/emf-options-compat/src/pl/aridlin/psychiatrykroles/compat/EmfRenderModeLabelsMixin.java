package pl.aridlin.psychiatrykroles.compat;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
@Mixin(targets="toni.sodiumoptionsmodcompat.integration.emf.EmfModelsOptionPage",remap=false)
public abstract class EmfRenderModeLabelsMixin {
 @ModifyArg(method="lambda$create$23",at=@At(value="INVOKE",target="Lnet/caffeinemc/mods/sodium/client/gui/options/control/CyclingControl;<init>(Lnet/caffeinemc/mods/sodium/client/gui/options/Option;Ljava/lang/Class;[Lnet/minecraft/network/chat/Component;)V"),index=2,remap=false,require=1)
 private static Component[] psychiatryk$correctRenderModeLabels(Component[] original) {
  if(original.length!=6)return original;
  return new Component[]{original[0],original[2],original[3],original[4],original[5]};
 }
}
