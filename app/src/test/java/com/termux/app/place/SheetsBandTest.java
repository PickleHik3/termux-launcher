package com.termux.app.place;

import static org.junit.Assert.assertEquals;

import android.app.Application;
import android.content.Context;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/** The band the sheets layer reaches through comes from the kept band alone. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class SheetsBandTest {

    private static final int NAV = 110;
    private static final int IME_FIT = 972;
    private static final int WIDTH = 400;
    private static final int HEIGHT = 800;

    @Test
    public void gestureNavigationWithTheKeyboardDownIsTheNavigationBarsHeight() {
        assertEquals(NAV, BottomInsets.sheetsBandPx(BottomInsets.keptPx(false, NAV, NAV)));
    }

    @Test
    public void theSystemKeyboardUpLeavesNoBand() {
        assertEquals(0, BottomInsets.sheetsBandPx(BottomInsets.keptPx(true, IME_FIT, NAV)));
    }

    @Test
    public void aNegativeKeptBandIsNoBand() {
        assertEquals(0, BottomInsets.sheetsBandPx(-4));
    }

    @Test
    public void theLayerReachesBelowTheRootsPaddingExactlyByTheBand() {
        for (int kept : new int[] {0, NAV}) {
            Context context = RuntimeEnvironment.getApplication();
            BottomInsetFrameLayout container = new BottomInsetFrameLayout(context);
            container.setPadding(0, 0, 0, kept);
            View layer = new View(context);
            container.addView(layer, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            container.setReachingLayer(layer);
            container.measure(View.MeasureSpec.makeMeasureSpec(WIDTH, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(HEIGHT, View.MeasureSpec.EXACTLY));
            container.layout(0, 0, WIDTH, HEIGHT);
            assertEquals(HEIGHT, layer.getBottom());
            int reachedBelowPadding = layer.getBottom() - (HEIGHT - container.getPaddingBottom());
            assertEquals(BottomInsets.sheetsBandPx(kept), reachedBelowPadding);
        }
    }
}
