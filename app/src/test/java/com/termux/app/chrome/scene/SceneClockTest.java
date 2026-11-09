package com.termux.app.chrome.scene;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Calendar;
import java.util.TimeZone;

/** The pure parts of the shader clock, the date uniform, the still's size and the thumbnail's render scale. */
public class SceneClockTest {

    @Test
    public void firstStepAfterAPauseAddsNothing() {
        assertEquals(0L, ShaderInputs.step(Long.MIN_VALUE, 123_456L));
        ShaderInputs in = new ShaderInputs();
        in.advance(1000L);
        assertEquals(0d, in.timeSec, 0d);
        in.advance(1050L);
        assertEquals(0.05d, in.timeSec, 1e-9);
        in.pause();
        in.advance(9000L);
        assertEquals(0.05d, in.timeSec, 1e-9);
    }

    @Test
    public void resumingContinuesInsteadOfJumping() {
        ShaderInputs in = new ShaderInputs();
        in.advance(0L);
        in.advance(50L);
        // Covered for an hour without a pause call: the step is capped, the clock does not jump.
        in.advance(3_600_050L);
        assertEquals((50 + ShaderInputs.MAX_STEP_MS) / 1000d, in.timeSec, 1e-9);
    }

    @Test
    public void backwardsAndStalledStepsAddNothing() {
        assertEquals(0L, ShaderInputs.step(2000L, 2000L));
        assertEquals(0L, ShaderInputs.step(2000L, 1000L));
        assertEquals(ShaderInputs.MAX_STEP_MS, ShaderInputs.step(0L, 3_600_000L));
    }

    @Test
    public void dateIsYearMonthFromZeroDaySecondsSinceMidnight() {
        Calendar c = Calendar.getInstance(TimeZone.getDefault());
        c.clear();
        c.set(2026, Calendar.NOVEMBER, 9, 13, 30, 15);
        c.set(Calendar.MILLISECOND, 500);
        float[] out = new float[4];
        ShaderInputs.fillDate(Calendar.getInstance(), c.getTimeInMillis(), out);
        assertEquals(2026f, out[0], 0f);
        assertEquals(10f, out[1], 0f);
        assertEquals(9f, out[2], 0f);
        assertEquals(13 * 3600f + 30 * 60f + 15.5f, out[3], 1e-2f);
    }

    @Test
    public void colourIsSrgbZeroToOne() {
        ShaderInputs in = new ShaderInputs();
        in.setColor(1, 0xff336699);
        assertEquals(0x33 / 255f, in.colors[3], 1e-6f);
        assertEquals(0x66 / 255f, in.colors[4], 1e-6f);
        assertEquals(0x99 / 255f, in.colors[5], 1e-6f);
    }

    @Test
    public void stillSizeKeepsAspectAndCaps() {
        assertArrayEquals(new int[] {1080, 2400}, SceneStill.fitSize(1080, 2400));
        assertArrayEquals(new int[] {1, 1}, SceneStill.fitSize(0, -5));
        int[] big = SceneStill.fitSize(8192, 4096);
        assertEquals(SceneStill.MAX_SIDE_PX, big[0]);
        assertEquals(SceneStill.MAX_SIDE_PX / 2, big[1]);
    }

    @Test
    public void thumbRendersAtMostAtTheCap() {
        assertEquals(1f, SceneThumbView.renderScale(200, 300), 0f);
        assertEquals(0.5f, SceneThumbView.renderScale(960, 480), 1e-6f);
    }

    @Test
    public void glassSigmaKeepsTheSceneFormula() {
        // Half the radius in pixels of the pass: 24 dp at density 3 over 4 = 9 px.
        assertEquals(9f, SceneLowRes.sigmaPx(24, 3f, 4), 1e-6f);
        assertEquals(SceneLowRes.sigmaPx(SceneLowRes.FALLBACK_BLUR_DP, 2f, 8), SceneLowRes.sigmaPx(0, 2f, 8), 0f);
        assertEquals(0, SceneGl.blurRadius(0.2f));
        assertEquals(27, SceneGl.blurRadius(9f));
        assertEquals(SceneGl.MAX_BLUR_RADIUS, SceneGl.blurRadius(40f));
    }
}
