package com.termux.app.surfaces.cards;

import androidx.annotation.NonNull;

import com.termux.app.surfaces.EditorRows;
import com.termux.app.surfaces.EditorRows.Offers;
import com.termux.app.surfaces.EditorRows.Row;
import com.termux.app.surfaces.EditorRows.Spec;

import java.util.List;

/**
 * The Keyboard card's rows (appearance-one-look spec §3, Keyboard): Type first, as glyphs, then
 * the keys' shape ending on Bottom padding, then Width and Floating height while the keyboard
 * floats and Split gap while it is split; Appearance, Background opacity and Key opacity side by
 * side with the link that moves them together between them, and Blur, each the keyboard's own or
 * Global's; and the Theme and Layouts links. The floating keyboard is sized by the grip on the
 * keyboard itself, so the card has no Size link.
 */
public final class KeyboardCard {

    private KeyboardCard() {}

    /** Every row of the card, tier by tier, in the order the card shows them. */
    @NonNull
    public static List<Spec> rows(@NonNull Offers offers) {
        // The opacity link stands between the two opacities it moves together.
        // Width and Floating height, and Split gap, stand only in their own forms.
        return new EditorRows.CardRows()
            .tier(1, Row.FORM)
            .pair(1, Row.KEY_SPACING, Row.KEY_CORNERS)
            .tier(1, Row.BOTTOM_PADDING)
            .pair(1, Row.KEYBOARD_WIDTH, Row.KEYBOARD_FLOAT_HEIGHT)
            .tier(1, Row.KEYBOARD_SPLIT_GAP)
            .tier(2, Row.OPACITY, Row.OPACITY_LINK, Row.KEY_OPACITY)
            .tier(3, Row.FROST)
            .links(3, Row.KEYBOARD_THEME, Row.LINK_LAYOUTS)
            .build();
    }
}
