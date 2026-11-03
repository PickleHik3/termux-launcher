package com.termux.app.surfaces;

import androidx.annotation.NonNull;

import com.termux.app.wall.PageOverviewPolicy;

/**
 * What the surface editor does when the Edit pages overview centres a card over it: a Home card
 * keeps Home's card, a terminal card gets the Workspace's, a display card has no card of its own
 * and folds the sheet to its handle.
 */
public enum OverviewFollow {
    HOME(EditorCard.HOME),
    WORKSPACE(EditorCard.WORKSPACE),
    FOLD(null);

    /** The card the editor shows for this choice, null when the sheet folds. */
    public final EditorCard card;

    OverviewFollow(EditorCard card) {
        this.card = card;
    }

    @NonNull
    public static OverviewFollow of(@NonNull PageOverviewPolicy.Kind kind) {
        switch (kind) {
            case HOME_PAGE: return HOME;
            case TERMINAL: return WORKSPACE;
            case DISPLAY:
            default: return FOLD;
        }
    }
}
