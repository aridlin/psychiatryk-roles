import java.util.List;
import pl.aridlin.psychiatrykroles.runtime.HudSchema;

/** Old HUD clients receive static fallbacks while new clients retain signal rules. */
public final class SignalHudCompatibilityTest {
    private static int checks;

    private static void check(boolean condition, String reason) {
        checks++;
        if (!condition) throw new AssertionError(reason);
    }

    private static void rejects(Runnable action, String reason) {
        boolean rejected = false;
        try {
            action.run();
        } catch (RuntimeException expected) {
            rejected = true;
        }
        check(rejected, reason);
    }

    public static void main(String[] args) {
        String signal = "{\"var\":\"signal.quest.progress\"}";
        String mixed = "{\"op\":\"gt\",\"args\":[" + signal + ",0]}";
        String local = "{\"var\":\"time\"}";
        var progress = new HudSchema.Node("bar", "progress", .12, .25, .4, .03,
            null, 0xFFFFFFFF, 0x44000000, .5, null, null,
            mixed, local, signal, signal);
        var text = new HudSchema.Node("caption", "text", .12, .20, 0, 0,
            "Quest", 0xFFFFFFFF, 0, 0, null, null,
            local, null, null, null);
        var scene = new HudSchema.Scene("quest", List.of(progress, text));
        HudSchema.validate(scene);
        check(HudSchema.parse(HudSchema.JSON.toJson(HudSchema.replace(scene))).scene().equals(scene),
            "new client scene retains signal expressions on the wire");
        var legacy = HudSchema.legacyScene(scene);
        HudSchema.validate(legacy);
        var fallback = legacy.nodes().get(0);
        check(fallback.visibleWhen() == null && fallback.yRule() == null &&
            fallback.valueRule() == null, "signal-dependent rules removed for older clients");
        check(local.equals(fallback.xRule()) && local.equals(legacy.nodes().get(1).visibleWhen()),
            "built-in client expressions remain active");
        check(fallback.x() == .12 && fallback.y() == .25 && fallback.value() == .5 &&
            fallback.w() == .4 && fallback.h() == .03,
            "static layout and progress fallback remain unchanged");
        check(fallback.color() == progress.color() &&
            fallback.background() == progress.background(),
            "visual styling remains unchanged");
        check(HudSchema.parse(HudSchema.JSON.toJson(HudSchema.replace(legacy))).scene()
            .equals(legacy), "older-client scene remains a valid HUD update");
        check(HudSchema.legacyScene(legacy).equals(legacy),
            "compatibility conversion is idempotent");
        rejects(() -> HudSchema.validate(new HudSchema.Scene("bad", List.of(
            new HudSchema.Node("bar", "progress", .1, .1, .2, .02, null,
                -1, 0, .5, null, null, null, null,
                "{\"var\":\"signal../escape\"}", null)))),
            "invalid signal path denied in HUD expression");

        System.out.println("SIGNAL_HUD_COMPAT_PASS checks=" + checks);
    }
}
