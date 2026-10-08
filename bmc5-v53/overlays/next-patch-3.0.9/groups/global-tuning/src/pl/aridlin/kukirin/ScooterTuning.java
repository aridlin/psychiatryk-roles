package pl.aridlin.kukirin;

import java.io.IOException;
import java.nio.file.*;
import java.util.Properties;

/** Server-owned handling: the client only receives authoritative snapshots. */
public final class ScooterTuning {
    public record Values(double steering, double normalGrip, double driftGrip,
                         double recovery, double tyreVolume, double minimumTurnRate) {
        /** Existing integrations retain their binary constructor. */
        public Values(double steering, double normalGrip, double driftGrip, double recovery, double tyreVolume) {
            this(steering, normalGrip, driftGrip, recovery, tyreVolume, 2.2);
        }
        public boolean valid() {
            return finite(steering,.25,2) && finite(normalGrip,.5,1)
                    && finite(driftGrip,.03,.4) && driftGrip < normalGrip
                    && finite(recovery,.05,1) && finite(tyreVolume,0,1)
                    && finite(minimumTurnRate,.5,6);
        }
        private static boolean finite(double value, double low, double high) {
            return Double.isFinite(value) && value >= low && value <= high;
        }
    }
    public static final Values DEFAULT = new Values(1,.85,.045,.42,.35,2.2);
    private static volatile Values current = DEFAULT, client = DEFAULT;
    private static long nextPoll, lastStamp = Long.MIN_VALUE, lastSize = Long.MIN_VALUE;
    public static final Path FILE = Path.of("config/goplanska-scooter/handling.properties");

    public static Values get(boolean clientSide) { return clientSide ? client : server(); }
    public static void receive(Values values) { if (values != null && values.valid()) client = values; }
    public static void resetClient() { client = DEFAULT; }
    public static synchronized Values reload() {
        nextPoll = 0; lastStamp = Long.MIN_VALUE; lastSize = Long.MIN_VALUE;
        return server();
    }
    public static synchronized Values server() {
        long now = System.currentTimeMillis();
        if (now < nextPoll) return current;
        nextPoll = now + 1000;
        try {
            if (!Files.exists(FILE)) { save(DEFAULT); return current; }
            long stamp = Files.getLastModifiedTime(FILE).toMillis(), size = Files.size(FILE);
            if (stamp == lastStamp && size == lastSize) return current;
            Properties properties = new Properties();
            try (var in = Files.newInputStream(FILE)) { properties.load(in); }
            Values values = new Values(number(properties,"steeringMultiplier",1),
                    number(properties,"normalGrip",.85),number(properties,"driftGrip",.045),
                    number(properties,"driftRecovery",.42),number(properties,"tyreVolume",.35),
                    number(properties,"minimumTurnRate",2.2));
            if (values.valid()) current = values;
            else System.err.println("[Scooter tuning] Invalid values; keeping previous configuration.");
            lastStamp = stamp; lastSize = size;
        } catch (Exception ex) {
            System.err.println("[Scooter tuning] Cannot read handling configuration: " + ex.getMessage());
        }
        return current;
    }
    private static double number(Properties properties, String key, double fallback) {
        return Double.parseDouble(properties.getProperty(key, Double.toString(fallback)));
    }
    public static synchronized void save(Values values) throws IOException {
        if (values == null || !values.valid()) throw new IllegalArgumentException("Invalid scooter tuning");
        Files.createDirectories(FILE.getParent());
        Path temp = Files.createTempFile(FILE.getParent(), "handling-", ".tmp");
        try {
            Files.writeString(temp, "# Shared server scooter handling; /scooteradmin. Applies live to everyone.\n"
                    + "steeringMultiplier=" + values.steering() + "\nnormalGrip=" + values.normalGrip()
                    + "\ndriftGrip=" + values.driftGrip() + "\ndriftRecovery=" + values.recovery()
                    + "\ntyreVolume=" + values.tyreVolume() + "\n"
                    + "# Minimum full-input ground turn rate in degrees per tick before steering/enchantment multipliers.\n"
                    + "minimumTurnRate=" + values.minimumTurnRate() + "\n");
            try { Files.move(temp, FILE, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException ex) { Files.move(temp, FILE, StandardCopyOption.REPLACE_EXISTING); }
            current = values; lastStamp = Files.getLastModifiedTime(FILE).toMillis(); lastSize = Files.size(FILE);
            nextPoll = 0;
        } finally { Files.deleteIfExists(temp); }
    }
}
