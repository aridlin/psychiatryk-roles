import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

/** Existing bootstrap entry point; the completed BMC migration is never replayed. */
public final class MigrationStarter {
    public static void main(String[] args) throws Exception {
        Path root = Path.of("").toAbsolutePath();
        try { PatchActivation.apply(root, Instant.now()); }
        catch (IllegalStateException unsafeState) { throw unsafeState; }
        catch (Exception rejected) {
            System.err.println("[Patch] Scheduled addon patch rejected; verified current release retained: " + rejected.getMessage());
        }
        Path arguments = root.resolve("libraries/net/neoforged/neoforge/21.1.250/unix_args.txt");
        if (!Files.isRegularFile(arguments)) throw new IOException("Exact NeoForge 21.1.250 runtime missing");
        String javaExe = Path.of(System.getProperty("java.home"), "bin/java").toString();
        Process child = new ProcessBuilder(List.of(javaExe, "-Xms512M", "-Xmx10G", "-XX:+UseG1GC",
                "-Djava.awt.headless=true", "@" + arguments, "nogui"))
                .directory(root.toFile()).inheritIO().start();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (child.isAlive()) {
                child.destroy();
                try { child.waitFor(50, java.util.concurrent.TimeUnit.SECONDS); }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
            }
        }));
        System.exit(child.waitFor());
    }
}
