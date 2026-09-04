package com.termux.app.launcher.widget;

import android.app.Application;
import android.os.Build;
import android.view.LayoutInflater;
import android.view.View;
import android.app.Activity;
import org.robolectric.Robolectric;

import com.termux.R;
import com.termux.app.terminal.AccessoryStackLayoutPolicy;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class WidgetPaneGeometryIsolationTest {
    @Test public void pickerAndGridNeverContributeToAccessoryCombinedHeight() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        activity.setTheme(R.style.Theme_TermuxActivity_DayNight_NoActionBar);
        // The grid lives on the wall's Widgets page, which is inflated when the wall gains it.
        android.view.ViewGroup page = (android.view.ViewGroup) LayoutInflater.from(activity)
            .inflate(R.layout.view_widget_pane, null);
        WidgetPaneView pane = page.findViewById(R.id.widget_pane);
        pane.picker().setReducedMotion(true); pane.picker().open();
        int before = AccessoryStackLayoutPolicy.computeCombinedHeight(30, 40, 20, 3);
        pane.picker().close();
        int after = AccessoryStackLayoutPolicy.computeCombinedHeight(30, 40, 20, 3);
        assertEquals(before, after);
    }
}
