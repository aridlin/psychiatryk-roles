package pl.aridlin.psychiatrykroles.compat;

import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipFile;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.spongepowered.asm.launch.*;
import org.spongepowered.asm.service.MixinService;

/** Real installed Sponge ModLauncher provider; file-only transformer transport. */
public final class ModLauncherBoundaryTest {
    private static int checks;
    private static void check(boolean ok) { checks++; if(!ok)throw new AssertionError("check "+checks); }
    public static void main(String[] args) throws Exception {
        MixinBootstrap.init();
        String target=System.getProperty("fixture.target.class");
        var provider=MixinService.getService().getBytecodeProvider();
        try { provider.getClassNode(target,false,0);throw new AssertionError("Old call unexpectedly worked"); }
        catch(IllegalArgumentException expected) {
            check(expected.getMessage().equals("ModLauncher service does not currently support retrieval of untransformed bytecode"));
        }
        ClassNode supported=provider.getClassNode(target,true,0);
        byte[] original;
        try(var z=new ZipFile(System.getProperty("fixture.target.jar"))) {
            original=z.getInputStream(z.getEntry(target.replace('.','/')+".class")).readAllBytes();
        }
        ClassNode raw=new ClassNode();new ClassReader(original).accept(raw,0);
        ClassWriter writer1=new ClassWriter(0),writer2=new ClassWriter(0);
        raw.accept(writer1);supported.accept(writer2);
        check(Arrays.equals(writer1.toByteArray(),writer2.toByteArray()));
        // The installed service explicitly refuses to process its own reason.
        check(new MixinLaunchPluginLegacy().handlesClass(Type.getObjectType(target.replace('.','/')),false,"mixin").isEmpty());
        String mode=args[0];
        if(mode.equals("jei"))check(new JeiTransferCompatPlugin().shouldApplyMixin(target,"pl.aridlin.psychiatrykroles.compat.JeiUnitTransferMixin"));
        if(mode.equals("flywheel")) {
            check(FlywheelLegacyCompatPlugin.normalizedHash(supported).equals(FlywheelLegacyCompatPlugin.LEGACY_NORMALIZED_SHA256));
            check(new FlywheelLegacyCompatPlugin().shouldApplyMixin(target,FlywheelLegacyCompatPlugin.MIXIN));
        }
        if(mode.equals("emf"))check(new EmfOptionsCompatPlugin().shouldApplyMixin(target,EmfOptionsCompatPlugin.MIXIN));
        System.out.println("{\"success\":true,\"mode\":\""+mode+"\",\"checks\":"+checks+",\"actual_modlauncher_provider\":true,\"old_false_failure_reproduced\":true,\"supported_true_flags0_shape_verified\":true,\"self_processing_skipped\":true,\"file_transport_double\":true,\"game_launched\":false}");
    }
}
