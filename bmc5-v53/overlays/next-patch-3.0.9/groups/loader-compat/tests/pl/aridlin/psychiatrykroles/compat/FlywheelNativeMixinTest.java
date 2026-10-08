package pl.aridlin.psychiatrykroles.compat;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Random;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.ZipFile;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.util.Textifier;
import org.objectweb.asm.util.TraceMethodVisitor;
import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.Mixins;
import org.spongepowered.asm.mixin.transformer.IMixinTransformer;

public final class FlywheelNativeMixinTest {
    private static int checks;
    private static final String TARGET = FlywheelLegacyCompatPlugin.TARGET;
    private static final String MIXIN = FlywheelLegacyCompatPlugin.MIXIN;
    private static void check(boolean value) { checks++; if (!value) throw new AssertionError("check " + checks); }
    private static ClassNode read(byte[] bytes) {ClassNode n = new ClassNode();new ClassReader(bytes).accept(n, 0);return n;}
    private static String trace(MethodNode m) {Textifier t = new Textifier();m.accept(new TraceMethodVisitor(t));StringWriter s = new StringWriter();t.print(new PrintWriter(s));return s.toString().replaceAll("(?m)^   FRAME.*\\n", "");}
    private static MethodNode method(ClassNode n, String name, String desc) {return n.methods.stream().filter(m -> m.name.equals(name) && m.desc.equals(desc)).findFirst().orElseThrow();}
    private static final class NativeLoader extends ClassLoader {
        final Path jar; final byte[] bitset;
        NativeLoader(Path jar, byte[] bitset) {super(FlywheelNativeMixinTest.class.getClassLoader());this.jar = jar;this.bitset = bitset;}
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
                Class<?> value = findLoadedClass(name);
                if (value == null && name.startsWith("dev.engine_room.flywheel.")) {
                    try {
                        byte[] b;
                        if (name.equals(TARGET)) b = bitset;
                        else try (ZipFile z = new ZipFile(jar.toFile())) {
                            var entry = z.getEntry(name.replace('.', '/') + ".class");
                            if (entry == null) throw new ClassNotFoundException(name);
                            b = z.getInputStream(entry).readAllBytes();
                        }
                        value = defineClass(name, b, 0, b.length);
                    } catch (java.io.IOException e) {throw new ClassNotFoundException(name, e);}
                }
                if (value == null) value = super.loadClass(name, false);
                if (resolve) resolveClass(value);
                return value;
            }
        }
    }
    private static void setField(Object target, Class<?> type, String name, Object value) throws Exception {
        Field f = type.getDeclaredField(name);f.setAccessible(true);f.set(target, value);
    }
    private static void parallelScenario(NativeLoader loader, int count, boolean succeeds) throws Exception {
        Class<?> bits = loader.loadClass(TARGET);
        Object mask = bits.getConstructor().newInstance();
        bits.getMethod("set", int.class).invoke(mask, 0);
        bits.getMethod("set", int.class).invoke(mask, count);
        Class<?> instancer = loader.loadClass("dev.engine_room.flywheel.backend.engine.indirect.IndirectInstancer");
        Class<?> page = loader.loadClass("dev.engine_room.flywheel.backend.engine.indirect.IndirectInstancer$InstancePage");
        Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");Field field = unsafeClass.getDeclaredField("theUnsafe");field.setAccessible(true);
        Object unsafe = field.get(null);
        Object actual = unsafeClass.getMethod("allocateInstance", Class.class).invoke(unsafe, instancer);
        setField(actual, instancer, "pages", new AtomicReference<>(Array.newInstance(page, count)));
        setField(actual, instancer, "mergeablePages", mask);
        try {instancer.getMethod("parallelUpdate").invoke(actual);check(succeeds);}
        catch (java.lang.reflect.InvocationTargetException e) {check(!succeeds && e.getCause() instanceof ArrayIndexOutOfBoundsException);}
    }
    public static void main(String[] args) throws Exception {
        String mode = args[0];
        MixinBootstrap.init();
        MixinEnvironment.getDefaultEnvironment().setSide(mode.equals("server") ? MixinEnvironment.Side.SERVER : MixinEnvironment.Side.CLIENT);
        FlywheelLegacyCompatPlugin plugin = new FlywheelLegacyCompatPlugin();
        check(!plugin.shouldApplyMixin("different.Target", MIXIN));
        check(!plugin.shouldApplyMixin(TARGET, "different.Mixin"));
        if (mode.equals("absent")) {
            check(!plugin.shouldApplyMixin(TARGET, MIXIN));
            System.out.println("{\"success\":true,\"checks\":" + checks + ",\"mode\":\"absent\",\"no_flywheel_dependency\":true}");return;
        }
        Path jar = Path.of(System.getProperty("fixture.target.jar"));byte[] original;
        try (ZipFile z = new ZipFile(jar.toFile())) {original = z.getInputStream(z.getEntry(TARGET.replace('.', '/') + ".class")).readAllBytes();}
        ClassNode before = read(original);
        if (mode.equals("fingerprint")) {System.out.println("SHA=" + FlywheelLegacyCompatPlugin.normalizedHash(before));return;}
        if (mode.equals("make-unknown")) {
            before.methods.get(0).access ^= 0x1000;
            ClassWriter writer = new ClassWriter(0);before.accept(writer);Files.write(Path.of(args[1]), writer.toByteArray());return;
        }
        boolean enabled = mode.equals("old");
        check(plugin.shouldApplyMixin(TARGET, MIXIN) == (enabled || mode.equals("server")));
        ClassNode unknown = read(original);unknown.sourceFile = "unknown-" + unknown.sourceFile;
        check(!FlywheelLegacyCompatPlugin.normalizedHash(unknown).equals(FlywheelLegacyCompatPlugin.LEGACY_NORMALIZED_SHA256));
        Mixins.addConfiguration("psychiatryk-flywheel-compat.mixins.json");
        Class<?> tt = Class.forName("org.spongepowered.asm.mixin.transformer.MixinTransformer");
        Constructor<?> ctor = tt.getDeclaredConstructor();ctor.setAccessible(true);
        IMixinTransformer transformer = (IMixinTransformer) ctor.newInstance();
        byte[] transformed = transformer.transformClassBytes(TARGET, TARGET, original);
        if (!enabled) {
            check(Arrays.equals(original, transformed));
            System.out.println("{\"success\":true,\"checks\":" + checks + ",\"mode\":\"" + mode + "\",\"byte_unchanged\":true,\"actual_sponge_transformer\":true}");return;
        }
        ClassNode after = read(transformed);check(!Arrays.equals(original, transformed));check(after.fields.size() == before.fields.size());
        for (MethodNode m : before.methods) if (!m.name.equals("clear") || !m.desc.equals("(II)V")) check(trace(m).equals(trace(method(after, m.name, m.desc))));
        NativeLoader oldLoader = new NativeLoader(jar, original);NativeLoader loader = new NativeLoader(jar, transformed);
        Class<?> bits = loader.loadClass(TARGET);var ctorBits = bits.getConstructor();var set = bits.getMethod("set", int.class);var clear = bits.getMethod("clear", int.class, int.class);var next = bits.getMethod("nextSetBit", int.class);var cap = bits.getMethod("currentCapacity");var toBits = bits.getMethod("toBitSet");
        int unpatchedFailures = 0;
        Class<?> oldBits = oldLoader.loadClass(TARGET);
        for (int n = 1; n <= 128; n++) {
            Object value = ctorBits.newInstance();set.invoke(value, 0);set.invoke(value, n);int capacity = (Integer) cap.invoke(value);
            clear.invoke(value, n, capacity + 1);check((Integer) next.invoke(value, n) == -1);check((Integer) next.invoke(value, 0) == 0);check((Integer) cap.invoke(value) == capacity);
            Object old = oldBits.getConstructor().newInstance();oldBits.getMethod("set", int.class).invoke(old, 0);oldBits.getMethod("set", int.class).invoke(old, n);oldBits.getMethod("clear", int.class, int.class).invoke(old, n, capacity + 1);
            if ((Integer) oldBits.getMethod("nextSetBit", int.class).invoke(old, n) >= n) unpatchedFailures++;
        }
        check(unpatchedFailures == 63);
        Random random = new Random(804);
        for (int caseNo = 0; caseNo < 512; caseNo++) {
            Object value = ctorBits.newInstance();BitSet expected = new BitSet();
            for (int i = 0; i < 128; i++) {int bit = random.nextInt(4096);set.invoke(value, bit);expected.set(bit);}
            int from = random.nextInt(4300);int end = from + random.nextInt(5000);if (caseNo == 0) end = Integer.MAX_VALUE;
            int capacity = (Integer) cap.invoke(value);clear.invoke(value, from, end);expected.clear(from, end == Integer.MAX_VALUE ? 8192 : end);
            check(expected.equals(toBits.invoke(value)));check((Integer) cap.invoke(value) == capacity);
        }
        parallelScenario(oldLoader, 5, false);parallelScenario(loader, 5, true);parallelScenario(oldLoader, 6, false);parallelScenario(loader, 6, true);
        Files.write(Path.of(args[1]), transformed);
        System.out.println("{\"success\":true,\"checks\":" + checks + ",\"mode\":\"old\",\"unpatched_tail_failures\":63,\"patched_tail_failures\":0,\"tail_cases\":128,\"random_range_cases\":512,\"actual_parallelUpdate_cases\":4,\"actual_sponge_transformer\":true,\"game_launched\":false}");
    }
}
