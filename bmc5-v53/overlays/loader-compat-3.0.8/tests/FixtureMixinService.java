import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.zip.ZipFile;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.launch.platform.container.ContainerHandleVirtual;
import org.spongepowered.asm.launch.MixinLaunchPluginLegacy;
import cpw.mods.modlauncher.serviceapi.ILaunchPluginService;
import org.spongepowered.asm.launch.platform.container.IContainerHandle;
import org.spongepowered.asm.logging.ILogger;
import org.spongepowered.asm.logging.LoggerAdapterConsole;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.service.*;

/** Test-only service: uses real Sponge Mixin, reading an exact external EMF option jar. */
public final class FixtureMixinService extends MixinServiceAbstract
        implements IClassBytecodeProvider, IClassProvider, IClassTracker, ITransformerProvider {
    private final MixinLaunchPluginLegacy actualProvider = createActualProvider();
    private MixinLaunchPluginLegacy createActualProvider() {
        try {
            // An SDK jar may contain the optional class. The absent-dependency
            // fixture must hide it from the native provider's resource fallback.
            var parent = Thread.currentThread().getContextClassLoader();
            Thread.currentThread().setContextClassLoader(new ClassLoader(parent) {
                public URL getResource(String name) {
                    if (System.getProperty("fixture.target.jar", "").isEmpty()
                        && name.equals(System.getProperty("fixture.target.class", "").replace('.', '/') + ".class")) return null;
                    if (System.getProperty("fixture.enum.jar", "").isEmpty()
                        && name.equals("traben/entity_model_features/config/EMFConfig$RenderModeChoice.class")) return null;
                    return super.getResource(name);
                }
            });
            var provider = new MixinLaunchPluginLegacy();
            var field = MixinLaunchPluginLegacy.class.getDeclaredField("transformerLoader");
            field.setAccessible(true);
            // Native provider/flag semantics, with a bounded file-only transport.
            ILaunchPluginService.ITransformerLoader loader = name -> {
                if (!provider.handlesClass(org.objectweb.asm.Type.getObjectType(name.replace('.', '/')), false, "mixin").isEmpty())
                    throw new AssertionError("Mixin must skip itself for its own retrieval reason");
                try { return nativeBytes(name); }
                catch (IOException e) { throw new ClassNotFoundException(name, e); }
            };
            field.set(provider, loader); return provider;
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }
    public String getName() { return "native-fixture"; }
    public boolean isValid() { return true; }
    public MixinEnvironment.Phase getInitialPhase() { return MixinEnvironment.Phase.DEFAULT; }
    public IClassProvider getClassProvider() { return this; }
    public IClassBytecodeProvider getBytecodeProvider() { return this; }
    public ITransformerProvider getTransformerProvider() { return this; }
    public IClassTracker getClassTracker() { return this; }
    public IMixinAuditTrail getAuditTrail() { return null; }
    public Collection<String> getPlatformAgents() { return List.of(); }
    public IContainerHandle getPrimaryContainer() { return new ContainerHandleVirtual("native-fixture"); }
    public InputStream getResourceAsStream(String name) { return getClass().getClassLoader().getResourceAsStream(name); }
    protected ILogger createLogger(String name) { return new LoggerAdapterConsole(name); }
    public URL[] getClassPath() { return new URL[0]; }
    public Class<?> findClass(String name) throws ClassNotFoundException { return findClass(name, true); }
    public Class<?> findClass(String name, boolean initialize) throws ClassNotFoundException { return Class.forName(name, initialize, getClass().getClassLoader()); }
    public Class<?> findAgentClass(String name, boolean initialize) throws ClassNotFoundException { return findClass(name, initialize); }
    public ClassNode getClassNode(String name) throws ClassNotFoundException, IOException { return getClassNode(name, true); }
    public ClassNode getClassNode(String name, boolean transformers) throws ClassNotFoundException, IOException { return getClassNode(name, transformers, 0); }
    public ClassNode getClassNode(String name, boolean transformers, int flags) throws ClassNotFoundException, IOException {
        if (name.equals(System.getProperty("fixture.target.class")) || name.equals("traben.entity_model_features.config.EMFConfig$RenderModeChoice"))
            return actualProvider.getClassNode(name, transformers, flags);
        ClassNode node = new ClassNode(); new ClassReader(nativeBytes(name)).accept(node, flags); return node;
    }
    private byte[] nativeBytes(String name) throws ClassNotFoundException, IOException {
        String resource = name.replace('.', '/') + ".class";
        byte[] bytes;
        if (name.equals(System.getProperty("fixture.target.class")) || resource.equals("traben/entity_model_features/config/EMFConfig$RenderModeChoice.class")) {
            String jar = System.getProperty(resource.startsWith("traben/") ? "fixture.enum.jar" : "fixture.target.jar", "");
            if (jar.isEmpty()) throw new ClassNotFoundException(name);
            try (ZipFile zip = new ZipFile(Path.of(jar).toFile())) {
                if (zip.getEntry(resource) == null) throw new ClassNotFoundException(name);
                bytes = zip.getInputStream(zip.getEntry(resource)).readAllBytes();
            }
        } else {
            try (InputStream in = getResourceAsStream(resource)) {
                if (in == null) throw new ClassNotFoundException(name);
                bytes = in.readAllBytes();
            }
        }
        return bytes;
    }
    public void registerInvalidClass(String name) {}
    public boolean isClassLoaded(String name) { return false; }
    public String getClassRestrictions(String name) { return ""; }
    public Collection<ITransformer> getTransformers() { return List.of(); }
    public Collection<ITransformer> getDelegatedTransformers() { return List.of(); }
    public void addTransformerExclusion(String name) {}
}
