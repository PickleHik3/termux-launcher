package com.termux.app.surfaces.cards;

import androidx.annotation.NonNull;

import com.termux.app.surfaces.EditorRows;
import com.termux.app.surfaces.EditorRows.Offers;
import com.termux.app.surfaces.EditorRows.Row;
import com.termux.app.surfaces.EditorRows.Spec;

import java.util.List;

/**
 * The Home card's rows (editor-redesign §2, Home): the widget grid's Columns and Rows; Home's
 * Look and its own glass (the Home half's Base, which the widgets, the status bar and the dock on
 * Home wear while they follow it); labels, icon size, folders and numerals as glyph and word
 * rows; the five options as tiles; the Elements list, where every
 * element on Home is shown, hidden or opened on its card; and the Global, Gestures and Edit
 * pages chips. The dock sizes its own icons on its card.
 */
public final class HomeCard {

    private HomeCard() {}

    /** Every row of the card, tier by tier, in the order the card shows them. */
    @NonNull
    public static List<Spec> rows(@NonNull Offers offers) {
        EditorRows.CardRows rows = new EditorRows.CardRows()
            .tier(1, Row.HOME_COLUMNS, Row.HOME_ROWS, Row.ICON_PACK, Row.HOME_ICON_SIZE,
                Row.LABEL_SIZE, Row.PAGE_TRANSITION)
            // Glass widgets is not in the spec's list; it stays here rather than be dropped.
            .tier(3, Row.SECTION_MORE_OPTIONS, Row.TWO_LINE, Row.LABEL_SHADOW, Row.PAGE_DOTS,
                Row.WALLPAPER_SCROLL, Row.FOLDERS, Row.NUMERALS, Row.GLASS_WIDGETS)
            .tier(3, Row.SECTION_LAYOUT, Row.ELEMENTS);
        com.termux.app.surfaces.GlassRows.appearance(rows, offers, 3)
            .tier(3, Row.MARGIN)
            .tier(3, Row.SECTION_LINKS, Row.LINK_GLOBAL, Row.LINK_GESTURES);
        // Pages is not offered while the overview it opens is already up.
        if (!offers.overviewUp) rows.tier(3, Row.LINK_PAGES);
        return rows.tier(3, Row.LINK_SHEETS).build();
    }
}
