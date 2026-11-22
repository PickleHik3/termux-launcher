package com.termux.terminal;

import androidx.annotation.Nullable;

/**
 * Turns the Ghostty core off for new panes after the library may have taken the process down.
 *
 * <p>A fault inside libghostty-vt kills the whole process, which here is the home screen, and no
 * per-session fallback can catch it. So before the first write to a Ghostty core in a process, a
 * marker naming the process is stored; it is cleared once every Ghostty core has been released
 * cleanly (its pane closed, or it fell back to the Java core). A marker still there at the next
 * start was left by a process that died with a Ghostty pane open:
 *
 * <ul>
 * <li>On API 30 and up the system says how that process ended; only a native crash
 * ({@code ApplicationExitInfo.REASON_CRASH_NATIVE}) after the marker was written turns the core
 * off. A kill for memory, a force-stop or an update leaves it on.</li>
 * <li>Below API 30 there is no record, so the marker alone turns it off.</li>
 * </ul>
 *
 * <p>Turning off is a runtime override: the user's stored engine choice is never written. It lasts
 * until the app is updated or reinstalled (the build stamp, {@code PackageInfo.lastUpdateTime},
 * changes), so a fixed build gets to try again, and a crash on the same build cannot loop the home
 * screen. Settings read it through {@link #isDisabledAfterCrash()}.
 *
 * <p>Main thread only. Nothing happens until the app {@link #install}s a guard; JVM tests and the
 * parse bench run without one.
 */
public final class GhosttyCrashGuard {

    /** {@code ApplicationExitInfo.REASON_CRASH_NATIVE}, spelled out so the JVM tests need no API 30 stub. */
    public static final int REASON_CRASH_NATIVE = 5;
    /** What {@link ExitReasons#reasonFor} returns when the system has no record of the process. */
    public static final int REASON_UNKNOWN = -1;

    /** Where the marker and the off record live; the app keeps them in no-backup files. */
    public interface Store {
        /** The marker's text, or null when there is none. */
        @Nullable String readMarker();

        void writeMarker(String marker);

        void clearMarker();

        /** The off record's text, or null when there is none. */
        @Nullable String readDisabled();

        void writeDisabled(String record);

        void clearDisabled();
    }

    /** How a process of this app ended, from the system's exit records (API 30 and up). */
    public interface ExitReasons {
        /**
         * The reason ({@code ApplicationExitInfo.REASON_*}) process {@code pid} ended for, if it
         * ended at or after {@code sinceMillis} (wall clock), else {@link #REASON_UNKNOWN}.
         */
        int reasonFor(int pid, long sinceMillis);
    }

    /** The wall clock the marker is stamped with, the clock exit records use. */
    public interface Clock {
        long currentTimeMillis();
    }

    /** Where the guard says what it decided. */
    public interface Log {
        void warn(String message);
    }

    private static GhosttyCrashGuard sInstalled;

    private final Store mStore;
    @Nullable private final ExitReasons mExitReasons;
    private final int mPid;
    private final long mBuildStamp;
    private final Clock mClock;
    private final Log mLog;

    private boolean mDisabled;
    @Nullable private String mDisabledReason;
    /** Whether this process's marker is stored. */
    private boolean mArmed;

    GhosttyCrashGuard(Store store, @Nullable ExitReasons exitReasons, int pid, long buildStamp, Clock clock, Log log) {
        mStore = store;
        mExitReasons = exitReasons;
        mPid = pid;
        mBuildStamp = buildStamp;
        mClock = clock;
        mLog = log;
    }

    /**
     * Read what the last process left and decide whether the Ghostty core may run in this one.
     * Once per process, before the first session is created.
     *
     * @param exitReasons null below API 30, where the marker alone decides.
     * @param buildStamp changes with every install of the app; an off record from another build is
     *                   dropped.
     */
    public static GhosttyCrashGuard install(Store store, @Nullable ExitReasons exitReasons, int pid, long buildStamp,
                                            Clock clock, Log log) {
        GhosttyCrashGuard guard = new GhosttyCrashGuard(store, exitReasons, pid, buildStamp, clock, log);
        guard.checkOnStart();
        sInstalled = guard;
        return guard;
    }

