package com.termux.view;

import android.view.ViewConfiguration;

/**
 * How long a still finger must rest before it is a hold. One number for every hold the launcher
 * recognises on the terminal place: the pane corner that opens its tab and the terminal's own hold
 * that keeps the loupe and decides between click, drag and selection by what the finger does next.
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
}
