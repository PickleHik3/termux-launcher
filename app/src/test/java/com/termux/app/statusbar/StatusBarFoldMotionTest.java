package com.termux.app.statusbar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * The fold as arithmetic: the finger's height, the release's time, and the stretch of the opening
 * the content is allowed to show over.
 */
public class StatusBarFoldMotionTest {

    private static final float EPS = 1e-4f;
    private static final int COMPACT = 28;
    private static final int OPEN = 96;

    @Test
    public void theFingerHasTheBarBetweenItsTwoForms() {
        assertEquals(28, StatusBarFoldMotion.heightForDrag(COMPACT, 0f, COMPACT, OPEN));
        assertEquals(58, StatusBarFoldMotion.heightForDrag(COMPACT, 30f, COMPACT, OPEN));
        // Past either form the bar stops; the finger can keep going.
        assertEquals(OPEN, StatusBarFoldMotion.heightForDrag(COMPACT, 300f, COMPACT, OPEN));
        assertEquals(COMPACT, StatusBarFoldMotion.heightForDrag(OPEN, -300f, COMPACT, OPEN));
        // Folding is the same arithmetic read the other way.
        assertEquals(66, StatusBarFoldMotion.heightForDrag(OPEN, -30f, COMPACT, OPEN));
    }

    @Test
    public void expansionIsTheHeightBetweenTheForms() {
        assertEquals(0f, StatusBarFoldMotion.expansion(COMPACT, COMPACT, OPEN), EPS);
        assertEquals(1f, StatusBarFoldMotion.expansion(OPEN, COMPACT, OPEN), EPS);
        assertEquals(0.5f, StatusBarFoldMotion.expansion(62, COMPACT, OPEN), EPS);
        assertEquals(0f, StatusBarFoldMotion.expansion(10, COMPACT, OPEN), EPS);
        // A bar whose two forms are one height (a minimal strip) is never open.
        assertEquals(0f, StatusBarFoldMotion.expansion(40, 40, 40), EPS);
    }

    @Test
    public void theContentShowsOnlyOverTheLastStretchOfTheOpening() {
        assertEquals(0f, StatusBarFoldMotion.contentAlpha(0f), EPS);
        assertEquals(0f, StatusBarFoldMotion.contentAlpha(0.3f), EPS);
        assertEquals(0f, StatusBarFoldMotion.contentAlpha(StatusBarFoldMotion.CONTENT_REVEAL_START),
            EPS);
        assertEquals(0.5f, StatusBarFoldMotion.contentAlpha(0.8f), EPS);
        assertEquals(1f, StatusBarFoldMotion.contentAlpha(1f), EPS);
        // Folding reads the same curve down: the content is gone before the bar is half closed.
        float previous = 2f;
        for (float expansion = 1f; expansion >= 0f; expansion -= 0.1f) {
            float alpha = StatusBarFoldMotion.contentAlpha(expansion);
            assertTrue(alpha <= previous);
            previous = alpha;
        }
        assertEquals(0f, StatusBarFoldMotion.contentAlpha(0.5f), EPS);
    }

    @Test
    public void theReleaseTakesTheWholeTimeForTheWholeWayAndLessForLess() {
        assertEquals(StatusBarFoldMotion.FULL_MS, StatusBarFoldMotion.durationMs(1f, 0f));
        assertEquals(StatusBarFoldMotion.FULL_MS / 2, StatusBarFoldMotion.durationMs(0.5f, 0f));
        // Never a snap, however little is left.
        assertEquals(StatusBarFoldMotion.MIN_MS, StatusBarFoldMotion.durationMs(0.05f, 0f));
        assertEquals(StatusBarFoldMotion.MIN_MS, StatusBarFoldMotion.durationMs(0f, 0f));
        // A finger moving away from the target adds nothing.
        assertEquals(StatusBarFoldMotion.FULL_MS, StatusBarFoldMotion.durationMs(1f, -4f));
    }

    @Test
    public void aFlickLandsSoonerAndASlowReleaseGlides() {
        // Half the way left, released at four whole ways a second: 125 ms by velocity, under the
        // 140 ms distance would give.
        assertEquals(125L, StatusBarFoldMotion.durationMs(0.5f, 4f));
        // A very fast flick is held to the minimum.
        assertEquals(StatusBarFoldMotion.MIN_MS, StatusBarFoldMotion.durationMs(0.5f, 40f));
        // A slow release is no slower than the distance says.
        assertEquals(140L, StatusBarFoldMotion.durationMs(0.5f, 0.5f));
        assertEquals(StatusBarFoldMotion.FULL_MS,
            StatusBarFoldMotion.durationMs(1f, Float.POSITIVE_INFINITY));
    }
}
