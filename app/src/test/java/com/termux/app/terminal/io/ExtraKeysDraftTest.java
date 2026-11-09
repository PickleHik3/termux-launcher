package com.termux.app.terminal.io;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.os.Build;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Arrays;

/** Every edit the page editor can make, on the JVM. org.json needs Robolectric's implementation. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class ExtraKeysDraftTest {

    private static ExtraKeysDraft draft(String one, String two) {
        return ExtraKeysDraft.of(Arrays.asList(
            ExtraKeysLayoutModel.parse(one), ExtraKeysLayoutModel.parse(two)));
    }

    @Test
    public void anEmptyPageTwoIsAbsentUntilAdded() {
        ExtraKeysDraft draft = draft("[[ESC]]", "[]");
        assertEquals(1, draft.pageCount());
        assertTrue(draft.canAddPage());
        assertEquals(1, draft.addPage());
        assertFalse(draft.canAddPage());
        assertEquals(-1, draft.addPage());
        assertEquals(2, draft.pageCount());
    }

    @Test
    public void aPageTwoWithKeysIsKept() {
        assertEquals(2, draft("[[ESC]]", "[[TAB]]").pageCount());
    }

    @Test
    public void pageTwoTakesItsFirstKeyWithoutAnyRow() {
        ExtraKeysDraft draft = draft("[[ESC]]", "[]");
        int page = draft.addPage();
        draft.addKey(page, 0, new ExtraKeysLayoutModel.Key("zz9"));
        assertEquals("[[\"zz9\"]]", draft.valueFor(1));
        assertEquals("[[\"ESC\"]]", draft.valueFor(0));
    }

    @Test
    public void absentPageSavesAsTheEmptyPage() {
        assertEquals("[]", draft("[[ESC]]", "[]").valueFor(1));
    }

    @Test
    public void movePageSwapsTheTwo() {
        ExtraKeysDraft draft = draft("[[ESC]]", "[[TAB]]");
        assertFalse(draft.canMovePage(0, -1));
        assertFalse(draft.canMovePage(1, 1));
        assertTrue(draft.movePage(0, 1));
        assertEquals("[[\"TAB\"]]", draft.valueFor(0));
        assertEquals("[[\"ESC\"]]", draft.valueFor(1));
    }

    @Test
    public void removingPageOneWithTwoPromotesPageTwo() {
        ExtraKeysDraft draft = draft("[[ESC]]", "[[TAB]]");
        assertEquals(0, draft.removePage(0));
        assertEquals(1, draft.pageCount());
        assertEquals("[[\"TAB\"]]", draft.valueFor(0));
        assertEquals("[]", draft.valueFor(1));
    }

    @Test
    public void removingPageTwoClearsIt() {
        ExtraKeysDraft draft = draft("[[ESC]]", "[[TAB]]");
        assertEquals(0, draft.removePage(1));
        assertEquals("[]", draft.valueFor(1));
        assertEquals("[[\"ESC\"]]", draft.valueFor(0));
    }

    @Test
    public void theOnlyPageIsEmptiedNotRemoved() {
        ExtraKeysDraft draft = draft("[[ESC]]", "[]");
        assertTrue(draft.canRemovePage(0));
        draft.removePage(0);
        assertEquals(1, draft.pageCount());
        assertEquals("[]", draft.valueFor(0));
        assertFalse("nothing left to remove", draft.canRemovePage(0));
    }

    @Test
    public void rowsMoveAndStopAtTheEdges() {
        ExtraKeysDraft draft = draft("[[ESC],[TAB],[ENTER]]", "[]");
        assertFalse(draft.canMoveRow(0, 0, -1));
        assertFalse(draft.canMoveRow(0, 2, 1));
        assertTrue(draft.moveRow(0, 2, -1));
        assertEquals("[[\"ESC\"],[\"ENTER\"],[\"TAB\"]]", draft.valueFor(0));
        draft.removeRow(0, 0);
        assertEquals("[[\"ENTER\"],[\"TAB\"]]", draft.valueFor(0));
    }

    @Test
    public void keysMoveAlongTheirRowOnly() {
        ExtraKeysDraft draft = draft("[[ESC,TAB,CTRL],[ALT]]", "[]");
        ExtraKeysLayoutModel.Key tab = draft.page(0).row(0).get(1);
        assertTrue(draft.canMoveKey(0, tab, 1));
        assertTrue(draft.moveKey(0, tab, 1));
        assertFalse("end of the row", draft.canMoveKey(0, tab, 1));
        assertEquals("[[\"ESC\",\"CTRL\",\"TAB\"],[\"ALT\"]]", draft.valueFor(0));
        ExtraKeysLayoutModel.Key alt = draft.page(0).row(1).get(0);
        assertFalse("a lone key has nowhere to go", draft.canMoveKey(0, alt, -1));
    }

    @Test
    public void removingAKeyThenUndoingBringsTheRowBack() {
        ExtraKeysDraft draft = draft("[[ESC,TAB]]", "[]");
        ExtraKeysDraft.Snapshot before = draft.snapshot();
        assertTrue(draft.removeKey(0, draft.page(0).row(0).get(0)));
        assertEquals("[[\"TAB\"]]", draft.valueFor(0));
        draft.restore(before);
        assertEquals("[[\"ESC\",\"TAB\"]]", draft.valueFor(0));
    }

    @Test
    public void undoKeepsEmptyRowsAndSurvivesLaterEdits() {
        ExtraKeysDraft draft = draft("[[ESC]]", "[]");
        draft.addRow(0);
        ExtraKeysDraft.Snapshot before = draft.snapshot();
        draft.removeRow(0, 1);
        draft.addKey(0, 0, new ExtraKeysLayoutModel.Key("X"));
        draft.restore(before);
        assertEquals(2, draft.page(0).rowCount());
        assertEquals("[[\"ESC\"]]", draft.valueFor(0));
    }

    @Test
    public void fingerprintReturnsWhenAnEditIsTakenBack() {
        ExtraKeysDraft draft = draft("[[ESC]]", "[]");
        String start = draft.fingerprint();
        ExtraKeysLayoutModel.Key key = new ExtraKeysLayoutModel.Key("Q");
        draft.addKey(0, 0, key);
        assertNotEquals(start, draft.fingerprint());
        draft.removeKey(0, key);
        assertEquals(start, draft.fingerprint());
    }

    @Test
    public void aKeyColourSurvivesTheSnapshot() {
        ExtraKeysDraft draft = draft("[[{key: ESC, color: 'primary'}]]", "[]");
        String before = draft.valueFor(0);
        ExtraKeysDraft.Snapshot snapshot = draft.snapshot();
        draft.removeKey(0, draft.page(0).row(0).get(0));
        draft.restore(snapshot);
        assertEquals(before, draft.valueFor(0));
    }
}
