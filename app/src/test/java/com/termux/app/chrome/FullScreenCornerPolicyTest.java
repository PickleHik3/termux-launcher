package com.termux.app.chrome;

import com.termux.app.statusbar.StatusRowKeepOut;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Full screen on the developer's phone (pong): 1080 × 2412 px, every corner 115px, one camera
 * cutout 454..540 × 0..126 with a safe top inset of 126px, no waterfall.
 */
public class FullScreenCornerPolicyTest {

    private static final int W = 1080;
    private static final int H = 2412;
    private static final float R = 115f;
    private static final int HOLE_LEFT = 454;
    private static final int HOLE_RIGHT = 540;
    private static final int SAFE_TOP = 126;
    private static final float E = 0.001f;

    private static StatusRowKeepOut.Screen pong() {
        return new StatusRowKeepOut.Screen(true, HOLE_LEFT, 0, HOLE_RIGHT, SAFE_TOP, SAFE_TOP,
            R, R, R, R, StatusRowKeepOut.Mode.AUTO, 0, 0, 11);
    }

    @Test public void aStatusRowAtTheVeryTopStepsInByTheCornerAtItsTopEdge() {
        // 115 at the row's top edge, ~6px at its bottom edge (y = 90): the deeper one counts.
        assertEquals(115f, StatusRowKeepOut.cornerInsetPx(R, 0), E);
        assertEquals(115f - (float) Math.sqrt(115 * 115 - 25 * 25),
            StatusRowKeepOut.cornerInsetPx(R, 90), E);
        StatusRowKeepOut.Answer answer = StatusRowKeepOut.answer(pong(), W, H, 0, 0, W, 90);
        assertEquals(115, answer.leftInsetPx);
        assertEquals(115, answer.rightInsetPx);
    }

    @Test public void theCornersHoldWhateverTheCameraModeIs() {
        StatusRowKeepOut.Screen off = new StatusRowKeepOut.Screen(false, 0, 0, 0, 0, SAFE_TOP,
            R, R, R, R, StatusRowKeepOut.Mode.OFF, 0, 0, 11);
        StatusRowKeepOut.Answer answer = StatusRowKeepOut.answer(off, W, H, 0, 0, W, 90);
        assertEquals(new StatusRowKeepOut.Answer(115, 115, 0, 0), answer);
    }

    @Test public void aKeyboardFrameInTheBottomBandCurvesWithTheScreen() {
        // A 24px chin: the frame's bottom stands at 2388, inside the 115px band.
        assertEquals(115f, FullScreenCornerPolicy.bottomCornerRadiusPx(true, 40f, H - 24, H, R), E);
        // At the very bottom too.
        assertEquals(115f, FullScreenCornerPolicy.bottomCornerRadiusPx(true, 0f, H, H, R), E);
        // A rounder card keeps its own.
        assertEquals(140f, FullScreenCornerPolicy.bottomCornerRadiusPx(true, 140f, H, H, R), E);
    }

    @Test public void aKeyboardFrameAboveTheBandOrOutsideFullScreenKeepsItsRadius() {
        assertEquals(40f, FullScreenCornerPolicy.bottomCornerRadiusPx(true, 40f, H - 115, H, R), E);
        assertEquals(40f, FullScreenCornerPolicy.bottomCornerRadiusPx(true, 40f, H - 300, H, R), E);
        assertEquals(40f, FullScreenCornerPolicy.bottomCornerRadiusPx(false, 40f, H - 24, H, R), E);
        assertEquals(40f, FullScreenCornerPolicy.bottomCornerRadiusPx(true, 40f, H - 24, H, 0f), E);
    }

    @Test public void aPaneAtTheTopWearsTheScreensTopRadius() {
        assertEquals(115f, FullScreenCornerPolicy.topCornerRadiusPx(true, 28f, 0, R), E);
        assertEquals(28f, FullScreenCornerPolicy.topCornerRadiusPx(true, 28f, 126, R), E);
        assertEquals(28f, FullScreenCornerPolicy.topCornerRadiusPx(false, 28f, 0, R), E);
    }

    @Test public void aPaneTopUnderTheCutoutStartsItsTextBelowTheLens() {
        assertEquals(126, FullScreenCornerPolicy.paneTextTopInsetPx(true, 0, 0, W,
            HOLE_LEFT, HOLE_RIGHT, SAFE_TOP));
        // A pane whose top already stands 40px down needs only what is left.
        assertEquals(86, FullScreenCornerPolicy.paneTextTopInsetPx(true, 0, 40, W,
            HOLE_LEFT, HOLE_RIGHT, SAFE_TOP));
    }

    @Test public void aPaneBesideTheCutoutOrBelowItsBandKeepsItsText() {
        // The left half of a split, 0..450: the hole at 454..540 is not over it.
        assertEquals(0, FullScreenCornerPolicy.paneTextTopInsetPx(true, 0, 0, 450,
            HOLE_LEFT, HOLE_RIGHT, SAFE_TOP));
        assertEquals(0, FullScreenCornerPolicy.paneTextTopInsetPx(true, 545, 0, W,
            HOLE_LEFT, HOLE_RIGHT, SAFE_TOP));
        assertEquals(0, FullScreenCornerPolicy.paneTextTopInsetPx(true, 0, 126, W,
            HOLE_LEFT, HOLE_RIGHT, SAFE_TOP));
        assertEquals(0, FullScreenCornerPolicy.paneTextTopInsetPx(false, 0, 0, W,
            HOLE_LEFT, HOLE_RIGHT, SAFE_TOP));
    }

    @Test public void aWindowBarAtThePanesTopFlowsAroundTheCutoutLikeTheStatusRow() {
        // The chip row standing at 0..84 across a full-width pane.
        StatusRowKeepOut.Answer answer = StatusRowKeepOut.answer(pong(), W, H, 0, 0, W, 84);
        assertEquals(new StatusRowKeepOut.Answer(115, 115, HOLE_LEFT - 11, HOLE_RIGHT + 11),
            answer);
        // In the right half of a split (540..1080) the hole is left of the row: no gap, only the
        // right corner.
        StatusRowKeepOut.Answer half = StatusRowKeepOut.answer(pong(), W, H, 560, 0, W, 84);
        assertEquals(0, half.leftInsetPx);
        assertEquals(115, half.rightInsetPx);
    }
}
