package com.termux.shared.termux.settings.preferences;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.SurfaceProperty;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.SurfaceSlot;

import com.termux.app.look.TerminalPageIdFixture;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Each half of the wall wears its own look values (ADR 0013): the shared keys mean both halves,
 * a half's own key wins for it once written, and a write that names no half lands on both.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class SurfaceHalfTest {

    private TermuxAppSharedPreferences preferences;
    private SharedPreferences store;

    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.getApplication().getApplicationContext();
        store = context.getSharedPreferences("surface-half-test", Context.MODE_PRIVATE);
        store.edit().clear().commit();
        preferences = new TermuxAppSharedPreferences(context, store, null);
        com.termux.app.look.TerminalPageIdFixture.give(preferences);
    }

    // ------------------------------------------------------------------ equal until touched

    @Test
    public void unsetHalves_readTheSharedValues() {
        preferences.setSurfaceBaseValue(SurfaceProperty.OPACITY, 40);
        preferences.detachSurfaceValue(SurfaceSlot.STATUS, SurfaceProperty.BLUR, 22);

        for (int half : new int[] {TermuxAppSharedPreferences.PAGE_WALL, TermuxAppSharedPreferences.terminalPageId()}) {
            assertEquals(40, preferences.getAppBarOpacity(half));
            assertEquals(40, preferences.getSurfaceBaseValue(SurfaceProperty.OPACITY, half));
            assertEquals(22, preferences.getStatusBarBlurRadius(half));
            assertFalse(preferences.isSurfaceInheriting(SurfaceSlot.STATUS, SurfaceProperty.BLUR,
                half));
        }
        assertEquals(40, preferences.getAppBarOpacity(com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.terminalPageId()));
    }

    // ------------------------------------------------------------------ a half's own value

    @Test
    public void halfBase_movesOnlyThatHalfsFollowers() {
        preferences.setSurfaceBaseValue(SurfaceProperty.OPACITY, 40);
        preferences.setSurfaceBaseValue(SurfaceProperty.OPACITY, TermuxAppSharedPreferences.PAGE_WALL, 12);

        assertEquals(12, preferences.getAppBarOpacity(TermuxAppSharedPreferences.PAGE_WALL));
        assertEquals(12, preferences.getStatusBarOpacity(TermuxAppSharedPreferences.PAGE_WALL));
        assertEquals(12, preferences.getInAppKeyboardBackgroundOpacity(TermuxAppSharedPreferences.PAGE_WALL));
        // Base is wall-wide since the halves became pages: Home's write is the wall's.
        assertEquals(12, preferences.getAppBarOpacity(TermuxAppSharedPreferences.terminalPageId()));
        // The no-half reads are today's callers: they answer the Workspace.
        assertEquals(12, preferences.getAppBarOpacity(com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.terminalPageId()));
        assertEquals(12, preferences.getSurfaceBaseValue(SurfaceProperty.OPACITY));
        // The terminal is only on the Workspace, and reads Base.
        assertEquals(12, preferences.getTerminalBackgroundOpacity());
    }

    @Test
    public void halfDetach_leavesTheOtherHalfLinked() {
        preferences.setSurfaceBaseValue(SurfaceProperty.TINT, 30);
        preferences.detachSurfaceValue(SurfaceSlot.DOCK, SurfaceProperty.TINT, TermuxAppSharedPreferences.terminalPageId(),
            80);

        assertEquals(80, preferences.getDockTintStrength(TermuxAppSharedPreferences.terminalPageId()));
        assertEquals(80, preferences.getDockTintStrength(com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.terminalPageId()));
        assertEquals(30, preferences.getDockTintStrength(TermuxAppSharedPreferences.PAGE_WALL));
        assertTrue(preferences.isSurfaceInheriting(SurfaceSlot.DOCK, SurfaceProperty.TINT,
            TermuxAppSharedPreferences.PAGE_WALL));
        assertEquals(1, preferences.surfaceOverrideCount(SurfaceSlot.DOCK, TermuxAppSharedPreferences.terminalPageId()));
        assertEquals(0, preferences.surfaceOverrideCount(SurfaceSlot.DOCK, TermuxAppSharedPreferences.PAGE_WALL));
    }

    @Test
    public void halfWrite_followsThatHalfsLink() {
        preferences.setSurfaceBaseValue(SurfaceProperty.GRAIN, 5);
        preferences.detachSurfaceValue(SurfaceSlot.KEYBOARD, SurfaceProperty.GRAIN,
            TermuxAppSharedPreferences.PAGE_WALL, 20);

        // Home's keyboard is detached: the write lands on its own value, Base stays.
        preferences.setSurfaceValue(SurfaceSlot.KEYBOARD, SurfaceProperty.GRAIN, TermuxAppSharedPreferences.PAGE_WALL, 33);
        assertEquals(33, preferences.getInAppKeyboardGrain(TermuxAppSharedPreferences.PAGE_WALL));
        assertEquals(5, preferences.getSurfaceBaseValue(SurfaceProperty.GRAIN, TermuxAppSharedPreferences.PAGE_WALL));

        // The link is wall-wide, so the Workspace's keyboard is detached too; its write lands on
        // the Terminal page's own value and Base stays.
        preferences.setSurfaceValue(SurfaceSlot.KEYBOARD, SurfaceProperty.GRAIN,
            TermuxAppSharedPreferences.terminalPageId(), 9);
        assertEquals(9, preferences.getInAppKeyboardGrain(TermuxAppSharedPreferences.terminalPageId()));
        assertEquals(33, preferences.getInAppKeyboardGrain(TermuxAppSharedPreferences.PAGE_WALL));
        assertEquals(5, preferences.getSurfaceBaseValue(SurfaceProperty.GRAIN, TermuxAppSharedPreferences.terminalPageId()));
        assertEquals(5, preferences.getDockGlassGrain(TermuxAppSharedPreferences.terminalPageId()));
        assertEquals(5, preferences.getDockGlassGrain(TermuxAppSharedPreferences.PAGE_WALL));
    }

    @Test
    public void canvas_holdsOneValue() {
        preferences.detachSurfaceValue(SurfaceSlot.CANVAS, SurfaceProperty.OPACITY,
            TermuxAppSharedPreferences.PAGE_WALL, 70);
        String canvasKey = TermuxAppSharedPreferences.surfaceOverrideKey(SurfaceSlot.CANVAS,
            SurfaceProperty.OPACITY);
        // One wall value under the plain key, never a page override.
        assertTrue(store.contains(canvasKey));
        assertFalse(store.contains(canvasKey + "@" + TerminalPageIdFixture.TERMINAL_PAGE_ID));
        assertEquals(70, preferences.getTerminalBackgroundOpacity());
        assertFalse(preferences.isSurfaceInheriting(SurfaceSlot.CANVAS, SurfaceProperty.OPACITY,
            TermuxAppSharedPreferences.terminalPageId()));
    }

    @Test
    public void cornersAndMargin_stayShared() {
        preferences.setSurfaceBaseValue(SurfaceProperty.SIDE_GAP, TermuxAppSharedPreferences.PAGE_WALL, 20);
        assertEquals(20, preferences.getSurfaceBaseValue(SurfaceProperty.SIDE_GAP,
            TermuxAppSharedPreferences.terminalPageId()));
        assertEquals(20, preferences.getLayoutMarginDp(false));
        assertEquals(20, preferences.getLayoutMarginDp(true));
    }

    // ------------------------------------------------------------------ writes that name no half

    @Test
    public void sharedWrite_clearsBothHalves() {
        preferences.setSurfaceBaseValue(SurfaceProperty.OPACITY, TermuxAppSharedPreferences.PAGE_WALL, 12);
        preferences.setSurfaceBaseValue(SurfaceProperty.OPACITY, TermuxAppSharedPreferences.terminalPageId(), 60);

        preferences.setSurfaceBaseValue(SurfaceProperty.OPACITY, 45);

        assertEquals(45, preferences.getAppBarOpacity(TermuxAppSharedPreferences.PAGE_WALL));
        assertEquals(45, preferences.getAppBarOpacity(TermuxAppSharedPreferences.terminalPageId()));
        // Base is one wall value: no half twins and no page override.
        for (String suffix : new String[]{"_home", "_workspace",
            "@" + TerminalPageIdFixture.TERMINAL_PAGE_ID})
            assertFalse(store.contains(SurfaceProperty.OPACITY.baseKey + suffix));
    }

    @Test
    public void settingsSlider_landsOnBothHalves_whateverEachHalfsLink() {
        preferences.setSurfaceBaseValue(SurfaceProperty.OPACITY, 30);
        // Home's status bar holds its own number; the link is wall-wide, so the Workspace's does
        // too.
        preferences.detachSurfaceValue(SurfaceSlot.STATUS, SurfaceProperty.OPACITY,
            TermuxAppSharedPreferences.PAGE_WALL, 90);

        preferences.setStatusBarOpacity(55);

        assertEquals(55, preferences.getStatusBarOpacity(TermuxAppSharedPreferences.PAGE_WALL));
        assertEquals(55, preferences.getStatusBarOpacity(TermuxAppSharedPreferences.terminalPageId()));
        // It wrote the wall's own value, so the surface stays detached on both halves.
        assertFalse(preferences.isSurfaceInheriting(SurfaceSlot.STATUS, SurfaceProperty.OPACITY,
            TermuxAppSharedPreferences.PAGE_WALL));
        assertFalse(preferences.isSurfaceInheriting(SurfaceSlot.STATUS, SurfaceProperty.OPACITY,
            TermuxAppSharedPreferences.terminalPageId()));
    }

    @Test
    public void sharedDetach_clearsBothHalvesOwnValues() {
        preferences.detachSurfaceValue(SurfaceSlot.DOCK, SurfaceProperty.BLUR, TermuxAppSharedPreferences.PAGE_WALL, 3);
        preferences.detachSurfaceValue(SurfaceSlot.DOCK, SurfaceProperty.BLUR, 17);

        assertEquals(17, preferences.getExtraKeysBlurRadius(TermuxAppSharedPreferences.PAGE_WALL));
        assertEquals(17, preferences.getExtraKeysBlurRadius(TermuxAppSharedPreferences.terminalPageId()));
    }

    @Test
    public void sharedMaterial_clearsBothHalves() {
        preferences.setSurfaceMaterial(TermuxAppSharedPreferences.PAGE_WALL, "frost");
        preferences.setSurfaceMaterialIntensity(TermuxAppSharedPreferences.PAGE_WALL, 80);
        assertEquals("frost", preferences.getSurfaceMaterial(TermuxAppSharedPreferences.PAGE_WALL));
        assertEquals(80, preferences.getSurfaceMaterialIntensity(TermuxAppSharedPreferences.PAGE_WALL));

        preferences.setSurfaceMaterial("clear");
        preferences.setSurfaceMaterialIntensity(10);
        assertEquals("clear", preferences.getSurfaceMaterial(TermuxAppSharedPreferences.PAGE_WALL));
        assertEquals(10, preferences.getSurfaceMaterialIntensity(TermuxAppSharedPreferences.PAGE_WALL));
    }

    // ------------------------------------------------------------------ the editor's helpers

    @Test
    public void resetSurface_putsOneHalfBackOnItsLook() {
        preferences.setSurfaceBaseValue(SurfaceProperty.OPACITY, 25);
        preferences.detachSurfaceValue(SurfaceSlot.KEYBOARD, SurfaceProperty.OPACITY,
            TermuxAppSharedPreferences.PAGE_WALL, 90);
        preferences.detachSurfaceValue(SurfaceSlot.KEYBOARD, SurfaceProperty.OPACITY,
            TermuxAppSharedPreferences.terminalPageId(), 80);

        preferences.resetSurfaceToLook(SurfaceSlot.KEYBOARD, TermuxAppSharedPreferences.PAGE_WALL);

        assertTrue(preferences.isSurfaceInheriting(SurfaceSlot.KEYBOARD, SurfaceProperty.OPACITY,
            TermuxAppSharedPreferences.PAGE_WALL));
        assertEquals(preferences.getSurfaceBaseValue(SurfaceProperty.OPACITY, TermuxAppSharedPreferences.PAGE_WALL),
            preferences.getSurfaceValue(SurfaceSlot.KEYBOARD, SurfaceProperty.OPACITY,
                TermuxAppSharedPreferences.PAGE_WALL));
        assertFalse(preferences.isSurfaceInheriting(SurfaceSlot.KEYBOARD, SurfaceProperty.OPACITY,
            TermuxAppSharedPreferences.terminalPageId()));
        assertEquals(80, preferences.getInAppKeyboardBackgroundOpacity(TermuxAppSharedPreferences.terminalPageId()));
    }

    @Test
    public void surfaceValue_matchesTheNamedGetters() {
        preferences.setSurfaceBaseValue(SurfaceProperty.BLUR, TermuxAppSharedPreferences.PAGE_WALL, 9);
        preferences.detachSurfaceValue(SurfaceSlot.STATUS, SurfaceProperty.GRAIN,
            TermuxAppSharedPreferences.terminalPageId(), 44);
        for (int half : new int[] {TermuxAppSharedPreferences.PAGE_WALL, TermuxAppSharedPreferences.terminalPageId()}) {
            assertEquals(preferences.getExtraKeysBlurRadius(half),
                preferences.getSurfaceValue(SurfaceSlot.DOCK, SurfaceProperty.BLUR, half));
            assertEquals(preferences.getStatusBarGrain(half),
                preferences.getSurfaceValue(SurfaceSlot.STATUS, SurfaceProperty.GRAIN, half));
        }
    }
}
