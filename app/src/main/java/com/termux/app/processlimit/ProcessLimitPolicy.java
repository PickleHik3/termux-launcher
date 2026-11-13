package com.termux.app.processlimit;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Android 12 and later stop an app's child processes past a limit (32 across all apps by default),
 * oldest first: the display server, a desktop session, the shells. Android gives no exit reason
 * for a child, so the launcher guesses: a child it did not stop that ends by SIGKILL, while the
 * limit is not known to be off, raises the notice ({@link ProcessLimitNotice} keeps it; three
 * places draw it). This class is the whole decision, pure, so every place reads one answer.
 */
public final class ProcessLimitPolicy {

    private ProcessLimitPolicy() {}

    /** The first Android with the limit. */
    public static final int ANDROID_12 = 31;
    /** A shell's exit code for a process ended by signal 9: 128 + 9. */
    public static final int SIGKILL_EXIT = 137;
    private static final int SIGKILL = 9;
    /** A limit at least this high is as good as off (the fix writes {@link Integer#MAX_VALUE}). */
    public static final long OFF_THRESHOLD = Integer.MAX_VALUE / 2;

    /** The global setting that turns the limit's monitor off when it reads {@code false}. */
    public static final String MONITOR_SETTING = "settings_enable_monitor_phantom_procs";
    /**
     * Where Android 14's Developer options switch (Disable child process restrictions) writes the
     * same flag; the global setting, when set, wins over it.
     */
    public static final String MONITOR_OVERRIDE_PROPERTY = "persist.sys.fflag.override." + MONITOR_SETTING;

    /** Whether the limit is known to be on, known to be off, or neither. */
    public enum Limit { ON, OFF, UNKNOWN }

    /** Where a Fix started from the card stands. */
    public enum Phase { IDLE, WORKING, FIXED, FAILED }

    /** What the card shows. */
    public enum Card { HIDDEN, NOTICE, WORKING, FIXED, FAILED }

    /** The card's buttons; the last of {@link #actions} is the primary one. */
    public enum Action { FIX, HOW_TO_FIX, DIAGNOSTICS, DISMISS, UNDO, DONE }

    /**
     * Lifts the limit: device config sync is held so a server push does not put it back, the limit
     * goes to the largest int, and the monitor is switched off where the system has that switch.
     * Each step runs whatever the one before did; the read that follows decides.
     */
    public static final String FIX_COMMAND =
        "device_config set_sync_disabled_for_tests persistent; "
            + "device_config put activity_manager max_phantom_processes 2147483647; "
            + "settings put global " + MONITOR_SETTING + " false";

    /** Puts back what {@link #FIX_COMMAND} changed, sync last. */
    public static final String REVERT_COMMAND =
        "device_config delete activity_manager max_phantom_processes; "
            + "settings delete global " + MONITOR_SETTING + "; "
            + "device_config set_sync_disabled_for_tests none";

    private static final String MAX_MARK = "max=";
    private static final String MONITOR_MARK = "monitor=";
    private static final String OVERRIDE_MARK = "override=";

    /** Prints both values on marked lines, for {@link #fromReadBack}. */
    public static final String READ_COMMAND =
        "echo \"" + MAX_MARK + "$(device_config get activity_manager max_phantom_processes)\"; "
            + "echo \"" + MONITOR_MARK + "$(settings get global " + MONITOR_SETTING + ")\"; "
            + "echo \"" + OVERRIDE_MARK + "$(getprop " + MONITOR_OVERRIDE_PROPERTY + ")\"";

    /** {@link #FIX_COMMAND}, then the read that confirms it, in one run. */
    @NonNull
    public static String fixThenRead() {
        return FIX_COMMAND + "; " + READ_COMMAND;
    }

    /** {@link #REVERT_COMMAND}, then the read that confirms it, in one run. */
    @NonNull
    public static String revertThenRead() {
        return REVERT_COMMAND + "; " + READ_COMMAND;
    }

    /** Whether a background script's exit code says it was ended by SIGKILL. */
    public static boolean isKillExit(int exitCode) {
        return exitCode == SIGKILL_EXIT;
    }

    /** Whether a shell ended by {@code signal} was ended by SIGKILL. */
    public static boolean isKillSignal(int signal) {
        return signal == SIGKILL;
    }

