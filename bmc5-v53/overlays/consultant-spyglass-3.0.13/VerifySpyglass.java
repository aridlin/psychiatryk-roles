import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipFile;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.util.CheckClassAdapter;

/** Bytecode verification for the two changed classes without starting Minecraft. */
public final class VerifySpyglass {
    public static void main(String[] args) throws Exception {
        try (ZipFile jar = new ZipFile(Path.of(args[0]).toFile())) {
            for (String name : new String[] {
                    "pl/aridlin/psychiatrykroles/CustomPresets.class",
                    "pl/aridlin/psychiatrykroles/PsychiatrykRoles.class"}) {
                byte[] bytes = jar.getInputStream(jar.getEntry(name)).readAllBytes();
                CheckClassAdapter.verify(new ClassReader(bytes), VerifySpyglass.class.getClassLoader(), false,
                        new PrintWriter(System.out, true));
                System.out.println("VERIFIED " + name);
            }
        }
    }
}
