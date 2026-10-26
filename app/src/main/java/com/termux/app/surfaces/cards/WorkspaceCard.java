package com.termux.app.surfaces.cards;

import androidx.annotation.NonNull;

import com.termux.app.surfaces.EditorRows;
import com.termux.app.surfaces.EditorRows.Offers;
import com.termux.app.surfaces.EditorRows.Row;
import com.termux.app.surfaces.EditorRows.Spec;

import java.util.List;

/**
 * The Workspace card's rows (editor-redesign §2, Workspace): Home's twin, the Workspace half's
 * Look and Base, which the bars on the Workspace wear while they follow it.
 */
public final class WorkspaceCard {

    private WorkspaceCard() {}

    /** Every row of the card, tier by tier, in the order the card shows them. */
    @NonNull
    public static List<Spec> rows(@NonNull Offers offers) {
        return new EditorRows.CardRows()
            .tier(1, Row.LOOK, Row.FROST, Row.OPACITY, Row.TINT)
            .tier(3, Row.MATCH_HOME, Row.LINK_GLOBAL)
            .build();
    }
}
