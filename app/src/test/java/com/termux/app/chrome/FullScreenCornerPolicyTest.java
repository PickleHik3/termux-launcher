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
    private static final int BOX_LEFT = HOLE_LEFT - 11;
    private static final int BOX_RIGHT = HOLE_RIGHT + 11;
    private static final int BOX_BOTTOM = SAFE_TOP;
    private static final float E = 0.001f;

    private static StatusRowKeepOut.Screen pong() {
        return new StatusRowKeepOut.Screen(true, HOLE_LEFT, 0, HOLE_RIGHT, SAFE_TOP, SAFE_TOP,
            R, R, R, R, StatusRowKeepOut.Mode.AUTO, 0, 0, 100, 11, 63);
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
            R, R, R, R, StatusRowKeepOut.Mode.OFF, 0, 0, 100, 11, 63);
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

    @Test public void theKeyboardClipRaisesOnlyItsBottomCorners() {
        LiveChromeShape.Clip card = LiveChromeShape.cardClip(40f);
        LiveChromeShape.Clip raised = LiveChromeShape.atLeast(card, 0f,
            FullScreenCornerPolicy.bottomCornerRadiusPx(true, 40f, H - 24, H, R));
        assertEquals(40f, raised.topLeft, E);
        assertEquals(40f, raised.topRight, E);
        assertEquals(115f, raised.bottomLeft, E);
        assertEquals(115f, raised.bottomRight, E);
        org.junit.Assert.assertTrue(raised.perCorner);
        // A clip already round enough is handed back as it was: the outline stays a round rect.
        LiveChromeShape.Clip round = LiveChromeShape.cardClip(140f);
        org.junit.Assert.assertSame(round, LiveChromeShape.atLeast(round, 0f, 115f));
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

    @Test public void aPaneTopUnderTheCutoutStartsItsTextBelowTheBox() {
        // The box (CameraHoleGeometry) is the one owner: pong's 443..551 x 0..126, margin in.
        assertEquals(126, FullScreenCornerPolicy.paneTextTopInsetPx(true, 0, 0, W,
            BOX_LEFT, BOX_RIGHT, BOX_BOTTOM));
        // A pane whose top already stands 40px down needs only what is left.
        assertEquals(86, FullScreenCornerPolicy.paneTextTopInsetPx(true, 0, 40, W,
            BOX_LEFT, BOX_RIGHT, BOX_BOTTOM));
    }

    @Test public void theTextStartsAtTheTracedBoxNotTheSafeInset() {
        // Changed with the camera wrap (2026-11-06, spec section 6 Q4): text used to start at the
        // cutout's safe top (126 on pong) whatever the hole's shape; it now starts at the traced
        // box's bottom, so a punch hole whose shape ends at 90 gives the first row 36 px back.
        assertEquals(90, FullScreenCornerPolicy.paneTextTopInsetPx(true, 0, 0, W, 443, 551, 90));
    }

    @Test public void aPaneBesideTheCutoutOrBelowItsBandKeepsItsText() {
        // The left of a split, 0..440: the box at 443..551 is not over it.
        assertEquals(0, FullScreenCornerPolicy.paneTextTopInsetPx(true, 0, 0, 440,
            BOX_LEFT, BOX_RIGHT, BOX_BOTTOM));
        assertEquals(0, FullScreenCornerPolicy.paneTextTopInsetPx(true, 556, 0, W,
            BOX_LEFT, BOX_RIGHT, BOX_BOTTOM));
        assertEquals(0, FullScreenCornerPolicy.paneTextTopInsetPx(true, 0, 126, W,
            BOX_LEFT, BOX_RIGHT, BOX_BOTTOM));
        assertEquals(0, FullScreenCornerPolicy.paneTextTopInsetPx(false, 0, 0, W,
            BOX_LEFT, BOX_RIGHT, BOX_BOTTOM));
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
