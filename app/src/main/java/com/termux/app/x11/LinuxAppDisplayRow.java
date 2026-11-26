package com.termux.app.x11;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.BuildConfig;
import com.termux.app.wayland.LinuxDisplayBackend;

/**
 * The Display row on a Linux app's page (Default, X11 Display or Wayland Display), in the values
 * the page's list stores: what the settings page reads and writes, so the page needs nothing from
 * the Wayland side ({@link LinuxDisplayBackend} answers every question here).
 */
public final class LinuxAppDisplayRow {

    /** The entry values of X11 Display and Wayland Display; Default has its own on the page. */
    public static final String VALUE_X11 = LinuxDisplayBackend.VALUE_X11;
    public static final String VALUE_WAYLAND = LinuxDisplayBackend.VALUE_WAYLAND;

    private LinuxAppDisplayRow() {}

    /** Whether the app's page has the row: where the choice can change a launch, in this build. */
    public static boolean offered(boolean desktop, boolean waylandDesktop, boolean inContainer) {
        return LinuxDisplayBackend.choosable(BuildConfig.WAYLAND_DISPLAY, desktop, waylandDesktop,
            inContainer);
    }

    /** Whether Default means Wayland Display now (the main setting, in this build). */
    public static boolean defaultIsWayland(@NonNull Context context) {
        return LinuxDisplayBackend.current(context) == LinuxDisplayBackend.WAYLAND;
    }

    /** The app's stored choice as an entry value, or null for Default. */
    @Nullable
    public static String value(@NonNull X11AppSettingsStore store, @NonNull String appId) {
        return store.display(appId).value;
    }

    /** Stores {@code value}; anything but {@link #VALUE_X11} or {@link #VALUE_WAYLAND} is Default. */
    public static void set(@NonNull X11AppSettingsStore store, @NonNull String appId,
                           @Nullable String value) {
        store.setDisplay(appId, LinuxDisplayBackend.Choice.parse(value));
    }
}
