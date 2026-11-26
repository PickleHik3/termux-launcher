package com.termux.app.overview;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.termux.app.overview.OverviewDropPolicy.Drop;
import com.termux.app.overview.OverviewDropPolicy.Stop;
import com.termux.app.overview.OverviewDropPolicy.Type;
import com.termux.app.page.PageKind;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class OverviewDropPolicyTest {

    private static Stop home(int id) { return new Stop(PageKind.HOME, id, -1); }
    private static Stop window(int place) { return new Stop(PageKind.TERMINAL, 9, place); }
    private static Stop assistant() { return new Stop(PageKind.ASSISTANT, 20, -1); }

    // H1 H2 W0 W1 W2 A H3  (indices 0..6)
    private static final List<Stop> WALL = Arrays.asList(home(1), home(2), window(0), window(1),
        window(2), assistant(), home(3));

    @Test public void homeAndWindowsCanBeHeldTheRestCannot() {
        assertTrue(OverviewDropPolicy.canDrag(WALL, 0));
        assertTrue(OverviewDropPolicy.canDrag(WALL, 3));
        assertFalse(OverviewDropPolicy.canDrag(WALL, 5));
        assertFalse(OverviewDropPolicy.canDrag(WALL, 7));
        assertFalse(OverviewDropPolicy.canDrag(WALL, -1));
    }

    @Test public void aHomePageLandsInAnyGapOutsideTheWindows() {
        assertEquals(7, OverviewDropPolicy.landing(WALL, 0, 7));
        assertEquals(2, OverviewDropPolicy.landing(WALL, 0, 2));
        assertEquals(5, OverviewDropPolicy.landing(WALL, 0, 5));
        assertEquals(0, OverviewDropPolicy.landing(WALL, 6, 0));
    }

    @Test public void aGapInsideTheWindowsGoesToTheNearerEnd() {
        assertEquals(2, OverviewDropPolicy.landing(WALL, 6, 3));
        assertEquals(5, OverviewDropPolicy.landing(WALL, 6, 4));
        assertEquals(2, OverviewDropPolicy.landing(WALL, 0, 3));
    }

    @Test public void aGapBesideTheHeldCardLandsNowhere() {
        assertEquals(OverviewDropPolicy.NONE, OverviewDropPolicy.landing(WALL, 1, 1));
        assertEquals(OverviewDropPolicy.NONE, OverviewDropPolicy.landing(WALL, 1, 2));
        assertEquals(OverviewDropPolicy.NONE, OverviewDropPolicy.landing(WALL, 6, 6));
        assertEquals(OverviewDropPolicy.NONE, OverviewDropPolicy.landing(WALL, 6, 7));
        // Snapped to the end beside itself: Home 2 dropped inside the windows next to it.
        assertEquals(OverviewDropPolicy.NONE, OverviewDropPolicy.landing(WALL, 1, 3));
    }

    @Test public void aWindowStaysAmongItsOwn() {
        assertEquals(4, OverviewDropPolicy.landing(WALL, 2, 4));
        assertEquals(5, OverviewDropPolicy.landing(WALL, 2, 5));
        assertEquals(OverviewDropPolicy.NONE, OverviewDropPolicy.landing(WALL, 3, 1));
        assertEquals(OverviewDropPolicy.NONE, OverviewDropPolicy.landing(WALL, 3, 6));
        assertEquals(OverviewDropPolicy.NONE, OverviewDropPolicy.landing(WALL, 5, 0));
    }

    @Test public void aHomeDropMovesThePageBesideTheCardItLandsBefore() {
        Drop d = OverviewDropPolicy.drop(WALL, 0, 6);
        assertEquals(Type.PAGE, d.type);
        assertEquals(1, d.pageId);
        assertEquals(3, d.refPageId);
        assertFalse(d.after);
        assertEquals(5, d.to);
    }

    @Test public void aDropAfterTheLastCardLandsAfterIt() {
        Drop d = OverviewDropPolicy.drop(WALL, 0, 7);
        assertEquals(Type.PAGE, d.type);
        assertEquals(3, d.refPageId);
        assertTrue(d.after);
        assertEquals(6, d.to);
    }

    @Test public void aHomeDropBeforeTheWindowsLandsBeforeTheWorkspace() {
        Drop d = OverviewDropPolicy.drop(WALL, 6, 3);
        assertEquals(Type.PAGE, d.type);
        assertEquals(9, d.refPageId);
        assertFalse(d.after);
    }

    @Test public void aWindowDropMovesItWithinTheRowByFinalPlace() {
        Drop forward = OverviewDropPolicy.drop(WALL, 2, 5);
        assertEquals(Type.WINDOW, forward.type);
        assertEquals(0, forward.from);
        assertEquals(2, forward.to);
        Drop back = OverviewDropPolicy.drop(WALL, 4, 2);
        assertEquals(Type.WINDOW, back.type);
        assertEquals(2, back.from);
        assertEquals(0, back.to);
        Drop next = OverviewDropPolicy.drop(WALL, 2, 4);
        assertEquals(0, next.from);
        assertEquals(1, next.to);
    }

    @Test public void lettingGoWhereItStartedChangesNothing() {
        assertEquals(Type.NONE, OverviewDropPolicy.drop(WALL, 1, 1).type);
        assertEquals(Type.NONE, OverviewDropPolicy.drop(WALL, 1, 2).type);
        assertEquals(Type.NONE, OverviewDropPolicy.drop(WALL, 3, 3).type);
        assertEquals(Type.NONE, OverviewDropPolicy.drop(WALL, 3, 4).type);
    }

    @Test public void anUnmovableCardOrAGapOutOfRangeChangesNothing() {
        assertEquals(Type.NONE, OverviewDropPolicy.drop(WALL, 5, 0).type);
        assertEquals(Type.NONE, OverviewDropPolicy.drop(WALL, 0, 99).type);
        assertEquals(Type.NONE, OverviewDropPolicy.drop(WALL, 0, -1).type);
    }

    @Test public void aLoneEmptyTerminalCardIsNotAWindow() {
        List<Stop> wall = Arrays.asList(home(1), new Stop(PageKind.TERMINAL, 9, -1));
        assertFalse(OverviewDropPolicy.canDrag(wall, 1));
        assertEquals(2, OverviewDropPolicy.landing(wall, 0, 2));
    }

    // A row of four waiting sessions: gaps 0..4.
    @Test public void aSessionLandsInAnyGapButBesideItself() {
        assertEquals(3, OverviewDropPolicy.sessionLanding(4, 0, 3));
        assertEquals(0, OverviewDropPolicy.sessionLanding(4, 2, 0));
        assertEquals(4, OverviewDropPolicy.sessionLanding(4, 1, 4));
        assertEquals(OverviewDropPolicy.NONE, OverviewDropPolicy.sessionLanding(4, 1, 1));
        assertEquals(OverviewDropPolicy.NONE, OverviewDropPolicy.sessionLanding(4, 1, 2));
        assertEquals(OverviewDropPolicy.NONE, OverviewDropPolicy.sessionLanding(4, 1, 5));
        assertEquals(OverviewDropPolicy.NONE, OverviewDropPolicy.sessionLanding(4, 4, 0));
        assertEquals(OverviewDropPolicy.NONE, OverviewDropPolicy.sessionLanding(4, 1, -1));
    }

    @Test public void aSessionDropGivesItsFinalPlaceInTheRow() {
        // Forward: the gap after card 2 puts card 0 at place 2.
        assertEquals(2, OverviewDropPolicy.sessionDrop(4, 0, 3));
        assertEquals(3, OverviewDropPolicy.sessionDrop(4, 0, 4));
        // Backward: the gap is the place.
        assertEquals(0, OverviewDropPolicy.sessionDrop(4, 2, 0));
        assertEquals(1, OverviewDropPolicy.sessionDrop(4, 3, 1));
        // Beside itself or outside: nothing changes.
        assertEquals(OverviewDropPolicy.NONE, OverviewDropPolicy.sessionDrop(4, 2, 3));
        assertEquals(OverviewDropPolicy.NONE, OverviewDropPolicy.sessionDrop(1, 0, 1));
    }
}
