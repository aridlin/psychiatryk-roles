import java.nio.file.Files;
import java.nio.file.Path;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

/** Relocate the one surviving music-helper call left behind by 3.0.11. */
public final class PatchMusicReference {
    private static final String OLD = "pl/aridlin/psychiatrykroles/music/MusicAudibility";
    private static final String CURRENT = "pl/aridlin/psychiatrykroles/audio/MusicAudibility";

    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("input.class output.class");
        ClassNode node = new ClassNode();
        new ClassReader(Files.readAllBytes(Path.of(args[0]))).accept(node, 0);
        if (!"pl/aridlin/kukirin/ScooterAudioClient".equals(node.name))
            throw new IllegalStateException("Unexpected class " + node.name);
        int changed = 0;
        for (var method : node.methods) {
            for (var instruction : method.instructions) {
                if (instruction instanceof MethodInsnNode call && OLD.equals(call.owner)) {
                    if (!call.name.equals("audible") || !call.desc.equals("(DFFFIZZ)Z"))
                        throw new IllegalStateException("Unexpected old helper call " + call.name + call.desc);
                    call.owner = CURRENT;
                    changed++;
                }
            }
        }
        if (changed != 1) throw new IllegalStateException("Expected one old helper call, found " + changed);
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        Files.write(Path.of(args[1]), writer.toByteArray());
    }
}
