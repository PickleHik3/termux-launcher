package com.termux.app.launcher.home;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shared.termux.TermuxConstants;

/**
 * The Settings › Home rows added with the settings restructure's second wave, stored in the
 * app's default preferences and read through this one class. Every default is what Home did
 * before the row existed, so an install that never opens these rows sees no change and nothing
 * needs pinning for existing installs.
 *
 * <p>Settings writes them through its page store, which hands every key {@link #owns} here; the
 * launcher reads them when it comes back to the front, never per frame.
 */
public final class HomeSettings {

    private HomeSettings() {}

    /** Settings › Home › Page transition: a {@link HomePageTransition} value. */
    public static final String KEY_PAGE_TRANSITION = "app_launcher_home_page_transition";

    /** Whether {@code key} is one of these rows. */
    public static boolean owns(@Nullable String key) {
        return KEY_PAGE_TRANSITION.equals(key);
    }

    @NonNull
    public static HomePageTransition pageTransition(@NonNull Context context) {
        return HomePageTransition.fromValue(getString(context, KEY_PAGE_TRANSITION, null));
    }

    public static void setPageTransition(@NonNull Context context, @NonNull HomePageTransition effect) {
        putString(context, KEY_PAGE_TRANSITION, effect.value);
    }

    @Nullable
    public static String getString(@NonNull Context context, @NonNull String key, @Nullable String defValue) {
        try {
            return preferences(context).getString(key, defValue);
        } catch (RuntimeException e) {
            return defValue;
        }
    }

    public static void putString(@NonNull Context context, @NonNull String key, @Nullable String value) {
        preferences(context).edit().putString(key, value).apply();
    }

    public static boolean getBoolean(@NonNull Context context, @NonNull String key, boolean defValue) {
        try {
            return preferences(context).getBoolean(key, defValue);
        } catch (RuntimeException e) {
            return defValue;
        }
    }

    public static void putBoolean(@NonNull Context context, @NonNull String key, boolean value) {
        preferences(context).edit().putBoolean(key, value).apply();
    }

    @NonNull
    private static SharedPreferences preferences(@NonNull Context context) {
        return context.getApplicationContext().getSharedPreferences(
            TermuxConstants.TERMUX_DEFAULT_PREFERENCES_FILE_BASENAME_WITHOUT_EXTENSION, Context.MODE_PRIVATE);
    }
}
