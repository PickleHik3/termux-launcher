package com.termux.app.x11;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.graphics.Rect;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** Where the touchpad stands in the keyboard frame: the whole of it, or a split keyboard's gap. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class DisplayTouchpadPlacementTest {

    /** 160dp at 2x. */
    private static final int MIN_GAP_PX = 320;

    @Test
    public void aGapWideEnoughToPointInBecomesTheTouchpad() {
        Rect gap = new Rect(400, 0, 400 + MIN_GAP_PX, 500);

        FrameLayout.LayoutParams params =
            DisplayTouchpadPlacement.padParams(gap, 500, 2f);

        assertTrue(DisplayTouchpadPlacement.fitsGap(gap, 2f));
        assertEquals(MIN_GAP_PX, params.width);
        assertEquals(500, params.height);
        assertEquals(400, params.leftMargin);
        assertEquals(Gravity.TOP | Gravity.START, params.gravity);
    }

    @Test
    public void aGapTooNarrowToPointInLeavesThePadOnTheWholeFrame() {
        Rect gap = new Rect(400, 0, 400 + MIN_GAP_PX - 1, 500);

        FrameLayout.LayoutParams params =
            DisplayTouchpadPlacement.padParams(gap, 500, 2f);

        assertFalse(DisplayTouchpadPlacement.fitsGap(gap, 2f));
        assertEquals(ViewGroup.LayoutParams.MATCH_PARENT, params.width);
        assertEquals(500, params.height);
        assertEquals(0, params.leftMargin);
        assertEquals(Gravity.TOP, params.gravity);
    }

    @Test
    public void thatSameGapIsWideEnoughOnADenserScreensDp() {
        Rect gap = new Rect(400, 0, 400 + MIN_GAP_PX - 1, 500);

        // 319px is 212dp at 1.5x, well past the minimum.
        assertTrue(DisplayTouchpadPlacement.fitsGap(gap, 1.5f));
        assertEquals(319, DisplayTouchpadPlacement.padParams(gap, 500, 1.5f).width);
    }

    @Test
    public void noGapAtAllIsTheWholeFrame() {
        FrameLayout.LayoutParams params = DisplayTouchpadPlacement.padParams(null, 500, 2f);

        assertFalse(DisplayTouchpadPlacement.fitsGap(null, 2f));
        assertEquals(ViewGroup.LayoutParams.MATCH_PARENT, params.width);
        assertEquals(500, params.height);
    }

    @Test
    public void aFrameThatHasNotBeenMeasuredLeavesThePadWrappingItsContent() {
        assertEquals(ViewGroup.LayoutParams.WRAP_CONTENT,
            DisplayTouchpadPlacement.padParams(null, 0, 2f).height);
        assertEquals(ViewGroup.LayoutParams.WRAP_CONTENT,
            DisplayTouchpadPlacement.padParams(new Rect(0, 0, MIN_GAP_PX, 0), -1, 2f).height);
    }

    @Test
    public void theMinimumIsWhatASplitKeyboardIsAskedToPartBy() {
        assertEquals(MIN_GAP_PX, DisplayTouchpadPlacement.minimumGapPx(2f));
        assertEquals(480, DisplayTouchpadPlacement.minimumGapPx(3f));
        assertEquals("an unmeasured screen asks for nothing",
            0, DisplayTouchpadPlacement.minimumGapPx(0f));
    }

    @Test
    public void aPartingWidenedToTheMinimumIsExactlyThePadsFrame() {
        int minimum = DisplayTouchpadPlacement.minimumGapPx(3f);
        Rect widened = new Rect(300, 0, 300 + minimum, 700);

        FrameLayout.LayoutParams params =
            DisplayTouchpadPlacement.padParams(widened, 700, 3f);

        assertTrue(DisplayTouchpadPlacement.fitsGap(widened, 3f));
        assertEquals(minimum, params.width);
        assertEquals(300, params.leftMargin);
        assertEquals(Gravity.TOP | Gravity.START, params.gravity);
    }

    @Test
    public void anUnmeasuredScreenDensityIsNoGap() {
        assertFalse(DisplayTouchpadPlacement.fitsGap(new Rect(0, 0, MIN_GAP_PX, 500), 0f));
    }

    @Test
    public void layoutIsOnlyReappliedWhenSomethingMoved() {
        FrameLayout.LayoutParams gapParams = DisplayTouchpadPlacement.padParams(
            new Rect(400, 0, 400 + MIN_GAP_PX, 500), 500, 2f);

        assertTrue(DisplayTouchpadPlacement.describes(
            DisplayTouchpadPlacement.padParams(new Rect(400, 0, 400 + MIN_GAP_PX, 500), 500, 2f),
            gapParams));
        assertFalse("a wider gap is a move", DisplayTouchpadPlacement.describes(
            DisplayTouchpadPlacement.padParams(new Rect(390, 0, 410 + MIN_GAP_PX, 500), 500, 2f),
            gapParams));
        assertFalse("a taller keyboard is a move", DisplayTouchpadPlacement.describes(
            DisplayTouchpadPlacement.padParams(new Rect(400, 0, 400 + MIN_GAP_PX, 600), 600, 2f),
            gapParams));
        assertFalse("the whole frame is a move", DisplayTouchpadPlacement.describes(
            DisplayTouchpadPlacement.padParams(null, 500, 2f), gapParams));
        assertFalse(DisplayTouchpadPlacement.describes(null, gapParams));
        assertFalse(DisplayTouchpadPlacement.describes(
            new ViewGroup.LayoutParams(MIN_GAP_PX, 500), gapParams));
    }
}
