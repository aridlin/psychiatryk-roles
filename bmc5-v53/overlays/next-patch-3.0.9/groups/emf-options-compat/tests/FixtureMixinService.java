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
import org.spongepowered.asm.launch.platform.container.IContainerHandle;
import org.spongepowered.asm.logging.ILogger;
import org.spongepowered.asm.logging.LoggerAdapterConsole;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.service.*;

/** Test-only service: uses real Sponge Mixin, reading an exact external EMF option jar. */
public final class FixtureMixinService extends MixinServiceAbstract
        implements IClassBytecodeProvider, IClassProvider, IClassTracker, ITransformerProvider {
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
    public ClassNode getClassNode(String name) throws ClassNotFoundException, IOException { return getClassNode(name, false); }
    public ClassNode getClassNode(String name, boolean transformers) throws ClassNotFoundException, IOException { return getClassNode(name, transformers, 0); }
    public ClassNode getClassNode(String name, boolean transformers, int flags) throws ClassNotFoundException, IOException {
        String resource = name.replace('.', '/') + ".class";
        byte[] bytes;
        if (resource.equals("toni/sodiumoptionsmodcompat/integration/emf/EmfModelsOptionPage.class") || resource.equals("traben/entity_model_features/config/EMFConfig$RenderModeChoice.class")) {
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
        ClassNode node = new ClassNode(); new ClassReader(bytes).accept(node, flags); return node;
    }
    public void registerInvalidClass(String name) {}
    public boolean isClassLoaded(String name) { return false; }
    public String getClassRestrictions(String name) { return ""; }
    public Collection<ITransformer> getTransformers() { return List.of(); }
    public Collection<ITransformer> getDelegatedTransformers() { return List.of(); }
    public void addTransformerExclusion(String name) {}
}
