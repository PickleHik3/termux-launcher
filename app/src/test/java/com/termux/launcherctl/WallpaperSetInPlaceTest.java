package com.termux.launcherctl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.app.WallpaperManager;
import android.content.Intent;
import android.os.Build;
import android.os.Looper;

import com.termux.app.TermuxActivity;
import com.termux.shared.termux.TermuxConstants.TERMUX_APP.TERMUX_ACTIVITY;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * {@code launcherctl wallpaper set} re-dresses the launcher the way the picker's Apply does: the
 * same in-place apply on the launcher on screen, and no recreate while wallpaper mode is already
 * on (a recreate there closed an open Look editor).
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class WallpaperSetInPlaceTest {

    private Application app;
    private TermuxAppSharedPreferences preferences;
    private BiConsumer<TermuxActivity, Boolean> pickerApply;
    private final List<Object[]> inPlaceCalls = new ArrayList<>();

    @Before
    public void setUp() {
        app = RuntimeEnvironment.getApplication();
        preferences = TermuxAppSharedPreferences.build(app, false);
        assertNotNull(preferences);
        ReflectionHelpers.setStaticField(TermuxActivity.class, "sPendingStyleReloadOnNextResume", false);
        ReflectionHelpers.setStaticField(TermuxActivity.class, "sPendingStyleReloadRecreateActivity", true);
        ReflectionHelpers.setStaticField(TermuxActivity.class, "sLiveActivity", null);
        pickerApply = TermuxActivity.sHomeWallpaperInPlace;
        TermuxActivity.sHomeWallpaperInPlace = (activity, photo) -> inPlaceCalls.add(new Object[]{activity, photo});
    }

    @After
    public void tearDown() {
        TermuxActivity.sHomeWallpaperInPlace = pickerApply;
        ReflectionHelpers.setStaticField(TermuxActivity.class, "sLiveActivity", null);
    }

    @Test
    public void aSetOnTheLauncherOnScreenRunsThePickersInPlaceApply() {
        preferences.setUseSystemWallpaperEnabled(true);
        TermuxActivity live = liveActivity(true);

        DeviceControlRoutes.homeWallpaperLanded(app, WallpaperManager.FLAG_SYSTEM | WallpaperManager.FLAG_LOCK);
        Shadows.shadowOf(Looper.getMainLooper()).idle();

        assertEquals(1, inPlaceCalls.size());
        assertSame(live, inPlaceCalls.get(0)[0]);
        assertEquals(Boolean.TRUE, inPlaceCalls.get(0)[1]);
    }

    @Test
    public void aSetWhileTheModeIsOnAsksForAnInPlaceRestyle() {
        preferences.setUseSystemWallpaperEnabled(true);
        int before = stylingBroadcasts().size();

        DeviceControlRoutes.homeWallpaperLanded(app, WallpaperManager.FLAG_SYSTEM);

        List<Intent> styling = stylingBroadcasts();
        assertEquals(before + 1, styling.size());
        assertFalse("mode already on: no recreate",
            styling.get(styling.size() - 1).getBooleanExtra(TERMUX_ACTIVITY.EXTRA_RECREATE_ACTIVITY, true));
        assertTrue(preferences.isUseSystemWallpaperEnabled());
    }

    @Test
    public void aSetThatSwitchesTheModeOnStillRecreates() {
        preferences.setUseSystemWallpaperEnabled(false);

        DeviceControlRoutes.homeWallpaperLanded(app, WallpaperManager.FLAG_SYSTEM);

        List<Intent> styling = stylingBroadcasts();
        assertTrue(styling.get(styling.size() - 1)
            .getBooleanExtra(TERMUX_ACTIVITY.EXTRA_RECREATE_ACTIVITY, false));
        assertTrue(preferences.isUseSystemWallpaperEnabled());
    }

    @Test
    public void aLauncherOffScreenOnlyRecordsTheRestyle() {
        preferences.setUseSystemWallpaperEnabled(true);
        liveActivity(false);
        int before = stylingBroadcasts().size();

        DeviceControlRoutes.homeWallpaperLanded(app, WallpaperManager.FLAG_SYSTEM);
        Shadows.shadowOf(Looper.getMainLooper()).idle();

        assertTrue(inPlaceCalls.isEmpty());
        List<Intent> styling = stylingBroadcasts();
        assertEquals(before + 1, styling.size());
        assertFalse(styling.get(styling.size() - 1)
            .getBooleanExtra(TERMUX_ACTIVITY.EXTRA_RECREATE_ACTIVITY, true));
    }

    @Test
    public void aLockOnlySetLeavesTheLauncherAlone() {
        preferences.setUseSystemWallpaperEnabled(true);
        liveActivity(true);
        int before = stylingBroadcasts().size();

        DeviceControlRoutes.homeWallpaperLanded(app, WallpaperManager.FLAG_LOCK);
        Shadows.shadowOf(Looper.getMainLooper()).idle();

        assertTrue(inPlaceCalls.isEmpty());
        assertEquals(before, stylingBroadcasts().size());
    }

    private TermuxActivity liveActivity(boolean visible) {
        TermuxActivity activity = Robolectric.buildActivity(TermuxActivity.class).get();
        ReflectionHelpers.setField(activity, "mIsVisible", visible);
        ReflectionHelpers.setStaticField(TermuxActivity.class, "sLiveActivity", new WeakReference<>(activity));
        return activity;
    }

    private List<Intent> stylingBroadcasts() {
        List<Intent> styling = new ArrayList<>();
        for (Intent intent : Shadows.shadowOf(app).getBroadcastIntents())
            if (TERMUX_ACTIVITY.ACTION_RELOAD_STYLE.equals(intent.getAction())) styling.add(intent);
        return styling;
    }
}
