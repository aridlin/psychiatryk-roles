import java.util.ArrayList;
import java.util.HashSet;
import pl.aridlin.psychiatrykroles.runtime.SignalSchema;
import pl.aridlin.psychiatrykroles.runtime.SignalState;

/** Server-thread state and delivery planning, without player or client objects. */
public final class SignalStateTest {
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
        var first = new SignalState();
        var second = new SignalState();
        check(first.nextMessage() == null && second.nextMessage() == null,
            "new player states have no pending packet");

        first.set(SignalSchema.valueNumber("quest.progress", .25));
        var initial = first.nextMessage();
        check(initial != null && initial.changes().size() == 1 &&
            initial.changes().get(0).equals(SignalSchema.valueNumber("quest.progress", .25)),
            "first value sent as one typed delta");
        check(first.nextMessage() == null, "unchanged value is not resent");
        first.set(SignalSchema.valueNumber("quest.progress", .25));
        check(first.nextMessage() == null, "same replacement value is not resent");
        first.set(SignalSchema.valueNumber("quest.progress", .5));
        check(first.nextMessage().changes().equals(
            java.util.List.of(SignalSchema.valueNumber("quest.progress", .5))),
            "changed value sent once");

        check(second.nextMessage() == null && second.size() == 0,
            "updates to one player cannot enter another player's state");
        second.set(SignalSchema.valueBoolean("quest.progress", true));
        check(second.nextMessage().changes().get(0).value().kind().equals("boolean"),
            "same signal ID may have a different type for another player");
        check(first.nextMessage() == null, "second player's update leaves first player unchanged");

        first.set(SignalSchema.clear("quest.progress"));
        var removed = first.nextMessage();
        check(removed != null && removed.changes().size() == 1 &&
            removed.changes().get(0).value() == null,
            "clearing a delivered key sends a tombstone");
        check(first.size() == 0 && first.nextMessage() == null,
            "clear frees capacity and does not repeat");
        first.set(SignalSchema.clear("missing"));
        check(first.nextMessage() == null, "clearing an absent key sends nothing");

        first.set(SignalSchema.valueText("notice", "Ready"));
        first.nextMessage();
        first.resetDelivery();
        var reset = first.nextMessage();
        check(reset != null && "reset".equals(reset.op()) && reset.changes().isEmpty(),
            "new handshake clears the client's stale signal map first");
        var replay = first.nextMessage();
        check(replay != null && replay.changes().equals(
            java.util.List.of(SignalSchema.valueText("notice", "Ready"))),
            "handshake replays desired state after reset");
        check(first.nextMessage() == null, "replay settles delivery state");
        first.clearAll();
        check(first.size() == 0 && first.nextMessage().changes().get(0).value() == null,
            "clearAll removes delivered state on the next update");

        var full = new SignalState();
        for (int i = 0; i < SignalSchema.MAX_SIGNALS; i++) {
            full.set(SignalSchema.valueNumber("n" + i, i));
        }
        check(full.size() == SignalSchema.MAX_SIGNALS, "exactly 16 active signals accepted");
        rejects(() -> full.set(SignalSchema.valueNumber("overflow", 1)),
            "17th active signal rejected");
        full.set(SignalSchema.valueNumber("n0", 99));
        check(full.size() == SignalSchema.MAX_SIGNALS,
            "updating an existing key at capacity is allowed");
        rejects(() -> full.set(new SignalSchema.Change("invalid",
                new SignalSchema.Value("number", Double.NaN, false, null))),
            "state rejects an externally constructed invalid change");

        var bulky = new SignalState();
        for (int i = 0; i < SignalSchema.MAX_SIGNALS; i++) {
            bulky.set(SignalSchema.valueText("u" + i,
                "界".repeat(SignalSchema.MAX_TEXT)));
        }
        var seen = new HashSet<String>();
        int packets = 0;
        SignalSchema.Message message;
        while ((message = bulky.nextMessage()) != null) {
            packets++;
            check("delta".equals(message.op()), "large state uses bounded deltas");
            check(SignalSchema.encode(message).getBytes(java.nio.charset.StandardCharsets.UTF_8).length
                <= SignalSchema.MAX_MESSAGE, "each planned packet fits the UTF-8 wire budget");
            for (var change : message.changes()) {
                check(seen.add(change.id()), "each key sent exactly once across chunks");
            }
            check(packets <= SignalSchema.MAX_SIGNALS, "chunking makes bounded forward progress");
        }
        check(packets > 1 && seen.size() == SignalSchema.MAX_SIGNALS,
            "all 16 multibyte values arrive in multiple bounded packets");
        check(bulky.nextMessage() == null, "large delivery fully settles");

        System.out.println("SIGNAL_STATE_PASS checks=" + checks);
    }
}
