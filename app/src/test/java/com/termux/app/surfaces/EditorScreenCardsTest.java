package com.termux.app.surfaces;

import com.termux.app.page.PageKind;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.termux.app.surfaces.EditorRows.Kind;
import com.termux.app.surfaces.EditorRows.Row;
import com.termux.app.surfaces.EditorRows.Spec;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The two page cards (appearance-one-look spec §3): Home's, and the Terminal's, which the
 * Workspace card merged into.
 */
public class EditorScreenCardsTest {

    private static final EditorRows.Offers ALL = new EditorRows.Offers(true, true);

    /** The Workspace's rows live on the Terminal card, each once: Contrast, Layout, Minimal. */
    @Test
    public void theTerminalCardHoldsTheWorkspacesRowsOnce() {
        List<Row> rows = rowsOf(EditorCard.TERMINAL);
        for (Row row : new Row[] {Row.CONTRAST, Row.ELEMENTS, Row.MINIMAL})
            assertEquals(row + " once", 1, java.util.Collections.frequency(rows, row));
        assertEquals("Minimal layout closes the Layout section",
            rows.indexOf(Row.ELEMENTS) + 1, rows.indexOf(Row.MINIMAL));
    }

    @Test
    public void theMatchRowsAndTheOwnLayoutSwitchAreGone() {
        // ADR 0015 §6: copy-to-the-other-half retired with the halves.
        for (Row row : Row.values()) {
            assertFalse("the Own layout switch is gone", row.name().equals("OWN_LAYOUT"));
            assertFalse(row + ": the Match rows are gone", row.name().startsWith("MATCH_"));
        }
    }

    @Test
    public void theTerminalCardStandsOnTheTerminalsPagesAndIsNamedByItsKeys() {
        assertTrue(EditorCard.TERMINAL.standsOn(PageKind.TERMINAL));
        assertTrue(EditorCard.TERMINAL.standsOn(PageKind.DISPLAY));
        assertFalse(EditorCard.TERMINAL.standsOn(PageKind.HOME));
        assertEquals(EditorCard.TERMINAL, EditorCard.forKey("terminal"));
        assertEquals(EditorCard.TERMINAL, EditorCard.forKey("workspace"));
        assertEquals(EditorCard.HOME, EditorCard.forScreen(PageKind.HOME));
        assertEquals(EditorCard.TERMINAL, EditorCard.forScreen(PageKind.TERMINAL));
        assertEquals(EditorCard.TERMINAL, EditorCard.forScreen(PageKind.DISPLAY));
    }

    private static List<Row> rowsOf(EditorCard card) {
        List<Row> out = new ArrayList<>();
        for (Spec spec : EditorRows.rows(card, ALL))
            out.add(spec.row);
        return out;
    }
}
