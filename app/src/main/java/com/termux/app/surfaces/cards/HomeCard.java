package com.termux.app.surfaces.cards;

import androidx.annotation.NonNull;

import com.termux.app.surfaces.EditorRows;
import com.termux.app.surfaces.EditorRows.Offers;
import com.termux.app.surfaces.EditorRows.Row;
import com.termux.app.surfaces.EditorRows.Spec;

import java.util.List;

/**
 * The Home card's rows in the condensed kinds (spec #326): the widget grid as Columns x Rows on
 * one row, the three short choices beside their labels, the four switches as chips that wrap, the
 * Layout list, and the Icon pack, Gestures and Pages links sharing one row at the card's end.
 * Order does the grouping in place of headings. Every knob of the earlier card is still here and
 * writes the key it wrote. No glass: the chrome wears one look on every page, set on Global
 * (ADR 0031).
 */
public final class HomeCard {

    private HomeCard() {}

    /** Every row of the card, in the order the card shows them. */
    @NonNull
    public static List<Spec> rows(@NonNull Offers offers) {
        EditorRows.CardRows rows = new EditorRows.CardRows()
            .steppers(1, Row.HOME_COLUMNS, Row.HOME_ROWS)
            .choice(1, Row.LABEL_SIZE, Row.FOLDERS, Row.NUMERALS)
            .chips(1, Row.TWO_LINE, Row.LABEL_SHADOW, Row.WALLPAPER_SCROLL, Row.GLASS_WIDGETS,
                Row.SEARCH_FIELD, Row.DRAWER_ICON);
        // Pages is not offered while the overview it opens is already up.
        if (offers.overviewUp) rows.links(3, Row.ICON_PACK, Row.LINK_GESTURES);
        else rows.links(3, Row.ICON_PACK, Row.LINK_GESTURES, Row.LINK_PAGES);
        return rows.build();
    }
}
