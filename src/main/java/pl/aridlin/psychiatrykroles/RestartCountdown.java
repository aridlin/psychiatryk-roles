package pl.aridlin.psychiatrykroles;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class RestartCountdown {
    private static final Pattern PART = Pattern.compile("(\\d+)([dhms])", Pattern.CASE_INSENSITIVE);
    static final long MAX_SECONDS = 30L * 24 * 3600;
    private final long deadlineNanos;
    private final List<Long> milestones;

    RestartCountdown(long seconds, long nowNanos) {
        if (seconds < 1 || seconds > MAX_SECONDS) throw new IllegalArgumentException("Duration must be 1s–30d");
        deadlineNanos = nowNanos + Duration.ofSeconds(seconds).toNanos();
        milestones = milestones(seconds);
    }

    static long parse(String value) {
        Matcher matcher = PART.matcher(value);
        long total = 0;
        int end = 0;
        while (matcher.find()) {
            if (matcher.start() != end) throw new IllegalArgumentException("Use e.g. 5m or 1h30m");
            long unit = switch (matcher.group(2).toLowerCase(java.util.Locale.ROOT)) {
                case "d" -> 86400; case "h" -> 3600; case "m" -> 60; default -> 1;
            };
            try { total = Math.addExact(total, Math.multiplyExact(Long.parseLong(matcher.group(1)), unit)); }
            catch (ArithmeticException | NumberFormatException invalid) { throw new IllegalArgumentException("Duration too large"); }
            end = matcher.end();
        }
        if (end != value.length() || total < 1 || total > MAX_SECONDS) throw new IllegalArgumentException("Use 1s–30d, e.g. 5m or 1h30m");
        return total;
    }

    static List<Long> milestones(long duration) {
        List<Long> values = new ArrayList<>();
        for (long seconds : new long[] {5, 10, 30, 60, 300, 600, 900, 1800, 3600, 7200, 21600, 43200}) {
            if (seconds < duration) values.add(seconds);
        }
        for (long day = 86400; day < duration; day += 86400) values.add(day);
        values.sort(Comparator.reverseOrder());
        return values;
    }

    long remainingSeconds(long nowNanos) {
        return Math.max(0, (deadlineNanos - nowNanos + 999_999_999L) / 1_000_000_000L);
    }

    boolean announcementDue(long remaining) {
        // After a lag spike, announce the actual remaining time once instead of stale milestones.
        return milestones.removeIf(milestone -> remaining <= milestone);
    }

    static String format(long seconds) {
        long days = seconds / 86400, hours = seconds / 3600 % 24, minutes = seconds / 60 % 60;
        List<String> parts = new ArrayList<>();
        if (days > 0) parts.add(days + "d");
        if (hours > 0) parts.add(hours + "h");
        if (minutes > 0) parts.add(minutes + "m");
        if (seconds % 60 > 0 || parts.isEmpty()) parts.add(seconds % 60 + "s");
        return String.join(" ", parts);
    }
}
