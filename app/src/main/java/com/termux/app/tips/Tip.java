package com.termux.app.tips;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * The three gestures the first Home teaches under the Terl desktop (#443), in the order the hints
 * come: one at a time, so only one card ever stands over the bar.
 */
public enum Tip {
    /** Hold the bar's free room: Overview. */
    BAR_HOLD("bar_hold"),
    /** Slide a finger along the marks to go through windows or pages. */
    MARK_SLIDE("mark_slide"),
    /** Hold empty Home: edit mode. */
    HOME_HOLD("home_hold");

    /** The name stored for the hint. */
    @NonNull public final String key;

    Tip(@NonNull String key) {
        this.key = key;
    }

    /** The hint stored as {@code key}, or null for a name no hint has. */
    @Nullable
    public static Tip of(@Nullable String key) {
        if (key == null) return null;
        for (Tip tip : values()) if (tip.key.equals(key)) return tip;
        return null;
    }
}
