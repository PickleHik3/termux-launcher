package com.termux.app.statusbar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.termux.app.page.PageKind;

import org.junit.Test;

/**
 * Which way the one status host presents: the strip on a Home that shows the bar and whenever
 * such a Home page can be on screen, the thin row at rest on a page with a pane, and the row
 * through every slide to and from a Home that shows no bar. The swap happens only where neither
 * page's top depends on it (each page's top is its own: PageTopInsets), so nothing moves when it
 * swaps.
 */
public class StatusPresentationTest {

    private static final StatusPresentation STRIP = StatusPresentation.STRIP;
    private static final StatusPresentation ROW = StatusPresentation.ROW;

    @Test
    public void theRowStandsAtRestOnEveryPageWithAPane() {
        for (boolean homeBar : new boolean[] {true, false}) {
            assertEquals(ROW, StatusPresentation.of(true, PageKind.TERMINAL, true, false, homeBar));
            assertEquals(ROW, StatusPresentation.of(true, PageKind.DISPLAY, true, false, homeBar));
            assertEquals(ROW, StatusPresentation.of(true, PageKind.ASSISTANT, true, false, homeBar));
        }
    }

    @Test
    public void aHomeThatShowsTheBarWearsTheStripAtRestAndMoving() {
        assertEquals(STRIP, StatusPresentation.of(true, PageKind.HOME, true, true, true));
        assertEquals(STRIP, StatusPresentation.of(true, PageKind.HOME, false, true, true));
    }

    @Test
    public void aSlideThatCanShowABarredHomeWearsTheStripFromItsFirstFrameToItsCommit() {
        // Leaving the Terminal toward Home: the strip from the first moving frame.
        assertEquals(STRIP, StatusPresentation.of(true, PageKind.TERMINAL, false, true, true));
        // Landed on the Terminal but Home still committed: the strip until the commit.
        assertEquals(STRIP, StatusPresentation.of(true, PageKind.HOME, true, true, true));
        // Committed to the Terminal at rest: the row.
        assertEquals(ROW, StatusPresentation.of(true, PageKind.TERMINAL, true, false, true));
    }

    @Test
    public void aHomeWithNoBarKeepsTheRowAtRestAndThroughEverySlide() {
        // Every step of Home -> Terminal and back, abandoned or landed: rest on Home, the wall
        // moving with Home committed, the wall landed with Home still committed, the commit.
        assertEquals(ROW, StatusPresentation.of(true, PageKind.HOME, true, true, false));
        assertEquals(ROW, StatusPresentation.of(true, PageKind.HOME, false, true, false));
        assertEquals(ROW, StatusPresentation.of(true, PageKind.TERMINAL, false, true, false));
        assertEquals(ROW, StatusPresentation.of(true, PageKind.TERMINAL, true, false, false));
        // The other end an Assistant or Display page.
        assertEquals(ROW, StatusPresentation.of(true, PageKind.ASSISTANT, false, true, false));
        assertEquals(ROW, StatusPresentation.of(true, PageKind.DISPLAY, false, true, false));
    }

    @Test
    public void aSlideBetweenWindowsKeepsTheRow() {
        assertEquals(ROW, StatusPresentation.of(true, PageKind.TERMINAL, false, false, true));
        assertEquals(ROW, StatusPresentation.of(true, PageKind.DISPLAY, false, false, true));
    }

    @Test
    public void theStripOrAHiddenBarNeverPresentsAsTheRow() {
        for (PageKind page : PageKind.values()) {
            for (boolean rest : new boolean[] {true, false}) {
                for (boolean homeBar : new boolean[] {true, false}) {
                    assertEquals(STRIP, StatusPresentation.of(false, page, rest, false, homeBar));
                    assertEquals(STRIP, StatusPresentation.of(false, page, rest, true, homeBar));
                }
            }
        }
    }

    @Test
    public void homeShowsTheBarOnlyWhereItIsShownAndNotPutAway() {
        assertTrue(StatusPresentation.homeShowsBar(true, false));
        assertFalse(StatusPresentation.homeShowsBar(true, true));
        assertFalse(StatusPresentation.homeShowsBar(false, false));
        assertFalse(StatusPresentation.homeShowsBar(false, true));
    }

    @Test
    public void theRowLiesOverTheCommittedPageOrThePageItRidesInWith() {
        assertEquals(PageKind.TERMINAL, StatusPresentation.rowPage(PageKind.TERMINAL, null));
        assertEquals(PageKind.TERMINAL, StatusPresentation.rowPage(PageKind.TERMINAL, PageKind.HOME));
        assertEquals(PageKind.DISPLAY, StatusPresentation.rowPage(PageKind.DISPLAY, PageKind.HOME));
        // On Home: the slide's other end, the Terminal when there is none or it is another Home.
        assertEquals(PageKind.TERMINAL, StatusPresentation.rowPage(PageKind.HOME, null));
        assertEquals(PageKind.TERMINAL, StatusPresentation.rowPage(PageKind.HOME, PageKind.HOME));
        assertEquals(PageKind.TERMINAL, StatusPresentation.rowPage(PageKind.HOME, PageKind.TERMINAL));
        assertEquals(PageKind.ASSISTANT, StatusPresentation.rowPage(PageKind.HOME, PageKind.ASSISTANT));
        assertEquals(PageKind.DISPLAY, StatusPresentation.rowPage(PageKind.HOME, PageKind.DISPLAY));
    }
}
