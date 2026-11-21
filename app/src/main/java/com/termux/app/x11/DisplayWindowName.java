package com.termux.app.x11;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * What the display window is called wherever the row names it — its chip in the status bar —
 * and which of the display's apps that name and the chip's icon belong to. A terminal window's
 * chip names what runs in its active pane; the display window's names the app in front the same
 * way, one app and no count, and says Display only while no app is open.
 *
 * <p>Pure: the activity hands it what {@link X11WindowList} and {@link X11WindowIconResolver}
 * know, and applies the answer.
 */
public final class DisplayWindowName {

    private DisplayWindowName() {}

    /**
     * The app in front, as an index into the display's windows: the focused one, else the newest
     * open — a window manager can leave nothing focused (the desktop was tapped, the focused app
     * just closed) while apps are still open, and the chip should not read Display then. -1 when
     * no app is open.
     */
    public static int frontIndex(int count, int active) {
        if (count <= 0) return -1;
        return active >= 0 && active < count ? active : count - 1;
    }

    /**
     * The name for the display window: the front app's own name from its desktop entry, as the
     * drawer lists it; else the window's own label (its class, or its title); else
     * {@code fallback}, which is Display. Blank names count as none.
     */
    @NonNull
    public static String of(@Nullable String appName, @Nullable String windowLabel,
                            @NonNull String fallback) {
        String app = clean(appName);
        if (app != null) return app;
        String window = clean(windowLabel);
        return window != null ? window : fallback;
    }

    @Nullable
    private static String clean(@Nullable String name) {
        if (name == null) return null;
        String trimmed = name.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
