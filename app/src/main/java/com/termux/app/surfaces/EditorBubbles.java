package com.termux.app.surfaces;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.termux.R;
import com.termux.app.wall.UseAs;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The first row of the editor bar (spec #326): three bubbles, Global, Home and Workspace. Home
 * and Workspace turn the frame to that side and open its card; Global opens its card and leaves
 * the frame where it is. The bar cards (Status bar, Dock, Keyboard) have no bubble: they open by
 * tapping the bar, and while one is up no bubble is marked. Pure: the bar draws the answer and
 * the controller routes it.
 */
public enum EditorBubbles {
    GLOBAL(EditorCard.APPEARANCE, R.string.appearance_card_customization),
    HOME(EditorCard.HOME, R.string.appearance_card_home),
    /** Terminal, Display and Assistant pages all open the Terminal's card. */
    WORKSPACE(EditorCard.TERMINAL, R.string.appearance_bubble_workspace);

    /** The card the bubble opens. */
    @NonNull public final EditorCard card;
    @StringRes public final int labelRes;

    EditorBubbles(@NonNull EditorCard card, @StringRes int labelRes) {
        this.card = card;
        this.labelRes = labelRes;
    }

    /** The bubbles offered under {@code useAs}, in order: Terminal only has no Home bubble. */
    @NonNull
    public static List<EditorBubbles> offered(@NonNull UseAs useAs) {
        List<EditorBubbles> out = new ArrayList<>(3);
        for (EditorBubbles bubble : values()) {
            if (UseAsCards.reaches(useAs, bubble.card))
                out.add(bubble);
        }
        return Collections.unmodifiableList(out);
    }

    /** The bubble of the card on show, or null for a bar card (or none). */
    @Nullable
    public static EditorBubbles markedFor(@Nullable EditorCard shown) {
        for (EditorBubbles bubble : values()) {
            if (bubble.card == shown)
                return bubble;
        }
        return null;
    }

    /** Whether the bubble turns the frame to its card's side (Global leaves it where it is). */
    public boolean turnsFrame() {
        return this != GLOBAL;
    }
}
