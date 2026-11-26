package com.termux.app.wall.pageswitch;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class MarksPillPolicyTest {

    private static final float EPS = 0.001f;

    @Test public void thePillIsItsMarksAndAnEvenInsetOnBothEndsAndNothingMore() {
        assertEquals(100f + 2f * MarksPillPolicy.INSET_DP, MarksPillPolicy.widthPx(100f, 1f), EPS);
        assertEquals(2f * (100f + 2f * MarksPillPolicy.INSET_DP),
            MarksPillPolicy.widthPx(200f, 2f), EPS);
        assertEquals("no marks, no pill", 0f, MarksPillPolicy.widthPx(0f, 3f), EPS);
    }

    @Test public void theLineMayTakeThePaneLessTheSideInsetAndThePillsOwn() {
        float room = MarksPillPolicy.lineRoomPx(360f, 1f);
        assertEquals(360f - 2f * (PageBarLayout.SIDE_INSET_DP + MarksPillPolicy.INSET_DP), room, EPS);
        assertEquals(0f, MarksPillPolicy.lineRoomPx(10f, 1f), EPS);
        // A pill holding all of that room stands exactly as far in as the side inset.
        float pill = MarksPillPolicy.widthPx(room, 1f);
        assertEquals(PageBarLayout.SIDE_INSET_DP, MarksPillPolicy.leftPx(0f, 360f, 360f, pill, 1f), EPS);
    }

    @Test public void thePillIsCentredOnTheWindowLikeTheGlyphPill() {
        float pill = 120f;
        assertEquals((360f - pill) / 2f, MarksPillPolicy.leftPx(0f, 360f, 360f, pill, 1f), EPS);
        assertEquals(PageBarBand.pillLeftPx(100f, 540f, 1000f, pill, 2f),
            MarksPillPolicy.leftPx(100f, 540f, 1000f, pill, 2f), EPS);
        assertEquals(MarksPillPolicy.INSET_DP * 2f + 50f, MarksPillPolicy.lineLeftPx(50f, 2f), EPS);
    }

    @Test public void theFittedLayoutIsOneSlabOfTheFittedWidthStandingWhereHomesSlabDoes() {
        PageBarLayout fitted = PageBarLayout.fitted(2f, 720f, 0f, 720f, 200f);
        assertEquals(200f, fitted.slabWidthPx(), EPS);
        assertEquals((720f - 200f) / 2f, fitted.slabLeftPx(), EPS);
        assertEquals(MarksPillPolicy.heightPx(2f), fitted.slabHeightPx(), EPS);
        PageBarLayout glyphs = new PageBarLayout(2f, 720f, 3);
        assertEquals("the slab's bottom is where the glyph slab's is", glyphs.slabBottomPx(),
            fitted.slabBottomPx(), EPS);
        assertTrue(fitted.slabTopPx() >= fitted.pillTopPx());
    }
}
