package com.termux.app.surfaces.cards;

import androidx.annotation.NonNull;

import com.termux.app.surfaces.EditorRows;
import com.termux.app.surfaces.EditorRows.Offers;
import com.termux.app.surfaces.EditorRows.Row;
import com.termux.app.surfaces.EditorRows.Spec;

import java.util.List;

/**
 * The Global card's rows (appearance-one-look spec §3, Global): Look, the glass every surface
 * wears unless it holds its own Opacity or Blur; Fine-tune, the rest of the glass, closed until
 * opened; Shape; Colours; Screen. Where each row lives is the spec's; Status position, Dock type
 * and the Layout list are on their own cards.
 */
public final class GlobalCard {

    private GlobalCard() {}

    /** Every row of the card, tier by tier, in the order the card shows them. */
    @NonNull
    public static List<Spec> rows(@NonNull Offers offers) {
        EditorRows.CardRows rows = new EditorRows.CardRows()
            .tier(1, Row.SECTION_LOOK, Row.LOOK, Row.OPACITY, Row.FROST, Row.TINT)
            .tier(3, Row.SECTION_FINE_TUNE, Row.GRAIN, Row.RIM, Row.GLASS_EDGE, Row.DEPTH,
                Row.VIBRANCY);
        // Out for a setting the user can change: the way to it stands under them.
        if (offers.refractionLink) rows.tier(3, Row.LINK_GLASS_REFRACTION);
        return rows
            .tier(3, Row.SECTION_SHAPE, Row.STYLE, Row.CORNERS, Row.MARGIN)
            .tier(3, Row.SECTION_COLOURS, Row.THEME, Row.CONTRAST, Row.TINT_HUE,
                Row.WALLPAPER_COLOURS, Row.TOOL_COLOURS)
            .tier(3, Row.SECTION_SCREEN, Row.SHOW_WALLPAPER, Row.FULL_SCREEN)
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
