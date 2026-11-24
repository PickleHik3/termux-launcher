package com.termux.app.surfaces.cards;

import androidx.annotation.NonNull;

import com.termux.app.surfaces.EditorRows;
import com.termux.app.surfaces.EditorRows.Offers;
import com.termux.app.surfaces.EditorRows.Row;
import com.termux.app.surfaces.EditorRows.Spec;

import java.util.List;

/**
 * The Global card's rows in the condensed kinds (spec #326): Wallpaper first (the way to the
 * picker, Show wallpaper under it), then what the card is (Look, Style, Theme), the sliders in
 * pairs by meaning (Opacity with Frost, Corner radius with Margin), the short choices beside
 * their labels, the switches as chips, the Tint colour and the links. Order does the grouping in
 * place of headings; Fine-tune (Depth with Shine, the glass refraction link) is the one folded
 * section. Every knob of the earlier card is still here and writes the key it wrote. Status
 * position, Dock type and the Layout list are on their own cards.
 */
public final class GlobalCard {

    private GlobalCard() {}

    /** Every row of the card, tier by tier, in the order the card shows them. */
    @NonNull
    public static List<Spec> rows(@NonNull Offers offers) {
        EditorRows.CardRows rows = new EditorRows.CardRows()
            .tier(1, Row.WALLPAPER, Row.SHOW_WALLPAPER, Row.LOOK)
            .pair(1, Row.OPACITY, Row.FROST)
            .tier(1, Row.TINT)
            .choice(3, Row.STYLE, Row.THEME)
            .pair(3, Row.CORNERS, Row.MARGIN)
            .tier(3, Row.TEXT_SIZE)
            .choice(3, Row.HOME_ICON_SIZE, Row.CONTRAST, Row.PAGE_TRANSITION)
            .chips(3, Row.ADAPTIVE_LEGIBILITY, Row.WALLPAPER_COLOURS, Row.FULL_SCREEN)
            .tier(3, Row.TINT_HUE)
            .links(3, Row.TOOL_COLOURS)
            .tier(3, Row.SECTION_FINE_TUNE)
            .pair(3, Row.DEPTH, Row.SHINE);
        // Out for a setting the user can change: the way to it stands under them.
        if (offers.refractionLink) rows.links(3, Row.LINK_GLASS_REFRACTION);
        return rows.build();
    }

    /**
     * Whether {@code row} is one of the glass material strips, which stand on the card at any
     * Look (the screens' Looks live on their own cards).
     */
    public static boolean isGlassMaterial(@NonNull Row row) {
        return row == Row.SHINE || row == Row.DEPTH;
    }
}
