package pl.aridlin.psychiatrykroles.compat;

import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.service.MixinService;

/** Exact bytecode gate: fixed and unfamiliar Flywheel builds remain unchanged. */
public final class FlywheelLegacyCompatPlugin implements IMixinConfigPlugin {
    static final String TARGET = "dev.engine_room.flywheel.backend.util.AtomicBitSet";
    static final String MIXIN = "pl.aridlin.psychiatrykroles.compat.FlywheelLegacyRangeMixin";
    static final String LEGACY_NORMALIZED_SHA256 = "319a0343a7de7a4f296b7e59cb77bf2d5dd86952a8bb9f452cec06f71fe01ccd";

    static String normalizedHash(ClassNode node) {
        try {
            ClassWriter writer = new ClassWriter(0);
            node.accept(writer);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(writer.toByteArray()));
        } catch (Exception error) { return ""; }
    }
    public boolean shouldApplyMixin(String target, String mixin) {
        if (!TARGET.equals(target) || !MIXIN.equals(mixin)
                || Boolean.getBoolean("psychiatryk.disableLegacyFlywheelFix")) return false;
        try {
            ClassNode node = MixinService.getService().getBytecodeProvider().getClassNode(target, true, 0);
            return LEGACY_NORMALIZED_SHA256.equals(normalizedHash(node));
        } catch (Exception | LinkageError unavailable) { return false; }
    }
    public void onLoad(String mixinPackage) {}
    public String getRefMapperConfig() { return null; }
    public void acceptTargets(Set<String> mine, Set<String> others) {}
    public List<String> getMixins() { return null; }
    public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
    public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
}
