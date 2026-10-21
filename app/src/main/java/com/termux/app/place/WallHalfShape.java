package com.termux.app.place;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.LayoutStyle;
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_APP;

import java.util.Objects;

/**
 * The Style, Corners and Margin the halves of the wall wear (ADR 0008): the Workspace's, on the
 * windows and on the Home pages alike, a Home layout of its own included (ADR 0009; linked since
 * #84, so crossing between the halves never changes the shape). Corners and Margin are in dp as
 * stored, Corners' -1 being the auto token. A value, so the chrome can compare the shape each
 * side wears ({@link com.termux.app.chrome.Dress#shape}).
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

    /**
     * One half's three values as preferences hold them now: the Workspace's on either half, since
     * the per-half accessors are linked (#84).
     */
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
