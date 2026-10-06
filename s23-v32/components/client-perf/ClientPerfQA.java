import java.io.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import io.netty.buffer.*;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pl.aridlin.psychiatrykroles.clientperf.SnapshotReflectionCache;

/** Offline comparison against real shipped method bytecode; no Minecraft launch. */
public class ClientPerfQA {
    public static volatile Object recorder;
    private static Class<?> reference;
    private static Method originalRead, originalEntity;
    private static byte[] referenceBytes() throws Exception {
        byte[] bytes;
        try (ZipFile zip = new ZipFile("work/replay-recording/flashback_neoforge_fixed-1.0.14.jar")) {
            bytes = zip.getInputStream(zip.getEntry("dev/flashbackfix/compat/ModdedPayloadSnapshotCache.class")).readAllBytes();
        }
        ClassNode input = new ClassNode(); new ClassReader(bytes).accept(input, 0);
        ClassNode output = new ClassNode(); output.version = Opcodes.V21;
        output.access = Opcodes.ACC_PUBLIC; output.name = "qa/Reference"; output.superName = "java/lang/Object";
        for (MethodNode method : input.methods) {
            if (!Set.of("readField", "findEntityId", "shouldExclude").contains(method.name)) continue;
            method.access = Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC;
            output.methods.add(method);
        }
        ClassWriter writer = new ClassWriter(0); output.accept(writer); return writer.toByteArray();
    }
    private static byte[] handlerBytes() throws Exception {
        ClassNode node = new ClassNode();
        new ClassReader(Files.readAllBytes(Path.of("work/client-perf/classes/pl/aridlin/psychiatrykroles/clientperf/mixin/IdlePayloadCaptureMixin.class"))).accept(node, 0);
        node.name = "qa/IdleCaptureHandler";
        for (MethodNode method : node.methods) {
            for (AbstractInsnNode instruction : method.instructions) {
                if (instruction instanceof FieldInsnNode field && field.owner.equals("com/moulberry/flashback/Flashback") && field.name.equals("RECORDER")) {
                    field.owner = "ClientPerfQA"; field.name = "recorder"; field.desc = "Ljava/lang/Object;";
                }
                if (instruction instanceof MethodInsnNode call && call.owner.endsWith("/SnapshotCacheAccess")) {
                    call.owner = "qa/Reference"; call.name = "shouldExclude"; call.itf = false;
                }
            }
        }
        ClassWriter writer = new ClassWriter(0); node.accept(writer); return writer.toByteArray();
    }
    static class Loader extends ClassLoader {
        Loader() { super(ClientPerfQA.class.getClassLoader()); }
        Class<?> define(String name, byte[] bytes) { return defineClass(name, bytes, 0, bytes.length); }
    }
    private static void eq(Object expected, Object actual, String label) {
        if (!Objects.equals(expected, actual)) throw new AssertionError(label+": "+expected+" != "+actual);
    }
    private static Object read(Object value, String field) throws Exception { return originalRead.invoke(null, value, field); }
    private static Object entity(CustomPacketPayload value) throws Exception { return originalEntity.invoke(null, value); }
    public static class Parent { private int entityId = 17; private UUID uuid = UUID.randomUUID(); }
    public static class Child extends Parent { private String pos = "point"; }
    public static class Shadow extends Parent { private String entityId = "not a number"; }
    public static class Mutable { public int id = 1; }
    public static class EmptyPayload implements CustomPacketPayload {
        public Type<? extends CustomPacketPayload> type() { return new Type<>(ResourceLocation.parse("create:empty")); }
    }
    public static class MethodPayload extends EmptyPayload { public int entityId() { return 42; } public int getEntityId() { return 43; } }
    public static class InheritedPayload extends MethodPayload {}
    public static class ThrowPayload extends EmptyPayload { public int entityId() { throw new IllegalArgumentException("expected fixture"); } public int getEntityId() { return 44; } }
    public static class WrongType extends EmptyPayload { public String entityId() { return "invalid"; } public long getEntityId() { return 45; } }
    public record RecordPayload(int entityId, String text) implements CustomPacketPayload {
        public Type<? extends CustomPacketPayload> type() { return new Type<>(ResourceLocation.parse("create:record")); }
        public int getEntityId() { return 900; }
    }
    public record WrongRecordPayload(String entityId) implements CustomPacketPayload {
        public Type<? extends CustomPacketPayload> type() { return new Type<>(ResourceLocation.parse("create:wrong_record")); }
        public int getEntityId() { return 46; }
    }
    public record IdPayload(ResourceLocation id) implements CustomPacketPayload {
        public Type<? extends CustomPacketPayload> type() { return new Type<>(id); }
    }
    public static void main(String[] args) throws Exception {
        Loader loader = new Loader(); reference = loader.define("qa.Reference", referenceBytes());
        originalRead = reference.getMethod("readField", Object.class, String.class);
        originalEntity = reference.getMethod("findEntityId", CustomPacketPayload.class);
        for (Object object : List.of(new Parent(), new Child(), new Shadow(), new EmptyPayload()))
            for (String field : List.of("id", "uuid", "pos", "entityId", "remainingBatches", "nothing"))
                eq(read(object, field), SnapshotReflectionCache.readField(object, field), "field "+object.getClass()+"/"+field);
        Mutable mutable = new Mutable(); eq(1, SnapshotReflectionCache.readField(mutable,"id"), "mutable before");
        mutable.id = 9; eq(9, SnapshotReflectionCache.readField(mutable,"id"), "mutable after");
        for (CustomPacketPayload payload : List.of(new EmptyPayload(), new MethodPayload(), new InheritedPayload(), new ThrowPayload(), new WrongType(), new RecordPayload(47,"a"), new WrongRecordPayload("bad")))
            eq(entity(payload), SnapshotReflectionCache.findEntityId(payload), "entity "+payload.getClass());
        System.out.println("PASS: cached reflection equals shipped original methods for absent/inherited/shadowed/mutable fields, records, getter precedence and throwing getters.");
        Class<?> handler = loader.define("qa.IdleCaptureHandler", handlerBytes());
        Method gate = handler.getDeclaredMethod("psychiatryk$skipUnusedIdleCapture", ConnectionProtocol.class,CustomPacketPayload.class,ByteBuf.class,int.class,int.class,CallbackInfo.class);
        gate.setAccessible(true);
        try {
            for (String id : List.of("sable:state", "minecraft:brand", "neoforge:network", "flashback:event", "flashback_neoforge_fixed:state", "create:sync_rail_graph", "sophisticatedstorage:sync", "psychiatryk_roles:party")) {
                for (ConnectionProtocol protocol : List.of(ConnectionProtocol.PLAY,ConnectionProtocol.CONFIGURATION)) {
                    for (boolean recording : List.of(false,true)) {
                        recorder = recording ? new Object() : null;
                        ByteBuf buffer = Unpooled.buffer(); buffer.writeBytes(new byte[]{12,34,56,78}); buffer.readerIndex(1);
                        int reader = buffer.readerIndex(), writer = buffer.writerIndex(), refs = buffer.refCnt();
                        CallbackInfo callback = new CallbackInfo("capture", true);
                        ResourceLocation location = ResourceLocation.parse(id);
                        gate.invoke(null,protocol,new IdPayload(location),buffer,0,writer,callback);
                        boolean excluded = (boolean) reference.getMethod("shouldExclude", ResourceLocation.class).invoke(null,location);
                        eq(protocol == ConnectionProtocol.PLAY && !recording && excluded,callback.isCancelled(),"gate "+id+"/"+protocol+"/"+recording);
                        eq(reader,buffer.readerIndex(),"reader unchanged"); eq(writer,buffer.writerIndex(),"writer unchanged"); eq(refs,buffer.refCnt(),"refcount unchanged");
                        eq(34,(int)buffer.getByte(1),"wire bytes unchanged"); buffer.release();
                    }
                }
            }
        } finally { recorder = null; }
        System.out.println("PASS: 32 real compiled handler/policy cases; recording and configuration preserved, snapshot-eligible Create/storage/roles idle pre-roll preserved, skipped capture leaves buffer bytes/indices/refcount intact.");
        // Use exact per-thread allocation counters instead of sampled JFR allocation weights.
        com.sun.management.ThreadMXBean bean = (com.sun.management.ThreadMXBean)java.lang.management.ManagementFactory.getThreadMXBean();
        EmptyPayload empty = new EmptyPayload(); for(int i=0;i<10_000;i++) SnapshotReflectionCache.findEntityId(empty);
        long tid=Thread.currentThread().threadId(), before=bean.getThreadAllocatedBytes(tid);
        for(int i=0;i<100_000;i++) { SnapshotReflectionCache.findEntityId(empty); SnapshotReflectionCache.readField(empty,"nothing"); }
        long delta=bean.getThreadAllocatedBytes(tid)-before;
        System.out.println("Cached negative discovery allocated "+delta+" bytes for 100000 field+entity iterations (JIT overhead included).");
        before=bean.getThreadAllocatedBytes(tid);
        for(int i=0;i<10_000;i++) { entity(empty); read(empty,"nothing"); }
        long originalDelta=bean.getThreadAllocatedBytes(tid)-before;
        System.out.println("Shipped original negative discovery allocated "+originalDelta+" bytes for 10000 field+entity iterations (reference invocation overhead included).");
    }
}
