package com.termux.app.statusbar;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class FullStatusBarGeometryTest {
    @Test public void defaultAndRoundedEndpointsAndInsets() {
        assertEquals(32, FullStatusBarGeometry.calculate(32, 600, 0, 0, 0, 0f).height);
        assertEquals(96, FullStatusBarGeometry.calculate(96, 600, 0, 0, 0, 0f).height);
        assertEquals(570, FullStatusBarGeometry.resolveFullHeight(600, 8, 18, 4));
        assertEquals(570, FullStatusBarGeometry.calculate(96, 600, 8, 18, 4, 1f).height);
    }

    @Test public void shortZeroRelayoutAndNoOvershoot() {
        assertEquals(0, FullStatusBarGeometry.resolveFullHeight(0, 8, 8, 2));
        assertEquals(96, FullStatusBarGeometry.calculate(96, 40, 0, 0, 0, 1f).height);
        assertEquals(300, FullStatusBarGeometry.calculate(96, 300, 0, 0, 0, 2f).height);
        assertEquals(96, FullStatusBarGeometry.calculate(96, 300, 0, 0, 0, -2f).height);
        assertTrue(FullStatusBarGeometry.calculate(96, 400, 0, 0, 0, .7f).height
            > FullStatusBarGeometry.calculate(96, 400, 0, 0, 0, .3f).height);
    }

    @Test public void finiteUnitClampsAndReadsNonFiniteAsZero() {
        assertEquals(0.5f, FullStatusBarGeometry.finiteUnit(0.5f), 0f);
        assertEquals(0f, FullStatusBarGeometry.finiteUnit(-1f), 0f);
        assertEquals(1f, FullStatusBarGeometry.finiteUnit(2f), 0f);
        assertEquals(0f, FullStatusBarGeometry.finiteUnit(Float.NaN), 0f);
        assertEquals(0f, FullStatusBarGeometry.finiteUnit(Float.POSITIVE_INFINITY), 0f);
        assertEquals(0f, FullStatusBarGeometry.finiteUnit(Float.NEGATIVE_INFINITY), 0f);
    }
}
