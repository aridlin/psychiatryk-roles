package pl.aridlin.psychiatrykroles.jukebox;

import com.mojang.blaze3d.vertex.PoseStack;
import io.wispforest.accessories.api.client.AccessoriesRendererRegistry;
import io.wispforest.accessories.api.client.AccessoryRenderer;
import io.wispforest.accessories.api.slot.SlotReference;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

/** Uses Minecraft's baked jukebox block and follows the torso, including crouching. */
public final class WearableJukeboxRenderer implements AccessoryRenderer {
    @Override
    public <M extends LivingEntity> void render(ItemStack stack, SlotReference reference,
            PoseStack pose, EntityModel<M> model, MultiBufferSource buffers, int light,
            float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
            float netHeadYaw, float headPitch) {
        if (!stack.is(Items.JUKEBOX) || !WearableJukebox.renderable(reference)
                || !(model instanceof HumanoidModel<?> humanoid)) return;
        pose.pushPose();
        try {
            // Body coordinates: y points down, and positive z is the player's back.
            // This cube spans x [-.25,.25], y [.125,.625], z [.14,.64].
            // No head transform or head yaw participates in its position.
            humanoid.body.translateAndRotate(pose);
            pose.translate(-0.25, 0.625, 0.64);
            pose.scale(0.5f, -0.5f, -0.5f);
            Minecraft.getInstance().getBlockRenderer().renderSingleBlock(
                    Blocks.JUKEBOX.defaultBlockState(), pose, buffers, light, OverlayTexture.NO_OVERLAY);
        } finally {
            pose.popPose();
        }
    }

    @EventBusSubscriber(modid = "psychiatryk_roles", bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Setup {
        @SubscribeEvent
        public static void setup(FMLClientSetupEvent event) {
            event.enqueueWork(() -> AccessoriesRendererRegistry.registerRenderer(
                    Items.JUKEBOX, WearableJukeboxRenderer::new));
        }
    }
}
