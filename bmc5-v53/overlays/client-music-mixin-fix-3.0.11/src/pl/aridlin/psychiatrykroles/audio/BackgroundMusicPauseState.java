package pl.aridlin.psychiatrykroles.audio;

import java.util.function.Consumer;

/** Holds only the channel this owner paused. No volume/category mutation or new tracks. */
public final class BackgroundMusicPauseState<S, H> {
    private S pausedMusic;
    private H pausedHandle;
    private int handoverTicks;

    public boolean update(boolean audible, boolean pending, boolean gamePaused,
                          S currentMusic, H handle, Consumer<H> pause, Consumer<H> resume) {
        boolean suppress = audible;
        if (audible) handoverTicks = 20;
        else if (pending && handoverTicks > 0) { handoverTicks--; suppress = true; }
        else handoverTicks = 0;
        if (pausedMusic != currentMusic || pausedHandle != handle) {
            pausedMusic = null; pausedHandle = null;
        }
        if (suppress) {
            if (currentMusic != null && handle != null) {
                pause.accept(handle); // Reassert after a vanilla menu/device resume.
                pausedMusic = currentMusic; pausedHandle = handle;
            }
            return true; // The vanilla scheduler does not consume its song delay.
        }
        if (pausedHandle != null && !gamePaused) {
            resume.accept(pausedHandle);
            pausedMusic = null; pausedHandle = null;
        }
        return false;
    }
}
