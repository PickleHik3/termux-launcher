package com.termux.app.terminal;

import android.os.SystemClock;

import com.termux.view.TerminalView;

/**
 * When the user last typed or scrolled, so a screen event never plays over either (spec decision
 * 3: a key commit or a scroll within {@link PaneScreenEvent#INPUT_QUIET_MS} holds them). The key
 * stamp is fed from the same place every key commit reports to the latency probe; the scroll
 * stamp is the terminal view's own.
 */
public final class InputQuiet {

    private static final long NEVER = Long.MIN_VALUE / 4;
    private static volatile long sLastKeyUptime = NEVER;

    private InputQuiet() {}

    /** A key was committed. */
    public static void noteKey() {
        sLastKeyUptime = SystemClock.uptimeMillis();
    }

    /** True when neither a key nor a scroll happened within the quiet window. */
    public static boolean isQuiet() {
        return quiet(SystemClock.uptimeMillis(), sLastKeyUptime,
            TerminalView.lastScrollUptimeMillis());
    }

    /** Pure: quiet at {@code now} given the last key and scroll stamps. */
    static boolean quiet(long now, long lastKey, long lastScroll) {
        return now - Math.max(lastKey, lastScroll) >= PaneScreenEvent.INPUT_QUIET_MS;
    }
}
