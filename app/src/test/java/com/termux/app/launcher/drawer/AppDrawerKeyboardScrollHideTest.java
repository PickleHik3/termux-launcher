package com.termux.app.launcher.drawer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AppDrawerKeyboardScrollHideTest {

    private static final float THRESHOLD = 60f;
    private static final float TRAVEL = 800f;

    /** A nudge of the list under the threshold leaves the keyboard where it is. */
    @Test
    public void underTheThresholdTheKeyboardStaysUp() {
        assertEquals(0f, AppDrawerKeyboardScrollHide.translationPx(0f, THRESHOLD, TRAVEL), 0f);
        assertEquals(0f, AppDrawerKeyboardScrollHide.translationPx(60f, THRESHOLD, TRAVEL), 0f);
        assertEquals(0f, AppDrawerKeyboardScrollHide.hiddenFraction(40f, THRESHOLD, TRAVEL), 0f);
    }

    /** Past it, the keyboard moves one pixel down for every pixel the list scrolls. */
    @Test
    public void pastTheThresholdTheKeyboardFollowsTheScrollOneToOne() {
        assertEquals(1f, AppDrawerKeyboardScrollHide.translationPx(61f, THRESHOLD, TRAVEL), 0f);
        assertEquals(200f, AppDrawerKeyboardScrollHide.translationPx(260f, THRESHOLD, TRAVEL), 0f);
        assertEquals(0.25f, AppDrawerKeyboardScrollHide.hiddenFraction(260f, THRESHOLD, TRAVEL),
            1e-6f);
    }

    /** It never goes further than its own travel, however far the list goes on. */
    @Test
    public void theTranslationStopsAtTheKeyboardsTravel() {
        assertEquals(TRAVEL, AppDrawerKeyboardScrollHide.translationPx(5000f, THRESHOLD, TRAVEL), 0f);
        assertEquals(1f, AppDrawerKeyboardScrollHide.hiddenFraction(5000f, THRESHOLD, TRAVEL), 0f);
    }

    /** The system keyboard cannot be slid: it is either up or, past the threshold, gone. */
    @Test
    public void aKeyboardWithNoTravelGoesAtTheThreshold() {
        assertEquals(0f, AppDrawerKeyboardScrollHide.hiddenFraction(60f, THRESHOLD, 0f), 0f);
        assertEquals(1f, AppDrawerKeyboardScrollHide.hiddenFraction(61f, THRESHOLD, 0f), 0f);
        assertEquals(0f, AppDrawerKeyboardScrollHide.translationPx(500f, THRESHOLD, 0f), 0f);
    }

    /** Scrolling back up adds nothing and takes nothing away: the keyboard does not come back. */
    @Test
    public void onlyScrollingDownTheListCounts() {
        float scrolled = AppDrawerKeyboardScrollHide.accumulate(0f, 100);
        scrolled = AppDrawerKeyboardScrollHide.accumulate(scrolled, -80);
        scrolled = AppDrawerKeyboardScrollHide.accumulate(scrolled, 0);
        assertEquals(100f, scrolled, 0f);
        assertEquals(140f, AppDrawerKeyboardScrollHide.accumulate(scrolled, 40), 0f);
    }

    /** A scroll that stops halfway or further finishes the dismissal; less springs it back. */
    @Test
    public void aScrollThatStopsPartWayDecidesByHowFarTheKeyboardWent() {
        assertTrue(AppDrawerKeyboardScrollHide.dismissesAtRest(0.5f));
        assertTrue(AppDrawerKeyboardScrollHide.dismissesAtRest(1f));
        assertFalse(AppDrawerKeyboardScrollHide.dismissesAtRest(0.49f));
        assertFalse(AppDrawerKeyboardScrollHide.dismissesAtRest(0f));
    }

    @Test
    public void theThresholdIsStrict() {
        assertFalse(AppDrawerKeyboardScrollHide.reachesThreshold(60f, THRESHOLD));
        assertTrue(AppDrawerKeyboardScrollHide.reachesThreshold(60.5f, THRESHOLD));
    }
}
