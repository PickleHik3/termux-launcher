package com.termux.app.statusbar;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The media widget's full row beside a shared clock. On a 411dp phone the widget is about 156dp
 * wide with the slot's 12dp gutter after it: too narrow for the title, so the art used to sit at
 * the start and the transport at the very end, 28dp apart, with the next button 12dp from the bar's
 * end where a near miss paged to the Display. These pin the cluster and the clearance.
 */
public class MediaWidgetLayoutTest {

    private static final float DENSITY = 2.625f;
    private static final float GUTTER_DP = 12f;
    private static final float EPS = .01f;

    private static float px(float dp) {
        return dp * DENSITY;
    }

    @Test
    public void withoutTitleTheArtSitsOneGapBeforeTheTransport() {
        MediaWidgetLayout.Full row = MediaWidgetLayout.full(px(156f), px(GUTTER_DP), DENSITY);
        assertFalse("156dp has no room for the title", row.showsText);
        assertTrue(row.showsArt);
        assertEquals(px(MediaWidgetLayout.GAP_DP),
            row.transportLeft - (row.artLeft + px(MediaWidgetLayout.ART_DP)), EPS);
    }

    @Test
    public void theNextButtonsTouchBoxStopsWhereTheNeighboursReachBegins() {
        for (float widthDp : new float[]{156f, 180f, 200f, 260f}) {
            MediaWidgetLayout.Full row = MediaWidgetLayout.full(px(widthDp), px(GUTTER_DP), DENSITY);
            float barEnd = px(widthDp) + px(GUTTER_DP);
            assertEquals("at " + widthDp + "dp", barEnd - px(StatusBarLensMetrics.NEIGHBOUR_REACH_DP),
                row.nextTouchRight, EPS);
            assertTrue(row.transportRight <= px(widthDp)
                - (px(StatusBarLensMetrics.NEIGHBOUR_REACH_DP) - px(GUTTER_DP)) + EPS);
        }
    }

    @Test
    public void roomTheSlotAlreadyLeavesCountsTowardsTheClearance() {
        float wide = px(StatusBarLensMetrics.NEIGHBOUR_REACH_DP + 20f);
        MediaWidgetLayout.Full row = MediaWidgetLayout.full(px(156f), wide, DENSITY);
        assertEquals("nothing left to clear: the transport ends at the widget's end",
            px(156f), row.transportRight, EPS);
    }

    @Test
    public void aWideWidgetShowsTheTitleBetweenArtAndTransport() {
        MediaWidgetLayout.Full row = MediaWidgetLayout.full(px(260f), px(GUTTER_DP), DENSITY);
        assertTrue(row.showsText);
        assertEquals(0f, row.artLeft, 0f);
        assertEquals(px(MediaWidgetLayout.ART_DP + MediaWidgetLayout.GAP_DP), row.textLeft, EPS);
        assertEquals(px(MediaWidgetLayout.GAP_DP), row.transportLeft - row.textRight, EPS);
    }

    @Test
    public void aNarrowClusterGivesUpTheInsetBeforeTheArt() {
        float cluster = MediaWidgetLayout.ART_DP + MediaWidgetLayout.GAP_DP
            + MediaWidgetLayout.TRANSPORT_DP;
        MediaWidgetLayout.Full row = MediaWidgetLayout.full(px(cluster + 4f), px(GUTTER_DP),
            DENSITY);
        assertTrue(row.showsArt);
        assertEquals(0f, row.artLeft, EPS);
        assertEquals(px(cluster), row.transportRight, EPS);
    }

    @Test
    public void tooNarrowForTheClusterDropsTheArtAndKeepsTheControls() {
        MediaWidgetLayout.Full row = MediaWidgetLayout.full(px(MediaWidgetLayout.TRANSPORT_DP + 10f),
            px(GUTTER_DP), DENSITY);
        assertFalse(row.showsArt);
        assertFalse(row.showsText);
        assertTrue(row.transportLeft >= 0f);
        assertTrue(row.transportRight <= px(MediaWidgetLayout.TRANSPORT_DP + 10f) + EPS);
    }
}
