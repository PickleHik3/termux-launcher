package com.termux.app.launcher.widget;

import android.app.Application;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.util.SizeF;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.S, application = Application.class)
public class WidgetSizeOptionsPolicyTest {
    @Test public void roundingPortraitLandscapeCategorySizesAndDedup() {
        WidgetSizeOptionsPolicy.Result portrait = WidgetSizeOptionsPolicy.calculate(null,
            201, 399, 2f, Configuration.ORIENTATION_PORTRAIT, 31);
        assertEquals(101, portrait.options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH));
        assertEquals(200, portrait.options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT));
        assertEquals(AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN,
            portrait.options.getInt(AppWidgetManager.OPTION_APPWIDGET_HOST_CATEGORY));
        WidgetSizeOptionsPolicy.Result landscape = WidgetSizeOptionsPolicy.calculate(portrait.options,
            600, 180, 2f, Configuration.ORIENTATION_LANDSCAPE, 31);
        assertEquals(101, landscape.options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH));
        assertEquals(300, landscape.options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH));
        assertEquals(90, landscape.options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT));
        ArrayList<SizeF> sizes = landscape.options.getParcelableArrayList(
            AppWidgetManager.OPTION_APPWIDGET_SIZES);
        assertEquals(2, sizes.size());
        assertFalse(WidgetSizeOptionsPolicy.calculate(landscape.options, 600, 180, 2f,
            Configuration.ORIENTATION_LANDSCAPE, 31).changed);
    }

    @Test public void densityChangesAndDegenerateSizes() {
        WidgetSizeOptionsPolicy.Result first = WidgetSizeOptionsPolicy.calculate(null,
            200, 100, 2f, Configuration.ORIENTATION_PORTRAIT, 30);
        assertTrue(first.changed);
        assertEquals(100, first.options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH));
        assertFalse(WidgetSizeOptionsPolicy.calculate(first.options, 0, 100, 2f,
            Configuration.ORIENTATION_PORTRAIT, 30).valid);
        assertEquals(200, WidgetSizeOptionsPolicy.calculate(first.options, 200, 100, 1f,
            Configuration.ORIENTATION_PORTRAIT, 30).options.getInt(
                AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH));
    }

    /** One grid for both orientations (ADR 0020 §12): both sizes at once, pushed on change. */
    @Test
    public void bothOrientationsGiveTheUnionAndBothSizesAndOnlyAChangeIsPushed() {
        WidgetSizeOptionsPolicy.Result first = WidgetSizeOptionsPolicy.calculateBoth(null,
            400, 600, 700, 300, 2f, 31);
        assertTrue(first.valid);
        assertTrue(first.changed);
        android.os.Bundle o = first.options;
        assertEquals(200, o.getInt(android.appwidget.AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH));
        assertEquals(150, o.getInt(android.appwidget.AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT));
        assertEquals(350, o.getInt(android.appwidget.AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH));
        assertEquals(300, o.getInt(android.appwidget.AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT));
        java.util.ArrayList<android.util.SizeF> sizes = o.getParcelableArrayList(
            android.appwidget.AppWidgetManager.OPTION_APPWIDGET_SIZES);
        assertEquals(2, sizes.size());
        assertTrue(sizes.contains(new android.util.SizeF(200, 300)));
        assertTrue(sizes.contains(new android.util.SizeF(350, 150)));
        WidgetSizeOptionsPolicy.Result again = WidgetSizeOptionsPolicy.calculateBoth(o,
            400, 600, 700, 300, 2f, 31);
        assertFalse("the same sizes push nothing", again.changed);
        assertTrue(WidgetSizeOptionsPolicy.calculateBoth(o, 400, 640, 700, 300, 2f, 31).changed);
    }
}
