package com.termux.app.wall.pageswitch;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.termux.app.dock.CapsuleGlass;
import com.termux.app.dock.UnderKeyboardBand;

import org.junit.Test;

/**
 * The key's pop-up is one more card of the stack: the extra-keys card's sides, the stack's row gap
 * above the card, the card's corner rule, a raised bar of one glyph and 8 dp above and below it.
 */
public class PageBarPopupFrameTest {

    private static final float EPS = 0.01f;
    /** Pong: 1080 px wide at 420 dpi. */
    private static final float D = 2.625f;
    private static final int LAYER = 1080;
    /** The extra-keys card under Floating (8 dp in) and Docked (flush). */
    private static final int FLOAT_LEFT = 21;
    private static final int FLOAT_RIGHT = LAYER - 21;
    /** The card's top with the keyboard up and with it down (the card rides lower). */
    private static final int TOP_KEYBOARD_UP = 1350;
    private static final int TOP_KEYBOARD_DOWN = 2050;
    private static final float CORNERS = 24f * D;
    /** What the activity answers under Floating: the stack's row gap and the card's rule. */
    private static final int GAP = UnderKeyboardBand.keyboardGapPx(D);
    private static final int BAR = PageBarPopupFrame.slabHeightPx(D);
    private static final float FLOAT_RADIUS = CapsuleGlass.radiusPx(CORNERS, BAR);

    @Test public void theRaisedBarIsOneGlyphAndEightDpAboveAndBelow() {
        assertEquals(8f, PageBarLayout.RAISED_PAD_DP, 0f);
        assertEquals(32.5f, PageBarLayout.raisedSlabHeightDp(), EPS);
        // Centred in the 48 dp touch row, which may exceed the drawn bar.
        assertEquals(40.25f, PageBarLayout.raisedSlabBottomDp(), EPS);
        assertEquals(48f, PageBarLayout.bandHeightDp(), EPS);
    }

    @Test public void floatingInBothKeyboardStates() {
        for (int cardTop : new int[] {TOP_KEYBOARD_UP, TOP_KEYBOARD_DOWN}) {
            PageBarPopupFrame f = new PageBarPopupFrame().set(D, LAYER, true, FLOAT_LEFT,
                FLOAT_RIGHT, cardTop, GAP, FLOAT_RADIUS);
            String at = "top=" + cardTop;
            // Sides: the card's.
            assertEquals(at, FLOAT_LEFT, f.slabLeftPx, EPS);
            assertEquals(at, FLOAT_RIGHT - FLOAT_LEFT, f.slabWidthPx, EPS);
            // Gap: the stack's row gap, the card's to the keyboard.
            assertEquals(at, UnderKeyboardBand.keyboardGapPx(D), f.gapPx);
            assertEquals(at, cardTop - f.gapPx, f.slabBottomPx(D), 1f);
            // Height and corners: the raised bar, a capsule under the default Corners.
            assertEquals(at, 32.5f * D, f.slabBottomPx(D) - f.slabTopPx(D), EPS);
            assertEquals(at, CapsuleGlass.radiusPx(CORNERS, Math.round(32.5f * D)), f.radiusPx,
                EPS);
            assertEquals(at, Math.round(32.5f * D) / 2f, f.radiusPx, EPS);
            assertEquals(at, Math.round(48f * D), f.viewHeightPx);
        }
    }

    @Test public void floatingWithSmallCornersKeepsThem() {
        PageBarPopupFrame f = new PageBarPopupFrame().set(D, LAYER, true, FLOAT_LEFT, FLOAT_RIGHT,
            TOP_KEYBOARD_UP, GAP, CapsuleGlass.radiusPx(6f * D, BAR));
        assertEquals(6f * D, f.radiusPx, EPS);
    }

    @Test public void dockedInBothKeyboardStates() {
        for (int cardTop : new int[] {TOP_KEYBOARD_UP, TOP_KEYBOARD_DOWN}) {
            PageBarPopupFrame f = new PageBarPopupFrame().set(D, LAYER, true, 0, LAYER, cardTop,
                0, 0f);
            assertEquals(0f, f.slabLeftPx, EPS);
            assertEquals(LAYER, f.slabWidthPx, EPS);
            // Docked rows stand flush and square: no gap, no radius.
            assertEquals(0, f.gapPx);
            assertEquals(0f, f.radiusPx, EPS);
            assertEquals(cardTop, f.slabBottomPx(D), 1f);
        }
    }

    @Test public void withoutACardItStandsAboveTheBandInsideTheSideInsets() {
        PageBarPopupFrame f = new PageBarPopupFrame().set(D, LAYER, false, 0, 0, 1400, GAP,
            FLOAT_RADIUS);
        assertEquals(PageBarLayout.SIDE_INSET_DP * D, f.slabLeftPx, EPS);
        assertEquals(LAYER - 2f * PageBarLayout.SIDE_INSET_DP * D, f.slabWidthPx, EPS);
        assertEquals(1400 - f.gapPx, f.slabBottomPx(D), 1f);
    }

    @Test public void neverAboveTheLayer() {
        PageBarPopupFrame f = new PageBarPopupFrame().set(D, LAYER, true, FLOAT_LEFT, FLOAT_RIGHT,
            10, GAP, FLOAT_RADIUS);
        assertEquals(0, f.viewTopPx);
    }

    @Test public void theRaisedLayoutKeepsTheRhythmToTheEnds() {
        PageBarPopupFrame f = new PageBarPopupFrame().set(D, LAYER, true, FLOAT_LEFT, FLOAT_RIGHT,
            TOP_KEYBOARD_UP, GAP, FLOAT_RADIUS);
        for (int n = 2; n <= 8; n++) {
            PageBarLayout l = PageBarLayout.raised(D, LAYER, n, f.slabLeftPx, f.slabWidthPx);
            String at = "n=" + n;
            assertEquals(at, f.slabLeftPx, l.slabLeftPx(), EPS);
            assertEquals(at, f.slabWidthPx, l.slabWidthPx(), EPS);
            assertEquals(at, l.slabLeftPx(), l.pillLeftPx(), EPS);
            // The end glyphs half the pitch from the bar's ends, both sides.
            float pitch = l.restCenterX(1) - l.restCenterX(0);
            assertEquals(at, pitch / 2f, l.restCenterX(0) - l.slabLeftPx(), EPS);
            assertEquals(at, pitch / 2f,
                l.slabLeftPx() + l.slabWidthPx() - l.restCenterX(n - 1), EPS);
            // Equal top and bottom padding; the glyph at rest size.
            assertEquals(at, 8f * D, l.slabCenterYPx() - l.glyphPx() / 2f - l.slabTopPx(), EPS);
            assertEquals(at, 8f * D, l.slabBottomPx() - l.slabCenterYPx() - l.glyphPx() / 2f, EPS);
            assertEquals(at, 32.5f * D, l.slabHeightPx(), EPS);
            assertEquals(at, 48f * D, l.pillHeightPx(), EPS);
            // The touch row holds the drawn bar.
            assertTrue(at, l.slabTopPx() >= l.pillTopPx() && l.slabBottomPx() <= l.pillBottomPx());
        }
    }
}
