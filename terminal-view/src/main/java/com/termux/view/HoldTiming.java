package com.termux.view;

import android.view.ViewConfiguration;

/**
 * How long a still finger must rest before it is a hold. One number for every hold the launcher
 * recognises on the terminal place: the pane corner that opens its tab and the terminal's own hold
 * that hands the finger the mouse and then decides between click, drag and selection by what it does next.
 *
 * <p>It rides on Android's long-press timeout so a user who slowed that down for a tremor gets a
 * slower launcher too, but stays under it so the corner overlay always wins the race against the
 * stock long press underneath. Three quarters of the system value, never under
 * {@link #MIN_HOLD_MS}: 300 ms on a default phone, 750 and 1125 ms at the Medium and Long
 * accessibility settings.
 */
public final class HoldTiming {

    /** A slow deliberate tap lands around 200 ms; the hold must stay clear of it. */
    static final long MIN_HOLD_MS = 250L;

    /** The share of the system long press that makes a hold. */
    static final int NUMERATOR = 3;
    static final int DENOMINATOR = 4;

    private HoldTiming() {
    }

    /**
     * The hold time for a given system long-press timeout.
     *
     * @param systemLongPressMs {@link ViewConfiguration#getLongPressTimeout()} or its stand-in.
     * @return the hold time in milliseconds, at least {@link #MIN_HOLD_MS}.
     */
    public static long holdTimeoutMs(long systemLongPressMs) {
        long scaled = systemLongPressMs * NUMERATOR / DENOMINATOR;
        return Math.max(MIN_HOLD_MS, scaled);
    }

    /** The hold time on this device right now. Read it per gesture, not once: the setting can change. */
    public static long holdTimeoutMs() {
        return holdTimeoutMs(ViewConfiguration.getLongPressTimeout());
    }

    /** The second stage is twice the system long press, felt as holding further rather than waiting. */
    static final int SELECT_MULTIPLIER = 2;

    /**
     * How long a finger that has already held must keep holding before the hold becomes text
     * selection. Always past {@link #holdTimeoutMs(long)} by at least {@link #MIN_HOLD_MS}, so the
     * first stage has room to be read as its own answer, and it follows the accessibility setting
     * the same way: 800 ms on a default phone, 2000 ms at Long.
     *
     * @param systemLongPressMs {@link ViewConfiguration#getLongPressTimeout()} or its stand-in.
     * @return the select time in milliseconds, always longer than the hold time.
     */
    public static long selectTimeoutMs(long systemLongPressMs) {
        return Math.max(holdTimeoutMs(systemLongPressMs) + MIN_HOLD_MS,
            systemLongPressMs * SELECT_MULTIPLIER);
    }

    /** The select time on this device right now, read per gesture like the hold time. */
    public static long selectTimeoutMs() {
        return selectTimeoutMs(ViewConfiguration.getLongPressTimeout());
    }
}
