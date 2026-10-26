package com.termux.app.surfaces.cards;

import androidx.annotation.NonNull;

import com.termux.app.surfaces.EditorRows;
import com.termux.app.surfaces.EditorRows.Offers;
import com.termux.app.surfaces.EditorRows.Row;
import com.termux.app.surfaces.EditorRows.Spec;

import java.util.List;

/** The Status bar card's rows (editor-redesign §2, Status bar). */
public final class StatusBarCard {

    private StatusBarCard() {}

    /** Every row of the card, tier by tier, in the order the card shows them. */
    @NonNull
    public static List<Spec> rows(@NonNull Offers offers) {
        return new EditorRows.CardRows()
            .tier(1, Row.FROST, Row.OPACITY, Row.TINT, Row.CORNERS, Row.MARGIN, Row.VISIBLE)
            .tier(2, Row.CLOCK_12H, Row.CLOCK_FACE)
            .tier(3, Row.LINK_INDICATORS)
            .build();
    }
}
