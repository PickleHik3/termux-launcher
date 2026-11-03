package com.termux.app.surfaces;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.termux.app.surfaces.EditorRows.Offers;
import com.termux.app.surfaces.EditorRows.Row;

import org.junit.Test;

public class EditPagesChipTest {

    @Test
    public void theEditPagesChipIsOfferedUntilTheOverviewIsUp() {
        assertTrue(EditorRows.tier(EditorCard.HOME, new Offers(true, true, true, false), 3)
            .contains(Row.LINK_PAGES));
        java.util.List<Row> up = EditorRows.tier(EditorCard.HOME, new Offers(true, true, true, true), 3);
        assertFalse(up.contains(Row.LINK_PAGES));
        assertTrue(up.contains(Row.LINK_GLOBAL));
        assertTrue(up.contains(Row.LINK_GESTURES));
    }
}
