package com.termux.app.terminal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.os.Build;
import android.view.View;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {Build.VERSION_CODES.P})
public class PaneRetroEffectTest {

    private static final String[] REQUIRED = {
        "uniform shader content", "uniform float2 uSize", "uniform float uDensity", "uniform float uRadius",
        "uniform half4 uTint", "half4 main(float2 "
    };
    private static final String[] FORBIDDEN = {"uint", "<<", ">>", "#define", "^", "fwidth", "dFdx"};

    @Test public void bothSourcesDeclareTheContractAndAvoidUnsupportedSyntax() {
        for (String src : new String[]{PaneRetroEffect.CRT_AGSL, PaneRetroEffect.TFT_AGSL}) {
            for (String r : REQUIRED) assertTrue(r, src.contains(r));
            for (String f : FORBIDDEN) assertFalse(f, src.contains(f));
        }
    }

    @Test public void belowApi33NothingIsAvailableAndNoEffectIsMade() {
        assertFalse(PaneRetroEffect.available());
        assertNull(new PaneRetroEffect().effectFor(PaneRetroStyle.CRT, 100f, 100f, 2f, 8f));
    }

    @Test public void displayedPointInvertsTheCrtBendAndLeavesOtherStylesAlone() {
        float w = 1016f, h = 2048f, bend = PaneRetroEffect.CRT_BEND;
        float[] out = new float[2];
        for (float[] p : new float[][] {{0f, 0f}, {w, h}, {w / 2f, h / 2f}, {40f, 1990f}, {700f, 300f}}) {
            PaneRetroEffect.displayedPoint(PaneRetroStyle.CRT_AMBER, w, h, p[0], p[1], out);
            // The shader samples content at bend(displayed); it must be the point we started from.
            float cx = out[0] / w * 2f - 1f, cy = out[1] / h * 2f - 1f;
            float f = (1f + bend * (cx * cx + cy * cy)) / (1f + 2f * bend);
            assertEquals(p[0], (cx * f + 1f) * 0.5f * w, 0.5f);
            assertEquals(p[1], (cy * f + 1f) * 0.5f * h, 0.5f);
        }
        PaneRetroEffect.displayedPoint(PaneRetroStyle.TFT, w, h, 123f, 456f, out);
        assertEquals(123f, out[0], 0f);
        assertEquals(456f, out[1], 0f);
        assertTrue("the shader carries the same bend", PaneRetroEffect.CRT_AGSL.contains("BEND = " + bend + ";"));
    }

    @Test public void setRetroStyleOnOldApiIsHarmless() {
        PaneContentFrame frame = new PaneContentFrame(RuntimeEnvironment.getApplication());
        for (PaneRetroStyle s : PaneRetroStyle.values()) frame.setRetroStyle(s);
        frame.setRetroStyle(null);
        frame.measure(View.MeasureSpec.makeMeasureSpec(300, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(300, View.MeasureSpec.EXACTLY));
        frame.layout(0, 0, 300, 300);
        assertEquals(300, frame.getWidth());
    }
}
