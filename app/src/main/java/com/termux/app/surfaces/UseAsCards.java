package com.termux.app.surfaces;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.wall.UseAs;

/** Which editor cards a Use as mode can open; the wall package knows nothing of the cards. */
public final class UseAsCards {

    private UseAsCards() {}

    /** Whether {@code card} can be opened: the Home card is gone under Terminal only. */
    public static boolean reaches(@NonNull UseAs useAs, @Nullable EditorCard card) {
        return card == null || useAs == UseAs.HOME || card != EditorCard.HOME;
    }

    /** The card a door opens under this mode: the editor at rest (null) for a card it took away. */
    @Nullable
    public static EditorCard doorCard(@NonNull UseAs useAs, @Nullable EditorCard card) {
        return reaches(useAs, card) ? card : null;
    }
}
