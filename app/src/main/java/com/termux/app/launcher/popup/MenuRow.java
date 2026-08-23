package com.termux.app.launcher.popup;

import android.widget.TextView;

import androidx.annotation.NonNull;

/**
 * One highlightable row of an anchored menu: the view the finger can land on, the action a release
 * over it commits, and whether hovering it opens the side menu.
 */
public final class MenuRow {

    @NonNull public final TextView rowView;
    @NonNull public final Runnable action;
    public final boolean opensSubmenu;

    public MenuRow(@NonNull TextView rowView, @NonNull Runnable action) {
        this(rowView, action, false);
    }

    public MenuRow(@NonNull TextView rowView, @NonNull Runnable action, boolean opensSubmenu) {
        this.rowView = rowView;
        this.action = action;
        this.opensSubmenu = opensSubmenu;
    }
}
