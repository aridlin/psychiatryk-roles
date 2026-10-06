package pl.aridlin.portablechams.client;

import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.*;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.*;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import pl.aridlin.portablechams.api.*;

/** Client wiring and explicit manual demo selection. Automatic scanning is absent. */
public final class ChamsClient {
    private static Object level;
    private static final Set<UUID> markedEntities=new HashSet<>();
    private static final Set<BlockPos> markedBlocks=new HashSet<>();
    private static boolean sessionEnabled=true;
    private static int lastRendered;
    private ChamsClient() {}
    public static void initialize(IEventBus modBus) {
        modBus.addListener((RegisterShadersEvent event)->{try{shaders(event);}catch(java.io.IOException error){throw new java.io.UncheckedIOException(error);}});
        NeoForge.EVENT_BUS.addListener(ChamsClient::tick);
        NeoForge.EVENT_BUS.addListener(ChamsClient::render);
        NeoForge.EVENT_BUS.addListener(ChamsClient::commands);
        Highlights.register(ResourceLocation.fromNamespaceAndPath("portable_chams","manual"),sink->{
            var mc=Minecraft.getInstance();if(mc.level==null)return;
            for(var entity:mc.level.entitiesForRendering())if(markedEntities.contains(entity.getUUID()))sink.entity(entity,HighlightStyle.halftone(0xFFFF55));
            for(var pos:markedBlocks)sink.block(pos,HighlightStyle.halftone(0xFFAA55));
        });
    }
    private static void shaders(RegisterShadersEvent e)throws java.io.IOException {
        e.registerShader(new ShaderInstance(e.getResourceProvider(),ResourceLocation.fromNamespaceAndPath("portable_chams","party_halftone"),DefaultVertexFormat.NEW_ENTITY),v->ChamsPass.mask=v);
        e.registerShader(new ShaderInstance(e.getResourceProvider(),ResourceLocation.fromNamespaceAndPath("portable_chams","item_mask"),DefaultVertexFormat.NEW_ENTITY),v->ChamsPass.itemMask=v);
        e.registerShader(new ShaderInstance(e.getResourceProvider(),ResourceLocation.fromNamespaceAndPath("portable_chams","occlusion_mask"),DefaultVertexFormat.BLOCK),v->ChamsPass.occlusionMask=v);
        e.registerShader(new ShaderInstance(e.getResourceProvider(),ResourceLocation.fromNamespaceAndPath("portable_chams","entity_occlusion_mask"),DefaultVertexFormat.NEW_ENTITY),v->ChamsPass.entityOcclusionMask=v);
        e.registerShader(new ShaderInstance(e.getResourceProvider(),ResourceLocation.fromNamespaceAndPath("portable_chams","party_composite"),DefaultVertexFormat.POSITION_TEX),v->ChamsPass.composite=v);
    }
    private static void tick(ClientTickEvent.Post e) {
        ChamsConfig.poll();var mc=Minecraft.getInstance();
        if(level!=mc.level){level=mc.level;markedEntities.clear();markedBlocks.clear();ChamsPass.release();}
    }
    private enum Shape {ENTITY,BLOCK,BOX,PLANE,GLYPH}
    private record Target(UUID id,AABB bounds,HighlightStyle style,Entity entity,Shape shape,boolean excludeBlocks) {}
    private static final class Collector implements HighlightCollector {
        private final Thread owner=Thread.currentThread();
        private final List<Target> targets=new ArrayList<>();
        private final Set<UUID> ids=new HashSet<>();
        private boolean active=true;
        private void add(Target target) {
            if(!active||owner!=Thread.currentThread())throw new IllegalStateException("HighlightCollector escaped its render callback");
            Objects.requireNonNull(target.style);Objects.requireNonNull(target.bounds);
            if(!finite(target.bounds)||targets.size()>=256||!ids.add(target.id))return;
            targets.add(target);
        }
        private static boolean finite(AABB b){return Double.isFinite(b.minX)&&Double.isFinite(b.minY)&&Double.isFinite(b.minZ)&&Double.isFinite(b.maxX)&&Double.isFinite(b.maxY)&&Double.isFinite(b.maxZ);}
        public void entity(Entity e,HighlightStyle style) {
            var mc=Minecraft.getInstance();if(e==null||e.isRemoved()||e.level()!=mc.level||e==mc.getCameraEntity()||(!(e instanceof LivingEntity)&&!(e instanceof ItemEntity)))return;
            add(new Target(e.getUUID(),e.getBoundingBox(),style,e,Shape.ENTITY,false));
        }
        public void block(BlockPos pos,HighlightStyle style) {
            var mc=Minecraft.getInstance();if(mc.level==null||!mc.level.hasChunkAt(pos))return;
            var state=mc.level.getBlockState(pos);if(state.isAir())return;
            var shape=state.getShape(mc.level,pos);var box=shape.isEmpty()?new AABB(pos):shape.bounds().move(pos);
            UUID id=UUID.nameUUIDFromBytes((mc.level.dimension().location()+":"+pos.asLong()).getBytes(StandardCharsets.UTF_8));
            add(new Target(id,box,style,null,Shape.BLOCK,true));
        }
        public void box(UUID id,AABB box,HighlightStyle style,boolean excluded){add(new Target(Objects.requireNonNull(id),box,style,null,Shape.BOX,excluded));}
        public void plane(UUID id,AABB box,HighlightStyle style){add(new Target(Objects.requireNonNull(id),box,style,null,Shape.PLANE,false));}
        public void glyph(UUID id,AABB box,HighlightStyle style){add(new Target(Objects.requireNonNull(id),box,style,null,Shape.GLYPH,false));}
    }
    private static void render(RenderLevelStageEvent e) {
        if(e.getStage()!=RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS||PortalGuard.nestedPortalRender())return;
        ChamsPass.frame++;lastRendered=0;var mc=Minecraft.getInstance();
        if(!sessionEnabled||!ChamsConfig.get().enabled()||!ChamsEffect.ready()||mc.level==null||mc.player==null)return;
        var collector=new Collector();Highlights.collect(collector);collector.active=false;
        var camera=e.getCamera().getPosition();collector.targets.sort(Comparator.comparingDouble(t->t.bounds.getCenter().distanceToSqr(camera)));
        for(var target:collector.targets) {
            if(target.bounds.getCenter().distanceTo(camera)>ChamsConfig.get().maxDistance())continue;
            if(lastRendered>=ChamsConfig.get().maxTargets())break;
            lastRendered++;ChamsEffect.halftone=target.style.halftone();
            try {
                switch(target.shape) {
                    case ENTITY -> {if(target.entity instanceof ItemEntity item)ChamsEffect.item(e,item,target.style.rgb());else ChamsEffect.entity(e,(LivingEntity)target.entity,target.style.rgb());}
                    case BLOCK,BOX -> {if(target.excludeBlocks)ChamsEffect.blockBox(e,target.id,target.bounds,target.style.rgb());else ChamsEffect.box(e,target.id,target.bounds,target.style.rgb());}
                    case PLANE -> ChamsEffect.plane(e,target.id,target.bounds,target.style.rgb());
                    case GLYPH -> ChamsEffect.chalk(e,target.id,target.bounds,target.style.rgb());
                }
            } finally {ChamsEffect.halftone=true;}
        }
    }
    private static void commands(RegisterClientCommandsEvent e) {
        var root=Commands.literal("portablechams").executes(c->{c.getSource().sendSuccess(()->Component.literal("Portable Chams: /portablechams mark | block | clear | toggle. API providers: "+Highlights.providerCount()),false);return 1;});
        root.then(Commands.literal("mark").executes(c->{var mc=Minecraft.getInstance();if(mc.hitResult instanceof EntityHitResult hit&&(hit.getEntity() instanceof LivingEntity||hit.getEntity() instanceof ItemEntity)){var id=hit.getEntity().getUUID();if(!markedEntities.add(id))markedEntities.remove(id);return 1;}c.getSource().sendFailure(Component.literal("Aim at a nearby living entity or dropped item."));return 0;}));
        root.then(Commands.literal("block").executes(c->{var mc=Minecraft.getInstance();if(mc.hitResult instanceof BlockHitResult hit&&hit.getType()==HitResult.Type.BLOCK){var pos=hit.getBlockPos().immutable();if(!markedBlocks.add(pos))markedBlocks.remove(pos);return 1;}return 0;}));
        root.then(Commands.literal("clear").executes(c->{markedEntities.clear();markedBlocks.clear();return 1;}));
        root.then(Commands.literal("toggle").executes(c->{sessionEnabled=!sessionEnabled;c.getSource().sendSuccess(()->Component.literal("Portable Chams "+(sessionEnabled?"enabled":"disabled")),false);return 1;}));
        e.getDispatcher().register(root);
    }
    public static int lastRenderedTargets() { return lastRendered; }
    public static boolean rendererReady() { return ChamsEffect.ready(); }
}
