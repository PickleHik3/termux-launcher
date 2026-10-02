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
        "uniform shader content", "uniform float2 uSize", "uniform float uDensity",
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
        assertNull(new PaneRetroEffect().effectFor(PaneRetroStyle.CRT, 100f, 100f, 2f));
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
