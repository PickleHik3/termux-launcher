package com.termux.app.wall.pageswitch;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.termux.app.page.PageKind;
import com.termux.app.wall.pageswitch.PageBarModel.Entry;
import com.termux.app.wall.pageswitch.PageBarModel.Input;

import org.junit.Test;

import java.util.List;

public class MarksLineTest {

    private static final float DENSITY = 2f;

    private static PageBarModel model(String kinds, int windows, int currentPageId, int currentWindow) {
        Input in = PageBarModelTest.wall(kinds, windows);
        in.currentPageId = currentPageId;
        in.currentWindow = currentWindow;
        return PageBarModel.build(in);
    }

    private static float wide() {
        return 400f * DENSITY;
    }

    @Test public void theLineHasTheWallsEntriesInTheWallsOrder() {
        PageBarModel m = model("HTHDA", 2, 1, -1);
        MarksLine line = new MarksLine(m.entries(), null, DENSITY, wide());
        assertEquals(m.entries().size(), line.slotCount());
        List<PageBarFold.Slot> slots = line.slots();
        PageKind[] want = {PageKind.HOME, PageKind.TERMINAL, PageKind.TERMINAL, PageKind.HOME,
            PageKind.DISPLAY, PageKind.ASSISTANT};
        for (int i = 0; i < want.length; i++) assertEquals(want[i], slots.get(i).kind);
    }

    @Test public void cellsRunFromTheStartAndMarksAreCentredInThem() {
        MarksLine line = new MarksLine(model("HTA", 1, 1, -1).entries(), null, DENSITY, wide());
        assertEquals(line.pitchPx() / 2f, line.centreAt(0), 1e-3f);
        assertEquals(line.pitchPx() * 2.5f, line.centreAt(2), 1e-3f);
        assertTrue(line.markSizePx() <= line.pitchPx());
        assertTrue("a short list sits together", line.pitchPx() <= MarksLine.MAX_PITCH_DP * DENSITY);
        assertEquals(0, line.slotAt(0f));
        assertEquals(1, line.slotAt(line.pitchPx() * 1.2f));
        assertEquals(-1, line.slotAt(-1f));
        assertEquals(-1, line.slotAt(line.contentLengthPx()));
    }

    @Test public void aShortLineFoldsByTheBarsRule() {
        PageBarModel m = model("HHHHT", 3, 1, -1);
        float length = 200f * DENSITY;
        int fit = MarksLine.maxSlots(DENSITY, length);
        MarksLine line = new MarksLine(m.entries(), null, DENSITY, length);
        PageBarFold.Result want = PageBarFold.fold(m.entries(), fit);
        assertEquals(want.slots.size(), line.slotCount());
        assertTrue(line.slotCount() < m.entries().size());
        assertTrue(line.contentLengthPx() <= length + 1e-3f);
    }

    @Test public void twoHomesBesideTheOthersStayOpenWhereTheRowHasRoom() {
        // Home, Home, a window, Assistant: four marks; the thin row's line is about 100dp long.
        PageBarModel m = model("HHTA", 1, 1, -1);
        float length = 100f * DENSITY;
        assertTrue("the page bar's slots would fold the Homes here",
            PageBarLayout.maxSlots(DENSITY, length) < m.entries().size());
        MarksLine line = new MarksLine(m.entries(), null, DENSITY, length);
        assertEquals(m.entries().size(), line.slotCount());
        for (PageBarFold.Slot slot : line.slots()) assertFalse(slot.folded());
        assertTrue(line.contentLengthPx() <= length + 1e-3f);
    }

    @Test public void theLineFoldsOnlyWhenTheMarksTrulyDoNotFit() {
        PageBarModel m = model("HHTA", 1, 1, -1);
        int n = m.entries().size();
        float justFits = n * MarksLine.MIN_PITCH_DP * DENSITY;
        assertEquals(n, new MarksLine(m.entries(), null, DENSITY, justFits).slotCount());
        MarksLine tight = new MarksLine(m.entries(), null, DENSITY, justFits - 1f);
        assertTrue(tight.slotCount() < n);
        assertTrue(tight.slots().get(0).folded());
    }

    @Test public void aTapGoesToThePageItLandsOn() {
        PageBarModel m = model("HTA", 1, 1, -1);
        MarksLine line = new MarksLine(m.entries(), null, DENSITY, wide());
        PageBarModel.Target assistant = line.targetAt(line.centreAt(2));
        assertNotNull(assistant);
        assertEquals(PageBarModel.Action.GO_PAGE, assistant.action);
        PageBarModel.Target window = line.targetAt(line.centreAt(1));
        assertNotNull(window);
        assertEquals(PageBarModel.Action.GO_TERMINAL, window.action);
        assertEquals(0, window.index);
    }

    @Test public void aTapOnTheCurrentPageOrOffTheLineDoesNothing() {
        PageBarModel m = model("HTA", 1, 3, -1);
        MarksLine line = new MarksLine(m.entries(), null, DENSITY, wide());
        assertNull("the assistant is where the user is", line.targetAt(line.centreAt(2)));
        assertNull(line.targetAt(line.contentLengthPx() + 10f));
        assertNull(line.targetAt(-5f));
    }

    @Test public void aFoldedStackGoesToItsCurrentMemberElseTheWindowToReturnTo() {
        PageBarModel m = model("T", 4, 1, 2);
        float length = 10f * DENSITY;
        MarksLine line = new MarksLine(m.entries(), null, DENSITY, length);
        assertEquals(1, line.slotCount());
        assertNull("the current window is in the stack", line.targetAt(line.centreAt(0)));

        PageBarModel away = model("HT", 4, 1, 2);
        MarksLine back = new MarksLine(away.entries(), null, DENSITY, length);
        PageBarModel.Target to = back.targetAt(back.centreAt(back.slotCount() - 1));
        assertNotNull(to);
        assertEquals(PageBarModel.Action.GO_TERMINAL, to.action);
        assertEquals(2, to.index);
    }

    @Test public void attentionIsPerSlotAndFoldsWithItsMembers() {
        PageBarModel m = model("HT", 3, 1, -1);
        List<Entry> entries = m.entries();
        boolean[] attention = new boolean[entries.size()];
        attention[3] = true;
        MarksLine open = new MarksLine(entries, attention, DENSITY, wide());
        assertFalse(open.attention(0));
        assertFalse(open.attention(1));
        assertTrue(open.attention(3));
        MarksLine folded = new MarksLine(entries, attention, DENSITY, 10f * DENSITY);
        boolean any = false;
        for (int i = 0; i < folded.slotCount(); i++) any |= folded.attention(i);
        assertTrue("a folded stack keeps its members' attention", any);
    }

    @Test public void anEmptyLineIsEmpty() {
        MarksLine line = new MarksLine(model("", 0, -1, -1).entries(), null, DENSITY, wide());
        assertEquals(0, line.slotCount());
        assertNull(line.targetAt(5f));
        assertEquals(-1, line.slotAt(5f));
    }
}
