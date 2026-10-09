import java.nio.file.Files;
import java.nio.file.Path;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

/** Add only the two vanilla spyglass use exceptions to the existing roles class. */
public final class PatchSpyglass implements Opcodes {
    private static final String ROLES = "pl/aridlin/psychiatrykroles/PsychiatrykRoles";
    private static final String PRESETS = "pl/aridlin/psychiatrykroles/CustomPresets";
    private static final String ITEM = "net/minecraft/world/item/ItemStack";

    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("input.class output.class");
        ClassNode node = new ClassNode();
        new ClassReader(Files.readAllBytes(Path.of(args[0]))).accept(node, 0);
        if (!ROLES.equals(node.name)) throw new IllegalStateException("Unexpected class " + node.name);
        int changed = 0;
        for (var method : node.methods) {
            if (method.name.equals("<clinit>")) {
                int listWrites = 0;
                for (var instruction : method.instructions) {
                    if (instruction instanceof FieldInsnNode field && field.getOpcode() == PUTSTATIC
                            && field.owner.equals(ROLES) && field.name.equals("ITEM_PRESETS")) {
                        method.instructions.insertBefore(field, new MethodInsnNode(INVOKESTATIC, PRESETS,
                                "extendSpyglass", "(Ljava/util/List;)Ljava/util/List;", false));
                        listWrites++;
                    }
                }
                if (listWrites != 1) throw new IllegalStateException("Expected one preset list initialization");
                changed++;
                continue;
            }
            if (method.name.equals("presetItem")) {
                if (!method.desc.equals("(Ljava/lang/String;Lnet/minecraft/server/level/ServerPlayer;J)L" + ITEM + ";"))
                    throw new IllegalStateException("Unexpected presetItem signature " + method.desc);
                InsnList first = new InsnList();
                LabelNode normal = new LabelNode();
                first.add(new VarInsnNode(ALOAD, 0));
                first.add(new VarInsnNode(ALOAD, 1));
                first.add(new VarInsnNode(LLOAD, 2));
                first.add(new MethodInsnNode(INVOKESTATIC, PRESETS, "spyglassExtra",
                        "(Ljava/lang/String;Lnet/minecraft/server/level/ServerPlayer;J)L" + ITEM + ";", false));
                first.add(new InsnNode(DUP));
                first.add(new JumpInsnNode(IFNULL, normal));
                first.add(new InsnNode(ARETURN));
                first.add(normal);
                first.add(new FrameNode(F_SAME1, 0, null, 1, new Object[] {ITEM}));
                first.add(new InsnNode(POP));
                method.instructions.insert(first);
                changed++;
                continue;
            }
            if (!method.name.equals("onItemUse") && !method.name.equals("onContainerInteraction")) continue;
            boolean block = method.name.equals("onContainerInteraction");
            String event = block
                    ? "net/neoforged/neoforge/event/entity/player/PlayerInteractEvent$RightClickBlock"
                    : "net/neoforged/neoforge/event/entity/player/PlayerInteractEvent$RightClickItem";
            if (!method.desc.equals("(L" + event + ";)V"))
                throw new IllegalStateException("Unexpected signature " + method.name + method.desc);
            InsnList first = new InsnList();
            LabelNode normal = new LabelNode();
            first.add(new VarInsnNode(ALOAD, 1));
            first.add(new MethodInsnNode(INVOKEVIRTUAL, event, "getItemStack", "()L" + ITEM + ";", false));
            first.add(new MethodInsnNode(INVOKESTATIC, PRESETS, "isConsultantSpyglass", "(L" + ITEM + ";)Z", false));
            first.add(new JumpInsnNode(IFEQ, normal));
            if (block) {
                first.add(new VarInsnNode(ALOAD, 1));
                first.add(new FieldInsnNode(GETSTATIC,
                        "net/neoforged/neoforge/common/util/TriState", "FALSE",
                        "Lnet/neoforged/neoforge/common/util/TriState;"));
                first.add(new MethodInsnNode(INVOKEVIRTUAL, event, "setUseBlock",
                        "(Lnet/neoforged/neoforge/common/util/TriState;)V", false));
                first.add(new VarInsnNode(ALOAD, 1));
                first.add(new FieldInsnNode(GETSTATIC,
                        "net/neoforged/neoforge/common/util/TriState", "TRUE",
                        "Lnet/neoforged/neoforge/common/util/TriState;"));
                first.add(new MethodInsnNode(INVOKEVIRTUAL, event, "setUseItem",
                        "(Lnet/neoforged/neoforge/common/util/TriState;)V", false));
            }
            first.add(new InsnNode(RETURN));
            first.add(normal);
            first.add(new FrameNode(F_SAME, 0, null, 0, null));
            method.instructions.insert(first);
            changed++;
        }
        if (changed != 4) throw new IllegalStateException("Expected list, item, and two interaction hooks; found " + changed);
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        Files.write(Path.of(args[1]), writer.toByteArray());
    }
}
