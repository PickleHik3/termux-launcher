package com.termux.app.surfaces.cards;

import androidx.annotation.NonNull;

import com.termux.app.surfaces.EditorRows;
import com.termux.app.surfaces.EditorRows.Offers;
import com.termux.app.surfaces.EditorRows.Row;
import com.termux.app.surfaces.EditorRows.Spec;

import java.util.List;

/**
 * The Home card's rows (editor-redesign §2, Home): Home's glass first, the Home half's Base,
 * which the widgets, the status bar and the dock on Home wear while they follow it.
 */
public final class HomeCard {

    private HomeCard() {}

    /** Every row of the card, tier by tier, in the order the card shows them. */
    @NonNull
    public static List<Spec> rows(@NonNull Offers offers) {
        return new EditorRows.CardRows()
            .tier(1, Row.FROST, Row.OPACITY, Row.TINT, Row.ICON_SIZE, Row.LABEL_SIZE,
                Row.GLASS_WIDGETS)
            .tier(2, Row.TWO_LINE, Row.LABEL_SHADOW, Row.FOLDERS, Row.NUMERALS, Row.PAGE_DOTS)
            .tier(3, Row.WALLPAPER_SCROLL, Row.MATCH_WORKSPACE, Row.LINK_GESTURES,
                Row.LINK_PAGES)
            .build();
    }
}
