package com.termux.app.surfaces;

import androidx.annotation.NonNull;

import com.termux.app.wall.PageOverviewPolicy;

/**
 * What the surface editor does when the Edit pages overview centres a card over it: a Home card
 * keeps Home's card, and a card of any Workspace page (a terminal, a Display page, the Assistant
 * page) gets the Workspace's, the one look they all wear (ADR 0025). The fold to the handle is
 * left for a card the editor has nothing for.
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
            case TERMINAL:
            case DISPLAY:
            case ASSISTANT: return WORKSPACE;
            default: return FOLD;
        }
    }
}
