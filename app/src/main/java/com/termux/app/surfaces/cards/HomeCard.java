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
 * rows; the five options as tiles; Match Workspace styling; the Elements list, where every
 * element on Home is shown, hidden or opened on its card; and the Global, Gestures and Edit
 * pages chips. The dock sizes its own icons on its card.
 */
public final class HomeCard {

    private HomeCard() {}

    /** Every row of the card, tier by tier, in the order the card shows them. */
    @NonNull
    public static List<Spec> rows(@NonNull Offers offers) {
        return new EditorRows.CardRows()
            .tier(1, Row.SECTION_GRID, Row.HOME_COLUMNS, Row.HOME_ROWS, Row.SECTION_LOOK,
                Row.LOOK, Row.SECTION_GLASS, Row.FROST, Row.OPACITY, Row.TINT)
            .tier(2, Row.SECTION_LABELS_FOLDERS, Row.LABEL_SIZE, Row.HOME_ICON_SIZE, Row.FOLDERS,
                Row.NUMERALS, Row.SECTION_OPTIONS, Row.GLASS_WIDGETS, Row.TWO_LINE,
                Row.LABEL_SHADOW, Row.PAGE_DOTS, Row.WALLPAPER_SCROLL)
            .tier(3, Row.MATCH_WORKSPACE, Row.ELEMENTS, Row.SECTION_MORE, Row.LINK_GLOBAL,
                Row.LINK_GESTURES, Row.LINK_PAGES)
            .build();
    }
}
