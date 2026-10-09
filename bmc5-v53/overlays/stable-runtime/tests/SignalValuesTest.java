import java.util.ArrayList;
import java.util.List;
import pl.aridlin.psychiatrykroles.runtime.SignalSchema;
import pl.aridlin.psychiatrykroles.runtime.SignalValues;

/** Client-side typed state checks without starting a Minecraft client. */
public final class SignalValuesTest {
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
        var values = new SignalValues();
        check(values.size() == 0 && values.numeric("missing") == 0,
            "empty signal store reads zero");
        values.apply(SignalSchema.delta(List.of(
            SignalSchema.valueNumber("quest.progress", .625),
            SignalSchema.valueBoolean("quest.ready", true),
            SignalSchema.valueBoolean("quest.hidden", false),
            SignalSchema.valueText("quest.label", "Find the exit"))));
        check(values.size() == 4, "all typed values applied");
        check(values.numeric("quest.progress") == .625, "number exposed to HUD expression");
        check(values.numeric("quest.ready") == 1 && values.numeric("quest.hidden") == 0,
            "booleans exposed as one or zero");
        check(values.numeric("quest.label") == 0 &&
            "Find the exit".equals(values.get("quest.label").text()),
            "text retained as text and not coerced into a HUD number");

        values.apply(SignalSchema.delta(List.of(SignalSchema.valueNumber("quest.progress", .75),
            SignalSchema.clear("quest.label"))));
        check(values.numeric("quest.progress") == .75 && values.get("quest.label") == null &&
            values.size() == 3, "delta updates and tombstones apply together");

        var full = new SignalValues();
        var sixteen = new ArrayList<SignalSchema.Change>();
        for (int i = 0; i < SignalSchema.MAX_SIGNALS; i++) {
            sixteen.add(SignalSchema.valueNumber("n" + i, i));
        }
        full.apply(SignalSchema.delta(sixteen));
        check(full.size() == SignalSchema.MAX_SIGNALS && full.numeric("n0") == 0,
            "16 active values accepted");
        rejects(() -> full.apply(SignalSchema.delta(List.of(
            SignalSchema.valueNumber("n0", 42),
            SignalSchema.valueNumber("overflow", 1)))),
            "17th key rejected without applying the earlier change");
        check(full.size() == SignalSchema.MAX_SIGNALS && full.numeric("n0") == 0 &&
            full.get("overflow") == null, "overflow rejection leaves prior state intact");
        full.apply(SignalSchema.delta(List.of(SignalSchema.clear("n1"),
            SignalSchema.valueNumber("replacement", 99))));
        check(full.size() == SignalSchema.MAX_SIGNALS && full.numeric("replacement") == 99,
            "atomic clear and replacement may reuse capacity");

        values.apply(SignalSchema.reset());
        check(values.size() == 0 && values.numeric("quest.progress") == 0 &&
            values.get("quest.ready") == null, "protocol reset clears all values");
        full.clear();
        check(full.size() == 0 && full.numeric("replacement") == 0,
            "disconnect clear empties retained state");

        System.out.println("SIGNAL_VALUES_PASS checks=" + checks);
    }
}
