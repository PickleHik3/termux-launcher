package com.termux.app.surfaces;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * The frame's pose against the hidden items' rail (SPEC §3.6a item 3), on pong's numbers (411 ×
 * 914 dp at 2.625): something hidden, one fixed offset lifted or not; nothing hidden, centred at
 * rest and in the hidden pose while an element is lifted.
 */
public class EditorFrameRailPoseTest {

    private static final float DENSITY = 2.625f;
    private static final int WINDOW_W = 1079;
    private static final int WINDOW_H = 2399;
    private static final int STATUS_PX = 108;
    private static final int NAV_PX = 63;
    private static final int CONTAINER_H = WINDOW_H - STATUS_PX - NAV_PX;
    private static final int VISIBLE_H = CONTAINER_H + STATUS_PX + NAV_PX;

    private static int dp(float value) {
        return Math.round(value * DENSITY);
    }

    private static EditorFramePose.Window pong() {
        return new EditorFramePose.Window(WINDOW_H, STATUS_PX, NAV_PX, DENSITY, 1f, STATUS_PX,
            VISIBLE_H, STATUS_PX, dp(48), dp(8));
    }

    private static EditorFramePose.Reserve reserve() {
        return new EditorFramePose.Reserve(HiddenStackPolicy.reservePx(DENSITY), 0, WINDOW_W,
            WINDOW_W, dp(HiddenStackPolicy.SIDE_DP));
    }

    /** No card, and a card at its half detent. */
    private static final float[] CARD_TOPS = {Float.MAX_VALUE, WINDOW_H - 1100f};

    private static float frameLeft(EditorFramePose.Pose pose) {
        return WINDOW_W * (1f - pose.scale) / 2f + pose.translationX;
    }

    @Test
    public void withSomethingHiddenTheLiftMovesNothing() {
        for (float cardTop : CARD_TOPS) {
            for (int hidden : new int[] {1, 4}) {
                EditorFramePose.Pose idle = EditorFramePose.railPose(pong(), cardTop, reserve(),
                    hidden, false);
                EditorFramePose.Pose lifted = EditorFramePose.railPose(pong(), cardTop,
                    reserve(), hidden, true);
                assertEquals(idle.scale, lifted.scale, 0f);
                assertEquals(idle.translationX, lifted.translationX, 0f);
                assertEquals(idle.translationY, lifted.translationY, 0f);
                assertTrue("clear of the rail",
                    frameLeft(idle) >= HiddenStackPolicy.reservePx(DENSITY) - 0.01f);
            }
        }
    }

    @Test
    public void withNothingHiddenTheFrameRestsCentred() {
        for (float cardTop : CARD_TOPS) {
            EditorFramePose.Pose rest = EditorFramePose.railPose(pong(), cardTop, reserve(), 0,
                false);
            EditorFramePose.Pose plain = EditorFramePose.pose(pong(), cardTop);
            assertEquals(0f, rest.translationX, 0f);
            assertEquals(plain.scale, rest.scale, 0f);
        }
    }

    @Test
    public void withNothingHiddenALiftTakesTheHiddenPose() {
        for (float cardTop : CARD_TOPS) {
            EditorFramePose.Pose lifted = EditorFramePose.railPose(pong(), cardTop, reserve(), 0,
                true);
            EditorFramePose.Pose hidden = EditorFramePose.railPose(pong(), cardTop, reserve(), 1,
                false);
            assertEquals(hidden.scale, lifted.scale, 0f);
            assertEquals(hidden.translationX, lifted.translationX, 0f);
            assertEquals(hidden.translationY, lifted.translationY, 0f);
            assertTrue("clear of the rail",
                frameLeft(lifted) >= HiddenStackPolicy.reservePx(DENSITY) - 0.01f);
            assertTrue("inside the window",
                frameLeft(lifted) + WINDOW_W * lifted.scale <= WINDOW_W + 0.01f);
        }
    }
}
