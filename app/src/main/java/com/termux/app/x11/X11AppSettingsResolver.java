package com.termux.app.x11;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

/** Which settings apply to a window: the app's override over the display's value. */
public final class X11AppSettingsResolver {

    private X11AppSettingsResolver() {}

    /** A valid {@code override} wins; otherwise {@code global}, as given. */
    @NonNull
    public static String effectiveTouchMode(@NonNull String global, @Nullable String override) {
        return X11AppSettings.isTouchMode(override) ? override : global;
    }

    /**
     * Whether the paste key adds Shift (Ctrl+Shift+V). A valid {@code override} wins over
     * {@code global}, except while a desktop session runs: its apps follow the display.
     */
    public static boolean pasteWithShift(@NonNull String global, @Nullable String override,
                                         boolean desktopRunning) {
        String effective = !desktopRunning && X11AppSettings.isPasteShortcut(override)
            ? override : global;
        return X11AppSettings.PASTE_CTRL_SHIFT_V.equals(effective);
    }

    /** The id of the app that drew a window of {@code wmClass}, or null for none. */
    @Nullable
    public static String appIdForWindowClass(@NonNull List<LinuxAppCatalog.LinuxApp> apps,
                                             @Nullable String wmClass) {
        if (wmClass == null || wmClass.trim().isEmpty()) return null;
        LinuxAppCatalog.LinuxApp app = X11WindowIconResolver.match(apps, wmClass);
        return app == null ? null : app.id;
    }
}
