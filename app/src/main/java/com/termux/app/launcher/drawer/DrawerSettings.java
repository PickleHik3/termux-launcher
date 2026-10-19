package com.termux.app.launcher.drawer;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.launcher.home.HomeSettings;

/**
 * The Settings › Dock &amp; drawer rows added with the settings restructure's third wave, stored in
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

    public static boolean owns(@Nullable String key) {
        return KEY_OPEN_ONLY_MATCH.equals(key) || KEY_REMEMBER_POSITION.equals(key);
    }

    public static boolean rememberPosition(@NonNull Context context) {
        return HomeSettings.getBoolean(context, KEY_REMEMBER_POSITION, false);
    }

    public static boolean openOnlyMatch(@NonNull Context context) {
        return HomeSettings.getBoolean(context, KEY_OPEN_ONLY_MATCH, false);
    }
}
