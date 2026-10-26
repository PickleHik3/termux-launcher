package com.termux.app.surfaces.cards;

import androidx.annotation.NonNull;

import com.termux.app.surfaces.EditorRows;
import com.termux.app.surfaces.EditorRows.Offers;
import com.termux.app.surfaces.EditorRows.Row;
import com.termux.app.surfaces.EditorRows.Spec;

import java.util.List;

/** The Dock card's rows (editor-redesign §2, Dock). */
public final class DockCard {

    private DockCard() {}

    /** Every row of the card, tier by tier, in the order the card shows them. */
    @NonNull
    public static List<Spec> rows(@NonNull Offers offers) {
        return new EditorRows.CardRows()
            .tier(1, Row.FROST, Row.OPACITY, Row.TINT, Row.HEIGHT, Row.CORNERS, Row.MARGIN,
                Row.APP_ICONS, Row.REFLECTION, Row.VISIBLE)
            .tier(2, Row.STYLE, Row.DESIGN)
            .tier(3, Row.SEARCH_FIELD, Row.DRAWER_ICON, Row.LINK_DOCK_ITEMS)
            .build();
    }
}
