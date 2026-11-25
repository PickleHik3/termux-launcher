package com.termux.app.window;

import androidx.annotation.NonNull;

/**
 * Who wants the activity window's keep-screen-on flag. The window has one flag and several
 * reasons to set it (the dictation pill, a Linux window holding an idle inhibitor); one reason
 * letting go must not clear it while another still holds, so each sets its own bit here and the
 * flag follows the union. The flag is touched only when the union changes. Main thread.
 */
public final class ScreenOnReasons {

    public static final int VOICE = 1;
    public static final int LINUX_WINDOW = 1 << 1;

    /** The window's keep-screen-on flag. */
    public interface Screen {
        void keepOn(boolean on);
    }

    private final Screen screen;
    private int reasons;

    public ScreenOnReasons(@NonNull Screen screen) {
        this.screen = screen;
    }

    /** Reason {@code reason} wants the screen on ({@code on}) or no longer does. */
    public void set(int reason, boolean on) {
        boolean was = reasons != 0;
        reasons = on ? reasons | reason : reasons & ~reason;
        boolean now = reasons != 0;
        if (now != was) screen.keepOn(now);
    }
}
