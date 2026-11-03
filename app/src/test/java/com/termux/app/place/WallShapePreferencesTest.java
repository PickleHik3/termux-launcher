package com.termux.app.place;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.LayoutStyle;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.SurfaceProperty;
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_APP;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/**
 * Style, Corners and Margin per half of the wall, linked since #84: either half reads and writes
 * the Workspace's values, the keys every surface reads, and Home's old keys are never read or
 * written.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class WallShapePreferencesTest {

    private TermuxAppSharedPreferences prefs;
    private SharedPreferences sp;

    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.getApplication().getApplicationContext();
        sp = context.getSharedPreferences("wall-half-shape-test", Context.MODE_PRIVATE);
        sp.edit().clear().commit();
        prefs = new TermuxAppSharedPreferences(context, sp, null);
    }

    @Test
    public void homeReadsTheWorkspacesValues() {
        prefs.setLayoutStyle(LayoutStyle.FLOATING);
        prefs.setSurfaceBaseValue(SurfaceProperty.CORNER_RADIUS, 30);
        prefs.setSurfaceBaseValue(SurfaceProperty.SIDE_GAP, 18);
        assertEquals(WallShape.of(LayoutStyle.FLOATING, 30, 18), WallShape.read(prefs));
        assertEquals(LayoutStyle.FLOATING, prefs.getLayoutStyle(true));
        assertEquals(30, prefs.getLayoutCornersDp(true));
        assertEquals(18, prefs.getLayoutMarginDp(true));
    }

    @Test
    public void aStaleHomeKeyIsNeverRead() {
        sp.edit().putInt(TERMUX_APP.KEY_HOME_SURFACE_SIDE_GAP, 4)
            .putString(TERMUX_APP.KEY_HOME_LAYOUT_STYLE, "floating").commit();
        prefs.setSurfaceBaseValue(SurfaceProperty.SIDE_GAP, 16);
        prefs.setLayoutStyle(LayoutStyle.DOCKED);
        assertEquals(16, prefs.getLayoutMarginDp(true));
        assertEquals(LayoutStyle.DOCKED, prefs.getLayoutStyle(true));
    }

    @Test
    public void aWriteFromEitherHalfIsTheWorkspacesValue() {
        prefs.setLayoutStyle(true, LayoutStyle.FLOATING);
        prefs.setLayoutCornersDp(true, 8);
        prefs.setLayoutMarginDp(true, 20);
        assertEquals("the Workspace's keys are the ones every surface reads",
            LayoutStyle.FLOATING, prefs.getLayoutStyle());
        assertEquals(8, prefs.getSurfaceBaseValue(SurfaceProperty.CORNER_RADIUS));
        assertEquals(20, prefs.getSurfaceBaseValue(SurfaceProperty.SIDE_GAP));
        prefs.setLayoutCornersDp(false, 24);
        assertEquals(24, prefs.getLayoutCornersDp(true));
        assertFalse(sp.contains(TERMUX_APP.KEY_HOME_LAYOUT_STYLE)
            || sp.contains(TERMUX_APP.KEY_HOME_SURFACE_CORNER_RADIUS)
            || sp.contains(TERMUX_APP.KEY_HOME_SURFACE_SIDE_GAP));
    }

    @Test
    public void autoCornersAreKeptAsTheAutoToken() {
        prefs.setLayoutCornersDp(true, -1);
        assertEquals(-1, prefs.getLayoutCornersDp(true));
        assertEquals(-1, prefs.getLayoutCornersDp(false));
    }

    @Test
    public void aMissingPreferencesStoreWearsTheDefaults() {
        assertEquals(WallShape.DEFAULT, WallShape.read(null));
    }
}
