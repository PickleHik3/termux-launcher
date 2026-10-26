package com.termux.app.surfaces.cards;

import androidx.annotation.NonNull;

import com.termux.app.surfaces.EditorRows;
import com.termux.app.surfaces.EditorRows.Offers;
import com.termux.app.surfaces.EditorRows.Row;
import com.termux.app.surfaces.EditorRows.Spec;

import java.util.List;

/**
 * The Keyboard card's rows (editor-redesign §2, Keyboard): Type first, as glyphs; Glass with
 * Background opacity and Key opacity side by side and the link that moves them together between
 * them; Shape, ending on Bottom padding; and the Theme and Layouts chips. The floating keyboard is
 * sized by the grip on the keyboard itself, so the card has no Size link.
 */
public final class KeyboardCard {

    private KeyboardCard() {}

    /** Every row of the card, tier by tier, in the order the card shows them. */
    @NonNull
    public static List<Spec> rows(@NonNull Offers offers) {
        return new EditorRows.CardRows()
            .tier(1, Row.FORM, Row.SECTION_GLASS, Row.FROST, Row.TINT, Row.OPACITY,
                Row.OPACITY_LINK, Row.KEY_OPACITY)
            .tier(2, Row.SECTION_SHAPE, Row.KEY_CORNERS, Row.KEY_SPACING, Row.HEIGHT,
                Row.CORNERS, Row.MARGIN, Row.BOTTOM_PADDING)
            .tier(3, Row.SECTION_MORE, Row.KEYBOARD_THEME, Row.LINK_LAYOUTS)
            .build();
    }
}
