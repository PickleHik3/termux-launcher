package com.termux.app.tips;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * The three gestures the first Home teaches under the Terl desktop (#443), in the order the hints
 * come: one at a time, so only one card ever stands over the bar. Then the two hints of a
 * situation (round 3, as the prototype): full screen and navigation mode, each shown the first
 * time its situation comes and never again.
 */
public enum Tip {
    /** Hold the bar's free room: Overview. */
    BAR_HOLD("bar_hold"),
    /** Slide a finger along the marks to go through windows or pages. */
    MARK_SLIDE("mark_slide"),
    /** Hold empty Home: edit mode. */
    HOME_HOLD("home_hold"),
    /** A window is full screen: a slide up brings the bar. */
    FULL_SCREEN("full_screen"),
    /** Navigation mode is on: drag to move around, hold a window to move it. */
    NAVIGATION("navigation");

    /** The name stored for the hint. */
    @NonNull public final String key;

    Tip(@NonNull String key) {
        this.key = key;
    }

    /** Whether the hint belongs to a situation (full screen, navigation mode), not to Home's three. */
    public boolean situational() {
        return this == FULL_SCREEN || this == NAVIGATION;
    }

    /** The hint stored as {@code key}, or null for a name no hint has. */
    @Nullable
    public static Tip of(@Nullable String key) {
        if (key == null) return null;
        for (Tip tip : values()) if (tip.key.equals(key)) return tip;
        return null;
    }
}
