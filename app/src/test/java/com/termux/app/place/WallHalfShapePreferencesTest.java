package com.termux.app.place;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

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
 * Home's own Style, Corners and Margin (wall rework phase two, slice 2): seeded from the
 * Workspace's while unset, so an upgrade changes nothing on screen; pinned by the first write; and
 * a write to one half never reaches the other.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class WallHalfShapePreferencesTest {

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
    public void anUpgradeShowsHomeTheWorkspacesValues() {
        prefs.setLayoutStyle(LayoutStyle.FLOATING);
        prefs.setSurfaceBaseValue(SurfaceProperty.CORNER_RADIUS, 30);
        prefs.setSurfaceBaseValue(SurfaceProperty.SIDE_GAP, 18);
        assertEquals(WallHalfShape.read(prefs, false), WallHalfShape.read(prefs, true));
        assertEquals(WallHalfShape.of(LayoutStyle.FLOATING, 30, 18), WallHalfShape.read(prefs, true));
        assertFalse("seeding writes nothing", sp.contains(TERMUX_APP.KEY_HOME_LAYOUT_STYLE)
            || sp.contains(TERMUX_APP.KEY_HOME_SURFACE_CORNER_RADIUS)
            || sp.contains(TERMUX_APP.KEY_HOME_SURFACE_SIDE_GAP));
    }

    @Test
    public void anUnsetHomeValueFollowsTheWorkspaceUntilItIsWritten() {
        prefs.setSurfaceBaseValue(SurfaceProperty.SIDE_GAP, 10);
        assertEquals(10, prefs.getLayoutMarginDp(true));
        prefs.setSurfaceBaseValue(SurfaceProperty.SIDE_GAP, 16);
        assertEquals("still following", 16, prefs.getLayoutMarginDp(true));
        prefs.setLayoutMarginDp(true, 4);
        prefs.setSurfaceBaseValue(SurfaceProperty.SIDE_GAP, 40);
        assertEquals("pinned by the first write", 4, prefs.getLayoutMarginDp(true));
        assertEquals(40, prefs.getLayoutMarginDp(false));
    }

    @Test
    public void eachHalfWritesOnlyItsOwnValues() {
        prefs.setLayoutStyle(false, LayoutStyle.DOCKED);
        prefs.setLayoutCornersDp(false, 24);
        prefs.setLayoutMarginDp(false, 12);
        prefs.setLayoutStyle(true, LayoutStyle.FLOATING);
        prefs.setLayoutCornersDp(true, 8);
        prefs.setLayoutMarginDp(true, 20);
        assertEquals(WallHalfShape.of(LayoutStyle.DOCKED, 24, 12), WallHalfShape.read(prefs, false));
        assertEquals(WallHalfShape.of(LayoutStyle.FLOATING, 8, 20), WallHalfShape.read(prefs, true));
        assertEquals("the Workspace's keys are the ones every surface reads", LayoutStyle.DOCKED,
            prefs.getLayoutStyle());
        assertEquals(24, prefs.getSurfaceBaseValue(SurfaceProperty.CORNER_RADIUS));
        assertEquals(12, prefs.getSurfaceBaseValue(SurfaceProperty.SIDE_GAP));
    }

    @Test
    public void homesAutoCornersAreKeptAsTheAutoToken() {
        prefs.setLayoutCornersDp(true, -1);
        assertEquals(-1, prefs.getLayoutCornersDp(true));
        assertTrue(sp.contains(TERMUX_APP.KEY_HOME_SURFACE_CORNER_RADIUS));
    }

    @Test
    public void aMissingPreferencesStoreWearsTheDefaults() {
        assertEquals(WallHalfShape.DEFAULT, WallHalfShape.read(null, true));
        assertEquals(WallHalfShape.DEFAULT, WallHalfShape.read(null, false));
    }
}
