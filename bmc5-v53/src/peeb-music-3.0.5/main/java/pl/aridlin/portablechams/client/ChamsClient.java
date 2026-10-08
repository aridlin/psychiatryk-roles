package pl.aridlin.portablechams.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent.Post;
import net.neoforged.neoforge.common.NeoForge;
import pl.aridlin.portablechams.api.HighlightCollector;
import pl.aridlin.portablechams.api.HighlightStyle;
import pl.aridlin.portablechams.api.Highlights;

public final class ChamsClient {
   private static Object level;
   private static final Set<UUID> markedEntities = new HashSet<>();
   private static final Set<BlockPos> markedBlocks = new HashSet<>();
   private static boolean sessionEnabled = true;
   private static int lastRendered;

   private ChamsClient() {
   }

   public static void initialize(IEventBus var0) {
      var0.addListener((RegisterShadersEvent var0x) -> {
         try {
            shaders(var0x);
         } catch (IOException var2) {
            throw new UncheckedIOException(var2);
         }
      });
      NeoForge.EVENT_BUS.addListener(ChamsClient::tick);
      NeoForge.EVENT_BUS.addListener(ChamsClient::render);
      NeoForge.EVENT_BUS.addListener(ChamsClient::commands);
      Highlights.register(ResourceLocation.fromNamespaceAndPath("portable_chams", "manual"), var0x -> {
         Minecraft var1 = Minecraft.getInstance();
         if (var1.level != null) {
            for (Entity var3 : var1.level.entitiesForRendering()) {
               if (markedEntities.contains(var3.getUUID())) {
                  var0x.entity(var3, HighlightStyle.halftone(16777045));
               }
            }

            for (BlockPos var5 : markedBlocks) {
               var0x.block(var5, HighlightStyle.halftone(16755285));
            }
         }
      });
   }

   private static void shaders(RegisterShadersEvent var0) throws IOException {
      var0.registerShader(
         new ShaderInstance(
            var0.getResourceProvider(), ResourceLocation.fromNamespaceAndPath("portable_chams", "party_halftone"), DefaultVertexFormat.NEW_ENTITY
         ),
         var0x -> ChamsPass.mask = var0x
      );
      var0.registerShader(
         new ShaderInstance(var0.getResourceProvider(), ResourceLocation.fromNamespaceAndPath("portable_chams", "item_mask"), DefaultVertexFormat.NEW_ENTITY),
         var0x -> ChamsPass.itemMask = var0x
      );
      var0.registerShader(
         new ShaderInstance(var0.getResourceProvider(), ResourceLocation.fromNamespaceAndPath("portable_chams", "occlusion_mask"), DefaultVertexFormat.BLOCK),
         var0x -> ChamsPass.occlusionMask = var0x
      );
      var0.registerShader(
         new ShaderInstance(
            var0.getResourceProvider(), ResourceLocation.fromNamespaceAndPath("portable_chams", "entity_occlusion_mask"), DefaultVertexFormat.NEW_ENTITY
         ),
         var0x -> ChamsPass.entityOcclusionMask = var0x
      );
      var0.registerShader(
         new ShaderInstance(
            var0.getResourceProvider(), ResourceLocation.fromNamespaceAndPath("portable_chams", "party_composite"), DefaultVertexFormat.POSITION_TEX
         ),
         var0x -> ChamsPass.composite = var0x
      );
   }

   private static void tick(Post var0) {
      ChamsConfig.poll();
      Minecraft var1 = Minecraft.getInstance();
      if (level != var1.level) {
         level = var1.level;
         markedEntities.clear();
         markedBlocks.clear();
         ChamsPass.release();
      }
   }

   private static void render(RenderLevelStageEvent var0) {
      if (ShaderCompatibility.stage(var0) && !PortalGuard.nestedPortalRender()) {
         ChamsPass.frame++;
         lastRendered = 0;
         Minecraft var1 = Minecraft.getInstance();
         if (sessionEnabled && ChamsConfig.get().enabled() && ChamsEffect.ready() && var1.level != null && var1.player != null) {
            ChamsClient.Collector var2 = new ChamsClient.Collector();
            Highlights.collect(var2);
            var2.active = false;
            Vec3 var3 = var0.getCamera().getPosition();
            var2.targets.sort(Comparator.comparingDouble(var1x -> var1x.bounds.getCenter().distanceToSqr(var3)));

            try (ShaderCompatibility var4 = ShaderCompatibility.matrices(var0)) {
               for (ChamsClient.Target var6 : var2.targets) {
                  if (!(var6.bounds.getCenter().distanceTo(var3) > ChamsConfig.get().maxDistance())) {
                     if (lastRendered >= ChamsConfig.get().maxTargets()) {
                        break;
                     }

                     lastRendered++;
                     ChamsEffect.halftone = var6.style.halftone();

                     try {
                        switch (var6.shape) {
                           case ENTITY:
                              if (var6.entity instanceof ItemEntity var7) {
                                 ChamsEffect.item(var0, var7, var6.style.rgb());
                              } else {
                                 ChamsEffect.entity(var0, (LivingEntity)var6.entity, var6.style.rgb());
                              }
                              break;
                           case BLOCK:
                           case BOX:
                              if (var6.excludeBlocks) {
                                 ChamsEffect.blockBox(var0, var6.id, var6.bounds, var6.style.rgb());
                              } else {
                                 ChamsEffect.box(var0, var6.id, var6.bounds, var6.style.rgb());
                              }
                              break;
                           case PLANE:
                              ChamsEffect.plane(var0, var6.id, var6.bounds, var6.style.rgb());
                              break;
                           case GLYPH:
                              ChamsEffect.chalk(var0, var6.id, var6.bounds, var6.style.rgb());
                        }
                     } finally {
                        ChamsEffect.halftone = true;
                     }
                  }
               }
            }
         }
      }
   }

