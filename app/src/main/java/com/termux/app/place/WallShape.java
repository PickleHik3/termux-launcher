package com.termux.app.place;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.LayoutStyle;
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_APP;

import java.util.Objects;

/**
 * The Style, Corners and Margin the wall wears (ADR 0008): one value for every page, the
 * Workspace's keys (linked since #84, and wall-wide since the page model, #171). Corners and Margin
 * are in dp as stored, Corners' -1 being the auto token. A value, so the chrome can compare the
 * shape two pages wear ({@link com.termux.app.chrome.Dress#shape}).
 */
public final class WallShape {

    /** What a launcher with no preferences yet wears. */
    public static final WallShape DEFAULT = new WallShape(
        LayoutStyle.parse(TERMUX_APP.DEFAULT_APP_LAUNCHER_DOCK_STYLE),
        TERMUX_APP.DEFAULT_SURFACE_BASE_CORNER_RADIUS, TERMUX_APP.DEFAULT_SURFACE_BASE_SIDE_GAP);

    @NonNull public final LayoutStyle style;
    public final int cornersDp;
    public final int marginDp;

    private WallShape(@NonNull LayoutStyle style, int cornersDp, int marginDp) {
        this.style = style;
        this.cornersDp = cornersDp;
        this.marginDp = marginDp;
    }

    @NonNull
    public static WallShape of(@NonNull LayoutStyle style, int cornersDp, int marginDp) {
        return new WallShape(style, cornersDp, marginDp);
    }

    /**
     * The three values as preferences hold them now.
     */
    @NonNull
    public static WallShape read(@Nullable TermuxAppSharedPreferences prefs) {
        if (prefs == null) return DEFAULT;
        return new WallShape(prefs.getLayoutStyle(), prefs.getLayoutCornersDp(false),
            prefs.getLayoutMarginDp(false));
    }

    public boolean isFloating() {
        return style == LayoutStyle.FLOATING;
    }

    @Override
    public boolean equals(@Nullable Object other) {
        if (this == other) return true;
        if (!(other instanceof WallShape)) return false;
        WallShape that = (WallShape) other;
        return style == that.style && cornersDp == that.cornersDp && marginDp == that.marginDp;
    }

    @Override
    public int hashCode() {
        return Objects.hash(style, cornersDp, marginDp);
    }

    @NonNull
    @Override
    public String toString() {
        return style + " corners=" + cornersDp + " margin=" + marginDp;
    }
}
