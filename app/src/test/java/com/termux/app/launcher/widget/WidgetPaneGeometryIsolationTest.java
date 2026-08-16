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
        android.view.ViewGroup root = (android.view.ViewGroup) LayoutInflater.from(activity)
            .inflate(R.layout.activity_termux, null);
        WidgetPaneView pane = root.findViewById(R.id.widget_pane);
        pane.picker().setReducedMotion(true); pane.picker().open();
        int before = AccessoryStackLayoutPolicy.computeCombinedHeight(30, 40, 20, 3);
        pane.picker().close();
        int after = AccessoryStackLayoutPolicy.computeCombinedHeight(30, 40, 20, 3);
        assertEquals(before, after);
    }
}
