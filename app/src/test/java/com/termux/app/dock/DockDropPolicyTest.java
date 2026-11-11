package com.termux.app.dock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.termux.app.launcher.widget.WidgetPaneController;

import org.junit.Test;

/** Where an icon carried onto the dock's row lands, and what the row does around it. */
public class DockDropPolicyTest {

    // ---- where it lands, and what a full page gives up

    /** Five to a page, ten pins: page 1 is full. A drop between its second and third icon. */
    @Test
    public void aDropOnAFullPageLandsUnderTheFingerAndItsLastIconMovesOn() {
        int at = DockDropPolicy.insertIndex(1, 5, 10, 7);
        assertEquals(7, at);
        assertEquals("it stays on the page it was dropped on", 1, DockDropPolicy.pageOf(at, 5));
        assertEquals("the page's old last icon (index 9) is now the next page's first",
            10, DockDropPolicy.pushedToNextPage(at, 5, 10));
    }

    /** Past the last icon of a full page the icon takes the last slot, not the next page's first. */
    @Test
    public void pastTheEndOfAFullPageItTakesTheLastSlot() {
        assertEquals(9, DockDropPolicy.insertIndex(1, 5, 10, 10));
        assertEquals(4, DockDropPolicy.insertIndex(0, 5, 10, 5));
        assertEquals(5, DockDropPolicy.pushedToNextPage(4, 5, 10));
    }

    @Test
    public void aPageWithRoomTakesItAtTheEndAndNothingMovesOn() {
        // Seven pins: page 1 holds two and has room for three more.
        assertEquals(7, DockDropPolicy.insertIndex(1, 5, 7, 9));
        assertEquals(-1, DockDropPolicy.pushedToNextPage(7, 5, 7));
        assertEquals(6, DockDropPolicy.insertIndex(1, 5, 7, 6));
    }

    @Test
    public void aDropBeforeThePageStartsAtItsFirstSlot() {
        assertEquals(5, DockDropPolicy.insertIndex(1, 5, 10, 2));
    }

    /** The most-used page comes after every pin: an icon dropped there joins the end of the row. */
    @Test
    public void aPagePastThePinsAppends() {
        assertEquals(10, DockDropPolicy.insertIndex(2, 5, 10, 11));
        assertEquals(0, DockDropPolicy.insertIndex(0, 5, 0, 3));
    }

    @Test
    public void theDockHasNoLimitSoAFullLastPageMakesANewOne() {
        int at = DockDropPolicy.insertIndex(1, 5, 10, 8);
        assertEquals(10, DockDropPolicy.pushedToNextPage(at, 5, 10));
        assertEquals(2, DockDropPolicy.pageOf(10, 5));
    }

    // ---- what a release over an icon makes

    @Test
    public void anArmedRestOnAnAppMakesAFolderAnyOtherReleaseGoesBeside() {
        assertEquals(DockDropPolicy.Outcome.MAKE_FOLDER,
            DockDropPolicy.outcome(false, true, false, false, true, true));
        assertEquals(DockDropPolicy.Outcome.INSERT,
            DockDropPolicy.outcome(false, true, false, false, true, false));
        assertTrue(DockDropPolicy.onIcon(0.28f));
        assertFalse(DockDropPolicy.onIcon(0.27f));
    }

    @Test
    public void anArmedRestOnAFolderJoinsItWhileItHasRoom() {
        assertEquals(DockDropPolicy.Outcome.ADD_TO_FOLDER,
            DockDropPolicy.outcome(false, false, true, false, true, true));
        assertEquals(DockDropPolicy.Outcome.FOLDER_FULL,
            DockDropPolicy.outcome(false, false, true, false, false, true));
        assertEquals(DockDropPolicy.Outcome.ALREADY_THERE,
            DockDropPolicy.outcome(false, false, true, true, true, true));
    }

    @Test
    public void aFolderThatIsNotArmedTakesNothing() {
        assertEquals(DockDropPolicy.Outcome.INSERT,
            DockDropPolicy.outcome(false, false, true, false, true, false));
    }

    /** The row never holds an app twice: one already pinned stays where it is. */
    @Test
    public void anAppAlreadyInTheRowIsRefused() {
        assertEquals(DockDropPolicy.Outcome.ALREADY_THERE,
            DockDropPolicy.outcome(true, true, false, false, true, true));
        assertEquals(DockDropPolicy.Outcome.ALREADY_THERE,
            DockDropPolicy.outcome(true, false, false, false, true, false));
    }

    @Test
    public void overEmptySlotsItGoesIn() {
        assertEquals(DockDropPolicy.Outcome.INSERT,
            DockDropPolicy.outcome(false, false, false, false, true, false));
    }

    // ---- the ends of the row

