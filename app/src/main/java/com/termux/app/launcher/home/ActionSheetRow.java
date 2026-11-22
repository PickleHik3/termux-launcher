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
    /**
     * The row opens a surface over the whole screen: the sheet hands it back only once the card
     * has slid away ({@link HomeActionsSheetPolicy#runsOnceHidden}).
     */
    public final boolean afterClose;

    public ActionSheetRow(@NonNull Object token, @NonNull CharSequence label,
                          @DrawableRes int icon, boolean accent, int group) {
        this(token, label, icon, accent, group, false);
    }

    public ActionSheetRow(@NonNull Object token, @NonNull CharSequence label,
                          @DrawableRes int icon, boolean accent, int group, boolean afterClose) {
        this.token = token;
        this.label = label;
        this.icon = icon;
        this.accent = accent;
        this.group = group;
        this.afterClose = afterClose;
    }
}
