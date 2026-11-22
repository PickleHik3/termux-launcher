package com.termux.privileged;

import java.util.regex.Pattern;

/**
 * The shell commands the launcher runs through the root shell to stop, freeze or tidy an app, as a
 * pure answer. A package name is checked before it reaches a command line; anything that is not a
 * plain dotted name returns {@code null} and the caller does nothing.
 */
public final class RootAppCommands {

    private static final Pattern PACKAGE = Pattern.compile("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+");

    private RootAppCommands() {
    }

    /** Whether {@code packageName} is a plain Android package name, safe to quote into a command. */
    public static boolean isValidPackage(String packageName) {
        return packageName != null && PACKAGE.matcher(packageName).matches();
    }

    public static String forceStop(String packageName) {
        if (!isValidPackage(packageName)) return null;
        return "am force-stop '" + packageName + "'";
    }

    /** {@code --user 0} is the primary user; the launcher runs there and the codebase resolves no other user id for pm. */
    public static String freeze(String packageName) {
        if (!isValidPackage(packageName)) return null;
        return "pm disable-user --user 0 '" + packageName + "'";
    }

    public static String unfreeze(String packageName) {
        if (!isValidPackage(packageName)) return null;
        return "pm enable '" + packageName + "'";
    }

    /** Clears the cache only; the {@code rm} fallback is for systems whose {@code pm} lacks {@code --cache-only}. */
    public static String clearCache(String packageName) {
        if (!isValidPackage(packageName)) return null;
        String quoted = "'" + packageName + "'";
        return "pm clear --cache-only " + quoted + " 2>/dev/null || rm -rf /data/data/" + quoted
            + "/cache/* /data/data/" + quoted + "/code_cache/*";
    }

    /** {@code signal} is {@code "TERM"} or {@code "KILL"}; {@code pid} must be above 1. */
    public static String killPid(int pid, String signal) {
        if (pid <= 1) return null;
        if (!"TERM".equals(signal) && !"KILL".equals(signal)) return null;
        return "kill -" + signal + " " + pid;
    }

    public static String isAlive(int pid) {
        if (pid <= 1) return null;
        return "kill -0 " + pid;
    }
}
