package com.termux.app.launcher.drawer;

import static org.junit.Assert.*;
import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class DockAppsRowDragLifecycleTest {
    @Test public void threeRowsRemainIndependentlyDisableable() {
        Context context = RuntimeEnvironment.getApplication();
        SharedPreferences raw = context.getSharedPreferences("b6-row-lifecycle", Context.MODE_PRIVATE);
        raw.edit().clear().commit();
        TermuxAppSharedPreferences prefs = new TermuxAppSharedPreferences(context, raw, null);
        prefs.setAppLauncherAppsRowEnabled(false);
        prefs.setAppLauncherAzRowEnabled(true);
        prefs.setAppLauncherExtraKeysRowEnabled(true);
        assertFalse(prefs.isAppLauncherAppsRowEnabled());
        assertTrue(prefs.isAppLauncherAzRowEnabled());
        assertTrue(prefs.isAppLauncherExtraKeysRowEnabled());
        prefs.setAppLauncherAzRowEnabled(false);
        assertFalse(prefs.isAppLauncherAzRowEnabled());
        assertTrue(prefs.isAppLauncherExtraKeysRowEnabled());
    }
}
