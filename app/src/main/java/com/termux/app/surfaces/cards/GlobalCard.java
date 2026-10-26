package com.termux.app.surfaces.cards;

import androidx.annotation.NonNull;

import com.termux.app.surfaces.EditorRows;
import com.termux.app.surfaces.EditorRows.Offers;
import com.termux.app.surfaces.EditorRows.Row;
import com.termux.app.surfaces.EditorRows.Spec;

import java.util.List;

/**
 * The Global card's rows (editor-redesign §1 "Global is one card", §2 Global): what both screens
 * share. Wallpaper first (Change, Show wallpaper, Wallpaper colours); then the colour (Theme,
 * Contrast, Tint colour); the glass material (Vibrancy, and Edge and Depth where the phone draws
 * the glass edge); Full screen and Minimal; and the Icon pack and Tool colours chips. Look and the
 * glass amounts live on each screen's card, Text size on the Terminal's.
 */
public final class GlobalCard {

    private GlobalCard() {}

    /** Every row of the card, tier by tier, in the order the card shows them. */
    @NonNull
    public static List<Spec> rows(@NonNull Offers offers) {
        EditorRows.CardRows rows = new EditorRows.CardRows()
            .tier(1, Row.SECTION_WALLPAPER, Row.WALLPAPER, Row.SHOW_WALLPAPER,
                Row.WALLPAPER_COLOURS, Row.SECTION_COLOUR, Row.THEME, Row.CONTRAST, Row.TINT_HUE)
            .tier(2, Row.SECTION_GLASS_MATERIAL, Row.VIBRANCY);
        if (offers.glassEdge)
            rows.tier(2, Row.GLASS_EDGE, Row.DEPTH);
        return rows
            .tier(2, Row.HINT_GLASS_MATERIAL, Row.SECTION_OPTIONS, Row.FULL_SCREEN, Row.MINIMAL)
            .tier(3, Row.SECTION_MORE, Row.ICON_PACK, Row.TOOL_COLOURS)
            .build();
    }

    /**
     * Whether {@code row} is one of the glass material strips, which stand on the card at any
     * Look (the screens' Looks live on their own cards).
     */
    public static boolean isGlassMaterial(@NonNull Row row) {
        return row == Row.VIBRANCY || row == Row.GLASS_EDGE || row == Row.DEPTH;
    }
}
