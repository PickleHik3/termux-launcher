package com.termux.app.place;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.LayoutStyle;
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_APP;

import java.util.Objects;

/**
 * The Style, Corners and Margin one half of the wall wears (ADR 0008): the Workspace's on the
 * windows, and on the Home pages too unless Home holds a Home layout, which brings Home's own
 * (ADR 0009). Corners and Margin are in dp as stored, Corners' -1 being the
 * auto token. A value, so the chrome can tell whether crossing between the halves changes its
 * shape at all ({@link com.termux.app.chrome.Dress#shape} on either side).
 */
public final class WallHalfShape {

    /** What a launcher with no preferences yet wears. */
    public static final WallHalfShape DEFAULT = new WallHalfShape(
        LayoutStyle.parse(TERMUX_APP.DEFAULT_APP_LAUNCHER_DOCK_STYLE),
        TERMUX_APP.DEFAULT_SURFACE_BASE_CORNER_RADIUS, TERMUX_APP.DEFAULT_SURFACE_BASE_SIDE_GAP);

    @NonNull public final LayoutStyle style;
    public final int cornersDp;
    public final int marginDp;

    private WallHalfShape(@NonNull LayoutStyle style, int cornersDp, int marginDp) {
        this.style = style;
        this.cornersDp = cornersDp;
        this.marginDp = marginDp;
    }

    @NonNull
    public static WallHalfShape of(@NonNull LayoutStyle style, int cornersDp, int marginDp) {
        return new WallHalfShape(style, cornersDp, marginDp);
    }

    /** One half's three values as preferences hold them now; Home's read the Workspace's while unset. */
    @NonNull
    public static WallHalfShape read(@Nullable TermuxAppSharedPreferences prefs, boolean home) {
        if (prefs == null) return DEFAULT;
        return new WallHalfShape(prefs.getLayoutStyle(home), prefs.getLayoutCornersDp(home),
            prefs.getLayoutMarginDp(home));
    }

    public boolean isFloating() {
        return style == LayoutStyle.FLOATING;
    }

    @Override
    public boolean equals(@Nullable Object other) {
        if (this == other) return true;
        if (!(other instanceof WallHalfShape)) return false;
        WallHalfShape that = (WallHalfShape) other;
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
