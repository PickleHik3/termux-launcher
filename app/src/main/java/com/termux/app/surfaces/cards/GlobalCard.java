package com.termux.app.surfaces.cards;

import androidx.annotation.NonNull;

import com.termux.app.surfaces.EditorRows;
import com.termux.app.surfaces.EditorRows.Offers;
import com.termux.app.surfaces.EditorRows.Row;
import com.termux.app.surfaces.EditorRows.Spec;

import java.util.List;

/**
 * The Customization card's rows (appearance-cards overhaul §2, Customization): Appearance, the
 * defaults every surface wears (the block with Vibrancy, and Tint as strength alone); Colours
 * (Mode, Contrast, Accent swatches, Dynamic colour); More options (Full screen, Show
 * wallpaper); Links (Menus and dialogs, TUI app themes). The whole-app Layout list comes later.
 */
public final class GlobalCard {

    private GlobalCard() {}

    /** Every row of the card, tier by tier, in the order the card shows them. */
    @NonNull
    public static List<Spec> rows(@NonNull Offers offers) {
        return new EditorRows.CardRows()
            .tier(1, Row.SECTION_APPEARANCE, Row.LOOK, Row.OPACITY, Row.FROST, Row.GRAIN,
                Row.TINT, Row.CORNERS, Row.RIM, Row.VIBRANCY, Row.GLASS_EDGE, Row.DEPTH)
            .tier(3, Row.SECTION_COLOURS, Row.THEME, Row.CONTRAST, Row.TINT_HUE,
                Row.WALLPAPER_COLOURS)
            .tier(3, Row.SECTION_MORE_OPTIONS, Row.FULL_SCREEN, Row.SHOW_WALLPAPER)
            .tier(3, Row.SECTION_LINKS, Row.LINK_SHEETS, Row.TOOL_COLOURS)
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
