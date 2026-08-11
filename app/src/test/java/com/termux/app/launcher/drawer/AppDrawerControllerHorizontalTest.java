package com.termux.app.launcher.drawer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import com.termux.R;
import com.termux.app.Spring;
import com.termux.app.TermuxActivity;
import com.termux.app.launcher.drawer.AppDrawerTransitionGeometry.Frame;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.util.ReflectionHelpers;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class AppDrawerControllerHorizontalTest {

    private TermuxActivity activity;
    private TermuxAppSharedPreferences preferences;
    private AppDrawerController controller;
    private AppDrawerContentView content;
    private AppDrawerPlaneView plane;

    @Before public void setUp() {
        activity = Robolectric.buildActivity(TermuxActivity.class).get();
        activity.setContentView(R.layout.activity_termux);
        SharedPreferences raw = activity.getSharedPreferences("b4-controller", Context.MODE_PRIVATE);
        raw.edit().clear().commit();
        preferences = new TermuxAppSharedPreferences(activity, raw, null);
        ReflectionHelpers.setField(activity, "mPreferences", preferences);
        controller = activity.getAppDrawerController();
        content = new AppDrawerContentView(activity);
        plane = new AppDrawerPlaneView(activity);
        ReflectionHelpers.setField(controller, "mContent", content);
        ReflectionHelpers.setField(controller, "mOpenRect", new Frame(0f, 0f, 720f, 1280f));
    }

    @Test public void verticalSubtractsRopeButHorizontalUsesFullWidthAndOwnKeys() {
        preferences.setAppLauncherDrawerViewType("vertical");
        preferences.setAppLauncherDrawerGridColumnsVertical(5);
        invokePrepare();
        assertEquals(AppDrawerViewType.VERTICAL, content.getViewType());
        assertEquals(5, ((AppDrawerAppsAdapter) content.getGrid().getAdapter())
            .getMetrics().columns);

        preferences.setAppLauncherDrawerViewType("horizontal");
        preferences.setAppLauncherDrawerGridColumnsHorizontal(6);
        preferences.setAppLauncherDrawerGridRowsHorizontal(2);
        invokePrepare();
        AppDrawerHorizontalGridMetrics metrics = content.getHorizontalAdapter().getMetrics();
        assertEquals(AppDrawerViewType.HORIZONTAL, content.getViewType());
        assertEquals(720f, metrics.usablePageWidthPx, 0f);
        assertEquals(6, metrics.columns);
        assertEquals(2, metrics.rows);
    }

    @Test public void preferenceReloadClosesAndReconfiguresTheExistingSingleTree() {
        ReflectionHelpers.setField(controller, "mPlane", plane);
        ReflectionHelpers.setField(controller, "mEngaged", true);
        ReflectionHelpers.setField(controller, "mOpen", true);
        Spring progress = ReflectionHelpers.getField(controller, "mProgress");
        progress.reset(1f);
        preferences.setAppLauncherDrawerViewType("horizontal");

        controller.onPreferencesReloaded();

        assertFalse(controller.isEngaged());
        assertFalse(controller.isOpen());
        assertEquals(AppDrawerViewType.HORIZONTAL, content.getViewType());
        assertSame(plane, ReflectionHelpers.getField(controller, "mPlane"));
        assertSame(content, ReflectionHelpers.getField(controller, "mContent"));
    }

    private void invokePrepare() {
        ReflectionHelpers.callInstanceMethod(controller, "prepareContent",
            ReflectionHelpers.ClassParameter.from(AppDrawerPlaneView.class, plane));
    }
}
