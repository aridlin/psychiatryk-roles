package pl.aridlin.psychiatrykroles.peeb.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import io.wispforest.accessories.api.slot.SlotReference;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pl.aridlin.psychiatrykroles.jukebox.WearableJukeboxRenderer;
import pl.aridlin.psychiatrykroles.jukebox.client.JukeboxCoverRenderer;

@Mixin({WearableJukeboxRenderer.class})
public abstract class WearableCoverMixin {
   @Inject(
      method = {"render"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/renderer/block/BlockRenderDispatcher;renderSingleBlock(Lnet/minecraft/world/level/block/state/BlockState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V",
         shift = Shift.AFTER
      )}
   )
   private void jukebox$cover(
      ItemStack var1,
      SlotReference var2,
      PoseStack var3,
      EntityModel<?> var4,
      MultiBufferSource var5,
      int var6,
      float var7,
      float var8,
      float var9,
      float var10,
      float var11,
      float var12,
      CallbackInfo var13
   ) {
      JukeboxCoverRenderer.worn(var2.entity(), var3, var5, var6);
   }
}
