package com.termux.app.surfaces.cards;

import androidx.annotation.NonNull;

import com.termux.app.surfaces.EditorRows;
import com.termux.app.surfaces.EditorRows.Offers;
import com.termux.app.surfaces.EditorRows.Row;
import com.termux.app.surfaces.EditorRows.Spec;

import java.util.List;

/**
 * The Home card's rows (appearance-one-look spec §3, Home): the widget grid, labels and icon pack;
 * More options; Widgets; Layout, the rows Home shows (hidden or shown here, the Terminal's own
 * list untouched); and the Gestures and Pages links. No glass: the chrome wears one look on every
 * page, set on Global (ADR 0031).
 */
public final class HomeCard {

    private HomeCard() {}

    /** Every row of the card, tier by tier, in the order the card shows them. */
    @NonNull
    public static List<Spec> rows(@NonNull Offers offers) {
        EditorRows.CardRows rows = new EditorRows.CardRows()
            .tier(1, Row.HOME_COLUMNS, Row.HOME_ROWS, Row.LABEL_SIZE, Row.ICON_PACK,
                Row.HOME_ICON_SIZE, Row.PAGE_TRANSITION)
            .tier(3, Row.SECTION_MORE_OPTIONS, Row.TWO_LINE, Row.LABEL_SHADOW, Row.PAGE_DOTS,
                Row.FOLDERS, Row.WALLPAPER_SCROLL)
            .tier(3, Row.SECTION_WIDGETS, Row.GLASS_WIDGETS, Row.NUMERALS)
            .tier(3, Row.SECTION_LAYOUT, Row.ELEMENTS)
            .tier(3, Row.SECTION_APPEARANCE, Row.WALLPAPER_DIM)
            .tier(3, Row.SECTION_LINKS, Row.LINK_GESTURES);
        // Pages is not offered while the overview it opens is already up.
        if (!offers.overviewUp) rows.tier(3, Row.LINK_PAGES);
        return rows.build();
    }
}
