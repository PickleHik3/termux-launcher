package com.termux.app.chrome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.os.Build;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** The floors that darken a see-through Look give way to the surface's own opacity. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class LowOpacityGlassTest {

    @Test
    public void aVeryTransparentSurfaceKeepsNoFloorAtAll() {
        assertEquals(0f, LowOpacityGlass.keep(0.02f), 0f);
        assertEquals(0f, LowOpacityGlass.keep(0.10f), 0f);
        assertEquals("a sheet draws at the Look's own 2%", 0.02f,
            LowOpacityGlass.floored(0.02f, 0.92f), 1e-6f);
        assertEquals("a chip likewise", 0.02f, LowOpacityGlass.floored(0.02f, 0.88f), 1e-6f);
    }

    @Test
    public void anOrdinarySurfaceKeepsTheWholeFloor() {
        assertEquals(1f, LowOpacityGlass.keep(0.25f), 0f);
        assertEquals(1f, LowOpacityGlass.keep(0.5f), 0f);
        assertEquals(0.92f, LowOpacityGlass.floored(0.5f, 0.92f), 1e-6f);
        assertEquals("never under its own opacity", 0.99f,
            LowOpacityGlass.floored(0.99f, 0.92f), 1e-6f);
    }

    @Test
    public void betweenTheTwoTheFloorFadesInWithoutAJump() {
        float last = 0f;
        for (int percent = 0; percent <= 100; percent++) {
            float now = LowOpacityGlass.floored(percent / 100f, 0.92f);
            assertTrue("monotonic at " + percent, now >= last);
            assertTrue("no jump at " + percent, now - last < 0.1f);
            last = now;
        }
    }

    @Test
    public void theFrostFilterTakesNothingOffUnderClearAndKeepsItsOffsetOtherwise() {
        assertSame("the ordinary filter is the one that was", GlassFilters.frost(),
            GlassFilters.frost(34));
        assertNotSame(GlassFilters.frost(), GlassFilters.frost(2));
        assertSame(GlassFilters.frost(2), GlassFilters.frost(8));
    }
}
