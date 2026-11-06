package com.termux.app.chrome.wallpaper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.view.View;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

import java.util.concurrent.TimeUnit;

/** The Live picker's page cross-fade: a fade by default, a flip under reduced motion, one page up at the end. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {android.os.Build.VERSION_CODES.TIRAMISU})
public class LivePageSwapTest {

    @Test
    public void reducedMotionFlips_andOtherwiseItFades() {
        assertEquals(0L, LivePageSwap.durationMs(true));
        assertTrue(LivePageSwap.durationMs(false) > 0L);
    }

    @Test
    public void theIncomingPageIsUpAtAlphaZero_thenTheOutgoingOneIsGoneAtAlphaOne() {
        Activity a = Robolectric.buildActivity(Activity.class).setup().get();
        View out = new View(a);
        View in = new View(a);
        in.setVisibility(View.GONE);
        LivePageSwap swap = new LivePageSwap();
        swap.swap(out, in, false);
        assertEquals(View.VISIBLE, in.getVisibility());
        assertEquals(0f, in.getAlpha(), 0f);
        ShadowLooper.idleMainLooper(1, TimeUnit.SECONDS);
        assertEquals(View.GONE, out.getVisibility());
        assertEquals(1f, out.getAlpha(), 0f);
        assertEquals(1f, in.getAlpha(), 0f);
    }

    @Test
    public void aNewSwapSettlesTheRunningOne_andReducedMotionFlipsAtOnce() {
        Activity a = Robolectric.buildActivity(Activity.class).setup().get();
        View one = new View(a);
        View two = new View(a);
        two.setVisibility(View.GONE);
        LivePageSwap swap = new LivePageSwap();
        swap.swap(one, two, false);
        swap.swap(two, one, true);
        assertEquals(View.VISIBLE, one.getVisibility());
        assertEquals(View.GONE, two.getVisibility());
        assertEquals(1f, one.getAlpha(), 0f);
        assertEquals(1f, two.getAlpha(), 0f);
    }
}
