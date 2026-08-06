package com.termux.app.statusbar;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SessionsPanelMetricsTest {

    private static final float CHROME_PX = 117f;
    /** Wide enough that no screen cap ever bites unless a test wants it to. */
    private static final int WIDE_SCREEN_PX = 2000;

    @Test
    public void width_neverLeavesTheMinMaxBand() {
        assertEquals(SessionsPanelMetrics.MIN_WIDTH_DP,
            SessionsPanelMetrics.calculate(0f, CHROME_PX, WIDE_SCREEN_PX, 1f).widthDp);
        assertEquals(SessionsPanelMetrics.MAX_WIDTH_DP,
            SessionsPanelMetrics.calculate(10_000f, CHROME_PX, WIDE_SCREEN_PX, 1f).widthDp);
    }

    @Test
    public void availableTitlePx_isExactlyTheTextTheWidthWasComputedFor() {
        // The invariant the old code broke: chrome was described in one place and the text cap in
        // another, so a new row button shrank the title without widening the panel.
        float text = 150f;
        SessionsPanelMetrics.Layout layout =
            SessionsPanelMetrics.calculate(text, CHROME_PX, WIDE_SCREEN_PX, 1f);

        assertEquals(text + CHROME_PX, layout.widthDp, .001f);
        assertEquals(text, layout.availableTitlePx, .001f);
        assertFalse(SessionsPanelMetrics.shouldMarquee(text, layout.availableTitlePx,
            SessionsPanelMetrics.MARQUEE_MIN_OVERFLOW_DP));
    }

    @Test
    public void textCapIsDerivedFromTheWidthClampRatherThanHardcoded() {
        // At the maximum width the title gets everything the clamp can carry and not a pixel more,
        // so the two constants cannot drift apart.
        SessionsPanelMetrics.Layout layout =
            SessionsPanelMetrics.calculate(10_000f, CHROME_PX, WIDE_SCREEN_PX, 1f);

        assertEquals(SessionsPanelMetrics.MAX_WIDTH_DP - CHROME_PX,
            layout.availableTitlePx, .001f);
    }

    @Test
    public void narrowScreen_shrinksTheTitleEvenThoughTheRequestedWidthIsUnchanged() {
        // StatusCardHost keeps 12dp per side, a cap the view itself never applies: the width it
        // asks for stays at the clamp while the space the title actually gets drops.
        SessionsPanelMetrics.Layout wide =
            SessionsPanelMetrics.calculate(10_000f, CHROME_PX, WIDE_SCREEN_PX, 1f);
        SessionsPanelMetrics.Layout narrow =
            SessionsPanelMetrics.calculate(10_000f, CHROME_PX, 260, 1f);

        assertEquals(wide.widthDp, narrow.widthDp);
        assertEquals(260 - 24 - CHROME_PX, narrow.availableTitlePx, .001f);
        assertTrue(narrow.availableTitlePx < wide.availableTitlePx);
    }

    @Test
    public void narrowScreen_neverShrinksBelowTheMinimumWidth() {
        SessionsPanelMetrics.Layout layout =
            SessionsPanelMetrics.calculate(10_000f, CHROME_PX, 40, 1f);

        assertEquals(SessionsPanelMetrics.MIN_WIDTH_DP - CHROME_PX,
            layout.availableTitlePx, .001f);
    }

    @Test
    public void density_scalesTheClampsRatherThanTheRawPixels() {
        SessionsPanelMetrics.Layout layout =
            SessionsPanelMetrics.calculate(10_000f, CHROME_PX * 2f, WIDE_SCREEN_PX, 2f);

        assertEquals(SessionsPanelMetrics.MAX_WIDTH_DP, layout.widthDp);
        assertEquals(SessionsPanelMetrics.MAX_WIDTH_DP * 2f - CHROME_PX * 2f,
            layout.availableTitlePx, .001f);
    }

    @Test
    public void shouldMarquee_ignoresAShortTailButRunsForARealOverflow() {
        float available = 200f;
        // A one-or-two-character tail: scrolls out and restarts inside the stock marquee delay.
        assertFalse(SessionsPanelMetrics.shouldMarquee(206f, available,
            SessionsPanelMetrics.MARQUEE_MIN_OVERFLOW_DP));
        // Exactly at the threshold still counts as too short to read as motion.
        assertFalse(SessionsPanelMetrics.shouldMarquee(
            available + SessionsPanelMetrics.MARQUEE_MIN_OVERFLOW_DP, available,
            SessionsPanelMetrics.MARQUEE_MIN_OVERFLOW_DP));
        assertTrue(SessionsPanelMetrics.shouldMarquee(
            available + SessionsPanelMetrics.MARQUEE_MIN_OVERFLOW_DP + .5f, available,
            SessionsPanelMetrics.MARQUEE_MIN_OVERFLOW_DP));
        assertTrue(SessionsPanelMetrics.shouldMarquee(340f, available,
            SessionsPanelMetrics.MARQUEE_MIN_OVERFLOW_DP));
        // Text that fits, and text with room to spare, both stay still.
        assertFalse(SessionsPanelMetrics.shouldMarquee(available, available,
            SessionsPanelMetrics.MARQUEE_MIN_OVERFLOW_DP));
        assertFalse(SessionsPanelMetrics.shouldMarquee(10f, available,
            SessionsPanelMetrics.MARQUEE_MIN_OVERFLOW_DP));
    }
}
