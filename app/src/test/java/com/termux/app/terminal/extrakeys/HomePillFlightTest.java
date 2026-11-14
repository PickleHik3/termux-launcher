package com.termux.app.terminal.extrakeys;

import static org.junit.Assert.assertEquals;

import com.termux.app.terminal.extrakeys.HomePillSpot.Rect;

import org.junit.Test;

public class HomePillFlightTest {

    private static final float D = 0.001f;
    private final Rect from = new Rect(800f, 900f, 1000f, 946f);   // 200 x 46 pill
    private final Rect to = new Rect(0f, 1700f, 1000f, 1820f);     // 1000 x 120 docked bar
    private final float[] out = new float[4];

    @Test
    public void aViewLaidOutAtTheFromRectStartsAtIdentity() {
        HomePillFlight.transform(from, to, 0f, from, out);
        assertEquals(0f, out[0], D);
        assertEquals(0f, out[1], D);
        assertEquals(1f, out[2], D);
        assertEquals(1f, out[3], D);
    }

    @Test
    public void aViewLaidOutAtTheToRectEndsAtIdentity() {
        HomePillFlight.transform(from, to, 1f, to, out);
        assertEquals(0f, out[0], D);
        assertEquals(0f, out[1], D);
        assertEquals(1f, out[2], D);
        assertEquals(1f, out[3], D);
    }

    @Test
    public void theSameFlightFromEitherBaseDrawsTheSameRect() {
        float t = 0.4f;
        HomePillFlight.transform(from, to, t, from, out);
        float cxA = from.centerX() + out[0];
        float wA = from.width() * out[2];
        HomePillFlight.transform(from, to, t, to, out);
        float cxB = to.centerX() + out[0];
        float wB = to.width() * out[2];
        assertEquals(cxA, cxB, D);
        assertEquals(wA, wB, D);
    }

    @Test
    public void theObjectStretchesAsItTravels() {
        HomePillFlight.transform(from, to, 1f, from, out);
        assertEquals(to.centerX() - from.centerX(), out[0], D);
        assertEquals(to.centerY() - from.centerY(), out[1], D);
        assertEquals(5f, out[2], D);
        assertEquals(120f / 46f, out[3], D);
    }

    @Test
    public void theCrossFadeRunsOverTheMiddleHalf() {
        assertEquals(1f, HomePillFlight.outgoingAlpha(0f), D);
        assertEquals(1f, HomePillFlight.outgoingAlpha(0.25f), D);
        assertEquals(0.5f, HomePillFlight.outgoingAlpha(0.5f), D);
        assertEquals(0f, HomePillFlight.outgoingAlpha(0.75f), D);
        assertEquals(0f, HomePillFlight.incomingAlpha(0.1f), D);
        assertEquals(0.5f, HomePillFlight.incomingAlpha(0.5f), D);
        assertEquals(1f, HomePillFlight.incomingAlpha(1f), D);
        assertEquals(1f, HomePillFlight.outgoingAlpha(0.6f) + HomePillFlight.incomingAlpha(0.6f), D);
    }

    @Test
    public void theKeyboardPoseInterpolatesLinearlyAndClamps() {
        HomePillFlight.keyboardPose(from, to, 0f, out);
        assertEquals(800f, out[0], D);
        HomePillFlight.keyboardPose(from, to, 0.5f, out);
        assertEquals(400f, out[0], D);
        assertEquals(1300f, out[1], D);
        assertEquals(1000f, out[2], D);
        assertEquals(1383f, out[3], D);
        HomePillFlight.keyboardPose(from, to, 2f, out);
        assertEquals(0f, out[0], D);
        assertEquals(1820f, out[3], D);
    }

    @Test
    public void theDurationIs280Ms() {
        assertEquals(280L, HomePillFlight.DURATION_MS);
    }
}