    /** Whether a crash turned the Ghostty core off for new panes in this build. */
    public static boolean isDisabledAfterCrash() {
        GhosttyCrashGuard guard = sInstalled;
        return guard != null && guard.mDisabled;
    }

    /** Why {@link #isDisabledAfterCrash()} is true, for logs; null when it is not. */
    @Nullable
    public static String disabledReason() {
        GhosttyCrashGuard guard = sInstalled;
        return guard == null || !guard.mDisabled ? null : guard.mDisabledReason;
    }

    /** Called by every Ghostty write; stores the marker before the first one. */
    static void beforeWrite() {
        GhosttyCrashGuard guard = sInstalled;
        if (guard != null && !guard.mArmed) guard.arm();
    }

    /** Called when a Ghostty core is released; the last one out clears the marker. */
    static void onCoreReleased(int coresStillAlive) {
        GhosttyCrashGuard guard = sInstalled;
        if (guard != null && coresStillAlive == 0) guard.disarm();
    }

    /** For tests: forget the installed guard. */
    static void uninstallForTests() {
        sInstalled = null;
    }

    boolean disabled() {
        return mDisabled;
    }

    boolean armed() {
        return mArmed;
    }

    void checkOnStart() {
        String record = safeRead(true);
        if (record != null) {
            int bar = record.indexOf('|');
            long stamp = parseLong(bar < 0 ? record : record.substring(0, bar));
            if (stamp == mBuildStamp) {
                mDisabled = true;
                mDisabledReason = bar < 0 ? "a crash" : record.substring(bar + 1);
            } else {
                // A new build: let it try again.
                safeClearDisabled();
            }
        }

        String marker = safeRead(false);
        if (marker == null) return;
        int space = marker.indexOf(' ');
        int pid = (int) parseLong(space < 0 ? marker : marker.substring(0, space));
        long since = space < 0 ? 0 : parseLong(marker.substring(space + 1));
        // Our own pid means a second install in this process, not a death.
        if (pid == mPid) return;
        safeClearMarker();
        String why;
        if (mExitReasons == null) {
            why = "the last process ended with a Ghostty pane open";
        } else {
            int reason;
            try {
                reason = mExitReasons.reasonFor(pid, since);
            } catch (RuntimeException e) {
                reason = REASON_UNKNOWN;
            }
            if (reason != REASON_CRASH_NATIVE) return;
            why = "the last process crashed in native code with a Ghostty pane open";
        }
        mDisabled = true;
        mDisabledReason = why;
        try {
            mStore.writeDisabled(mBuildStamp + "|" + why);
        } catch (RuntimeException e) {
            mLog.warn("Terminal core: could not store the ghostty off record (" + e + ")");
        }
        mLog.warn("Terminal core: ghostty turned off for new panes until the app is updated: " + why);
    }

    private void arm() {
        // Set first: a store that throws must not be retried on every write.
        mArmed = true;
        try {
            mStore.writeMarker(mPid + " " + mClock.currentTimeMillis());
        } catch (RuntimeException e) {
            mLog.warn("Terminal core: could not store the ghostty crash marker (" + e + ")");
        }
    }

    private void disarm() {
        if (!mArmed) return;
        mArmed = false;
        safeClearMarker();
    }

    @Nullable
    private String safeRead(boolean disabledRecord) {
        try {
            String s = disabledRecord ? mStore.readDisabled() : mStore.readMarker();
            return s == null || s.trim().isEmpty() ? null : s.trim();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private void safeClearMarker() {
        try {
            mStore.clearMarker();
        } catch (RuntimeException ignored) {
        }
    }

    private void safeClearDisabled() {
        try {
            mStore.clearDisabled();
        } catch (RuntimeException ignored) {
        }
    }

    private static long parseLong(String s) {
        try {
            return Long.parseLong(s.trim());
        } catch (NumberFormatException e) {
            return Long.MIN_VALUE;
        }
    }
}
