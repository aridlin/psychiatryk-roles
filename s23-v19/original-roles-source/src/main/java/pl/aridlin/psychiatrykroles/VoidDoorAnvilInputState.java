package pl.aridlin.psychiatrykroles;

import java.util.UUID;

/** Tracks insertions so server slot refreshes cannot erase a code being typed. */
public final class VoidDoorAnvilInputState {
    public enum Update { VANILLA, CLEAR, KEEP }

    private UUID activePair;

    public Update onInput(UUID pair) {
        if (pair == null) {
            activePair = null;
            return Update.VANILLA;
        }
        if (pair.equals(activePair)) return Update.KEEP;
        activePair = pair;
        return Update.CLEAR;
    }
}
