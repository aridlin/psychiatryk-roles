import java.util.ArrayList;
import java.util.List;
import pl.aridlin.psychiatrykroles.runtime.SignalSchema;

/** Pure protocol checks: no Minecraft process, networking, or graphics context. */
public final class SignalProtocolTest {
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

    private static SignalSchema.Message roundtrip(SignalSchema.Message message) {
        String wire = SignalSchema.encode(message);
        check(wire.getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= SignalSchema.MAX_MESSAGE,
            "encoded signal message fits wire budget");
        SignalSchema.Message decoded = SignalSchema.parse(wire);
        check(decoded.equals(message), "signal message roundtrips without type loss");
        return decoded;
    }

    public static void main(String[] args) {
        check(SignalSchema.MAX_SIGNALS == 16 && SignalSchema.MAX_CHANGES == 16,
            "per-player and per-update signal counts stay bounded");
        check(SignalSchema.MAX_TEXT == 48 && SignalSchema.MAX_MESSAGE == 2048,
            "text and UTF-8 message limits stay bounded");

        var number = SignalSchema.valueNumber("quest.progress", .625);
        var enabled = SignalSchema.valueBoolean("quest.ready", true);
        var disabled = SignalSchema.valueBoolean("quest.hidden", false);
        var label = SignalSchema.valueText("quest.label", "Find the exit");
        var decoded = roundtrip(SignalSchema.delta(List.of(number, enabled, disabled, label)));
        check(SignalSchema.isSignal(SignalSchema.encode(SignalSchema.delta(List.of(number)))),
            "signal snapshot recognized within the existing runtime envelope");
        check(!SignalSchema.isSignal("{\"schema\":1,\"channel\":\"hud\"}") &&
            !SignalSchema.isSignal("{\"schema\":1,\"channel\":\"menu\"}"),
            "HUD and menu snapshots stay on their existing handlers");
        check(decoded.changes().get(0).equals(number), "number retains its ID and value");
        check(decoded.changes().get(1).equals(enabled), "true retains its boolean type");
        check(decoded.changes().get(2).equals(disabled), "false retains its boolean type");
        check(decoded.changes().get(3).equals(label), "text retains its string type");
        check(!number.value().equals(enabled.value()) && !enabled.value().equals(label.value()),
            "different signal types cannot compare as the same value");

        var clear = SignalSchema.clear("quest.label");
        check(clear.value() == null, "clear carries no replacement value");
        check(roundtrip(SignalSchema.delta(List.of(clear))).changes().get(0).value() == null,
            "clear survives the wire roundtrip");
        check("reset".equals(roundtrip(SignalSchema.reset()).op()),
            "reset remains a distinct operation");

        SignalSchema.valueNumber("n", -1_000_000);
        SignalSchema.valueNumber("n", 1_000_000);
        SignalSchema.valueText("t", "界".repeat(SignalSchema.MAX_TEXT));
        SignalSchema.valueBoolean("b", true);
        SignalSchema.valueNumber("a".repeat(32), 0);
        check(true, "inclusive value, text, and ID boundaries accepted");
        for (double bad : new double[] {Double.NaN, Double.POSITIVE_INFINITY,
                Double.NEGATIVE_INFINITY, -1_000_000.01, 1_000_000.01}) {
            rejects(() -> SignalSchema.valueNumber("n", bad), "nonfinite or out-of-range number rejected");
        }
        rejects(() -> SignalSchema.valueText("t", "x".repeat(SignalSchema.MAX_TEXT + 1)),
            "oversized text rejected before encoding");
        rejects(() -> SignalSchema.valueText("t", null), "null text rejected");
        for (String bad : List.of("", "A", "../escape", "a/b", "a:b", "a\nstop", "a".repeat(33))) {
            rejects(() -> SignalSchema.valueNumber(bad, 1), "unsafe signal ID rejected: " + bad);
        }

        var sixteen = new ArrayList<SignalSchema.Change>();
        for (int i = 0; i < SignalSchema.MAX_CHANGES; i++) {
            sixteen.add(SignalSchema.valueNumber("s" + i, i));
        }
        roundtrip(SignalSchema.delta(sixteen));
        var seventeen = new ArrayList<>(sixteen);
        seventeen.add(SignalSchema.valueNumber("overflow", 1));
        rejects(() -> SignalSchema.delta(seventeen), "17 changes in one update rejected");
        rejects(() -> SignalSchema.delta(List.of(number, number)), "duplicate signal ID rejected");

        var escaped = new ArrayList<SignalSchema.Change>();
        var multibyte = new ArrayList<SignalSchema.Change>();
        for (int i = 0; i < SignalSchema.MAX_CHANGES; i++) {
            escaped.add(SignalSchema.valueText("e" + "a".repeat(29) + i,
                "\"".repeat(SignalSchema.MAX_TEXT)));
            multibyte.add(SignalSchema.valueText("u" + i, "界".repeat(SignalSchema.MAX_TEXT)));
        }
        rejects(() -> SignalSchema.encode(SignalSchema.delta(escaped)),
            "escaped JSON text cannot exceed the wire byte budget");
        rejects(() -> SignalSchema.encode(SignalSchema.delta(multibyte)),
            "multibyte UTF-8 text cannot exceed the wire byte budget");
        rejects(() -> SignalSchema.parse("x".repeat(SignalSchema.MAX_MESSAGE + 1)),
            "oversized incoming signal message rejected");
        rejects(() -> SignalSchema.parse("{"), "incomplete JSON rejected");
        rejects(() -> SignalSchema.parse("[]"), "wrong JSON shape rejected");
        rejects(() -> SignalSchema.parse("{}"), "missing required fields rejected");
        rejects(() -> SignalSchema.parse(SignalSchema.encode(SignalSchema.reset())
            .replace("\"schema\":1", "\"schema\":1.5")),
            "fractional protocol version cannot be truncated to v1");

        String hello = SignalSchema.hello();
        check(hello != null && !hello.isBlank() && hello.length() <= SignalSchema.MAX_MESSAGE,
            "capability hello is bounded");
        SignalSchema.parseHello(hello);
        check(true, "capability hello roundtrips");
        rejects(() -> SignalSchema.parseHello(hello.replace("\"schema\":1", "\"schema\":1.5")),
            "fractional capability version rejected");
        check("quest.progress".equals(SignalSchema.variable("signal.quest.progress")),
            "HUD signal variable resolves a safe ID");
        for (String bad : List.of("signal.", "signal../escape", "signal.a/b",
                "signal.A", "signal." + "a".repeat(33), "quest.progress")) {
            rejects(() -> SignalSchema.variable(bad), "unsafe HUD signal variable rejected: " + bad);
        }

        System.out.println("SIGNAL_PROTOCOL_PASS checks=" + checks);
    }
}
