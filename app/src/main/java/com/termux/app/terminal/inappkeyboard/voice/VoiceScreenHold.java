package com.termux.app.terminal.inappkeyboard.voice;

import androidx.annotation.NonNull;

/**
 * When the dictation holds the screen on. Talking is not touching, so the phone's screen timeout
 * would dim and then lock mid-sentence, and a long cleanup would dim the screen just as its text
 * landed. So the screen stays on for as long as the pill or its panel is on screen — listening,
 * transcribing, cleaning up, and while the text waits for undo, Copy or ✓ — and lets go the moment
 * the pill closes or the activity is paused.
 *
 * <p>Text that only waits, untouched, lets go after {@link #IDLE_RELEASE_MS}: a panel forgotten on
 * a desk must not keep the screen lit until the battery is gone. Only the hold goes; the text
 * stays, and touching the pill takes the hold again. Three minutes is long enough to read seven
 * lines back and think about them, and longer than the usual phone timeouts (15 seconds to 2
 * minutes), so once it has passed the phone's own timeout, counted from the last touch, dims the
 * screen soon after.
 *
 * <p>Pure: the window flag and the timer are behind {@link Screen} and {@link Timer}, so the rule
 * is tested without a window. Main thread.
 */
public final class VoiceScreenHold {

    /** How long untouched text that waits keeps the screen on. */
    public static final long IDLE_RELEASE_MS = 3 * 60_000L;

    /** The window's keep-screen-on flag. */
    public interface Screen {
        void keepOn(boolean on);
    }

    /** A one-shot delay on the main thread. */
    public interface Timer {
        void schedule(@NonNull Runnable task, long delayMs);

        void cancel(@NonNull Runnable task);
    }

    private final Screen screen;
    private final Timer timer;
    private final Runnable idle = this::onIdle;
    private boolean held;

    public VoiceScreenHold(@NonNull Screen screen, @NonNull Timer timer) {
        this.screen = screen;
        this.timer = timer;
    }

    /**
     * The pill is on screen: hold the screen on. {@code waiting} is true once the text is final
     * and only waits for the panel's buttons, which starts the idle release; false while the
     * dictation is still listening, transcribing or cleaning up, which holds without a limit.
     * Called again on every touch of the pill, which starts the idle release over.
     */
    public void hold(boolean waiting) {
        timer.cancel(idle);
        if (!held) {
            held = true;
            screen.keepOn(true);
        }
        if (waiting) timer.schedule(idle, IDLE_RELEASE_MS);
    }

    /** The pill has closed, or the activity is no longer in front: the screen may sleep. */
    public void release() {
        timer.cancel(idle);
        if (!held) return;
        held = false;
        screen.keepOn(false);
    }

    public boolean isHeld() {
        return held;
    }

    private void onIdle() {
        if (!held) return;
        held = false;
        screen.keepOn(false);
    }
}
