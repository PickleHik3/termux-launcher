package com.termux.privileged;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Centralized storage for privileged backend and endpoint policy toggles.
 */
public final class PrivilegedPolicyStore {

    private static final String PREFS_NAME = "termux_privileged_policy";

    public static final String KEY_MASTER_ENABLED = "priv_master_enabled";
    public static final String KEY_PREFER_SHIZUKU = "priv_prefer_shizuku";
    public static final String KEY_ALLOW_SHELL_FALLBACK = "priv_allow_shell_fallback";
    /** The privileged lane: catalog tools marked {@code priv=shizuku} may run as shell (see {@code com.termux.privileged.lane}). */
    public static final String KEY_LANE_ENABLED = "priv_lane_enabled";

    private PrivilegedPolicyStore() {
    }

    /** True when the master switch and the lane's own switch are both on; the lane refuses every request otherwise. */
    public static boolean isLaneEnabled(Context context) {
        return isMasterEnabled(context) && isLaneSwitchOn(context);
    }

    /** The lane's own switch alone, for the settings row, which shows it even while the master switch is off. */
    public static boolean isLaneSwitchOn(Context context) {
        return getPrefs(context).getBoolean(KEY_LANE_ENABLED, true);
    }

    public static void setLaneEnabled(Context context, boolean enabled) {
        getPrefs(context).edit().putBoolean(KEY_LANE_ENABLED, enabled).apply();
    }

    public static boolean isMasterEnabled(Context context) {
        return getPrefs(context).getBoolean(KEY_MASTER_ENABLED, true);
    }

    public static void setMasterEnabled(Context context, boolean enabled) {
        getPrefs(context).edit().putBoolean(KEY_MASTER_ENABLED, enabled).apply();
    }

    public static boolean isPreferShizuku(Context context) {
        return getPrefs(context).getBoolean(KEY_PREFER_SHIZUKU, true);
    }

    public static void setPreferShizuku(Context context, boolean enabled) {
        getPrefs(context).edit().putBoolean(KEY_PREFER_SHIZUKU, enabled).apply();
    }

    public static boolean isShellFallbackEnabled(Context context) {
        return getPrefs(context).getBoolean(KEY_ALLOW_SHELL_FALLBACK, true);
    }

    public static void setShellFallbackEnabled(Context context, boolean enabled) {
        getPrefs(context).edit().putBoolean(KEY_ALLOW_SHELL_FALLBACK, enabled).apply();
    }

    private static SharedPreferences getPrefs(Context context) {
        if (context == null) {
            throw new IllegalStateException("Context is required for privileged policy access");
        }
        return context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
}
