package pl.aridlin.portablechams.api;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.LoggerFactory;

/** Public client API. Providers supply their own permissions, target selection and lifetime. */
public final class Highlights {
    private Highlights() {}
    private record Provider(Consumer<HighlightCollector> callback) {}
    private static final Map<ResourceLocation, Provider> PROVIDERS = new ConcurrentHashMap<>();
    private static final Set<ResourceLocation> FAILED = ConcurrentHashMap.newKeySet();

    /** Register one provider. Close the returned handle when your adapter is unloaded. */
    public static AutoCloseable register(ResourceLocation id, Consumer<HighlightCollector> callback) {
        Objects.requireNonNull(id); Objects.requireNonNull(callback);
        var provider = new Provider(callback);
        if (PROVIDERS.putIfAbsent(id, provider) != null) throw new IllegalArgumentException("Provider already registered: " + id);
        return () -> { PROVIDERS.remove(id, provider); FAILED.remove(id); };
    }

    /** Renderer entry point; providers are visited deterministically. A broken adapter cannot abort another one. */
    public static void collect(HighlightCollector collector) {
        var snapshot = new ArrayList<>(PROVIDERS.entrySet());
        snapshot.sort(Map.Entry.comparingByKey());
        for (var entry : snapshot) {
            try { entry.getValue().callback.accept(collector); FAILED.remove(entry.getKey()); }
            catch (RuntimeException error) {
                if (FAILED.add(entry.getKey())) LoggerFactory.getLogger("PortableChams").warn("Highlight provider failed: {}", entry.getKey(), error);
            }
        }
    }
    public static int providerCount() { return PROVIDERS.size(); }
}
