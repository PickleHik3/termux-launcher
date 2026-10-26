package com.termux.app.surfaces.cards;

import androidx.annotation.NonNull;

import com.termux.app.surfaces.EditorRows;
import com.termux.app.surfaces.EditorRows.Offers;
import com.termux.app.surfaces.EditorRows.Row;
import com.termux.app.surfaces.EditorRows.Spec;

import java.util.List;

/**
 * The Keyboard card's rows (editor-redesign §2, Keyboard): Key opacity stands beside Opacity, the
 * pair the opacity link moves together.
 */
public final class KeyboardCard {

    private KeyboardCard() {}

    /** Every row of the card, tier by tier, in the order the card shows them. */
    @NonNull
    public static List<Spec> rows(@NonNull Offers offers) {
        return new EditorRows.CardRows()
            .tier(1, Row.FROST, Row.OPACITY, Row.KEY_OPACITY, Row.TINT, Row.KEY_CORNERS,
                Row.KEY_SPACING, Row.HEIGHT, Row.CORNERS, Row.MARGIN)
            .tier(2, Row.KEYBOARD_THEME)
            .tier(3, Row.FORM, Row.LINK_KEYBOARD_SIZE, Row.LINK_LAYOUTS)
            .build();
    }
}