   private static void commands(RegisterClientCommandsEvent var0) {
      LiteralArgumentBuilder var1 = (LiteralArgumentBuilder)Commands.literal("portablechams")
         .executes(
            var0x -> {
               ((CommandSourceStack)var0x.getSource())
                  .sendSuccess(
                     () -> Component.literal("Portable Chams: /portablechams mark | block | clear | toggle. API providers: " + Highlights.providerCount()),
                     false
                  );
               return 1;
            }
         );
      var1.then(Commands.literal("mark").executes(var0x -> {
         Minecraft var1x = Minecraft.getInstance();
         if (var1x.hitResult instanceof EntityHitResult var2 && (var2.getEntity() instanceof LivingEntity || var2.getEntity() instanceof ItemEntity)) {
            UUID var4 = var2.getEntity().getUUID();
            if (!markedEntities.add(var4)) {
               markedEntities.remove(var4);
            }

            return 1;
         }

         ((CommandSourceStack)var0x.getSource()).sendFailure(Component.literal("Aim at a nearby living entity or dropped item."));
         return 0;
      }));
      var1.then(Commands.literal("block").executes(var0x -> {
         Minecraft var1x = Minecraft.getInstance();
         if (var1x.hitResult instanceof BlockHitResult var2 && var2.getType() == Type.BLOCK) {
            BlockPos var4 = var2.getBlockPos().immutable();
            if (!markedBlocks.add(var4)) {
               markedBlocks.remove(var4);
            }

            return 1;
         }

         return 0;
      }));
      var1.then(Commands.literal("clear").executes(var0x -> {
         markedEntities.clear();
         markedBlocks.clear();
         return 1;
      }));
      var1.then(Commands.literal("toggle").executes(var0x -> {
         sessionEnabled = !sessionEnabled;
         ((CommandSourceStack)var0x.getSource()).sendSuccess(() -> Component.literal("Portable Chams " + (sessionEnabled ? "enabled" : "disabled")), false);
         return 1;
      }));
      var0.getDispatcher().register(var1);
   }

   public static int lastRenderedTargets() {
      return lastRendered;
   }

   public static boolean rendererReady() {
      return ChamsEffect.ready();
   }

   private static final class Collector implements HighlightCollector {
      private final Thread owner = Thread.currentThread();
      private final List<ChamsClient.Target> targets = new ArrayList<>();
      private final Set<UUID> ids = new HashSet<>();
      private boolean active = true;

      private void add(ChamsClient.Target var1) {
         if (this.active && this.owner == Thread.currentThread()) {
            Objects.requireNonNull(var1.style);
            Objects.requireNonNull(var1.bounds);
            if (finite(var1.bounds) && this.targets.size() < 256 && this.ids.add(var1.id)) {
               this.targets.add(var1);
            }
         } else {
            throw new IllegalStateException("HighlightCollector escaped its render callback");
         }
      }

      private static boolean finite(AABB var0) {
         return Double.isFinite(var0.minX)
            && Double.isFinite(var0.minY)
            && Double.isFinite(var0.minZ)
            && Double.isFinite(var0.maxX)
            && Double.isFinite(var0.maxY)
            && Double.isFinite(var0.maxZ);
      }

      public void entity(Entity var1, HighlightStyle var2) {
         Minecraft var3 = Minecraft.getInstance();
         if (var1 != null
            && !var1.isRemoved()
            && var1.level() == var3.level
            && var1 != var3.getCameraEntity()
            && (var1 instanceof LivingEntity || var1 instanceof ItemEntity)) {
            this.add(new ChamsClient.Target(var1.getUUID(), var1.getBoundingBox(), var2, var1, ChamsClient.Shape.ENTITY, false));
         }
      }

      public void block(BlockPos var1, HighlightStyle var2) {
         Minecraft var3 = Minecraft.getInstance();
         if (var3.level != null && var3.level.hasChunkAt(var1)) {
            BlockState var4 = var3.level.getBlockState(var1);
            if (!var4.isAir()) {
               VoxelShape var5 = var4.getShape(var3.level, var1);
               AABB var6 = var5.isEmpty() ? new AABB(var1) : var5.bounds().move(var1);
               UUID var7 = UUID.nameUUIDFromBytes((var3.level.dimension().location() + ":" + var1.asLong()).getBytes(StandardCharsets.UTF_8));
               this.add(new ChamsClient.Target(var7, var6, var2, null, ChamsClient.Shape.BLOCK, true));
            }
         }
      }

      public void box(UUID var1, AABB var2, HighlightStyle var3, boolean var4) {
         this.add(new ChamsClient.Target(Objects.requireNonNull(var1), var2, var3, null, ChamsClient.Shape.BOX, var4));
      }

      public void plane(UUID var1, AABB var2, HighlightStyle var3) {
         this.add(new ChamsClient.Target(Objects.requireNonNull(var1), var2, var3, null, ChamsClient.Shape.PLANE, false));
      }

      public void glyph(UUID var1, AABB var2, HighlightStyle var3) {
         this.add(new ChamsClient.Target(Objects.requireNonNull(var1), var2, var3, null, ChamsClient.Shape.GLYPH, false));
      }
   }

   private static enum Shape {
      ENTITY,
      BLOCK,
      BOX,
      PLANE,
      GLYPH;
   }

   private static record Target(UUID id, AABB bounds, HighlightStyle style, Entity entity, ChamsClient.Shape shape, boolean excludeBlocks) {
   }
}
