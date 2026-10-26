package com.termux.app.surfaces.cards;

import androidx.annotation.NonNull;

import com.termux.app.surfaces.EditorRows;
import com.termux.app.surfaces.EditorRows.Offers;
import com.termux.app.surfaces.EditorRows.Row;
import com.termux.app.surfaces.EditorRows.Spec;

import java.util.List;

/**
 * The Terminal card's rows (editor-redesign §2, Terminal); Effect only where its shader runs.
 */
public final class TerminalCard {

    private TerminalCard() {}

    /** Every row of the card, tier by tier, in the order the card shows them. */
    @NonNull
    public static List<Spec> rows(@NonNull Offers offers) {
        EditorRows.CardRows rows = new EditorRows.CardRows()
            .tier(1, Row.FROST, Row.OPACITY, Row.TINT, Row.TEXT_SIZE, Row.CORNERS, Row.MARGIN,
                Row.SPLIT_GAP)
            .tier(2, Row.CONTRAST)
            .tier(3, Row.CURSOR_TRAIL);
        if (offers.terminalEffect)
            rows.tier(3, Row.EFFECT);
        return rows.tier(3, Row.LINK_FONT).build();
    }
}
