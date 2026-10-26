package com.termux.app.surfaces;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import com.termux.app.dock.PeekGlassGeometry;
import com.termux.app.dock.PeekGlassPreferences;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.SurfaceProperty;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.SurfaceSlot;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.WallHalf;
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_APP;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/** A card's Reset puts back every store key the card's rows show. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class EditorCardResetTest {

    private TermuxAppSharedPreferences prefs;
    private SharedPreferences store;

    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.getApplication().getApplicationContext();
        store = context.getSharedPreferences("editor-card-reset-test", Context.MODE_PRIVATE);
        store.edit().clear().commit();
        prefs = new TermuxAppSharedPreferences(context, store, null);
    }

    @Test
    public void homeFrostComesBackEvenWhenTheEditorOpenedOnIt() {
        prefs.setSurfaceBaseValue(SurfaceProperty.BLUR, WallHalf.HOME, 40);
        prefs.setSurfaceBaseValue(SurfaceProperty.OPACITY, WallHalf.HOME, 90);
        EditorCardReset.resetStore(prefs, EditorCard.HOME, WallHalf.HOME);
        assertEquals(TERMUX_APP.DEFAULT_SURFACE_BASE_BLUR,
            prefs.getSurfaceBaseValue(SurfaceProperty.BLUR, WallHalf.HOME));
        assertEquals(TERMUX_APP.DEFAULT_SURFACE_BASE_OPACITY,
            prefs.getSurfaceBaseValue(SurfaceProperty.OPACITY, WallHalf.HOME));
    }

    @Test
    public void workspaceResetLeavesHomeAlone() {
        prefs.setSurfaceBaseValue(SurfaceProperty.BLUR, WallHalf.HOME, 40);
        prefs.setSurfaceBaseValue(SurfaceProperty.BLUR, WallHalf.WORKSPACE, 41);
        EditorCardReset.resetStore(prefs, EditorCard.WORKSPACE, WallHalf.WORKSPACE);
        assertEquals(40, prefs.getSurfaceBaseValue(SurfaceProperty.BLUR, WallHalf.HOME));
        assertEquals(TERMUX_APP.DEFAULT_SURFACE_BASE_BLUR,
            prefs.getSurfaceBaseValue(SurfaceProperty.BLUR, WallHalf.WORKSPACE));
    }

    @Test
    public void dockResetCoversItsOwnKeysAndGlassPosition() {
        prefs.setLayoutCornersDp(false, 4);
        prefs.setLayoutMarginDp(false, 30);
        prefs.setAppLauncherButtonCount(12);
        PeekGlassPreferences.setReflection(store, 5);
        prefs.setSurfaceGlassPosition(SurfaceSlot.DOCK, 77);
        EditorCardReset.resetStore(prefs, EditorCard.DOCK, WallHalf.WORKSPACE);
        assertEquals(TERMUX_APP.DEFAULT_SURFACE_BASE_CORNER_RADIUS, prefs.getLayoutCornersDp(false));
        assertEquals(TERMUX_APP.DEFAULT_SURFACE_BASE_SIDE_GAP, prefs.getLayoutMarginDp(false));
        assertEquals(TERMUX_APP.DEFAULT_APP_LAUNCHER_BUTTON_COUNT, prefs.getAppLauncherButtonCount());
        assertEquals(PeekGlassGeometry.REFLECTION_DEFAULT, PeekGlassPreferences.reflection(store));
        assertEquals(-1, prefs.getSurfaceGlassPosition(SurfaceSlot.DOCK));
    }

    @Test
    public void keyboardResetReturnsKeyOpacityToTheTheme() {
        prefs.setInAppKeyboardKeyOpacity(59);
        EditorCardReset.resetStore(prefs, EditorCard.KEYBOARD, WallHalf.WORKSPACE);
        assertFalse(prefs.getInAppKeyboardKeyOpacity() >= 0);
    }
}
