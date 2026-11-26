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
        for (int w = 0; w < windows; w++) in.windowNames.set(w, "win" + (w + 1));
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
        assertEquals(41f + 22f + 40f + 1f + 5f, r.cellEnd(2), DELTA);
        assertEquals(116f, r.cellStart(3), DELTA);
        assertEquals(138f, r.contentLengthPx(), DELTA);
        assertEquals("the mark stays in the cell's leading part", 41f + 11f, r.markCentreAt(2), DELTA);
        assertEquals(41f + 11f + 6.5f + 1f, r.chipTextStart(), DELTA);
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
        PageBarModel.Input in = PageBarModelTest.wall("HTD", 1);
        in.pages.set(2, new PageBarModel.Page(3, com.termux.app.page.PageKind.DISPLAY, "Screen"));
        in.currentPageId = 3;
        MarksRowLayout r = row(PageBarModel.build(in).entries(), 400f, 30f);
        assertTrue(MarksNameChip.wears(r.entries().get(2)));
        assertEquals(2, r.chipSlot());
        in.pages.set(2, new PageBarModel.Page(3, com.termux.app.page.PageKind.DISPLAY, null));
        assertEquals("a Display with no name of its own keeps the plain mark", -1,
            row(PageBarModel.build(in).entries(), 400f, 30f).chipSlot());
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
        assertEquals(r.markCentreAt(2) + 7.5f, r.chipTextStart(), DELTA);
    }

    @Test public void pillSpansNumberNameAndEndPadding() {
        float text = 40f;
        MarksRowLayout r = row(entries("HTA", 2, 2, 1), 400f, text);
        float lead = MarksNameChip.leadPx(MARK);
        assertEquals("as high as the current mark's solid shape", 13f,
            MarksNameChip.pillHeight(MARK), DELTA);
        float width = MarksNameChip.pillWidthPx(MARK, text);
        assertEquals(lead + 1f + text + 5f, width, DELTA);
        float left = r.markCentreAt(2) - lead / 2f;
        assertEquals("the name ends inside the pill by the end padding",
            left + width - 5f, r.chipTextStart() + text, DELTA);
        assertTrue("the pill sits inside its cell", left >= r.cellStart(2) - DELTA);
        assertTrue(left + width <= r.cellEnd(2) + DELTA);
    }

    @Test public void anUnnamedWindowKeepsThePlainMark() {
        PageBarModel.Input in = PageBarModelTest.wall("HTA", 2);
        in.currentPageId = 2;
        in.currentWindow = 1;
        List<PageBarModel.Entry> e = PageBarModel.build(in).entries();
        assertFalse(e.get(2).named);
        assertFalse(MarksNameChip.wears(e.get(2)));
        MarksRowLayout r = row(e, 400f, 40f);
        assertEquals(-1, r.chipSlot());
        assertEquals(92f, r.contentLengthPx(), DELTA);
        in.windowNames.set(1, "build");
        assertTrue(PageBarModel.build(in).entries().get(2).named);
    }
}
