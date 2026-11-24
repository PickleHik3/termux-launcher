package com.termux.app.statusbar;

import static org.junit.Assert.assertEquals;

import com.termux.app.page.PageKind;

import org.junit.Test;

/**
 * Which way the one status host presents: the strip on Home and whenever a Home page can be on
 * screen, the thin row at rest on a page with a pane. The swap happens only where neither page's
 * top depends on it (each page's top is its own: PageTopInsets), so nothing moves when it swaps.
 */
public class StatusPresentationTest {

    private static final StatusPresentation STRIP = StatusPresentation.STRIP;
    private static final StatusPresentation ROW = StatusPresentation.ROW;

    @Test
    public void theRowStandsAtRestOnEveryPageWithAPane() {
        assertEquals(ROW, StatusPresentation.of(true, PageKind.TERMINAL, true, false));
        assertEquals(ROW, StatusPresentation.of(true, PageKind.DISPLAY, true, false));
        assertEquals(ROW, StatusPresentation.of(true, PageKind.ASSISTANT, true, false));
    }

    @Test
    public void homeWearsTheStripAtRestAndMoving() {
        assertEquals(STRIP, StatusPresentation.of(true, PageKind.HOME, true, true));
        assertEquals(STRIP, StatusPresentation.of(true, PageKind.HOME, false, true));
    }

    @Test
    public void aSlideThatCanShowHomeWearsTheStripFromItsFirstFrameToItsCommit() {
        // Leaving the Terminal toward Home: the strip from the first moving frame.
        assertEquals(STRIP, StatusPresentation.of(true, PageKind.TERMINAL, false, true));
        // Landed on the Terminal but Home still committed: the strip until the commit.
        assertEquals(STRIP, StatusPresentation.of(true, PageKind.HOME, true, true));
        // Committed to the Terminal at rest: the row.
        assertEquals(ROW, StatusPresentation.of(true, PageKind.TERMINAL, true, false));
    }

    @Test
    public void aSlideBetweenWindowsKeepsTheRow() {
        assertEquals(ROW, StatusPresentation.of(true, PageKind.TERMINAL, false, false));
        assertEquals(ROW, StatusPresentation.of(true, PageKind.DISPLAY, false, false));
    }

    @Test
    public void theStripOrAHiddenBarNeverPresentsAsTheRow() {
        for (PageKind page : PageKind.values()) {
            for (boolean rest : new boolean[] {true, false}) {
                assertEquals(STRIP, StatusPresentation.of(false, page, rest, false));
                assertEquals(STRIP, StatusPresentation.of(false, page, rest, true));
            }
        }
    }
}
