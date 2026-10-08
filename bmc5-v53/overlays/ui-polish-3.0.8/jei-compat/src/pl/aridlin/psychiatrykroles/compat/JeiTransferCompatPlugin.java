package pl.aridlin.psychiatrykroles.compat;

import java.util.List;
import java.util.Set;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.service.MixinService;

/** Bridges only JEI's legacy unit-transfer record; no JEI linkage at class load. */
public final class JeiTransferCompatPlugin implements IMixinConfigPlugin {
    private static final String TARGET = "mezz.jei.common.transfer.TransferOperation";
    private static final String MIXIN = "pl.aridlin.psychiatrykroles.compat.JeiUnitTransferMixin";

    @Override public void onLoad(String mixinPackage) {}
    @Override public String getRefMapperConfig() { return null; }
    @Override public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (!TARGET.equals(targetClassName) || !MIXIN.equals(mixinClassName)) return false;
        try {
            return legacyUnitRecord(MixinService.getService().getBytecodeProvider()
                    .getClassNode(targetClassName, false));
        } catch (ClassNotFoundException | java.io.IOException | LinkageError unavailable) {
            return false;
        }
    }

    static boolean legacyUnitRecord(ClassNode node) {
        if (node == null || !"mezz/jei/common/transfer/TransferOperation".equals(node.name)
                || !"java/lang/Record".equals(node.superName)
                || (node.access & Opcodes.ACC_FINAL) == 0) return false;
        int instanceFields = 0;
        boolean inventory = false, crafting = false, constructor = false;
        boolean inventoryAccessor = false, craftingAccessor = false;
        for (FieldNode field : node.fields) {
            if ((field.access & Opcodes.ACC_STATIC) != 0) continue;
            instanceFields++;
            if (!"I".equals(field.desc) || (field.access & Opcodes.ACC_FINAL) == 0) return false;
            inventory |= "inventorySlotId".equals(field.name);
            crafting |= "craftingSlotId".equals(field.name);
        }
        for (MethodNode method : node.methods) {
            if ("count".equals(method.name)) return false;
            constructor |= "<init>".equals(method.name) && "(II)V".equals(method.desc);
            inventoryAccessor |= "inventorySlotId".equals(method.name) && "()I".equals(method.desc);
            craftingAccessor |= "craftingSlotId".equals(method.name) && "()I".equals(method.desc);
        }
        return instanceFields == 2 && inventory && crafting && constructor
                && inventoryAccessor && craftingAccessor;
    }

    @Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
    @Override public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
}
