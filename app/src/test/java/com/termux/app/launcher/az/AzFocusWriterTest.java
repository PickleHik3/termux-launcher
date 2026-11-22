package com.termux.app.launcher.az;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import android.app.Activity;
import android.app.Application;
import android.os.Build;
import android.os.Looper;
import android.view.View;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.annotation.Config;

/** The A-Z focus writer owns the focused launch view's alpha, scale and translation. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {Build.VERSION_CODES.P}, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class AzFocusWriterTest {

    private static final float REST_ALPHA = 0.26f;

    private static void settle(View view, float alpha) {
        for (int i = 0; i < 40; i++) {
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(20));
            if (Math.abs(view.getAlpha() - alpha) < 0.001f) return;
        }
        fail("alpha never reached " + alpha + ", stuck at " + view.getAlpha());
    }

    private static View view() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        return new View(activity);
    }

    @Test
    public void focusWithoutABounceLeavesTheTargetAtRest() {
        View target = view();
        target.setScaleX(2f);
        target.setTranslationY(5f);
        new AzFocusWriter().focus(target, false);
        assertEquals(REST_ALPHA, target.getAlpha(), 0.0001f);
        assertEquals(1f, target.getScaleX(), 0f);
        assertEquals(1f, target.getScaleY(), 0f);
        assertEquals(0f, target.getTranslationY(), 0f);
    }

    @Test
    public void focusWithABounceSettlesAtRest() {
        View target = view();
        new AzFocusWriter().focus(target, true);
        settle(target, REST_ALPHA);
        assertEquals(1f, target.getScaleX(), 0f);
    }

    @Test
    public void focusOnASecondTargetClearsTheFirst() {
        View first = view();
        View second = view();
        AzFocusWriter writer = new AzFocusWriter();
        writer.focus(first, false);
        writer.focus(second, false);
        settle(first, 1f);
        assertEquals(REST_ALPHA, second.getAlpha(), 0.0001f);
    }

    @Test
    public void clearRestoresTheTarget() {
        View target = view();
        AzFocusWriter writer = new AzFocusWriter();
        writer.focus(target, false);
        writer.clear();
        settle(target, 1f);
        assertEquals(1f, target.getScaleX(), 0f);
        assertEquals(0f, target.getTranslationY(), 0f);
    }
}
