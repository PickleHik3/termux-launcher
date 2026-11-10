package com.termux.app.material;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** The two modal sheets' shared open and close numbers. */
public class ModalSheetMotionTest {
    @Test public void theSheetsOpenAndCloseIn267MsWith28DpTopCorners() {
        assertEquals(267L, ModalSheetMotion.OPEN_CLOSE_MS);
        assertEquals(28f, ModalSheetMotion.TOP_CORNER_DP, 0f);
    }

    @Test public void theDimIsFullAtRestAndGoneWhenTheCardIsAway() {
        assertEquals(1f, ModalSheetMotion.scrimAlpha(0f, 800f), 0f);
        assertEquals(0f, ModalSheetMotion.scrimAlpha(800f, 800f), 0f);
        assertEquals(0.5f, ModalSheetMotion.scrimAlpha(400f, 800f), 1e-6f);
    }

    @Test public void theDimClampsOutsideTheTravel() {
        assertEquals(1f, ModalSheetMotion.scrimAlpha(-30f, 800f), 0f);
        assertEquals(0f, ModalSheetMotion.scrimAlpha(1200f, 800f), 0f);
        assertEquals(1f, ModalSheetMotion.scrimAlpha(0f, 0f), 0f);
        assertEquals(1f, ModalSheetMotion.scrimAlpha(0f, -5f), 0f);
    }

    @Test public void aSlideCaughtPartWayContinuesAtTheSameSpeed() {
        assertEquals(267L, ModalSheetMotion.remainingDuration(1f, true));
        assertEquals(0L, ModalSheetMotion.remainingDuration(0f, true));
        assertEquals(267L, ModalSheetMotion.remainingDuration(0f, false));
        assertEquals(0L, ModalSheetMotion.remainingDuration(1f, false));
        assertEquals(Math.round(267 * 0.25f), ModalSheetMotion.remainingDuration(0.25f, true));
        assertEquals(Math.round(267 * 0.75f), ModalSheetMotion.remainingDuration(0.25f, false));
    }

    @Test public void aFractionOutsideZeroToOneNeverGivesANegativeDuration() {
        assertEquals(0L, ModalSheetMotion.remainingDuration(-2f, true));
        assertEquals(267L, ModalSheetMotion.remainingDuration(3f, true));
        assertEquals(267L, ModalSheetMotion.remainingDuration(-2f, false));
        assertEquals(0L, ModalSheetMotion.remainingDuration(3f, false));
    }
}
