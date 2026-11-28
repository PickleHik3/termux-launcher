package com.termux.app.tiling;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.os.Build;
import android.os.Looper;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/** A finger resting on a place's row predicts the landing (review P3-5: the list and Overview alike). */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class LandingRestTest {

    private final List<Place> told = new ArrayList<>();

    private static void rest(long ms) {
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms));
    }

    @Test
    public void aRowRestedOnIsToldAfterTheRest() {
        LandingRest rest = new LandingRest(told::add);
        rest.over(Place.workspace(4));
        rest(LandingRest.REST_MS - 1);
        assertTrue(told.isEmpty());
        rest(1);
        assertEquals(java.util.Collections.singletonList(Place.workspace(4)), told);
    }

    @Test
    public void aFingerThatMovesOnTellsNothing() {
        LandingRest rest = new LandingRest(told::add);
        rest.over(Place.workspace(4));
        rest(50);
        rest.over(Place.workspace(5));
        rest(50);
        rest.over(null);
        rest(500);
        assertTrue(told.isEmpty());
        rest.closed();
        assertTrue("nothing was told, so closing tells nothing", told.isEmpty());
    }

    @Test
    public void closingAfterATellTellsNull() {
        LandingRest rest = new LandingRest(told::add);
        rest.over(Place.home(0));
        rest(LandingRest.REST_MS);
        rest.closed();
        assertEquals(java.util.Arrays.asList(Place.home(0), null), told);
    }
}
