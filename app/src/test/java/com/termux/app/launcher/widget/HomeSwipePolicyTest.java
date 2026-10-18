package com.termux.app.launcher.widget;

import org.junit.Test;

import static com.termux.app.launcher.widget.HomeSwipePolicy.Axis.HORIZONTAL;
import static com.termux.app.launcher.widget.HomeSwipePolicy.Axis.UNDECIDED;
import static com.termux.app.launcher.widget.HomeSwipePolicy.Axis.VERTICAL;
import static org.junit.Assert.assertEquals;

/** The Home page's slop race between paging and the palette gesture, and the lift's rule. */
public class HomeSwipePolicyTest {

    private static final float SLOP = 16f;

    @Test public void withinTheSlop_nothingIsDecided() {
        assertEquals(UNDECIDED, HomeSwipePolicy.axis(0f, 0f, SLOP));
        assertEquals(UNDECIDED, HomeSwipePolicy.axis(10f, -12f, SLOP));
        assertEquals(UNDECIDED, HomeSwipePolicy.axis(16f, 16f, SLOP));
    }

    @Test public void mostlyVertical_isThePaletteGesture() {
        assertEquals(VERTICAL, HomeSwipePolicy.axis(5f, -20f, SLOP));
        assertEquals(VERTICAL, HomeSwipePolicy.axis(-12f, 30f, SLOP));
    }

    @Test public void mostlySideways_pages() {
        assertEquals(HORIZONTAL, HomeSwipePolicy.axis(20f, 5f, SLOP));
        assertEquals(HORIZONTAL, HomeSwipePolicy.axis(-30f, -29f, SLOP));
    }

    @Test public void aTiePastTheSlop_goesVertical() {
        assertEquals(VERTICAL, HomeSwipePolicy.axis(20f, -20f, SLOP));
    }

    @Test public void nan_staysUndecided() {
        assertEquals(UNDECIDED, HomeSwipePolicy.axis(Float.NaN, 40f, SLOP));
    }

    @Test public void release_farEnough_goesTheWayItTravelled() {
        assertEquals(-1, HomeSwipePolicy.release(-40f, 0f, 32f, 500f));
        assertEquals(1, HomeSwipePolicy.release(40f, 0f, 32f, 500f));
    }

    @Test public void release_shortAndSlow_isNothing() {
        assertEquals(0, HomeSwipePolicy.release(-20f, -100f, 32f, 500f));
        assertEquals(0, HomeSwipePolicy.release(0f, -900f, 32f, 500f));
    }

    @Test public void release_shortButFlung_counts() {
        assertEquals(-1, HomeSwipePolicy.release(-20f, -600f, 32f, 500f));
        assertEquals(1, HomeSwipePolicy.release(20f, 600f, 32f, 500f));
    }

    @Test public void release_flungBack_isNothing() {
        assertEquals(0, HomeSwipePolicy.release(-80f, 600f, 32f, 500f));
        assertEquals(0, HomeSwipePolicy.release(80f, -600f, 32f, 500f));
    }
}
