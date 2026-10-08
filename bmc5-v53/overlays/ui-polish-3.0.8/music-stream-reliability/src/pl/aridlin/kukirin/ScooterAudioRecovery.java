package pl.aridlin.kukirin;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** A bounded per-listener retry ledger. No local account credentials are used. */
final class ScooterAudioRecovery {
    private static final int LIMIT = 256;
    private final Map<UUID, State> states = new LinkedHashMap<>();
    private static final class State {
        final UUID session;
        final String url, sha, bearer;
        int failures;
        long retryTick;
        boolean notified;
        State(UUID session, String url, String sha, String bearer) {
            this.session = session; this.url = url; this.sha = sha; this.bearer = bearer;
        }
        boolean matches(UUID session, String url, String sha, String bearer) {
            return this.session.equals(session) && this.url.equals(url) && this.sha.equals(sha) && this.bearer.equals(bearer);
        }
        @Override public String toString() { return "MusicRetryState[session=" + session + ",failures=" + failures + "]"; }
    }
    private State state(UUID source, UUID session, String url, String sha, String bearer) {
        State state = states.get(source);
        if (state == null || !state.matches(session, url, sha, bearer)) {
            state = new State(session, url, sha, bearer);
            if (!states.containsKey(source) && states.size() >= LIMIT) states.remove(states.keySet().iterator().next());
            states.put(source, state);
        }
        return state;
    }
    synchronized boolean permitted(UUID source, UUID session, String url, String sha, String bearer, long tick) {
        return tick >= state(source, session, url, sha, bearer).retryTick;
    }
    /** Return true only for the first notification of this playback identity. */
    synchronized boolean failed(UUID source, UUID session, String url, String sha, String bearer, long tick) {
        State state = state(source, session, url, sha, bearer);
        if (tick < state.retryTick) return false; // duplicate async failure from the same attempt
        state.failures = Math.min(16, state.failures + 1);
        long delay = Math.min(2400L, 100L << Math.min(5, state.failures - 1));
        state.retryTick = tick > Long.MAX_VALUE - delay ? Long.MAX_VALUE : tick + delay;
        boolean first = !state.notified; state.notified = true; return first;
    }
    synchronized void succeeded(UUID source, UUID session, String url, String sha, String bearer) {
        State state = state(source, session, url, sha, bearer); state.failures = 0; state.retryTick = 0;
        // A later network wobble in the same song does not repeat the chat warning.
    }
    synchronized void forget(UUID source) { states.remove(source); }
    synchronized void clear() { states.clear(); }
    synchronized int size() { return states.size(); }
    static String diagnostic(Throwable error) {
        for (int depth = 0; depth < 8 && (error instanceof java.util.concurrent.CompletionException
             || error instanceof java.util.concurrent.ExecutionException) && error.getCause() != null; depth++) error = error.getCause();
        return error instanceof ScooterHttpAudioStream.Failure failure ? failure.code : error == null ? "unknown" : error.getClass().getSimpleName();
    }
}
