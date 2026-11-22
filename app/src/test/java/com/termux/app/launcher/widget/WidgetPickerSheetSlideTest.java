package com.termux.app.launcher.widget;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Application;
import android.os.Looper;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.termux.R;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;

import java.time.Duration;

/** The widget picker opens and closes by its slide, and a drag-dismissed sheet opens again. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, qualifiers = "w411dp-h918dp-420dpi", application = Application.class)
public class WidgetPickerSheetSlideTest {
    private WidgetPickerSheetView picker;

    @Before public void setUp() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        ContextThemeWrapper themed = new ContextThemeWrapper(activity,
            R.style.Theme_TermuxActivity_DayNight_NoActionBar);
        FrameLayout content = new FrameLayout(themed);
        activity.setContentView(content, new ViewGroup.LayoutParams(1080, 2412));
        picker = new WidgetPickerSheetView(themed, item -> { });
        content.addView(picker, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    @Test public void aProgrammaticCloseEndsGoneAndAtRest() {
        picker.open();
        idle(1000);
        assertEquals(View.VISIBLE, picker.getVisibility());
        assertEquals(0f, picker.cardTranslationYForTest(), 0f);
        picker.close();
        assertFalse(picker.isOpen());
        idle(1000);
        assertEquals(View.GONE, picker.getVisibility());
        assertEquals(0f, picker.cardTranslationYForTest(), 0f);
        assertEquals(BottomSheetBehavior.STATE_EXPANDED, picker.behaviorForTest().getState());
    }

    @Test public void aReopenAfterADragDismissEndsExpandedAtRest() {
        picker.open();
        idle(1000);
        picker.behaviorForTest().setState(BottomSheetBehavior.STATE_HIDDEN);
        idle(2000);
        assertEquals(View.GONE, picker.getVisibility());
        assertEquals(BottomSheetBehavior.STATE_HIDDEN, picker.behaviorForTest().getState());
        picker.open();
        idle(1000);
        assertTrue(picker.isOpen());
        assertEquals(View.VISIBLE, picker.getVisibility());
        assertEquals(BottomSheetBehavior.STATE_EXPANDED, picker.behaviorForTest().getState());
        assertEquals(0f, picker.cardTranslationYForTest(), 0f);
    }

    private static void idle(long ms) {
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms));
    }
}
