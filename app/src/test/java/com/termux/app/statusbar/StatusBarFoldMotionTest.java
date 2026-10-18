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

    // ---------------------------------------------------------------- three stops

    private static final int CAPSULE_TRAVEL = 40;
    private static final int FOLD_TRAVEL = OPEN - COMPACT;

    @Test
    public void theThreeFormsStandOnOneAxisOfFingerTravel() {
        assertEquals(0f, StatusBarFoldMotion.stopPx(StatusBarFormPolicy.Form.CAPSULE,
            CAPSULE_TRAVEL, FOLD_TRAVEL), EPS);
        assertEquals(40f, StatusBarFoldMotion.stopPx(StatusBarFormPolicy.Form.COMPACT,
            CAPSULE_TRAVEL, FOLD_TRAVEL), EPS);
        assertEquals(108f, StatusBarFoldMotion.stopPx(StatusBarFormPolicy.Form.EXPANDED,
            CAPSULE_TRAVEL, FOLD_TRAVEL), EPS);
        // One drag from the capsule crosses compact and stops at expanded.
        assertEquals(108f, StatusBarFoldMotion.positionForDrag(0f, 500f, 0f, 108f), EPS);
        assertEquals(25f, StatusBarFoldMotion.positionForDrag(40f, -15f, 0f, 108f), EPS);
        assertEquals(0f, StatusBarFoldMotion.positionForDrag(40f, -90f, 0f, 108f), EPS);
    }

    @Test
    public void theCapsuleMorphsOverItsOwnStretchAndTheHeightOverTheRest() {
        assertEquals(1f, StatusBarFoldMotion.capsuleProgress(0f, CAPSULE_TRAVEL), EPS);
        assertEquals(0.5f, StatusBarFoldMotion.capsuleProgress(20f, CAPSULE_TRAVEL), EPS);
        assertEquals(0f, StatusBarFoldMotion.capsuleProgress(40f, CAPSULE_TRAVEL), EPS);
        assertEquals(0f, StatusBarFoldMotion.capsuleProgress(90f, CAPSULE_TRAVEL), EPS);
        // From the capsule, the height only starts moving once the finger is past compact.
        assertEquals(0f, StatusBarFoldMotion.foldTowardOpenPx(0f, 30f, CAPSULE_TRAVEL), EPS);
        assertEquals(20f, StatusBarFoldMotion.foldTowardOpenPx(0f, 60f, CAPSULE_TRAVEL), EPS);
        // From expanded down into the capsule's stretch, the height lands at compact.
        assertEquals(-68f, StatusBarFoldMotion.foldTowardOpenPx(108f, 10f, CAPSULE_TRAVEL), EPS);
        assertEquals(COMPACT, StatusBarFoldMotion.heightForDrag(OPEN, -68f, COMPACT, OPEN));
    }

    @Test
    public void theNearestStopWinsAndATieGoesUp() {
        StatusBarFormPolicy.Form capsule = StatusBarFormPolicy.Form.CAPSULE;
        StatusBarFormPolicy.Form expanded = StatusBarFormPolicy.Form.EXPANDED;
        assertEquals(capsule, StatusBarFoldMotion.nearest(19f, CAPSULE_TRAVEL, FOLD_TRAVEL,
            capsule, expanded));
        assertEquals(StatusBarFormPolicy.Form.COMPACT, StatusBarFoldMotion.nearest(20f,
            CAPSULE_TRAVEL, FOLD_TRAVEL, capsule, expanded));
        // Halfway between compact (40) and expanded (108) is 74.
        assertEquals(StatusBarFormPolicy.Form.COMPACT, StatusBarFoldMotion.nearest(73f,
            CAPSULE_TRAVEL, FOLD_TRAVEL, capsule, expanded));
        assertEquals(expanded, StatusBarFoldMotion.nearest(74f, CAPSULE_TRAVEL, FOLD_TRAVEL,
            capsule, expanded));
        // A bar without a capsule never lands in it.
        assertEquals(StatusBarFormPolicy.Form.COMPACT, StatusBarFoldMotion.nearest(0f,
            CAPSULE_TRAVEL, FOLD_TRAVEL, StatusBarFormPolicy.Form.COMPACT, expanded));
    }

    @Test
    public void theBarsContentLeavesBeforeTheCapsulesArrives() {
        assertEquals(1f, StatusBarFoldMotion.barContentAlpha(0f), EPS);
        assertEquals(0.5f, StatusBarFoldMotion.barContentAlpha(0.2f), EPS);
        assertEquals(0f, StatusBarFoldMotion.barContentAlpha(0.4f), EPS);
        assertEquals(0f, StatusBarFoldMotion.capsuleContentAlpha(0.6f), EPS);
        assertEquals(0.5f, StatusBarFoldMotion.capsuleContentAlpha(0.8f), EPS);
        assertEquals(1f, StatusBarFoldMotion.capsuleContentAlpha(1f), EPS);
    }
}
