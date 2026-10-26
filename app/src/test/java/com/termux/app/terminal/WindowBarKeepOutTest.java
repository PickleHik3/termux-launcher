package com.termux.app.terminal;

import com.termux.app.statusbar.StatusRowKeepOut;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

/**
 * The window bar's keep-out with pong's numbers: 1080 x 2412 px, corner radius 115 px, the
 * camera's box x 454..540 y 0..126, a 4 dp margin at density 2.625 (11 px).
 */
public class WindowBarKeepOutTest {

    private static final int W = 1080;
    private static final int H = 2412;

    private static StatusRowKeepOut.Screen pong() {
        return new StatusRowKeepOut.Screen(true, 454, 0, 540, 126, 126, 115f, 115f, 115f, 115f,
            StatusRowKeepOut.Mode.AUTO, 0, 0, Math.round(4f * 2.625f));
    }

    @Test public void aBarAtTheScreenTopStepsInFromTheCornersAndLeavesTheCameraGap() {
        StatusRowKeepOut.Answer answer = WindowBarKeepOut.answer(pong(), W, H, 0, 0, W, 90);
        assertEquals(115, answer.leftInsetPx);
        assertEquals(115, answer.rightInsetPx);
        assertEquals(443, answer.gapStartPx);
        assertEquals(551, answer.gapEndPx);
    }

    @Test public void theCornersAddToTheStatusRowsTrailingGap() {
        StatusRowKeepOut.Answer answer = WindowBarKeepOut.answer(pong(), W, H, 0, 0, W, 90);
        assertArrayEquals(new int[] {115, 115 + 24},
            WindowBarKeepOut.sidePaddingPx(answer, 24, false));
        // Right to left the trailing end is the left side.
        assertArrayEquals(new int[] {115 + 24, 115},
            WindowBarKeepOut.sidePaddingPx(answer, 24, true));
    }

    @Test public void outsideFullScreenTheBarIsAsItWas() {
        StatusRowKeepOut.Answer answer = WindowBarKeepOut.answer(null, W, H, 0, 0, W, 90);
        assertSame(StatusRowKeepOut.Answer.NONE, answer);
        assertArrayEquals(new int[] {0, 24}, WindowBarKeepOut.sidePaddingPx(answer, 24, false));
    }

    @Test public void aBarBelowTheBandHasNothingToKeepClearOf() {
        // Under a 126 px system bar the bar starts past both the corner's band and the camera.
        assertEquals(StatusRowKeepOut.Answer.NONE,
            WindowBarKeepOut.answer(pong(), W, H, 0, 126, W, 216));
    }

    @Test public void aBarTheStatusRowAlreadyFlowedLeftOfTheGapAsksNothingMore() {
        // The status row stood the bar at 115..443: its own answer is NONE, so nothing doubles.
        assertEquals(StatusRowKeepOut.Answer.NONE,
            WindowBarKeepOut.answer(pong(), W, H, 115, 0, 443, 90));
    }
}
