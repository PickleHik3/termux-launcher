package com.termux.app.wall.pageswitch;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;

public class MarksNameChipTest {

    private static final float MARK = 16f;
    private static final float DELTA = 1e-3f;

    /** Pages by letter; the current page is {@code page} (1-based), its window {@code window}. */
    private static List<PageBarModel.Entry> entries(String kinds, int windows, int page, int window) {
        PageBarModel.Input in = PageBarModelTest.wall(kinds, windows);
        in.currentPageId = page;
        in.currentWindow = window;
        return PageBarModel.build(in).entries();
    }

    private static MarksRowLayout row(List<PageBarModel.Entry> e, float length, float text) {
        return new MarksRowLayout(e, null, false, MARK, length, text);
    }

    @Test public void currentWindowWidensByItsNameAndTheRestMoveRight() {
        // Home 1 | Terminal 1, Terminal 2 (current) | Assistant
        MarksRowLayout r = row(entries("HTA", 2, 2, 1), 400f, 40f);
        assertEquals(2, r.chipSlot());
        assertEquals(41f, r.cellStart(2), DELTA);
        assertEquals(41f + 22f + 40f + 3f, r.cellEnd(2), DELTA);
        assertEquals(113f, r.cellStart(3), DELTA);
        assertEquals(135f, r.contentLengthPx(), DELTA);
        assertEquals("the mark stays in the cell's leading part", 41f + 11f, r.markCentreAt(2), DELTA);
        assertEquals(41f + 22f, r.chipTextStart(), DELTA);
    }

    @Test public void aTapAnywhereOnTheChipHitsItsSlot() {
        MarksRowLayout r = row(entries("HTA", 2, 2, 1), 400f, 40f);
        assertEquals(2, r.slotAt(41f + 50f));
    }

    @Test public void noChipOnAssistantOrHomeOrWithNoWidth() {
        assertEquals(-1, row(entries("HTA", 2, 3, 0), 400f, 40f).chipSlot());
        assertEquals(-1, row(entries("HTA", 2, 1, 0), 400f, 40f).chipSlot());
        MarksRowLayout plain = row(entries("HTA", 2, 2, 1), 400f, 0f);
        assertEquals(-1, plain.chipSlot());
        assertEquals(92f, plain.contentLengthPx(), DELTA);
    }

    @Test public void displayWindowWearsTheChipToo() {
        MarksRowLayout r = row(entries("HTD", 1, 3, 0), 400f, 30f);
        assertTrue(MarksNameChip.wears(r.entries().get(2)));
        assertEquals(2, r.chipSlot());
    }

    @Test public void theChipWidensTheRowSoItFoldsSooner() {
        List<PageBarModel.Entry> e = entries("HTA", 4, 2, 3);
        MarksRowLayout plain = row(e, 400f, 0f);
        float room = plain.contentLengthPx() + 10f;
        assertFalse(row(e, room, 0f).slot(1).folded());
        MarksRowLayout chip = row(e, room, 60f);
        assertTrue("the windows fold to make room for the chip", chip.slot(1).folded());
        assertEquals("a folded stack wears no chip", -1, chip.chipSlot());
    }

    @Test public void nameIsCutWithAnEllipsisPastFourteenCharacters() {
        List<PageBarModel.Entry> e = entries("HTA", 1, 2, 0);
        assertEquals("Terminal 1", MarksNameChip.text(e.get(1)));
        PageBarModel.Input in = PageBarModelTest.wall("HTA", 1);
        in.currentPageId = 2;
        in.currentWindow = 0;
        in.windowNames.set(0, "abcdefghijklmn");
        assertEquals("abcdefghijklmn", MarksNameChip.text(PageBarModel.build(in).entries().get(1)));
        in.windowNames.set(0, "abcdefghijklmno");
        assertEquals("abcdefghijklm…", MarksNameChip.text(PageBarModel.build(in).entries().get(1)));
    }

    @Test public void touchSlideAnchorsOnTheChipsMark() {
        MarksRowLayout r = row(entries("HTA", 2, 2, 1), 400f, 40f);
        MarksTouchLayout t = new MarksTouchLayout(r, 1f, 2, 400f);
        assertEquals(r.markCentreAt(2), t.centreAt(t.anchorSlot()), DELTA);
    }

    @Test public void layoutIsDirectionFreeSoRtlOnlyMirrorsTheView() {
        // The layout runs along the line from its start; the view mirrors x for right-to-left.
        MarksRowLayout r = row(entries("HTA", 2, 2, 1), 400f, 40f);
        assertEquals(r.cellStart(2), r.chipTextStart() - 22f, DELTA);
    }
}
