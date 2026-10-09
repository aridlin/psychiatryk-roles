import pl.aridlin.psychiatrykroles.runtime.SignalRate;

/** Rolling-window rate decisions for one server-to-client channel per player. */
public final class SignalRateTest {
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
        check(SignalRate.MAX_PACKETS == 5 && SignalRate.WINDOW_NANOS == 1_000_000_000L,
            "five packets in a rolling second");
        var first = new SignalRate();
        var second = new SignalRate();
        for (int i = 0; i < SignalRate.MAX_PACKETS; i++) {
            check(first.available(0), "one of the first five sends remains available");
            first.record(0);
        }
        check(!first.available(0) && !first.available(SignalRate.WINDOW_NANOS - 1),
            "sixth packet blocked throughout the first second");
        rejects(() -> first.record(0), "record cannot bypass the cap");
        check(second.available(0), "one player's cap cannot throttle another player");
        second.record(0);
        check(second.available(0), "independent player retains remaining allowance");
        check(first.available(SignalRate.WINDOW_NANOS),
            "old sends expire exactly at the one-second boundary");
        first.record(SignalRate.WINDOW_NANOS);
        check(first.available(SignalRate.WINDOW_NANOS),
            "only the new packet occupies the recovered window");

        var rolling = new SignalRate();
        for (int i = 0; i < SignalRate.MAX_PACKETS; i++) {
            rolling.record(i * 200_000_000L);
        }
        check(!rolling.available(999_999_999L), "staggered five packets still cap the window");
        check(rolling.available(1_000_000_000L), "oldest staggered packet expires first");
        rolling.record(1_000_000_000L);
        check(!rolling.available(1_199_999_999L),
            "replacement packet keeps the rolling window full");
        check(rolling.available(1_200_000_000L),
            "next staggered packet expires at its own one-second boundary");

        System.out.println("SIGNAL_RATE_PASS checks=" + checks);
    }
}
