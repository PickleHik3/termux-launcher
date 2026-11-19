package com.termux.app.surfaces.cards;

import androidx.annotation.NonNull;

import com.termux.app.surfaces.EditorRows;
import com.termux.app.surfaces.EditorRows.Offers;
import com.termux.app.surfaces.EditorRows.Row;
import com.termux.app.surfaces.EditorRows.Spec;

import java.util.List;

/**
 * The Workspace card's rows (editor-redesign §2, Workspace): Home's twin, with no glass (the
 * chrome wears one look on every page, set on Global, ADR 0031); the terminal's Contrast, the same setting the Global card's row writes; Match Home styling (not under Use as Terminal only, which has no Home to match); the Elements list,
 * where every element on the Workspace is shown, hidden or opened on its card; and the Global
 * chip.
 */
public final class WorkspaceCard {

    private WorkspaceCard() {}

    /** Every row of the card, tier by tier, in the order the card shows them. */
    @NonNull
    public static List<Spec> rows(@NonNull Offers offers) {
        // No glass rows: the chrome wears one look on every page (ADR 0031), set on Global.
        return new EditorRows.CardRows()
            .tier(1, Row.SECTION_APPEARANCE, Row.CONTRAST, Row.MINIMAL)
            .tier(3, Row.SECTION_LAYOUT, Row.ELEMENTS)
            .build();
    }
}
