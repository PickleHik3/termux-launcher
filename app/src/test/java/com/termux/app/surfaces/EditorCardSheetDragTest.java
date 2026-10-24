package com.termux.app.surfaces;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * Where a released header drag sends the card: a fling goes the way it was thrown, a slow
 * release to the nearer detent, and a drag well below the half detent puts the card down.
 */
public class EditorCardSheetDragTest {

    private static final float HALF_AT = 600f;
    private static final int HALF_PX = 700;
    private static final float FLING = 400f;

    private static int release(float at, float velocity) {
        return EditorCardSheet.releaseDetent(at, HALF_AT, HALF_PX, velocity, FLING);
    }

    @Test
    public void aFlingUpFromTheHalfDetentGoesFull() {
        assertEquals(EditorCardSheet.DETENT_FULL, release(560f, -2000f));
    }

    @Test
    public void aFlingDownFromFullGoesHalf() {
        assertEquals(EditorCardSheet.DETENT_HALF, release(80f, 2000f));
    }

    @Test
    public void aFlingDownFromTheHalfDetentPutsTheCardDown() {
        assertEquals(-1, release(650f, 2000f));
    }

    @Test
    public void aSlowReleaseGoesToTheNearerDetent() {
        assertEquals(EditorCardSheet.DETENT_FULL, release(200f, 0f));
        assertEquals(EditorCardSheet.DETENT_HALF, release(420f, 0f));
    }

    @Test
    public void farBelowTheHalfDetentPutsTheCardDown() {
        assertEquals(-1, release(HALF_AT + HALF_PX * 0.5f, -2000f));
    }
}
