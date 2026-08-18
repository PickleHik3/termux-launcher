package com.termux.app.launcher.drawer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Column geometry.
 *
 * <p>Two reference tracks, because the clamps are the whole content of this class: the 1080p phone at
 * 3x with a ~1500px track, and the 1440p device at 3.5x with the ~2100px one the plan calls the only
 * real test of the metrics — 27 letters over a track that tall would otherwise be given a 26dp slot
 * each and read as a heading list rather than a scrubber.
 */
public class AppDrawerRopeMetricsTest {

    private static final float EPS = 1e-3f;

    private static final int ALL_LETTERS = AppDrawerRopeModel.MAX_LETTERS;
    private static final float DENSITY = 3f;
    private static final float TRACK_HEIGHT = 1500f;

    private final AppDrawerRopeMetrics mPhone =
        AppDrawerRopeMetrics.resolve(TRACK_HEIGHT, ALL_LETTERS, DENSITY);

    @Test
    public void columnWidthIsTheOnlyNumberTheGridNeedsBeforeAnyLettersExist() {
        assertEquals(AppDrawerRopeMetrics.COLUMN_WIDTH_DP * DENSITY,
            AppDrawerRopeMetrics.resolveColumnWidthPx(DENSITY), EPS);
        assertEquals(mPhone.columnWidthPx, AppDrawerRopeMetrics.resolveColumnWidthPx(DENSITY), EPS);
        // A density that has not been resolved yet must not produce a zero-width strip.
        assertTrue(AppDrawerRopeMetrics.resolveColumnWidthPx(0f) > 0f);
    }

    @Test
    public void aPhoneTrackFillsItselfAndKeepsTheGlyphInsideItsSlot() {
        float inset = AppDrawerRopeMetrics.TRACK_INSET_DP * DENSITY;
        assertEquals(ALL_LETTERS, mPhone.letterCount);
        assertEquals(inset, mPhone.trackTopPx, EPS);
        assertEquals(TRACK_HEIGHT - (inset * 2f), mPhone.trackHeightPx, EPS);
        assertEquals((TRACK_HEIGHT - (inset * 2f)) / ALL_LETTERS, mPhone.slotHeightPx, EPS);
        assertEquals(mPhone.slotHeightPx * AppDrawerRopeMetrics.GLYPH_SLOT_FRACTION,
            mPhone.glyphTextSizePx, EPS);
        assertTrue(mPhone.glyphTextSizePx < mPhone.slotHeightPx);
        assertEquals(AppDrawerRopeMetrics.ENTRY_OFFSET_DP * DENSITY, mPhone.entryOffsetPx, EPS);
    }

    @Test
    public void aTallTrackCapsTheSlotAndTheGlyphAndCentresWhatIsLeft() {
        // 1440p, 27 letters over 2100px: the glyph ceiling is the binding constraint.
        AppDrawerRopeMetrics tall = AppDrawerRopeMetrics.resolve(2100f, ALL_LETTERS, 3.5f);
        assertEquals(AppDrawerRopeMetrics.MAX_GLYPH_DP * 3.5f, tall.glyphTextSizePx, EPS);
        assertTrue(tall.slotHeightPx > tall.glyphTextSizePx);
        assertTrue(tall.slotHeightPx <= AppDrawerRopeMetrics.MAX_SLOT_DP * 3.5f);
        assertTrue(tall.trackTopPx + tall.trackHeightPx <= 2100f);

        // A tablet-height track hits the slot ceiling instead, and the shorter track is then centred
        // in the space rather than stretched or left at the top.
        AppDrawerRopeMetrics tablet = AppDrawerRopeMetrics.resolve(2600f, ALL_LETTERS, 2f);
        float inset = AppDrawerRopeMetrics.TRACK_INSET_DP * 2f;
        assertEquals(AppDrawerRopeMetrics.MAX_SLOT_DP * 2f, tablet.slotHeightPx, EPS);
        float usable = 2600f - (inset * 2f);
        assertEquals(inset + ((usable - tablet.trackHeightPx) * 0.5f), tablet.trackTopPx, EPS);
        float bottomGap = 2600f - (tablet.trackTopPx + tablet.trackHeightPx);
        assertEquals(tablet.trackTopPx, bottomGap, EPS);
    }

