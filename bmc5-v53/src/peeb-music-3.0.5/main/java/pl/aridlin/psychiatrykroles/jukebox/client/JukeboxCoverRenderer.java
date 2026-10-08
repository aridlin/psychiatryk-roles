package pl.aridlin.psychiatrykroles.jukebox.client;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.HashSet;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.MultiBufferSource.BufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.entity.JukeboxBlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent.Stage;
import pl.aridlin.kukirin.MusicNowPlayingClient;

@EventBusSubscriber(
   modid = "psychiatryk_peeb",
   value = {Dist.CLIENT}
)
public final class JukeboxCoverRenderer {
   private static final ResourceLocation WHITE = ResourceLocation.parse("psychiatryk_peeb:textures/entity/white.png");
   private static final double MAX_RENDER_DISTANCE_SQUARED = 16384.0;
   public static long renderedPlaced;
   public static long renderedWorn;
   private static JukeboxCoverRenderer.Draw lastPlaced;
   private static JukeboxCoverRenderer.Draw lastWorn;

   public static JukeboxCoverRenderer.Draw lastPlaced() {
      return current(lastPlaced);
   }

   public static JukeboxCoverRenderer.Draw lastWorn() {
      return current(lastWorn);
   }

   private static JukeboxCoverRenderer.Draw current(JukeboxCoverRenderer.Draw var0) {
      if (var0 == null) {
         return null;
      } else {
         MusicNowPlayingClient.SourceView var1 = MusicNowPlayingClient.view(var0.source());
         return var1 == null
               || !var0.session().equals(var1.session())
               || (var0.block() == null ? var1.entity() == null || var1.entity().getId() != var0.entityId() : !var0.block().equals(var1.block()))
            ? null
            : var0;
      }
   }

   @SubscribeEvent
   public static void placed(RenderLevelStageEvent var0) {
      if (var0.getStage() == Stage.AFTER_BLOCK_ENTITIES) {
         Minecraft var1 = Minecraft.getInstance();
         if (var1.level != null && var1.player != null) {
            BufferSource var2 = var1.renderBuffers().bufferSource();
            PoseStack var3 = var0.getPoseStack();
            Vec3 var4 = var0.getCamera().getPosition();
            HashSet<RenderType> var5 = new HashSet<>();
            HashSet var6 = new HashSet();
            lastPlaced = null;
            var3.pushPose();

            try {
               var3.translate(-var4.x, -var4.y, -var4.z);

               for (MusicNowPlayingClient.SourceView var8 : MusicNowPlayingClient.sources()) {
                  BlockPos var9 = var8.block();
                  if (var9 != null
                     && var8.entity() == null
                     && var8.discArtwork() != null
                     && var1.level.hasChunkAt(var9)
                     && var6.add(var9)
                     && !(var9.getCenter().distanceToSqr(var4) > 16384.0)
                     && var1.level.getBlockEntity(var9) instanceof JukeboxBlockEntity
                     && var0.getFrustum().isVisible(new AABB(var9).inflate(0.01))) {
                     int var10 = LevelRenderer.getLightColor(var1.level, var9.above());
                     RenderType var11 = RenderType.entityCutoutNoCull(WHITE);
                     RenderType var12 = RenderType.entityCutoutNoCull(var8.discArtwork());
                     var3.pushPose();

                     try {
                        var3.translate((float)var9.getX(), (float)var9.getY(), (float)var9.getZ());
                        JukeboxCoverGeometry.vinylRim(var3, var2.getBuffer(var11), var10);
                        JukeboxCoverGeometry.vinylArt(var3, var2.getBuffer(var12), var10, var8.elapsedSeconds());
                     } finally {
                        var3.popPose();
                     }

                     var5.add(var11);
                     var5.add(var12);
                     lastPlaced = new JukeboxCoverRenderer.Draw(
                        var8.source(),
                        var8.session(),
                        var8.discArtwork(),
                        var10,
                        JukeboxCoverGeometry.rotationDegrees(var8.elapsedSeconds()),
                        var9.immutable(),
                        -1
                     );
                     renderedPlaced++;
                  }
               }
            } finally {
               var3.popPose();

               for (RenderType var16 : var5) {
                  var2.endBatch(var16);
               }
            }
         } else {
            lastPlaced = null;
            lastWorn = null;
         }
      }
   }

   public static void worn(LivingEntity var0, PoseStack var1, MultiBufferSource var2, int var3) {
      Minecraft var4 = Minecraft.getInstance();
      if (var4.level != null && var0.level() == var4.level && var0.isAlive() && !var0.isRemoved()) {
         MusicNowPlayingClient.SourceView var5 = MusicNowPlayingClient.view(var0.getUUID());
         if (var5 != null && var5.entity() == var0 && var5.block() == null && var5.artwork() != null) {
            JukeboxCoverGeometry.worn(var1, var2.getBuffer(RenderType.entityCutoutNoCull(var5.artwork())), var3);
            lastWorn = new JukeboxCoverRenderer.Draw(var5.source(), var5.session(), var5.artwork(), var3, 0.0, null, var0.getId());
            renderedWorn++;
         }
      }
   }

   private JukeboxCoverRenderer() {
   }

   public static record Draw(UUID source, UUID session, ResourceLocation artwork, int light, double rotationDegrees, BlockPos block, int entityId) {
   }
}
