package pl.aridlin.spectate.mixin;
import pl.aridlin.spectate.SpectateClient;
import net.minecraft.client.*;
import net.minecraft.client.renderer.*;
import net.minecraft.world.item.ItemDisplayContext;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(GameRenderer.class)
public class SpectateHandsMixin {
 @Inject(method="renderItemInHand",at=@At("TAIL"))
 private void spectatorHands(Camera camera,float partial,org.joml.Matrix4f projection,CallbackInfo ci){var mc=Minecraft.getInstance();var target=SpectateClient.target();if(target==null||!mc.options.getCameraType().isFirstPerson()||mc.options.hideGui)return;var data=SpectateClient.data;var pose=new PoseStack();var buffers=mc.renderBuffers().bufferSource();int light=mc.getEntityRenderDispatcher().getPackedLightCoords(target,partial);for(int hand=0;hand<2;hand++){var item=hand==0?data.inventory().get(Math.min(8,Math.max(0,data.selected()))):data.inventory().size()>40?data.inventory().get(40):net.minecraft.world.item.ItemStack.EMPTY;pose.pushPose();pose.translate(hand==0?.56:-.56,-.52,-.72);pose.mulPose(Axis.YP.rotationDegrees(hand==0?-35:35));mc.getItemRenderer().renderStatic(target,item,hand==0?ItemDisplayContext.FIRST_PERSON_RIGHT_HAND:ItemDisplayContext.FIRST_PERSON_LEFT_HAND,hand==1,pose,buffers,target.level(),light,net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY,target.getId());pose.popPose();}buffers.endBatch();}
}
