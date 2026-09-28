package com.termux.app.editorshell;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.termux.app.editorshell.EditorShellSheetPolicy.Snap;

import org.junit.Test;

/**
 * Where either editor's card settles once a pull lets go — the two heights it rests at, and the
 * firm pull past its resting height that closes it — and which drags are the sheet's rather than
 * the scrolling body's.
 */
public class EditorShellSheetPolicyTest {

    /** A pull's travel on a phone in portrait, in px; the card's own height is a little more. */
    private static final float TRAVEL = 400f;
    private static final float HEIGHT = 1800f;
    private static final float FLING = 2400f;
    private static final float DISMISS = 450f;

    private static Snap settle(float offset, float velocityUp) {
        return EditorShellSheetPolicy.settle(offset, TRAVEL, velocityUp, FLING, DISMISS);
    }

    @Test
    public void aSlowPullGoesToWhicheverHeightIsNearer() {
        assertEquals(Snap.COLLAPSED, settle(0f, 0f));
        assertEquals(Snap.COLLAPSED, settle(199f, 0f));
        assertEquals(Snap.EXPANDED, settle(200f, 0f));
        assertEquals(Snap.EXPANDED, settle(TRAVEL, 0f));
        // A slow drift the wrong way does not count as a flick.
        assertEquals(Snap.EXPANDED, settle(300f, -FLING / 2f));
        assertEquals(Snap.COLLAPSED, settle(100f, FLING / 2f));
    }

    @Test
    public void aFlickDecidesTheDirectionOnItsOwn() {
        assertEquals("flicked up from rest", Snap.EXPANDED, settle(20f, FLING));
        assertEquals("flicked down from the top", Snap.COLLAPSED, settle(TRAVEL - 20f, -FLING));
        assertEquals("a downward flick above rest never closes: rest is in the way",
            Snap.COLLAPSED, settle(10f, -3f * FLING));
    }

    @Test
    public void aFirmPullBelowRestCloses() {
        assertEquals("a nudge below rest springs back", Snap.COLLAPSED, settle(-100f, 0f));
        assertEquals("just short of the line still springs back",
            Snap.COLLAPSED, settle(-DISMISS + 1f, 0f));
        assertEquals("past it, the card is dismissed", Snap.DISMISS, settle(-DISMISS, 0f));
        assertEquals("and a downward flick below rest is firm enough on its own",
            Snap.DISMISS, settle(-40f, -FLING));
        assertEquals("but not an upward one", Snap.COLLAPSED, settle(-40f, FLING));
    }

    @Test
    public void aCardWithNoTravelOnlyRestsOrCloses() {
        // A landscape screen: the card already stands the short edge.
        assertEquals(Snap.COLLAPSED, EditorShellSheetPolicy.settle(300f, 0f, FLING, FLING, DISMISS));
        assertEquals(Snap.COLLAPSED, EditorShellSheetPolicy.settle(0f, 0f, FLING, FLING, DISMISS));
        assertEquals(Snap.DISMISS, EditorShellSheetPolicy.settle(-DISMISS, 0f, 0f, FLING, DISMISS));
        assertEquals(Snap.COLLAPSED, EditorShellSheetPolicy.toggled(0f, 0f));
    }

    @Test
    public void theOffsetIsClampedToTheTravelAndTheCardsHeight() {
        assertEquals(TRAVEL, EditorShellSheetPolicy.clampOffsetPx(9000f, TRAVEL, HEIGHT), 0f);
        assertEquals(-HEIGHT, EditorShellSheetPolicy.clampOffsetPx(-9000f, TRAVEL, HEIGHT), 0f);
        assertEquals(150f, EditorShellSheetPolicy.clampOffsetPx(150f, TRAVEL, HEIGHT), 0f);
        assertEquals("no travel means no growing", 0f,
            EditorShellSheetPolicy.clampOffsetPx(150f, 0f, HEIGHT), 0f);
    }

    @Test
    public void theTwoChannelsReadOffTheOneOffset() {
        assertEquals(0.5f, EditorShellSheetPolicy.expansionOf(200f, TRAVEL), 0.0001f);
        assertEquals(1f, EditorShellSheetPolicy.expansionOf(900f, TRAVEL), 0.0001f);
        assertEquals("below rest the card is not grown at all", 0f,
            EditorShellSheetPolicy.expansionOf(-120f, TRAVEL), 0f);
        assertEquals("nor with nowhere to grow", 0f, EditorShellSheetPolicy.expansionOf(120f, 0f), 0f);
        assertEquals(120f, EditorShellSheetPolicy.overshootOf(-120f), 0f);
        assertEquals("above rest the card is not pushed down at all", 0f,
            EditorShellSheetPolicy.overshootOf(120f), 0f);
    }

