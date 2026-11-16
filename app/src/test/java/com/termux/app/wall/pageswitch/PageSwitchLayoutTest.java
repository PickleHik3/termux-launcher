package com.termux.app.wall.pageswitch;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.termux.app.ScrubWave;

import org.junit.Test;

public class PageSwitchLayoutTest {

    private static final float D = 2f;
    private static final int W = 1000;

    private PageSwitchLayout layout(int count) {
        return new PageSwitchLayout(D, W, count, 30f);
    }

    @Test public void cellsAreEqualAcrossPaddedWidth() {
        PageSwitchLayout l = layout(4);
        float pad = 12f * D;
        assertEquals((W - 2 * pad) / 4f, l.cellWidthPx(), 1e-3f);
        assertEquals(pad + l.cellWidthPx() / 2f, l.cellCentreX(0), 1e-3f);
        assertEquals(W - pad - l.cellWidthPx() / 2f, l.cellCentreX(3), 1e-3f);
    }

    @Test public void adozenCellsFit() {
        PageSwitchLayout l = layout(12);
        assertTrue(l.cellCentreX(11) + l.cellWidthPx() / 2f <= W - 12f * D + 1e-3f);
    }

    @Test public void indexAtXClamps() {
        PageSwitchLayout l = layout(5);
        assertEquals(0, l.indexAtX(-100f));
        assertEquals(0, l.indexAtX(0f));
        assertEquals(4, l.indexAtX(W + 100f));
        for (int i = 0; i < 5; i++) assertEquals(i, l.indexAtX(l.cellCentreX(i)));
        assertEquals(-1, layout(0).indexAtX(10f));
    }

    @Test public void panelHeightFitsLiftAndTitle() {
        PageSwitchLayout l = layout(4);
        float expected = 8f * D * 2 + 30f + 6f * D + 15f * D + 40f * D;
        assertEquals(expected, l.panelHeightPx(), 1e-3f);
        // the lifted cell's top is at or below the title's bottom plus gap
        float liftedTop = l.cellTopRestPx() - 15f * D;
        assertEquals(8f * D + 30f + 6f * D, liftedTop, 1e-3f);
        // the rest cell's bottom is inside the panel
        assertTrue(l.cellTopRestPx() + 40f * D <= l.panelHeightPx() - 8f * D + 1e-3f);
    }

    @Test public void waveAtAndAwayFromFinger() {
        PageSwitchLayout l = layout(6);
        float[] out = new float[2];
        float fx = l.cellCentreX(2);
        l.wave(2, fx, 1f, out);
        assertEquals(15f * D, out[0], 1e-3f);
        assertEquals(1.34f, out[1], 1e-5f);
        l.wave(3, fx, 1f, out);
        float env = ScrubWave.envelope(1f);
        assertEquals(ScrubWave.lift(env) * 15f * D, out[0], 1e-3f);
        assertEquals(1f + 0.34f * env, out[1], 1e-5f);
        l.wave(5, fx, 1f, out);
        assertTrue(out[0] < 0.5f * D);
        l.wave(2, fx, 0f, out);
        assertEquals(0f, out[0], 0f);
        assertEquals(1f, out[1], 0f);
    }

    @Test public void titleStaysInsidePadding() {
        PageSwitchLayout l = layout(4);
        float pad = 12f * D;
        assertEquals(W - 2 * pad, l.maxTitleWidthPx(), 1e-3f);
        assertEquals(pad + 200f, l.titleCentreX(l.cellCentreX(0), 400f), 1e-3f);
        assertEquals(W - pad - 200f, l.titleCentreX(l.cellCentreX(3), 400f), 1e-3f);
        assertEquals(500f, l.titleCentreX(500f, 200f), 1e-3f);
    }
}
