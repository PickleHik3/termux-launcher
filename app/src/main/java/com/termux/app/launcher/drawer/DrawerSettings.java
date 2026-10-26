package com.termux.app.launcher.drawer;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.launcher.home.HomeSettings;

/**
 * The Settings › Dock rows added with the settings restructure's third wave, stored in
 * the app's default preferences beside Home's and read only through here. Every default is the
 * drawer as it was, so an install that never opens the rows sees no change and nothing is pinned.
 *
 * <p>Settings writes them through its page store, which hands every key {@link #owns} to
 * {@link HomeSettings}' generic accessors; the drawer reads them once per open, never per frame.
 */
public final class DrawerSettings {

    private DrawerSettings() {}

    /** App search › Open the only match. Off: Enter opens it, as it always did. */
    public static final String KEY_OPEN_ONLY_MATCH = "app_launcher_drawer_open_only_match";

    /** Drawer › Remember position. Off: the list starts at the top on every open. */
    public static final String KEY_REMEMBER_POSITION = "app_launcher_drawer_remember_position";

    /** Drawer › Start in the lower half. Off: the list starts under the search pill. */
    public static final String KEY_LOWER_START = "app_launcher_drawer_lower_start";

    /** Drawer › A–Z strip: an {@link AppDrawerAzSide} value. Right, as the drawer was drawn. */
    public static final String KEY_AZ_SIDE = "app_launcher_drawer_az_side";

    public static boolean owns(@Nullable String key) {
        return KEY_OPEN_ONLY_MATCH.equals(key) || KEY_REMEMBER_POSITION.equals(key)
            || KEY_LOWER_START.equals(key) || KEY_AZ_SIDE.equals(key);
    }

    @NonNull
    public static AppDrawerAzSide azSide(@NonNull Context context) {
        return AppDrawerAzSide.fromValue(HomeSettings.getString(context, KEY_AZ_SIDE, null));
    }

    public static boolean lowerStart(@NonNull Context context) {
        return HomeSettings.getBoolean(context, KEY_LOWER_START, false);
    }

    public static boolean rememberPosition(@NonNull Context context) {
        return HomeSettings.getBoolean(context, KEY_REMEMBER_POSITION, false);
    }

    public static boolean openOnlyMatch(@NonNull Context context) {
        return HomeSettings.getBoolean(context, KEY_OPEN_ONLY_MATCH, false);
    }
}
