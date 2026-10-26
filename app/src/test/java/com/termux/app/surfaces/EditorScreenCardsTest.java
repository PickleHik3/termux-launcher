package com.termux.app.surfaces;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.termux.app.surfaces.EditorRows.Kind;
import com.termux.app.surfaces.EditorRows.Row;
import com.termux.app.surfaces.EditorRows.Spec;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.WallHalf;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The two screens' cards (editor-redesign §1, §2): the new Workspace card, Home's twin, and the
 * Match rows that replace Home's Own layout switch.
 */
public class EditorScreenCardsTest {

    private static final EditorRows.Offers ALL = new EditorRows.Offers(true, true);

    @Test
    public void theWorkspaceCardHoldsItsLookItsGlassItsMatchAndGlobal() {
        assertEquals(Arrays.asList(Row.SECTION_LOOK, Row.LOOK, Row.SECTION_GLASS, Row.FROST,
            Row.OPACITY, Row.TINT), EditorRows.tier(EditorCard.WORKSPACE, ALL, 1));
        assertEquals(Arrays.asList(Row.MATCH_HOME, Row.ELEMENTS, Row.SECTION_MORE,
            Row.LINK_GLOBAL), EditorRows.tier(EditorCard.WORKSPACE, ALL, 3));
    }

    @Test
    public void theHomeCardHasAMatchRowAndNoOwnLayoutSwitch() {
        List<Row> rows = rowsOf(EditorCard.HOME);
        assertTrue(rows.contains(Row.MATCH_WORKSPACE));
        assertFalse(rows.contains(Row.MATCH_HOME));
        for (Row row : Row.values())
            assertFalse("the Own layout switch is gone", row.name().equals("OWN_LAYOUT"));
    }

    @Test
    public void theMatchRowsAreActionsOnTheirScreensCardsAlone() {
        assertEquals(Kind.ACTION, Row.MATCH_HOME.kind);
        assertEquals(Kind.ACTION, Row.MATCH_WORKSPACE.kind);
        for (EditorCard card : EditorCard.values()) {
            List<Row> rows = rowsOf(card);
            assertEquals(card + " and Match Home styling", card == EditorCard.WORKSPACE,
                rows.contains(Row.MATCH_HOME));
            assertEquals(card + " and Match Workspace styling", card == EditorCard.HOME,
                rows.contains(Row.MATCH_WORKSPACE));
        }
    }

    @Test
    public void theWorkspaceCardsLookAndGlassAreTheWorkspaceHalfsWhateverTheFrameShows() {
        for (WallHalf frame : WallHalf.values()) {
            for (Row row : new Row[] {Row.LOOK, Row.FROST, Row.OPACITY, Row.TINT})
                assertEquals(row + " with the frame on " + frame, WallHalf.WORKSPACE,
                    EditorRows.halfFor(EditorCard.WORKSPACE, row, frame));
            assertNull(EditorRows.halfFor(EditorCard.WORKSPACE, Row.MATCH_HOME, frame));
            assertNull(EditorRows.halfFor(EditorCard.WORKSPACE, Row.LINK_GLOBAL, frame));
            assertNull(EditorRows.halfFor(EditorCard.HOME, Row.MATCH_WORKSPACE, frame));
        }
    }

    @Test
    public void theWorkspaceCardStandsOnTheWorkspaceAndIsNamedByItsKey() {
        assertTrue(EditorCard.WORKSPACE.standsOn(WallHalf.WORKSPACE));
        assertFalse(EditorCard.WORKSPACE.standsOn(WallHalf.HOME));
        assertFalse("the screens meet through Match, not the menu's Copy",
            EditorCard.WORKSPACE.perHalf());
        assertNull(EditorCard.WORKSPACE.slot);
        assertEquals(EditorCard.WORKSPACE, EditorCard.forKey("workspace"));
        assertEquals(EditorCard.HOME, EditorCard.forScreen(WallHalf.HOME));
        assertEquals(EditorCard.WORKSPACE, EditorCard.forScreen(WallHalf.WORKSPACE));
        assertFalse("its glass is the Base itself",
            EditorRows.marksOwnValue(EditorCard.WORKSPACE, Row.FROST));
    }

    private static List<Row> rowsOf(EditorCard card) {
        List<Row> out = new ArrayList<>();
        for (Spec spec : EditorRows.rows(card, ALL))
            out.add(spec.row);
        return out;
    }
}
