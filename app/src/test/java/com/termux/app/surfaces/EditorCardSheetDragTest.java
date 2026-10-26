package com.termux.app.surfaces;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * Where a released header drag sends the card, among its three detents (full, half, peek): a
 * fling goes to the next detent the way it was thrown, a slow release to the nearest, and nothing
 * puts the card down. A tap on the header goes up a detent, and from full back to peek. The peek
 * detent is the header over the navigation inset.
 */
public class EditorCardSheetDragTest {

    private static final float HALF_AT = 600f;
    private static final float PEEK_AT = 1200f;
    private static final float FLING = 400f;

    private static int release(float at, float velocity) {
        return EditorCardSheet.releaseDetent(at, HALF_AT, PEEK_AT, velocity, FLING);
    }

    @Test
    public void aFlingUpFromTheHalfDetentGoesFull() {
        assertEquals(EditorCardSheet.DETENT_FULL, release(560f, -2000f));
    }

    @Test
    public void aFlingUpFromPeekStopsAtHalf() {
        assertEquals(EditorCardSheet.DETENT_HALF, release(1150f, -2000f));
    }

    @Test
    public void aFlingDownFromFullGoesHalf() {
        assertEquals(EditorCardSheet.DETENT_HALF, release(80f, 2000f));
    }

    @Test
    public void aFlingDownFromTheHalfDetentGoesToPeekNotOffScreen() {
        assertEquals(EditorCardSheet.DETENT_PEEK, release(650f, 2000f));
        assertEquals(EditorCardSheet.DETENT_PEEK, release(PEEK_AT, 5000f));
    }

    @Test
    public void aSlowReleaseGoesToTheNearestDetent() {
        assertEquals(EditorCardSheet.DETENT_FULL, release(200f, 0f));
        assertEquals(EditorCardSheet.DETENT_HALF, release(420f, 0f));
        assertEquals(EditorCardSheet.DETENT_HALF, release(880f, 0f));
        assertEquals(EditorCardSheet.DETENT_PEEK, release(1000f, 0f));
    }

    @Test
    public void aTapGoesUpADetentAndFromFullBackToPeek() {
        assertEquals(EditorCardSheet.DETENT_HALF,
            EditorCardSheet.tapDetent(EditorCardSheet.DETENT_PEEK));
        assertEquals(EditorCardSheet.DETENT_FULL,
            EditorCardSheet.tapDetent(EditorCardSheet.DETENT_HALF));
        assertEquals(EditorCardSheet.DETENT_PEEK,
            EditorCardSheet.tapDetent(EditorCardSheet.DETENT_FULL));
    }

    @Test
    public void peekIsTheHeaderOverTheInsetNeverTallerThanHalf() {
        assertEquals(72 + 24, EditorCardSheet.peekHeightPx(72, 24, 400));
        assertEquals(80, EditorCardSheet.peekHeightPx(72, 24, 80));
        assertEquals(EditorCardSheet.GRAB_DP + EditorCardSheet.HEADER_ROW_DP,
            EditorCardSheet.HEADER_DP);
        assertEquals("the grab zone is 24dp tall", 24, EditorCardSheet.GRAB_DP);
    }
}