    @Test
    public void aShortTrackShrinksSlotsRatherThanOverflowingOrOverlapping() {
        // A landscape or split-screen plane: 27 letters over 400px is honestly cramped, and the only
        // wrong answers are letters drawn on top of each other or letters drawn off the track.
        AppDrawerRopeMetrics shortTrack = AppDrawerRopeMetrics.resolve(400f, ALL_LETTERS, DENSITY);
        assertTrue(shortTrack.trackHeightPx <= 400f);
        assertTrue(shortTrack.trackTopPx >= 0f);
        assertTrue(shortTrack.trackTopPx + shortTrack.trackHeightPx <= 400f);
        assertTrue(shortTrack.glyphTextSizePx <= shortTrack.slotHeightPx);
        assertTrue(shortTrack.glyphTextSizePx > 0f);
        assertEquals(shortTrack.centerYForIndex(0) + (26f * shortTrack.slotHeightPx),
            shortTrack.centerYForIndex(26), EPS);

        // Degenerate inputs degrade instead of poisoning the column with NaN.
        AppDrawerRopeMetrics unmeasured = AppDrawerRopeMetrics.resolve(0f, ALL_LETTERS, DENSITY);
        assertEquals(0f, unmeasured.slotHeightPx, 0f);
        assertEquals(0f, unmeasured.glyphTextSizePx, 0f);
        assertEquals(0, unmeasured.indexForY(500f, -1));
        AppDrawerRopeMetrics noDensity = AppDrawerRopeMetrics.resolve(TRACK_HEIGHT, ALL_LETTERS, 0f);
        assertFalse(Float.isNaN(noDensity.slotHeightPx));
        assertTrue(noDensity.glyphTextSizePx > 0f);
    }

    @Test
    public void letterCountIsClampedToTheChainAndNeverZero() {
        assertEquals(ALL_LETTERS, AppDrawerRopeMetrics.resolve(TRACK_HEIGHT, 40, DENSITY).letterCount);
        assertEquals(1, AppDrawerRopeMetrics.resolve(TRACK_HEIGHT, 0, DENSITY).letterCount);
        assertEquals(1, AppDrawerRopeMetrics.resolve(TRACK_HEIGHT, -3, DENSITY).letterCount);
        // Fewer letters means taller slots up to the ceiling, not a stretched track.
        AppDrawerRopeMetrics few = AppDrawerRopeMetrics.resolve(TRACK_HEIGHT, 6, DENSITY);
        assertEquals(AppDrawerRopeMetrics.MAX_SLOT_DP * DENSITY, few.slotHeightPx, EPS);
        assertEquals(6, few.letterCount);
    }

    @Test
    public void centresWalkTheTrackAndClampAtBothEnds() {
        assertEquals(mPhone.trackTopPx + (mPhone.slotHeightPx * 0.5f),
            mPhone.centerYForIndex(0), EPS);
        assertEquals(mPhone.trackTopPx + mPhone.trackHeightPx - (mPhone.slotHeightPx * 0.5f),
            mPhone.centerYForIndex(ALL_LETTERS - 1), EPS);
        assertEquals(mPhone.centerYForIndex(0), mPhone.centerYForIndex(-5), EPS);
        assertEquals(mPhone.centerYForIndex(ALL_LETTERS - 1),
            mPhone.centerYForIndex(ALL_LETTERS + 5), EPS);
    }

