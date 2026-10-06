package pl.aridlin.psychiatrykroles.io;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Integration point for explicit flushed saves and server shutdown. */
public final class SableIoBarrier {
    public interface Store {
        OrderedIoOwner psychiatryk$ioOwner();
        void psychiatryk$durableBarrier() throws IOException;
    }
    private static final Set<Store> STORES = ConcurrentHashMap.newKeySet();
    private SableIoBarrier() {}
    public static void register(Store store) { STORES.add(store); }
    public static void unregister(Store store) { STORES.remove(store); }
    public static int activeOwners() { return STORES.size(); }
    public static int pendingJobs() {
        int count = 0;
        for (Store store : STORES) count += store.psychiatryk$ioOwner().pendingJobs();
        return count;
    }
    public static int failedOwners() {
        int count = 0;
        for (Store store : STORES) if (store.psychiatryk$ioOwner().hasFailure()) count++;
        return count;
    }
    /** Includes a real force(true) after previously queued jobs; failures are not acknowledged. */
    public static void flushAll() throws IOException {
        IOException failure = null;
        for (Store store : STORES.toArray(Store[]::new)) {
            try { store.psychiatryk$durableBarrier(); }
            catch (IOException error) {
                if (failure == null) failure = error;
                else failure.addSuppressed(error);
            }
        }
        if (failure != null) throw failure;
    }
}
