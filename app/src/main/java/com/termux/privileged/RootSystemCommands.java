package com.termux.privileged;

import androidx.annotation.NonNull;

/**
 * The shell lines behind the root system actions (lock, restart, quick toggles). Pure so each line
 * can be tested; they run through {@link PrivilegedBackendManager#executeCommand} as root.
 *
 * <p>Each prefers the service front end ({@code svc}, {@code cmd}) and falls back to the older
 * tool, because which one exists depends on the Android version and the ROM.
 */
public final class RootSystemCommands {

    private RootSystemCommands() {}

    /** Screen off: KEYCODE_SLEEP, then power-key toggle where that is unknown. */
    @NonNull
    public static String lock() {
        return "input keyevent 223 || input keyevent 26";
    }

    @NonNull
    public static String reboot() {
        return "svc power reboot || reboot";
    }

    @NonNull
    public static String rebootRecovery() {
        return "svc power reboot recovery || reboot recovery";
    }

    @NonNull
    public static String rebootBootloader() {
        return "svc power reboot bootloader || reboot bootloader";
    }

    @NonNull
    public static String wifi(boolean on) {
        return "svc wifi " + (on ? "enable" : "disable");
    }

    @NonNull
    public static String data(boolean on) {
        return "svc data " + (on ? "enable" : "disable");
    }

    @NonNull
    public static String airplane(boolean on) {
        return "cmd connectivity airplane-mode " + (on ? "enable" : "disable")
            + " 2>/dev/null || { settings put global airplane_mode_on " + (on ? "1" : "0")
            + " && am broadcast -a android.intent.action.AIRPLANE_MODE --ez state "
            + (on ? "true" : "false") + "; }";
    }

    @NonNull
    public static String dnd(boolean on) {
        return "cmd notification set_dnd " + (on ? "priority" : "off");
    }
}