    @Test
    public void aTapOnTheHandleGoesToTheFarEnd() {
        assertEquals(Snap.EXPANDED, EditorShellSheetPolicy.toggled(0f, TRAVEL));
        assertEquals(Snap.COLLAPSED, EditorShellSheetPolicy.toggled(1f, TRAVEL));
        assertEquals("from half way up it goes on up", Snap.EXPANDED,
            EditorShellSheetPolicy.toggled(0.49f, TRAVEL));
    }

    // ------------------------------------------------------------------------- whose drag it is

    @Test
    public void aDragOnTheHeaderOrTheHandleIsAlwaysTheSheets() {
        assertTrue("up", EditorShellSheetPolicy.claimsDrag(false, 30f, 0f, TRAVEL, false));
        assertTrue("down, with the list scrolled",
            EditorShellSheetPolicy.claimsDrag(false, -30f, 1f, TRAVEL, false));
        assertTrue("and on a card with nowhere to grow, which can still be pulled down to close",
            EditorShellSheetPolicy.claimsDrag(false, -30f, 0f, 0f, true));
    }

    @Test
    public void aCollapsedSheetGrowsBeforeItsBodyScrolls() {
        assertTrue("pulled up at rest: the card grows first",
            EditorShellSheetPolicy.claimsDrag(true, 30f, 0f, TRAVEL, true));
        assertFalse("pulled up all the way: now the list scrolls",
            EditorShellSheetPolicy.claimsDrag(true, 30f, 1f, TRAVEL, true));
        assertFalse("a card whose rows already fit has nothing to grow into",
            EditorShellSheetPolicy.claimsDrag(true, 30f, 0f, 0f, true));
    }

    @Test
    public void aBodyAtItsTopHandsADownwardDragToTheSheet() {
        assertTrue("at the top of the list: the card comes down",
            EditorShellSheetPolicy.claimsDrag(true, -30f, 1f, TRAVEL, true));
        assertFalse("part way down the list: the list scrolls back first",
            EditorShellSheetPolicy.claimsDrag(true, -30f, 1f, TRAVEL, false));
        assertFalse("no movement is nobody's drag",
            EditorShellSheetPolicy.claimsDrag(false, 0f, 0f, TRAVEL, true));
    }

    // ------------------------------------------------------------------------------ the heights

    @Test
    public void aLongCardRestsAtItsRestingHeightAndPullsUpToTheRoom() {
        // pong's Layout card: rows and a miniature well past the screen.
        int natural = 3200;
        assertEquals(1930, EditorShellSheetPolicy.restHeightPx(natural, 1930, 2300, 2380));
        assertEquals(2300, EditorShellSheetPolicy.expandedHeightPx(natural, 1930, 2300, 2380));
        assertEquals("half way is half way", 1930 + 185,
            EditorShellSheetPolicy.heightPx(natural, 1930, 2300, 2380, 0.5f));
        assertEquals("a pull past the ends is clamped", 2300,
            EditorShellSheetPolicy.heightPx(natural, 1930, 2300, 2380, 4f));
        assertEquals(1930, EditorShellSheetPolicy.heightPx(natural, 1930, 2300, 2380, -1f));
    }

    @Test
    public void aShortCardIsAsTallAsWhatItHoldsAndHasNoTravel() {
        assertEquals(900, EditorShellSheetPolicy.restHeightPx(900, 1930, 2300, 2380));
        assertEquals(900, EditorShellSheetPolicy.expandedHeightPx(900, 1930, 2300, 2380));
        assertEquals("between the two, the pull goes only as far as the content does",
            2100, EditorShellSheetPolicy.expandedHeightPx(2100, 1930, 2300, 2380));
    }

    @Test
    public void theParentsRoomBoundsBothHeights() {
        // A landscape screen, where the resting height asked for is the whole screen.
        assertEquals(1000, EditorShellSheetPolicy.restHeightPx(3000, 1080, 1080, 1000));
        assertEquals(1000, EditorShellSheetPolicy.expandedHeightPx(3000, 1080, 1080, 1000));
        assertEquals("no resting height asked for is the whole room", 1000,
            EditorShellSheetPolicy.restHeightPx(3000, 0, 0, 1000));
        assertEquals("a pulled-up height under the resting one leaves the card at rest", 800,
            EditorShellSheetPolicy.expandedHeightPx(3000, 1200, 800, 1000));
    }
}