    @Test
    public void indexForYIsStickyAcrossASlotBoundary() {
        float slot = mPhone.slotHeightPx;
        float hysteresis = slot * AppDrawerRopeMetrics.LETTER_SLOT_HYSTERESIS_RATIO;
        float boundary = mPhone.trackTopPx + (5f * slot);

        // With nothing to be sticky about, the raw slot wins.
        assertEquals(5, mPhone.indexForY(boundary + 1f, -1));
        assertEquals(4, mPhone.indexForY(boundary - 1f, -1));

        // Moving down: a finger just over the boundary keeps the letter it had…
        assertEquals(4, mPhone.indexForY(boundary + 1f, 4));
        assertEquals(4, mPhone.indexForY(boundary + hysteresis - 0.5f, 4));
        // …until it commits.
        assertEquals(5, mPhone.indexForY(boundary + hysteresis + 0.5f, 4));

        // And symmetrically moving back up.
        assertEquals(5, mPhone.indexForY(boundary - 1f, 5));
        assertEquals(5, mPhone.indexForY(boundary - hysteresis + 0.5f, 5));
        assertEquals(4, mPhone.indexForY(boundary - hysteresis - 0.5f, 4 + 1));

        // A jump of more than one slot is a finger that moved, not a finger on a boundary.
        assertEquals(20, mPhone.indexForY(mPhone.centerYForIndex(20), 4));
    }

    @Test
    public void aScrubOffEitherEndStaysOnTheEndLetter() {
        assertEquals(0, mPhone.indexForY(-2000f, -1));
        assertEquals(0, mPhone.indexForY(mPhone.trackTopPx - 1f, 0));
        assertEquals(ALL_LETTERS - 1, mPhone.indexForY(5000f, -1));
        assertEquals(ALL_LETTERS - 1,
            mPhone.indexForY(5000f, ALL_LETTERS - 1));
        // A stale previous index from a shorter letter set cannot leak out of range.
        assertEquals(3, mPhone.indexForY(mPhone.centerYForIndex(3), ALL_LETTERS + 4));
    }

    @Test
    public void theAnchorArrivesHomeBeforeTheAlphaAndBothStartAtTheEntryPoint() {
        // The column is the last thing to appear and the anchor is home before p = 1, so the tail is
        // still settling when the plane finishes.
        assertEquals(mPhone.entryOffsetPx, mPhone.anchorPx(0f), EPS);
        assertEquals(mPhone.entryOffsetPx, mPhone.anchorPx(AppDrawerRopeMetrics.COLUMN_IN_START), EPS);
        assertEquals(0f, mPhone.anchorPx(AppDrawerRopeMetrics.COLUMN_IN_END), EPS);
        assertEquals(0f, mPhone.anchorPx(1f), EPS);
        assertEquals(mPhone.entryOffsetPx * 0.5f, mPhone.anchorPx(
            (AppDrawerRopeMetrics.COLUMN_IN_START + AppDrawerRopeMetrics.COLUMN_IN_END) * 0.5f), EPS);
        assertTrue(AppDrawerRopeMetrics.COLUMN_IN_END < 1f);

        // Alpha is 0 for the whole first third — which is what makes the last stretch of a close a
        // rope nobody sees — and is fully opaque before the anchor lands, so the settle is visible.
        assertEquals(0f, AppDrawerRopeMetrics.alpha(0f), EPS);
        assertEquals(0f, AppDrawerRopeMetrics.alpha(AppDrawerRopeMetrics.COLUMN_IN_START), EPS);
        assertEquals(1f, AppDrawerRopeMetrics.alpha(AppDrawerRopeMetrics.COLUMN_ALPHA_END), EPS);
        assertEquals(1f, AppDrawerRopeMetrics.alpha(1f), EPS);
        assertTrue(AppDrawerRopeMetrics.COLUMN_ALPHA_END < AppDrawerRopeMetrics.COLUMN_IN_END);
        assertTrue(AppDrawerRopeMetrics.alpha(0.5f) > 0f
            && AppDrawerRopeMetrics.alpha(0.5f) < 1f);
    }
}
