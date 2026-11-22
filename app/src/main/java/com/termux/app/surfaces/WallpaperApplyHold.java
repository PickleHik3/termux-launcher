package com.termux.app.surfaces;

/**
 * How long the Wallpaper page stays up, busy, after its Done has applied a wallpaper, before the
 * editor underneath is revealed. The editor is revealed once, already wearing the new wallpaper
 * and the new Material colours: a system wallpaper with new colours makes Android relaunch the
 * activity for the theme overlay, and revealing the editor before that would show the old
 * accents for about a second and then rebuild it.
 *
 * <p>Measured on the emulator (2026-10-30, base 8c3b3e471): the system colours callback fires
 * first, the configuration change follows 0.1–0.25 s later, and the activity is destroyed and
 * built again 0.6–1.2 s after the colours callback. A relaunch ends the hold by itself (the next
 * activity opens straight onto the card); this rule only decides when no relaunch is coming.</p>
 *
 * <ul>
 *   <li>No colours callback since the apply began: nothing is coming once
 *   {@link #COLOURS_QUIET_MS} have passed since the apply finished.</li>
 *   <li>A colours callback came (during the apply or after it): wait for the relaunch until
 *   {@link #RELAUNCH_WAIT_MS} after the latest one, which is past the slowest measured relaunch.</li>
 *   <li>Never longer than {@link #CEILING_MS} after the apply finished, however many callbacks
 *   arrive: the hold must not hang.</li>
 * </ul>
 *
 * <p>Pure: times are any one monotonic clock, in milliseconds.</p>
 */
final class WallpaperApplyHold {

    /** No colours callback has arrived since the apply began. */
    static final long NONE = Long.MIN_VALUE;

    /**
     * After an apply that brought no colours callback, how long one may still arrive. The callback
     * follows the system's own colour extraction, which ends well inside this on the emulator.
     */
    static final long COLOURS_QUIET_MS = 500L;

    /**
     * After a colours callback, how long the relaunch it announces may take to arrive: the
     * slowest measured was 1.2 s, with margin for a slower phone.
     */
    static final long RELAUNCH_WAIT_MS = 1_600L;

    /** The longest the page is ever held after the apply finished. */
    static final long CEILING_MS = COLOURS_QUIET_MS + RELAUNCH_WAIT_MS + 400L;

    private WallpaperApplyHold() {}

    /**
     * How long the hold still has to run at {@code now}, the apply having finished at
     * {@code finishedAt} with the latest colours callback at {@code coloursAt} ({@link #NONE} for
     * none). 0 means reveal now.
     */
    static long remainingMs(long finishedAt, long coloursAt, long now) {
        long deadline = coloursAt == NONE
            ? finishedAt + COLOURS_QUIET_MS
            : coloursAt + RELAUNCH_WAIT_MS;
        deadline = Math.min(deadline, finishedAt + CEILING_MS);
        return Math.max(0L, deadline - now);
    }
}
