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

    /** Settings › Home › Icons and labels: the icon's size, a percentage of the dock's 48 dp. */
    public static final String KEY_ICON_SCALE = "app_launcher_home_icon_scale";
    /** The label's size: {@code small}, {@code default} or {@code large}. */
    public static final String KEY_LABEL_SIZE = "app_launcher_home_label_size";
    /** A long name may take a second line. Off: one line, as Home always drew it. */
    public static final String KEY_LABEL_TWO_LINES = "app_launcher_home_label_two_lines";
    /** The label's soft shadow. On: as Home always drew it. */
    public static final String KEY_LABEL_SHADOW = "app_launcher_home_label_shadow";

    /** Whether {@code key} is one of these rows. */
    public static boolean owns(@Nullable String key) {
        if (key == null) return false;
        if (KEY_PAGE_TRANSITION.equals(key) || KEY_ICON_SCALE.equals(key) || KEY_LABEL_SIZE.equals(key)
            || KEY_LABEL_TWO_LINES.equals(key) || KEY_LABEL_SHADOW.equals(key)) return true;
        for (HomeGestures.Gesture gesture : HomeGestures.Gesture.values()) {
            if (gesture.key.equals(key) || gesture.appKey().equals(key)) return true;
        }
        return false;
    }

    /** How Home's app icons and folders are drawn (Settings › Home › Icons and labels). */
    @NonNull
    public static com.termux.app.launcher.widget.HomeIconStyle iconStyle(@NonNull Context context) {
        return new com.termux.app.launcher.widget.HomeIconStyle(
            com.termux.app.launcher.widget.HomeIconStyle.parseScale(getString(context, KEY_ICON_SCALE, null)),
            com.termux.app.launcher.widget.HomeIconStyle.LabelSize.fromValue(
                getString(context, KEY_LABEL_SIZE, null)),
            getBoolean(context, KEY_LABEL_TWO_LINES, false),
            getBoolean(context, KEY_LABEL_SHADOW, true));
    }

    /** What {@code gesture} on a Home page's free area does (Settings › Home › Gestures). */
    @NonNull
    public static HomeGestures.Action gestureAction(@NonNull Context context,
                                                    @NonNull HomeGestures.Gesture gesture) {
        return HomeGestures.parse(gesture, getString(context, gesture.key, null));
    }

    public static void setGestureAction(@NonNull Context context, @NonNull HomeGestures.Gesture gesture,
                                        @NonNull HomeGestures.Action action) {
        putString(context, gesture.key, HomeGestures.storedValue(gesture, action));
    }

    /** The app {@code gesture} opens, as a flattened component name; null when none is picked. */
    @Nullable
    public static String gestureApp(@NonNull Context context, @NonNull HomeGestures.Gesture gesture) {
        return getString(context, gesture.appKey(), null);
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