    /**
     * Whether a child's end raises the notice: SIGKILL on Android 12 or later, not sent by the
     * launcher, while the limit is not known to be off.
     */
    public static boolean raises(int sdkInt, boolean killedBySigkill, boolean sentByLauncher,
                                 @NonNull Limit limit) {
        return sdkInt >= ANDROID_12 && killedBySigkill && !sentByLauncher && limit != Limit.OFF;
    }

    /**
     * The monitor flag Android goes by: the global setting when it holds a value, else the
     * Developer options override. Either may be null.
     */
    @Nullable
    public static String effectiveMonitor(@Nullable String setting, @Nullable String override) {
        if (setting != null) {
            String trimmed = setting.trim();
            if (!trimmed.isEmpty() && !"null".equals(trimmed)) return trimmed;
        }
        return override;
    }

    /** The monitor setting as read: {@code false} means off; anything else says nothing. */
    @NonNull
    public static Limit fromMonitorSetting(@Nullable String value) {
        return value != null && "false".equals(value.trim().toLowerCase(Locale.ROOT))
            ? Limit.OFF : Limit.UNKNOWN;
    }

    /**
     * {@link #READ_COMMAND}'s output. Off when the limit is at least {@link #OFF_THRESHOLD} or the
     * monitor reads {@code false}; on when both lines came back otherwise (an unset value prints
     * {@code null}, Android's default); unknown when the lines are missing (no backend, an error).
     */
    @NonNull
    public static Limit fromReadBack(@Nullable String output) {
        if (output == null) return Limit.UNKNOWN;
        String max = null;
        String monitor = null;
        String override = null;
        for (String line : output.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith(MAX_MARK)) max = trimmed.substring(MAX_MARK.length()).trim();
            else if (trimmed.startsWith(MONITOR_MARK)) monitor = trimmed.substring(MONITOR_MARK.length()).trim();
            else if (trimmed.startsWith(OVERRIDE_MARK)) override = trimmed.substring(OVERRIDE_MARK.length()).trim();
        }
        if (max == null && monitor == null) return Limit.UNKNOWN;
        if (fromMonitorSetting(effectiveMonitor(monitor, override)) == Limit.OFF) return Limit.OFF;
        if (max != null) {
            try {
                if (Long.parseLong(max) >= OFF_THRESHOLD) return Limit.OFF;
            } catch (NumberFormatException ignored) {
                // "null": unset, so Android's default.
            }
        }
        return Limit.ON;
    }

    /** Two reads of the same limit: off if either says off, on if either says on. */
    @NonNull
    public static Limit combine(@NonNull Limit first, @NonNull Limit second) {
        if (first == Limit.OFF || second == Limit.OFF) return Limit.OFF;
        if (first == Limit.ON || second == Limit.ON) return Limit.ON;
        return Limit.UNKNOWN;
    }

    /** Whether a raised notice still stands once the limit has been read again. */
    public static boolean stillPending(boolean pending, @NonNull Limit limit) {
        return pending && limit != Limit.OFF;
    }

    /** What the card shows: a Fix in progress or just finished, else the notice while it stands. */
    @NonNull
    public static Card card(int sdkInt, boolean pending, @NonNull Limit limit, @NonNull Phase phase) {
        switch (phase) {
            case WORKING: return Card.WORKING;
            case FIXED: return Card.FIXED;
            case FAILED: return Card.FAILED;
            default:
                return sdkInt >= ANDROID_12 && stillPending(pending, limit) ? Card.NOTICE : Card.HIDDEN;
        }
    }

    /**
     * The card's buttons, primary last: Fix where the launcher can lift the limit itself, else the
     * guide and Diagnostics; Undo right after a Fix.
     */
    @NonNull
    public static List<Action> actions(@NonNull Card card, boolean privileged) {
        switch (card) {
            case NOTICE:
                return privileged
                    ? Arrays.asList(Action.DISMISS, Action.FIX)
                    : Arrays.asList(Action.DISMISS, Action.DIAGNOSTICS, Action.HOW_TO_FIX);
            case FIXED: return Arrays.asList(Action.UNDO, Action.DONE);
            case FAILED: return Arrays.asList(Action.DISMISS, Action.HOW_TO_FIX);
            default: return Collections.emptyList();
        }
    }

    /** Whether Diagnostics' row offers to restore Android's default. */
    public static boolean offersRestore(@NonNull Limit limit, boolean privileged) {
        return limit == Limit.OFF && privileged;
    }
}
