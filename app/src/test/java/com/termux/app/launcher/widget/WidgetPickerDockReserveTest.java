package com.termux.app.launcher.widget;

import static org.junit.Assert.assertEquals;

import android.app.Activity;
import android.app.Application;
import android.os.Build;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** The Add widget sheet ends above the dock's band instead of under it. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.S, application = Application.class)
public class WidgetPickerDockReserveTest {
    @Test public void sheetBottomPaddingFollowsTheDockReserve() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        activity.setTheme(com.termux.R.style.Theme_TermuxActivity_DayNight_NoActionBar);
        WidgetPickerSheetView picker = new WidgetPickerSheetView(activity, item -> { });
        assertEquals(0, picker.getPaddingBottom());
        picker.setDockReservePx(240);
        assertEquals(240, picker.getPaddingBottom());
        picker.setDockReservePx(-5);
        assertEquals(0, picker.getPaddingBottom());
    }
}
