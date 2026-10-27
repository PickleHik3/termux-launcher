package com.termux.shared.termux.settings.preferences;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.SurfaceProperty;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.SurfaceSlot;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.WallHalf;

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

        for (WallHalf half : WallHalf.values()) {
            assertEquals(40, preferences.getAppBarOpacity(half));
            assertEquals(40, preferences.getSurfaceBaseValue(SurfaceProperty.OPACITY, half));
            assertEquals(22, preferences.getStatusBarBlurRadius(half));
            assertFalse(preferences.isSurfaceInheriting(SurfaceSlot.STATUS, SurfaceProperty.BLUR,
                half));
        }
        assertEquals(40, preferences.getAppBarOpacity());
    }

    // ------------------------------------------------------------------ a half's own value

    @Test
    public void halfBase_movesOnlyThatHalfsFollowers() {
        preferences.setSurfaceBaseValue(SurfaceProperty.OPACITY, 40);
        preferences.setSurfaceBaseValue(SurfaceProperty.OPACITY, WallHalf.HOME, 12);

        assertEquals(12, preferences.getAppBarOpacity(WallHalf.HOME));
        assertEquals(12, preferences.getStatusBarOpacity(WallHalf.HOME));
        assertEquals(12, preferences.getInAppKeyboardBackgroundOpacity(WallHalf.HOME));
        // Base is wall-wide since the halves became pages: Home's write is the wall's.
        assertEquals(12, preferences.getAppBarOpacity(WallHalf.WORKSPACE));
        // The no-half reads are today's callers: they answer the Workspace.
        assertEquals(12, preferences.getAppBarOpacity());
        assertEquals(12, preferences.getSurfaceBaseValue(SurfaceProperty.OPACITY));
        // The terminal is only on the Workspace, and reads Base.
        assertEquals(12, preferences.getTerminalBackgroundOpacity());
    }

    @Test
    public void halfDetach_leavesTheOtherHalfLinked() {
        preferences.setSurfaceBaseValue(SurfaceProperty.TINT, 30);
        preferences.detachSurfaceValue(SurfaceSlot.DOCK, SurfaceProperty.TINT, WallHalf.WORKSPACE,
            80);

        assertEquals(80, preferences.getDockTintStrength(WallHalf.WORKSPACE));
        assertEquals(80, preferences.getDockTintStrength());
        assertEquals(30, preferences.getDockTintStrength(WallHalf.HOME));
        assertTrue(preferences.isSurfaceInheriting(SurfaceSlot.DOCK, SurfaceProperty.TINT,
            WallHalf.HOME));
        assertEquals(1, preferences.surfaceOverrideCount(SurfaceSlot.DOCK, WallHalf.WORKSPACE));
        assertEquals(0, preferences.surfaceOverrideCount(SurfaceSlot.DOCK, WallHalf.HOME));
    }

    @Test
    public void halfWrite_followsThatHalfsLink() {
        preferences.setSurfaceBaseValue(SurfaceProperty.GRAIN, 5);
        preferences.detachSurfaceValue(SurfaceSlot.KEYBOARD, SurfaceProperty.GRAIN,
            WallHalf.HOME, 20);

        // Home's keyboard is detached: the write lands on its own value, Base stays.
        preferences.setSurfaceValue(SurfaceSlot.KEYBOARD, SurfaceProperty.GRAIN, WallHalf.HOME, 33);
        assertEquals(33, preferences.getInAppKeyboardGrain(WallHalf.HOME));
        assertEquals(5, preferences.getSurfaceBaseValue(SurfaceProperty.GRAIN, WallHalf.HOME));

        // The link is wall-wide, so the Workspace's keyboard is detached too; its write lands on
        // the Terminal page's own value and Base stays.
        preferences.setSurfaceValue(SurfaceSlot.KEYBOARD, SurfaceProperty.GRAIN,
            WallHalf.WORKSPACE, 9);
        assertEquals(9, preferences.getInAppKeyboardGrain(WallHalf.WORKSPACE));
        assertEquals(33, preferences.getInAppKeyboardGrain(WallHalf.HOME));
        assertEquals(5, preferences.getSurfaceBaseValue(SurfaceProperty.GRAIN, WallHalf.WORKSPACE));
        assertEquals(5, preferences.getDockGlassGrain(WallHalf.WORKSPACE));
        assertEquals(5, preferences.getDockGlassGrain(WallHalf.HOME));
    }

    @Test
    public void canvas_holdsOneValue() {
        preferences.detachSurfaceValue(SurfaceSlot.CANVAS, SurfaceProperty.OPACITY,
            WallHalf.HOME, 70);
        String canvasKey = TermuxAppSharedPreferences.surfaceOverrideKey(SurfaceSlot.CANVAS,
            SurfaceProperty.OPACITY);
        // One wall value under the plain key, never a page override.
        assertTrue(store.contains(canvasKey));
        assertFalse(store.contains(canvasKey + "@" + TerminalPageIdFixture.TERMINAL_PAGE_ID));
        assertEquals(70, preferences.getTerminalBackgroundOpacity());
        assertFalse(preferences.isSurfaceInheriting(SurfaceSlot.CANVAS, SurfaceProperty.OPACITY,
            WallHalf.WORKSPACE));
    }

    @Test
    public void cornersAndMargin_stayShared() {
        preferences.setSurfaceBaseValue(SurfaceProperty.SIDE_GAP, WallHalf.HOME, 20);
        assertEquals(20, preferences.getSurfaceBaseValue(SurfaceProperty.SIDE_GAP,
            WallHalf.WORKSPACE));
        assertEquals(20, preferences.getLayoutMarginDp(false));
        assertEquals(20, preferences.getLayoutMarginDp(true));
    }

    // ------------------------------------------------------------------ writes that name no half

    @Test
    public void sharedWrite_clearsBothHalves() {
        preferences.setSurfaceBaseValue(SurfaceProperty.OPACITY, WallHalf.HOME, 12);
        preferences.setSurfaceBaseValue(SurfaceProperty.OPACITY, WallHalf.WORKSPACE, 60);

        preferences.setSurfaceBaseValue(SurfaceProperty.OPACITY, 45);

        assertEquals(45, preferences.getAppBarOpacity(WallHalf.HOME));
        assertEquals(45, preferences.getAppBarOpacity(WallHalf.WORKSPACE));
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
            WallHalf.HOME, 90);

        preferences.setStatusBarOpacity(55);

        assertEquals(55, preferences.getStatusBarOpacity(WallHalf.HOME));
        assertEquals(55, preferences.getStatusBarOpacity(WallHalf.WORKSPACE));
        // It wrote the wall's own value, so the surface stays detached on both halves.
        assertFalse(preferences.isSurfaceInheriting(SurfaceSlot.STATUS, SurfaceProperty.OPACITY,
            WallHalf.HOME));
        assertFalse(preferences.isSurfaceInheriting(SurfaceSlot.STATUS, SurfaceProperty.OPACITY,
            WallHalf.WORKSPACE));
    }

    @Test
    public void sharedDetach_clearsBothHalvesOwnValues() {
        preferences.detachSurfaceValue(SurfaceSlot.DOCK, SurfaceProperty.BLUR, WallHalf.HOME, 3);
        preferences.detachSurfaceValue(SurfaceSlot.DOCK, SurfaceProperty.BLUR, 17);

        assertEquals(17, preferences.getExtraKeysBlurRadius(WallHalf.HOME));
        assertEquals(17, preferences.getExtraKeysBlurRadius(WallHalf.WORKSPACE));
    }

    @Test
    public void sharedMaterial_clearsBothHalves() {
        preferences.setSurfaceMaterial(WallHalf.HOME, "frost");
        preferences.setSurfaceMaterialIntensity(WallHalf.HOME, 80);
        assertEquals("frost", preferences.getSurfaceMaterial(WallHalf.HOME));
        assertEquals(80, preferences.getSurfaceMaterialIntensity(WallHalf.HOME));

        preferences.setSurfaceMaterial("clear");
        preferences.setSurfaceMaterialIntensity(10);
        assertEquals("clear", preferences.getSurfaceMaterial(WallHalf.HOME));
        assertEquals(10, preferences.getSurfaceMaterialIntensity(WallHalf.HOME));
    }

    // ------------------------------------------------------------------ the editor's helpers

    @Test
    public void copyLook_makesTheOtherHalfEqual() {
        preferences.setSurfaceBaseValue(SurfaceProperty.OPACITY, 40);
        preferences.setSurfaceBaseValue(SurfaceProperty.OPACITY, WallHalf.WORKSPACE, 75);
        preferences.setSurfaceBaseValue(SurfaceProperty.BLUR, WallHalf.WORKSPACE, 14);
        preferences.detachSurfaceValue(SurfaceSlot.STATUS, SurfaceProperty.TINT,
            WallHalf.WORKSPACE, 66);
        preferences.detachSurfaceValue(SurfaceSlot.DOCK, SurfaceProperty.GRAIN, WallHalf.HOME, 50);
        preferences.setSurfaceMaterial(WallHalf.WORKSPACE, "solid");

        preferences.copyLookToOtherHalf(WallHalf.WORKSPACE);

        assertEquals(75, preferences.getAppBarOpacity(WallHalf.HOME));
        assertEquals(14, preferences.getExtraKeysBlurRadius(WallHalf.HOME));
        assertEquals(66, preferences.getStatusBarTintStrength(WallHalf.HOME));
        // Home's detach was the wall's, so the Workspace's dock grain was detached as well and
        // the copy leaves Home detached with the same number.
        assertFalse(preferences.isSurfaceInheriting(SurfaceSlot.DOCK, SurfaceProperty.GRAIN,
            WallHalf.HOME));
        assertEquals(preferences.getDockGlassGrain(WallHalf.WORKSPACE),
            preferences.getDockGlassGrain(WallHalf.HOME));
        assertEquals("solid", preferences.getSurfaceMaterial(WallHalf.HOME));
    }

    @Test
    public void copySurface_copiesWhatShows_notTheLink() {
        preferences.setSurfaceBaseValue(SurfaceProperty.OPACITY, 10);
        // The Workspace's dock holds 70 as the Terminal page's own value; Home follows the
        // wall's Base (10), so Home's dock must hold 70 as its own to look the same.
        preferences.detachSurfaceValue(SurfaceSlot.DOCK, SurfaceProperty.OPACITY,
            WallHalf.WORKSPACE, 70);
        preferences.copySurfaceToOtherHalf(SurfaceSlot.DOCK, WallHalf.WORKSPACE);

        assertEquals(70, preferences.getAppBarOpacity(WallHalf.HOME));
        assertFalse(preferences.isSurfaceInheriting(SurfaceSlot.DOCK, SurfaceProperty.OPACITY,
            WallHalf.HOME));
        // Home's other surfaces keep Home's Look.
        assertEquals(10, preferences.getStatusBarOpacity(WallHalf.HOME));
    }

    @Test
    public void copySurface_staysLinked_whereTheBasesAgree() {
        preferences.setSurfaceBaseValue(SurfaceProperty.BLUR, 12);
        preferences.copySurfaceToOtherHalf(SurfaceSlot.STATUS, WallHalf.HOME);
        assertTrue(preferences.isSurfaceInheriting(SurfaceSlot.STATUS, SurfaceProperty.BLUR,
            WallHalf.WORKSPACE));
        assertEquals(12, preferences.getStatusBarBlurRadius(WallHalf.WORKSPACE));
    }

    @Test
    public void resetSurface_putsOneHalfBackOnItsLook() {
        preferences.setSurfaceBaseValue(SurfaceProperty.OPACITY, 25);
        preferences.detachSurfaceValue(SurfaceSlot.KEYBOARD, SurfaceProperty.OPACITY,
            WallHalf.HOME, 90);
        preferences.detachSurfaceValue(SurfaceSlot.KEYBOARD, SurfaceProperty.OPACITY,
            WallHalf.WORKSPACE, 80);

        preferences.resetSurfaceToLook(SurfaceSlot.KEYBOARD, WallHalf.HOME);

        assertTrue(preferences.isSurfaceInheriting(SurfaceSlot.KEYBOARD, SurfaceProperty.OPACITY,
            WallHalf.HOME));
        assertEquals(preferences.getSurfaceBaseValue(SurfaceProperty.OPACITY, WallHalf.HOME),
            preferences.getSurfaceValue(SurfaceSlot.KEYBOARD, SurfaceProperty.OPACITY,
                WallHalf.HOME));
        assertFalse(preferences.isSurfaceInheriting(SurfaceSlot.KEYBOARD, SurfaceProperty.OPACITY,
            WallHalf.WORKSPACE));
        assertEquals(80, preferences.getInAppKeyboardBackgroundOpacity(WallHalf.WORKSPACE));
    }

    @Test
    public void surfaceValue_matchesTheNamedGetters() {
        preferences.setSurfaceBaseValue(SurfaceProperty.BLUR, WallHalf.HOME, 9);
        preferences.detachSurfaceValue(SurfaceSlot.STATUS, SurfaceProperty.GRAIN,
            WallHalf.WORKSPACE, 44);
        for (WallHalf half : WallHalf.values()) {
            assertEquals(preferences.getExtraKeysBlurRadius(half),
                preferences.getSurfaceValue(SurfaceSlot.DOCK, SurfaceProperty.BLUR, half));
            assertEquals(preferences.getStatusBarGrain(half),
                preferences.getSurfaceValue(SurfaceSlot.STATUS, SurfaceProperty.GRAIN, half));
        }
    }
}
