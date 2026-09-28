package com.termux.app.layouteditor;

import static org.junit.Assert.assertEquals;

import com.termux.app.layouteditor.LayoutEditorSheet.Snap;

import org.junit.Test;

/**
 * Where the Layout editor's card settles once a pull on its handle lets go: the two heights it
 * rests at, and the firm pull past its resting height that closes it.
 */
public class LayoutEditorSheetTest {

    /** A pull's travel on a phone in portrait, in px; the card's own height is a little more. */
    private static final float TRAVEL = 400f;
    private static final float HEIGHT = 1800f;
    private static final float FLING = 2400f;
    private static final float DISMISS = 450f;

    private static Snap settle(float offset, float velocityUp) {
        return LayoutEditorSheet.settle(offset, TRAVEL, velocityUp, FLING, DISMISS);
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
        assertEquals(Snap.COLLAPSED, LayoutEditorSheet.settle(300f, 0f, FLING, FLING, DISMISS));
        assertEquals(Snap.COLLAPSED, LayoutEditorSheet.settle(0f, 0f, FLING, FLING, DISMISS));
        assertEquals(Snap.DISMISS, LayoutEditorSheet.settle(-DISMISS, 0f, 0f, FLING, DISMISS));
        assertEquals(Snap.COLLAPSED, LayoutEditorSheet.toggled(0f, 0f));
    }

    @Test
    public void theOffsetIsClampedToTheTravelAndTheCardsHeight() {
        assertEquals(TRAVEL, LayoutEditorSheet.clampOffsetPx(9000f, TRAVEL, HEIGHT), 0f);
        assertEquals(-HEIGHT, LayoutEditorSheet.clampOffsetPx(-9000f, TRAVEL, HEIGHT), 0f);
        assertEquals(150f, LayoutEditorSheet.clampOffsetPx(150f, TRAVEL, HEIGHT), 0f);
        assertEquals("no travel means no growing", 0f,
            LayoutEditorSheet.clampOffsetPx(150f, 0f, HEIGHT), 0f);
    }

    @Test
    public void theTwoChannelsReadOffTheOneOffset() {
        assertEquals(0.5f, LayoutEditorSheet.expansionOf(200f, TRAVEL), 0.0001f);
        assertEquals(1f, LayoutEditorSheet.expansionOf(900f, TRAVEL), 0.0001f);
        assertEquals("below rest the card is not grown at all", 0f,
            LayoutEditorSheet.expansionOf(-120f, TRAVEL), 0f);
        assertEquals("nor with nowhere to grow", 0f, LayoutEditorSheet.expansionOf(120f, 0f), 0f);
        assertEquals(120f, LayoutEditorSheet.overshootOf(-120f), 0f);
        assertEquals("above rest the card is not pushed down at all", 0f,
            LayoutEditorSheet.overshootOf(120f), 0f);
    }

    @Test
    public void aTapOnTheHandleGoesToTheFarEnd() {
        assertEquals(Snap.EXPANDED, LayoutEditorSheet.toggled(0f, TRAVEL));
        assertEquals(Snap.COLLAPSED, LayoutEditorSheet.toggled(1f, TRAVEL));
        assertEquals("from half way up it goes on up", Snap.EXPANDED,
            LayoutEditorSheet.toggled(0.49f, TRAVEL));
    }
}
