package com.termux.app.statusbar;

/**
 * When the system-stats sampler may take its next sample, as a pure function of what is on screen.
 *
 * <p>Every sample that cannot read {@code /proc/stat} itself costs a privileged command, and on
 * Shizuku a command is a remote process: a binder proxy, a death registration and three pipes on
 * each side. So the sampler runs only while someone can see a reading (the status bar's CPU/RAM
 * widgets, a Home widget, or the open stats card), only while the screen is on and the launcher is
 * in front, and never faster than {@link #MIN_PERIOD_MS} — and a caller that asks it to start again
 * with the same settings does not get an extra sample for asking.
 */
final class StatsPollCadence {

    private StatsPollCadence() {}

    /** What {@link #periodMs} answers when nothing may be sampled now. */
    static final long PAUSED = -1L;

    /**
     * The fastest the sampler ever goes, the open card included. Two seconds still reads as live
     * on a monitor and keeps a privileged lane at no more than one command per two seconds.
     */
    static final long MIN_PERIOD_MS = 2000L;

    /**
     * How long a paused sampler waits before it looks again at whether it may run. The check
     * itself is two plain binder calls (power and activity state) and runs no command.
     */
    static final long PAUSED_RECHECK_MS = 5000L;

    /**
     * How soon after the previous sample a card opening may take one of its own. The card wants
     * its process list at once, but a card toggled open and shut must not become a sample per tap.
     */
    static final long CARD_OPEN_MIN_GAP_MS = 1000L;

    /**
     * The sampling period, or {@link #PAUSED}.
     *
     * @param consumerVisible something showing a reading is on screen (the bar's CPU/RAM widgets,
     *     a Home system widget)
     * @param cardOpen the stats card is open; it is a reading on screen of its own
     * @param interactive the screen is on
     * @param foreground the launcher is the app in front
     * @param requestedMs the period the consumer asked for
     */
    static long periodMs(boolean consumerVisible, boolean cardOpen, boolean interactive,
                         boolean foreground, long requestedMs) {
        if (!interactive || !foreground) return PAUSED;
        if (!consumerVisible && !cardOpen) return PAUSED;
        return Math.max(MIN_PERIOD_MS, requestedMs);
    }

    /**
     * How long a (re)start waits before its first sample.
     *
     * <p>Before this, every start sampled at once — and the activity restarts the sampler on every
     * status-bar refresh, so the six-second bar cadence became one privileged command per refresh.
     * Now a start owes a sample only when the last one is a full period old; a card opening (the
     * top-process list switched on) may cut that short, down to {@link #CARD_OPEN_MIN_GAP_MS}.
     *
     * @param sinceLastSampleMs time since the previous sample started, or a negative value when
     *     there has been none
     */
    static long firstDelayMs(boolean topTurnedOn, long sinceLastSampleMs, long periodMs) {
        if (sinceLastSampleMs < 0L) return 0L;
        long gap = topTurnedOn ? Math.min(CARD_OPEN_MIN_GAP_MS, periodMs) : periodMs;
        return Math.max(0L, gap - sinceLastSampleMs);
    }

    /**
     * Whether a start call changes anything. One that repeats the running settings is a no-op, so
     * it neither samples nor moves the next tick.
     */
    static boolean startChangesSchedule(boolean running, long runningPeriodMs, boolean runningTop,
                                        long requestedPeriodMs, boolean requestedTop) {
        return !running || runningPeriodMs != requestedPeriodMs || runningTop != requestedTop;
    }
}
