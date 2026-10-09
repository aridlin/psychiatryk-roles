package pl.aridlin.psychiatrykroles.runtime.grapple;

import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.world.phys.Vec3;

public final class GrappleRulesTest {
    private static int checks;
    private static void check(boolean value, String what) {
        checks++;
        if (!value) throw new AssertionError(what);
    }
    private static void rejects(String raw) throws Exception {
        boolean rejected = false;
        try { GrappleRules.parse(raw); } catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected, "invalid rules rejected: " + raw);
    }
    public static void main(String[] args) throws Exception {
        Path file = Files.createTempDirectory("grapple-rules-").resolve("grapple.properties");
        GrappleRules rules = new GrappleRules(file);
        check(rules.snapshot().equals(GrappleRules.DEFAULT), "safe default");
        check(Files.isRegularFile(file), "editable sample created");
        var configured = GrappleRules.parse("teammateImpulseScale=0.5\nscooterImpulseScale=1.75\n"
            + "redstoneOnAttach=true\nredstoneCooldownTicks=32\n");
        check(configured.teammateImpulseScale() == .5 && configured.scooterImpulseScale() == 1.75,
            "server force scales parse");
        check(configured.redstoneOnAttach() && configured.redstoneCooldownTicks() == 32,
            "redstone defaults parse");
        Files.writeString(file, "teammateImpulseScale=0.5\nscooterImpulseScale=1.75\n"
            + "redstoneOnAttach=true\nredstoneCooldownTicks=32\n");
        check(rules.reload().equals(configured), "hot reload accepts a valid atomic snapshot");
        Files.writeString(file, "teammateImpulseScale=NaN\n");
        check(rules.reload().equals(configured), "bad edit retains last valid values");
        rejects("teammateImpulseScale=-0.1\n");
        rejects("scooterImpulseScale=2.1\n");
        rejects("redstoneOnAttach=yes\n");
        rejects("redstoneCooldownTicks=0\n");
        rejects("runCode=anything\n");

        Vec3 incoming = new Vec3(2, 0, 0);
        Vec3 capped = GrappleBehavior.capped(incoming, new Vec3(4, 1, 0), .25, .3);
        Vec3 force = capped.subtract(incoming);
        check(force.horizontalDistance() <= .2500001 && force.length() <= .3000001,
            "script force stays within native budgets");
        check(capped.x > incoming.x, "incoming momentum preserved");
        check(GrappleBehavior.capped(incoming, new Vec3(Double.NaN, 0, 0), .25, .3).equals(incoming),
            "non-finite script motion rejected");
        var event = new GrapplePullEvent(null, null, "teammate", incoming, incoming, .25, .3);
        check(!event.setImpulse(Double.POSITIVE_INFINITY, 0, 0), "non-finite setter rejected");
        check(!event.setImpulse(17, 0, 0), "oversized setter rejected");
        check(event.setImpulse(.1, .2, 0) && event.getImpulseY() == .2, "bounded setter accepted");
        System.out.println("PASS " + checks + " grapple rule and motion checks");
    }
}
