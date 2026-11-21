package com.termux.app.place;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.termux.app.wall.PageRef;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.List;

/**
 * A page's room from its own rest, on every layout fixture, Docked and Floating
 * apart, the keyboard up and down: it is today's room at that rest (the pass's content
 * reservation), whichever page is in front, so a crossing between two rests, which reads only
 * the two rests, changes no page's room.
 */
@RunWith(Parameterized.class)
public class PageRoomTest {

    @Parameterized.Parameters(name = "{0}")
    public static List<Object[]> fixtures() {
        return LayoutFixtures.parameters();
    }

    private static final boolean[] BOOLS = {false, true};
    private static final PageRef[] PAGES = {PageRef.HOME, PageRef.TERMINAL};

    private final LayoutFixtures.Fixture f;

    public PageRoomTest(LayoutFixtures.Fixture fixture) {
        f = fixture;
    }

    @Test public void aPagesRoomAtItsRestIsTodaysRoomForThatRest() {
        for (boolean floating : BOOLS) for (boolean up : BOOLS) for (PageRef page : PAGES) {
            int today = f.settle(page, up, floating).contentReservationPx;
            int room = PageRoom.bottomInsetPx(f.rest(page, page, up, floating));
            assertEquals(f.name + " " + page + " floating=" + floating + " up=" + up, today, room);
        }
    }

    /**
     * What stands on the front page's room (the flat body surface, a side stack's end) ends where
     * the content column ended before the column filled the window: the window less the stack and
     * its margin, plus what the content reached past the stack's top by.
     */
    @Test public void whatStandsOnAPagesRoomEndsWhereTheContentColumnEndedAtRest() {
        final int window = 2400;
        for (boolean floating : BOOLS) for (boolean up : BOOLS) for (PageRef page : PAGES) {
            StackLayout.Result r = f.settle(page, up, floating);
            int columnBottom = window - r.stackHeightPx - r.bottomMarginPx + r.contentOverlapPx;
            int room = PageRoom.bottomInsetPx(f.rest(page, page, up, floating));
            assertEquals(f.name + " " + page + " floating=" + floating + " up=" + up,
                columnBottom, window - room);
        }
    }

    /**
     * The old column hung above the stack container, which stands on the kept band by its margin
     * or by its foot's height (BottomInsets): what a page left of the root layout's height is the
     * container's top above the window's bottom, less what the content reached past it, plus the
     * edge gap while the stack is away. The wall's inset is that number, for every rest and every
     * kept band.
     */
    @Test public void aPagesWallInsetIsTheOldColumnsRoomWithTheKeptBand() {
        for (int kept : new int[] {0, 24, 48}) for (boolean foot : BOOLS)
            for (boolean floating : BOOLS) for (boolean up : BOOLS) for (PageRef page : PAGES) {
            StackLayout.Result r = f.settle(page, up, floating);
            BottomInsets insets = BottomInsets.forKept(0, kept, foot, r.bottomMarginPx,
                r.flushBottomInsetPx);
            int containerTop = r.stackHeightPx + insets.stackExtraHeightPx + insets.stackMarginPx;
            int oldRoom = containerTop - r.contentOverlapPx + r.contentEdgeGapPx;
            int room = PageRoom.bottomInsetPx(f.rest(page, page, up, floating));
            assertEquals(f.name + " " + page + " floating=" + floating + " up=" + up
                + " kept=" + kept + " foot=" + foot, oldRoom, PageRoom.wallInsetPx(room, kept));
        }
    }

    @Test public void aPagesRoomIsItsOwnWhicheverPageIsInFront() {
        for (boolean floating : BOOLS) for (boolean up : BOOLS) for (PageRef page : PAGES) {
            int own = PageRoom.bottomInsetPx(f.rest(page, page, up, floating));
            for (PageRef front : PAGES) {
                // Read while another page is in front, the keyboard as the page's own rest has it.
                int fromFront = PageRoom.bottomInsetPx(f.rest(front, page, up, floating));
                if (front.equals(page)) assertEquals(own, fromFront);
                else if (page.isHome()) assertEquals("Home's chrome takes none", 0, fromFront);
                else assertTrue(f.name + ": the Terminal's room from Home is its keyboard-down or"
                    + " keyboard-up rest, never between", fromFront >= 0);
            }
        }
    }

    @Test public void aCrossingBetweenAnyTwoRestsChangesNoPagesRoom() {
        for (boolean floating : BOOLS) for (boolean upFrom : BOOLS) for (boolean upTo : BOOLS)
            for (PageRef from : PAGES) for (PageRef to : PAGES) {
            // A crossing reads each end's rest once, its own keyboard: the room of each page is
            // the same number at the first frame, at every frame and at the landing.
            int toRoom = PageRoom.bottomInsetPx(f.rest(from, to, upTo, floating));
            int toRoomLanded = PageRoom.bottomInsetPx(f.rest(to, to, upTo, floating));
            for (int i = 0; i <= 8; i++) {
                assertEquals(f.name + " " + from + "->" + to + " frame " + i, toRoomLanded,
                    PageRoom.bottomInsetPx(f.rest(from, to, upTo, floating)));
            }
            if (to.equals(from) || !upTo || to.isHome()) {
                assertEquals(f.name + " " + from + "->" + to, toRoomLanded, toRoom);
            }
        }
    }

    @Test public void aReturnResizesOnlyWhenTheRestChangedWhileAway() {
        for (boolean floating : BOOLS) for (boolean up : BOOLS) {
            int room = PageRoom.bottomInsetPx(f.rest(PageRef.TERMINAL, PageRef.TERMINAL, up,
                floating));
            assertFalse(f.name, PageRoom.resizesOnReturn(room, room));
            int moved = PageRoom.bottomInsetPx(f.rest(PageRef.TERMINAL, PageRef.TERMINAL, !up,
                floating));
            assertEquals(f.name + ": the keyboard changed while away",
                room != moved, PageRoom.resizesOnReturn(room, moved));
        }
    }
}
