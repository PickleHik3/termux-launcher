package com.termux.app.statusbar;

import android.app.Application;
import android.os.Build;
import android.view.View;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;

/** The place strip's dissolve, and the end of it the window chips feather themselves. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class PlaceContentStripFadeTest {

    @Test public void atRestTheEndFacingTheReadingsIsTheChipsToFeather() {
        PlaceContentStrip strip = strip();
        assertEquals(1f, strip.getLeftFadingEdgeStrength(), 0f);
        assertEquals(0f, strip.getRightFadingEdgeStrength(), 0f);
    }

    @Test public void slidingThePlaceAwayDissolvesBothEnds() {
        PlaceContentStrip strip = strip();
        int length = strip.getHorizontalFadingEdgeLength();
        strip.setTranslationX(length / 2f);
        assertEquals(0.5f, strip.getRightFadingEdgeStrength(), 0.01f);
        strip.setTranslationX(-3f * length);
        assertEquals(1f, strip.getRightFadingEdgeStrength(), 0f);
        assertEquals(1f, strip.getLeftFadingEdgeStrength(), 0f);
        strip.setTranslationX(0f);
        assertEquals(0f, strip.getRightFadingEdgeStrength(), 0f);
    }

    @Test public void rightToLeftTheReadingsAreOnTheLeft() {
        PlaceContentStrip strip = strip();
        strip.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        assertEquals(0f, strip.getLeftFadingEdgeStrength(), 0f);
        assertEquals(1f, strip.getRightFadingEdgeStrength(), 0f);
    }

    private static PlaceContentStrip strip() {
        return new PlaceContentStrip(ApplicationProvider.getApplicationContext(), null);
    }
}
