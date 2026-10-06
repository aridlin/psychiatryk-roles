package pl.aridlin.clientqa;

import java.nio.file.*;
import java.util.*;
import com.google.gson.*;
import com.mojang.brigadier.CommandDispatcher;
import com.moulberry.flashback.Flashback;
import dev.flashbackfix.compat.InboundPayloadCapture;
import dev.flashbackfix.compat.ModdedPayloadSnapshotCache;
import io.netty.buffer.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.commands.*;
import net.minecraft.network.*;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.*;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.*;
import pl.aridlin.partymarkers.ChamsCategories;
import pl.aridlin.psychiatrykroles.clientperf.mixin.SnapshotCacheAccess;
import com.sonicether.soundphysics.SoundPhysicsMod;

/** Title-only runtime proof. Test helper is excluded from release artifacts. */
@Mod("goplanska_client_perf_qa")
public final class ClientPerfQA {
    private int ticks;
    private boolean done;
    private final Map<String,Object> checks = new LinkedHashMap<>();
    private static final Path OUT=Path.of(System.getProperty("user.home"), "Documents/Codex/2026-10-02/make/outputs/save-io-v32/client-runtime-report.json");
    public ClientPerfQA() { NeoForge.EVENT_BUS.addListener(this::tick); }
    private void tick(ClientTickEvent.Post event) {
        var mc=Minecraft.getInstance();
        if(done || !(mc.screen instanceof TitleScreen) || ++ticks<30) return;
        done=true;
        boolean success=false;
        try {
            check("title_without_world",mc.level==null && mc.getSingleplayerServer()==null);
            check("recording_idle",Flashback.RECORDER==null);
            check("mixin_invoker_sable_excluded",SnapshotCacheAccess.psychiatryk$excludedFromSnapshot(ResourceLocation.fromNamespaceAndPath("sable","qa_save")));
            check("mixin_invoker_create_preserved",!SnapshotCacheAccess.psychiatryk$excludedFromSnapshot(ResourceLocation.fromNamespaceAndPath("create","qa_state")));
            capture("idle_excluded_play",ConnectionProtocol.PLAY,"sable",false);
            capture("snapshot_play",ConnectionProtocol.PLAY,"create",true);
            capture("configuration_excluded_namespace",ConnectionProtocol.CONFIGURATION,"sable",true);
            var record=new EntityPayload(47);
            check("runtime_reflection_record_precedence",Objects.equals(ModdedPayloadSnapshotCache.findEntityId(record),47));
            var dispatcher=new CommandDispatcher<CommandSourceStack>();
            ChamsCategories.commands(new RegisterClientCommandsEvent(dispatcher,null));
            var source=new CommandSourceStack(CommandSource.NULL,Vec3.ZERO,Vec2.ZERO,null,0,"QA",Component.literal("QA"),null,null);
            var others=new EnumMap<ChamsCategories.Kind,Boolean>(ChamsCategories.Kind.class);
            for(var kind:ChamsCategories.Kind.values()) if(kind!=ChamsCategories.Kind.SCOOTER) others.put(kind,ChamsCategories.enabled(kind));
            check("scooter_off_command",dispatcher.execute("chams scooter off",source)==1);
            check("scooter_off_chams_and_dots",!ChamsCategories.enabled(ChamsCategories.Kind.SCOOTER) && !ChamsCategories.memberEnabled("scooter"));
            check("scooter_off_saved",!JsonParser.parseString(Files.readString(Path.of("config/goplanska-chams.json"))).getAsJsonObject().get("scooterHighlights").getAsBoolean());
            check("scooter_on_command",dispatcher.execute("chams scooter on",source)==1);
            check("scooter_on_chams_and_dots",ChamsCategories.enabled(ChamsCategories.Kind.SCOOTER) && ChamsCategories.memberEnabled("scooter"));
            check("scooter_on_saved",JsonParser.parseString(Files.readString(Path.of("config/goplanska-chams.json"))).getAsJsonObject().get("scooterHighlights").getAsBoolean());
            check("other_categories_unchanged",others.entrySet().stream().allMatch(e->ChamsCategories.enabled(e.getKey())==e.getValue()));
            var sound=SoundPhysicsMod.CONFIG;
            check("sound_rays_24",sound.environmentEvaluationRayCount.get()==24);
            check("sound_bounces_2",sound.environmentEvaluationRayBounces.get()==2);
            check("sound_occlusion_rays_8",sound.maxOcclusionRays.get()==8);
            check("sound_moving_enabled",sound.updateMovingSounds.get());
            check("sound_moving_interval_5",sound.soundUpdateInterval.get()==5);
            check("sound_direction_retained",sound.soundDirectionEvaluation.get());
            check("sound_safe_level_access",!sound.unsafeLevelAccess.get());
            checks.put("renderer",org.lwjgl.opengl.GL11.glGetString(org.lwjgl.opengl.GL11.GL_RENDERER));
            success=true;
        } catch(Throwable error) {
            error.printStackTrace();
            checks.put("error",error.toString());
        } finally {
            InboundPayloadCapture.reset(); ModdedPayloadSnapshotCache.reset();
            checks.put("success",success);
            try {Files.createDirectories(OUT.getParent());Files.writeString(OUT,new GsonBuilder().setPrettyPrinting().create().toJson(checks));}
            catch(Exception error){error.printStackTrace();}
            System.out.println("CLIENT PERF QA "+new Gson().toJson(checks));
            mc.stop();
        }
    }
    private void capture(String name,ConnectionProtocol protocol,String namespace,boolean expected) {
        InboundPayloadCapture.reset();ModdedPayloadSnapshotCache.reset();
        var payload=new Payload(ResourceLocation.fromNamespaceAndPath(namespace,"qa_state"));
        var raw=Unpooled.buffer();
        try {
            var buffer=new FriendlyByteBuf(raw);buffer.writeVarInt(0);buffer.writeResourceLocation(payload.id());buffer.writeByte(42);
            int before=CaptureProbe.slices,reader=raw.readerIndex(),writer=raw.writerIndex();
            InboundPayloadCapture.capture(protocol,payload,buffer,0,writer);
            var encoded=InboundPayloadCapture.get(payload,protocol);
            check(name+"_capture",(encoded!=null)==expected);
            check(name+"_allocation_gate",CaptureProbe.slices-before==(expected?1:0));
            check(name+"_buffer_unchanged",raw.readerIndex()==reader && raw.writerIndex()==writer && raw.refCnt()==1);
            if(expected) check(name+"_raw_bytes",encoded.data().length==1 && encoded.data()[0]==42);
        } finally {raw.release();}
    }
    private void check(String name,boolean value) {checks.put(name,value);if(!value) throw new AssertionError(name);}
    public record Payload(ResourceLocation id) implements CustomPacketPayload {public Type<Payload> type(){return new Type<>(id);}}
    public record EntityPayload(int entityId) implements CustomPacketPayload {
        public int getEntityId(){return 900;}
        public Type<EntityPayload> type(){return new Type<>(ResourceLocation.fromNamespaceAndPath("create","qa_entity"));}
    }
}
