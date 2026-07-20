package com.termux.shared.termux.settings.preferences;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class DockAppearancePreferencesTest {

    private TermuxAppSharedPreferences preferences;

    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.getApplication().getApplicationContext();
        SharedPreferences store = context.getSharedPreferences(
            "dock-appearance-preferences-test", Context.MODE_PRIVATE);
        store.edit().clear().commit();
        preferences = new TermuxAppSharedPreferences(context, store, null);
    }

    @Test
    public void blurRadiusUsesTheSameBoundsAsItsSlider() {
        preferences.setExtraKeysBlurRadius(-4);
        assertEquals(0, preferences.getExtraKeysBlurRadius());

        preferences.setExtraKeysBlurRadius(31);
        assertEquals(30, preferences.getExtraKeysBlurRadius());
    }

    @Test
    public void iconCountUsesTheSameBoundsAsItsSlider() {
        preferences.setAppLauncherButtonCount(0);
        assertEquals(1, preferences.getAppLauncherButtonCount());

        preferences.setAppLauncherButtonCount(21);
        assertEquals(20, preferences.getAppLauncherButtonCount());
    }

    @Test
    public void dockHeightRejectsValuesOutsideSupportedStorageRange() {
        preferences.setAppLauncherBarHeightScale(-1f);
        assertEquals(0.4f, preferences.getAppLauncherBarHeightScale(), 0f);

        preferences.setAppLauncherBarHeightScale(4f);
        assertEquals(3f, preferences.getAppLauncherBarHeightScale(), 0f);
    }
}
