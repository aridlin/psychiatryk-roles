package pl.aridlin.partymarkers;

import java.util.ArrayList;
import java.util.List;
import net.deadlydiamond98.way.common.events.WayTickingEvent;
import net.deadlydiamond98.way.util.PlayerLocation;
import net.deadlydiamond98.way.util.mixin.IWayPlayer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/** Fixed GUI-size party dots using WAY's server-authorized, dimension-local positions. */
@Mod(value = "goplanska_party_markers", dist = Dist.CLIENT)
public final class PartyMarkers {
    private record Dot(float x, float y, int color, String label, double distance, boolean player) {}
    private Vec3 previousPosition;
    private double travelSpeed=4.317;
    private Object speedLevel;
    private static String eta(double distance,double speed){long seconds=Math.max(1,Math.round(distance/Math.max(.5,speed)));return seconds>=3600?(seconds/3600)+"h "+((seconds%3600)/60)+"m":seconds>=60?(seconds/60)+"m "+(seconds%60)+"s":seconds+"s";}
    private final List<Dot> dots = new ArrayList<>();
    private Object frameLevel;
    private long frameTime;

    public PartyMarkers() {
        NeoForge.EVENT_BUS.addListener(this::project);
        NeoForge.EVENT_BUS.addListener(this::draw);
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.ClientTickEvent.Post e)->{var mc=Minecraft.getInstance();if(mc.player==null||speedLevel!=mc.level){previousPosition=null;speedLevel=mc.level;travelSpeed=4.317;}if(mc.player!=null){var now=mc.player.position();if(previousPosition!=null){double speed=now.distanceTo(previousPosition)*20;if(speed>.2&&speed<60)travelSpeed=travelSpeed*.8+speed*.2;}previousPosition=now;}});
        org.slf4j.LoggerFactory.getLogger("GoplanskaPartyMarkers").info("Smooth party dots and adaptive halftone 2.0.6 loaded");
    }

    private void project(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        if (qouteall.imm_ptl.core.render.context_management.PortalRendering.isRendering()) return;
        dots.clear();
        Minecraft mc = Minecraft.getInstance();
        frameLevel = mc.level;
        frameTime = System.nanoTime();
        if (mc.player == null || mc.level == null) return;
        if (qouteall.imm_ptl.core.render.context_management.PortalRendering.isRendering()) return;
        Matrix4f transform = new Matrix4f(event.getProjectionMatrix()).mul(com.mojang.blaze3d.systems.RenderSystem.getModelViewMatrix()).mul(event.getPoseStack().last().pose());
        Vec3 camera = event.getCamera().getPosition();
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        for (var member : ClientWaypoints.members()) {
    if(!ChamsCategories.enabled(member.kind().equals("player")?ChamsCategories.Kind.PARTY:ChamsCategories.Kind.MARKED))continue;
            if (!member.dimension().equals(mc.level.dimension().location().toString())) continue;
            Vec3 target = ClientWaypoints.position(member);
            var entity = mc.level.getEntity(member.entityId());
            if(entity!=null&&!entity.getUUID().equals(member.uuid()))entity=null;
            if (entity != null) target = new Vec3(net.minecraft.util.Mth.lerp(partial,entity.xOld,entity.getX()),net.minecraft.util.Mth.lerp(partial,entity.yOld,entity.getY()),net.minecraft.util.Mth.lerp(partial,entity.zOld,entity.getZ())).add(0, entity.getBbHeight() + 0.5, 0);
            Vec3 relative = target.subtract(camera);
            Vector4f clip = transform.transform(new Vector4f((float) relative.x, (float) relative.y, (float) relative.z, 1));
            float[] point = Projection.screen(clip.x, clip.y, clip.w);
            if (point != null) dots.add(new Dot(point[0], point[1], member.color(), member.name(), target.subtract(0,entity!=null?entity.getBbHeight()+.5:member.kind().equals("player")?2.3:2.45,0).distanceTo(mc.player.position()), member.kind().equals("player")));
        }
        for (var waypoint : ClientWaypoints.points()) {
            if (!waypoint.dimension().equals(mc.level.dimension().location().toString())) continue;
            Vec3 target = new Vec3(waypoint.x(),waypoint.y()+.5,waypoint.z());
            Vec3 relative = target.subtract(camera);
            Vector4f clip = transform.transform(new Vector4f((float)relative.x,(float)relative.y,(float)relative.z,1));
            float[] point = Projection.screen(clip.x,clip.y,clip.w);
            if(point!=null)dots.add(new Dot(point[0],point[1],0xff55ffff,waypoint.name()+" "+Math.round(target.distanceTo(mc.player.position()))+" blocks",target.distanceTo(mc.player.position()),false));
        }
    }

    private void draw(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();

        if (mc.player == null || mc.level != frameLevel || mc.options.hideGui || mc.screen != null
                || System.nanoTime() - frameTime > 500_000_000L) return;
        GuiGraphics gui = event.getGuiGraphics();
        for (Dot dot : dots) {
            float x = dot.x * gui.guiWidth();
            float y = dot.y * gui.guiHeight();
            smoothDot(gui,x,y,4f/(float)mc.getWindow().getGuiScale(),dot.color|0xff000000);
            if(!dot.label.isEmpty() && Math.hypot(x-gui.guiWidth()/2f,y-gui.guiHeight()/2f) <= 14f/(float)mc.getWindow().getGuiScale()){String label=dot.label+(dot.player?" · "+Math.round(dot.distance)+" blocks":"");gui.drawString(mc.font,label,Math.round(x+6),Math.round(y-4),dot.color,true);if(dot.player&&mc.options.keyShift.isDown())gui.drawString(mc.font,"~ETA "+eta(dot.distance,travelSpeed)+" (straight line)",Math.round(x+6),Math.round(y+7),dot.color,true);}
        }
    }

    private static void smoothDot(GuiGraphics gui,float x,float y,float radius,int color){
        // Use Minecraft's GUI overlay render type, so shader/depth state from world rendering cannot hide the dot.
        var b=gui.bufferSource().getBuffer(net.minecraft.client.renderer.RenderType.guiOverlay());
        var matrix=gui.pose().last().pose();float feather=1f/(float)Minecraft.getInstance().getWindow().getGuiScale();
        for(int i=0;i<32;i++){double a=i*Math.PI*2/32,c=(i+1)*Math.PI*2/32;float ax=(float)Math.cos(a),ay=(float)Math.sin(a),cx=(float)Math.cos(c),cy=(float)Math.sin(c);
          vertex(b,matrix,x,y,color);vertex(b,matrix,x,y,color);vertex(b,matrix,x+cx*radius,y+cy*radius,color);vertex(b,matrix,x+ax*radius,y+ay*radius,color);
          int transparent=color&0xffffff;
          vertex(b,matrix,x+cx*radius,y+cy*radius,color);vertex(b,matrix,x+cx*(radius+feather),y+cy*(radius+feather),transparent);vertex(b,matrix,x+ax*(radius+feather),y+ay*(radius+feather),transparent);vertex(b,matrix,x+ax*radius,y+ay*radius,color);
        }
        gui.flush();
    }
    private static void vertex(com.mojang.blaze3d.vertex.VertexConsumer b,org.joml.Matrix4f m,float x,float y,int color){b.addVertex(m,x,y,400).setColor(color);}
}
