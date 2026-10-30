package com.termux.app.launcher.home;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;

/**
 * One row of {@link HomeActionsSheet} when a caller other than Home fills it: what the row says,
 * its glyph, whether the glyph wears the accent, and which of the card's two groups it stands in.
 * {@link #token} comes back to the caller when the row is tapped.
 */
public final class ActionSheetRow {
    @NonNull public final Object token;
    @NonNull public final CharSequence label;
    @DrawableRes public final int icon;
    public final boolean accent;
    /** 0 for the first group, 1 for the second. */
    public final int group;

    public ActionSheetRow(@NonNull Object token, @NonNull CharSequence label,
                          @DrawableRes int icon, boolean accent, int group) {
        this.token = token;
        this.label = label;
        this.icon = icon;
        this.accent = accent;
        this.group = group;
    }
}