    @Test
    public void aRestAtEitherEndTurnsTowardThatEnd() {
        assertEquals(-1, DockDropPolicy.edgeDirection(10f, 1000f, 60f, 1, 3));
        assertEquals(1, DockDropPolicy.edgeDirection(990f, 1000f, 60f, 1, 3));
        assertEquals(0, DockDropPolicy.edgeDirection(500f, 1000f, 60f, 1, 3));
    }

    /** A carried icon does not wrap round from the last page to the first, or back. */
    @Test
    public void theEndsOfTheRunDoNotTurn() {
        assertEquals(0, DockDropPolicy.edgeDirection(10f, 1000f, 60f, 0, 3));
        assertEquals(0, DockDropPolicy.edgeDirection(990f, 1000f, 60f, 2, 3));
        assertEquals(0, DockDropPolicy.edgeDirection(990f, 1000f, 60f, 0, 1));
    }

    @Test
    public void aRowTooNarrowForTwoBandsNeverTurns() {
        assertEquals(0, DockDropPolicy.edgeDirection(10f, 100f, 60f, 1, 3));
    }

    @Test
    public void theRowHoldsTwiceAsLongAsHomeOnItsBand() {
        assertEquals(700L, DockDropPolicy.EDGE_HOLD_MS);
        assertEquals(350L, WidgetPaneController.EDGE_FLIP_DELAY_MS);
        assertEquals(24, DockDropPolicy.EDGE_BAND_DP);
    }

    // ---- leaving the row for Home

    /** The row spans (0, 2000)–(1000, 2100); density 2, so the hand-over is 48 px out. */
    @Test
    public void aFingerWanderingJustOffTheRowStaysTheRows() {
        assertFalse(DockDropPolicy.handsOverToHome(500f, 1990f, 0f, 2000f, 1000f, 2100f, 2f));
        assertFalse(DockDropPolicy.handsOverToHome(500f, 2050f, 0f, 2000f, 1000f, 2100f, 2f));
    }

    @Test
    public void aFingerWellAboveTheRowIsHomes() {
        assertTrue(DockDropPolicy.handsOverToHome(500f, 1952f, 0f, 2000f, 1000f, 2100f, 2f));
        assertTrue(DockDropPolicy.handsOverToHome(500f, 2148f, 0f, 2000f, 1000f, 2100f, 2f));
    }

    @Test
    public void aFingerBesideTheRowStaysTheRows() {
        assertFalse(DockDropPolicy.handsOverToHome(1060f, 2050f, 0f, 2000f, 1000f, 2100f, 2f));
        assertFalse(DockDropPolicy.handsOverToHome(-80f, 2050f, 0f, 2000f, 1000f, 2100f, 2f));
    }

    // ---- Undo

    @Test
    public void undoPutsThePinBackWhereItWasOrAtTheEnd() {
        assertEquals(3, DockDropPolicy.restoreIndex(3, 9));
        assertEquals(2, DockDropPolicy.restoreIndex(3, 2));
        assertEquals(0, DockDropPolicy.restoreIndex(-1, 4));
    }

    // ---- a second finger turning the page while an icon is carried

    private static final float COMMIT_PX = 120f;
    private static final float DENSITY = 2f;

    @Test
    public void aSecondFingerSwipedLeftTurnsForwardAndRightBack() {
        assertEquals(1, DockDropPolicy.secondFingerTurn(-300f, 10f, 0f, COMMIT_PX, DENSITY, 1, 3));
        assertEquals(-1, DockDropPolicy.secondFingerTurn(300f, 10f, 0f, COMMIT_PX, DENSITY, 1, 3));
    }

    @Test
    public void aShortOrVerticalSwipeTurnsNothing() {
        assertEquals(0, DockDropPolicy.secondFingerTurn(-40f, 0f, 0f, COMMIT_PX, DENSITY, 1, 3));
        assertEquals(0, DockDropPolicy.secondFingerTurn(-300f, 400f, 0f, COMMIT_PX, DENSITY, 1, 3));
    }

    @Test
    public void aFlingTurnsOnAShortTravel() {
        assertEquals(1, DockDropPolicy.secondFingerTurn(-80f, 0f, -3000f, COMMIT_PX, DENSITY, 0, 2));
    }

    @Test
    public void itDoesNotWrapPastEitherEndOrTurnOnePage() {
        assertEquals(0, DockDropPolicy.secondFingerTurn(300f, 0f, 0f, COMMIT_PX, DENSITY, 0, 3));
        assertEquals(0, DockDropPolicy.secondFingerTurn(-300f, 0f, 0f, COMMIT_PX, DENSITY, 2, 3));
        assertEquals(0, DockDropPolicy.secondFingerTurn(-300f, 0f, 0f, COMMIT_PX, DENSITY, 0, 1));
    }
}
