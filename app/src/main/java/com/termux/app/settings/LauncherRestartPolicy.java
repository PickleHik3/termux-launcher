package com.termux.app.settings;

/**
 * Settings › General › Restart launcher. It relaunches the launcher's screens (the activity is
 * recreated on its next resume, the path a new wallpaper's colours already take) and never ends the
 * process: the terminal service lives in the same process as the launcher, so killing it would end
 * every shell and leave the phone with no home screen until the system rebuilt one. A recreate that
 * cannot happen (no launcher activity alive) costs nothing, because the next launch is fresh.
 */
public final class LauncherRestartPolicy {

    /** A second tap on Restart within this window is the same restart, not another one. */
    public static final long REPEAT_GUARD_MS = 4_000L;

    private LauncherRestartPolicy() {}

    /**
     * @param lastAcceptedAtMs when the previous restart was accepted, or a negative number for none
     */
    public static boolean accept(long nowMs, long lastAcceptedAtMs) {
        return lastAcceptedAtMs < 0 || nowMs < lastAcceptedAtMs
            || nowMs - lastAcceptedAtMs >= REPEAT_GUARD_MS;
    }
}
