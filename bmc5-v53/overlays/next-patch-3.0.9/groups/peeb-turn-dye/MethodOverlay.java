import java.nio.file.*;
import java.util.*;
import java.io.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.util.*;

/** Preserve deployed bytecode outside the requested client render methods. */
public final class MethodOverlay {
    static ClassNode node(byte[] data) {
        ClassNode result = new ClassNode(); new ClassReader(data).accept(result, 0); return result;
    }
    static String trace(MethodNode method) {
        StringWriter string = new StringWriter(); PrintWriter out = new PrintWriter(string);
        Textifier text = new Textifier(); method.accept(new TraceMethodVisitor(text));
        text.print(out); out.flush(); return string.toString();
    }
    static boolean selected(MethodNode method, boolean mesh) {
        return mesh ? Set.of("animate", "skin", "draw").contains(method.name)
                    : method.name.equals("player");
    }
    public static void main(String[] args) throws Exception {
        boolean mesh = args[3].equals("mesh");
        byte[] original = Files.readAllBytes(Path.of(args[0]));
        ClassNode donor = node(Files.readAllBytes(Path.of(args[1]))), old = node(original);
        List<MethodNode> replacements = donor.methods.stream().filter(m -> selected(m, mesh)).toList();
        if (replacements.size() != (mesh ? 4 : 1)) throw new AssertionError("Unexpected render method scope");
        for (MethodNode method : replacements)
            if (old.methods.stream().noneMatch(m -> m.name.equals(method.name) && m.desc.equals(method.desc)))
                throw new AssertionError("Method signature drift");
        ClassReader reader = new ClassReader(original); ClassWriter writer = new ClassWriter(reader, 0);
        reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                                             String signature, String[] exceptions) {
                if (replacements.stream().anyMatch(m -> m.name.equals(name) && m.desc.equals(descriptor))) return null;
                if (mesh && name.equals("protectEyes")) return null;
                return super.visitMethod(access, name, descriptor, signature, exceptions);
            }
            public void visitEnd() { for (MethodNode method : replacements) method.accept(writer); super.visitEnd(); }
        }, 0);
        byte[] output = writer.toByteArray(); ClassNode next = node(output); int unchanged = 0;
        for (MethodNode method : old.methods) {
            if (mesh && method.name.equals("protectEyes")) continue;
            MethodNode after = next.methods.stream().filter(m -> m.name.equals(method.name) && m.desc.equals(method.desc)).findFirst().orElseThrow();
            if (!selected(method, mesh)) {
                if (!trace(method).equals(trace(after))) throw new AssertionError("Unrequested method changed: " + method.name);
                unchanged++;
            }
        }
        if (next.fields.size() != old.fields.size() || next.methods.size() != old.methods.size() - (mesh ? 1 : 0))
            throw new AssertionError("Unexpected field/method set");
        Path out = Path.of(args[2]); Files.createDirectories(out.getParent()); Files.write(out, output);
        System.out.println("{\"success\":true,\"unchanged_methods\":" + unchanged + ",\"removed_eye_push_method\":" + mesh + "}");
    }
}
