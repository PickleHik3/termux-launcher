package com.termux.app.surfaces.cards;

import androidx.annotation.NonNull;

import com.termux.app.surfaces.EditorRows;
import com.termux.app.surfaces.EditorRows.Offers;
import com.termux.app.surfaces.EditorRows.Row;
import com.termux.app.surfaces.EditorRows.Spec;

import java.util.List;

/**
 * The palette's card (editor-redesign §2, Global; still titled Appearance until its own wave): the
 * shared glass, the glass edge where it is drawn, and the shared text size, in the order the Look
 * page's Custom row had them.
 */
public final class GlobalCard {

    private GlobalCard() {}

    /** Every row of the card, tier by tier, in the order the card shows them. */
    @NonNull
    public static List<Spec> rows(@NonNull Offers offers) {
        EditorRows.CardRows rows = new EditorRows.CardRows()
            .tier(1, Row.LOOK, Row.FROST, Row.OPACITY, Row.TINT);
        if (offers.glassEdge)
            rows.tier(1, Row.GLASS_EDGE);
        rows.tier(1, Row.VIBRANCY);
        if (offers.glassEdge)
            rows.tier(1, Row.DEPTH);
        return rows
            .tier(1, Row.TEXT_SIZE, Row.WALLPAPER, Row.THEME)
            .tier(2, Row.ICON_PACK, Row.MONOCHROME, Row.WALLPAPER_COLOURS, Row.CONTRAST,
                Row.FULL_SCREEN, Row.MINIMAL)
            .tier(3, Row.TINT_HUE, Row.TOOL_COLOURS)
            .build();
    }
}
