package com.termux.app.surfaces.cards;

import androidx.annotation.NonNull;

import com.termux.app.surfaces.EditorRows;
import com.termux.app.surfaces.EditorRows.Offers;
import com.termux.app.surfaces.EditorRows.Row;
import com.termux.app.surfaces.EditorRows.Spec;

import java.util.List;

/**
 * The Workspace card's rows (editor-redesign §2, Workspace): Home's twin, the Workspace half's
 * Look as tiles and its glass, which the bars on the Workspace wear while they follow it; the
 * terminal's Contrast, the same setting the Global card's row writes; Match Home styling (not under Use as Terminal only, which has no Home to match); the Elements list,
 * where every element on the Workspace is shown, hidden or opened on its card; and the Global
 * chip.
 */
public final class WorkspaceCard {

    private WorkspaceCard() {}

    /** Every row of the card, tier by tier, in the order the card shows them. */
    @NonNull
    public static List<Spec> rows(@NonNull Offers offers) {
        // Contrast and Minimal layout join the Appearance section, with no heading of their own.
        return com.termux.app.surfaces.GlassRows.appearance(new EditorRows.CardRows(), offers, 1)
            .tier(1, Row.MARGIN, Row.CONTRAST, Row.MINIMAL)
            .tier(3, Row.SECTION_LAYOUT, Row.ELEMENTS)
            .build();
    }
}
