package com.termux.app.launcher.widget;

import android.app.Application;
import android.graphics.Rect;
import android.os.Build;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/** Pure decision table for {@link WidgetCornerPolicy}; see WidgetCellView for the glue. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.S, application = Application.class)
public class WidgetCornerPolicyTest {
    private static final Rect CELL = new Rect(0, 0, 100, 100);
    private static final Rect BACKGROUND_BOUNDS = new Rect(4, 4, 90, 90);

    @Test public void noBackgroundChildClipsTheWholeCellToTheSmallerRadius() {
        WidgetCornerPolicy.Decision decision = WidgetCornerPolicy.decide(null, CELL, 12f, 20f);
        assertEquals(CELL, decision.clip);
        assertEquals(12f, decision.radius, 0f);
    }

    @Test public void backgroundThatOptedOutOfClippingGetsNoClipAtAll() {
        WidgetCornerPolicy.BackgroundChild background =
            new WidgetCornerPolicy.BackgroundChild(true, BACKGROUND_BOUNDS);
        WidgetCornerPolicy.Decision decision = WidgetCornerPolicy.decide(background, CELL, 12f, 20f);
        assertNull(decision.clip);
    }

    @Test public void backgroundThatDidNotOptOutClipsItsOwnRectangleNotTheCell() {
        WidgetCornerPolicy.BackgroundChild background =
            new WidgetCornerPolicy.BackgroundChild(false, BACKGROUND_BOUNDS);
        WidgetCornerPolicy.Decision decision = WidgetCornerPolicy.decide(background, CELL, 12f, 20f);
        assertEquals(BACKGROUND_BOUNDS, decision.clip);
        assertEquals(12f, decision.radius, 0f);
    }

    @Test public void radiusIsAlwaysTheSmallerOfOursAndTheSystemsWhicheverThatIs() {
        WidgetCornerPolicy.BackgroundChild background =
            new WidgetCornerPolicy.BackgroundChild(false, BACKGROUND_BOUNDS);
        assertEquals(20f, WidgetCornerPolicy.decide(background, CELL, 28f, 20f).radius, 0f);
        assertEquals(12f, WidgetCornerPolicy.decide(background, CELL, 12f, 20f).radius, 0f);
    }
}
