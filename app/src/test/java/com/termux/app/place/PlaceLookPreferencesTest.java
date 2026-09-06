package com.termux.app.place;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import com.termux.app.surfaces.SurfaceEditorRows;
import com.termux.app.wall.PaneWallPage;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.SurfaceProperty;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.SurfaceSlot;
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_APP;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * The look layer of the places: what a place resolves to before it has anything of its own, that a
 * scoped write is scoped and nothing else, and that the whole layer can be taken away and put back.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class PlaceLookPreferencesTest {

    private static final String KEY_STATUS_BLUR = TERMUX_APP.KEY_STATUS_BAR_BLUR_RADIUS;

    private Application app;
    private SharedPreferences store;
    private PlaceLookPreferences look;
    private TermuxAppSharedPreferences prefs;

    @Before
    public void setUp() {
        app = RuntimeEnvironment.getApplication();
        store = app.getSharedPreferences("place-look-test", Context.MODE_PRIVATE);
        store.edit().clear().commit();
        look = new PlaceLookPreferences(store);
        prefs = new TermuxAppSharedPreferences(app, look, null);
    }

    private List<String> statusBlurKeys() {
        return SurfaceEditorRows.scopeKeys(
            SurfaceEditorRows.forCell(SurfaceSlot.STATUS, SurfaceProperty.BLUR));
    }

    // ------------------------------------------------------------------ resolution order

    @Test
    public void aPlaceWithNothingOfItsOwnReadsTheSharedLook() {
        look.setRenderPlace(PaneWallPage.DISPLAY);
        assertEquals(TERMUX_APP.DEFAULT_SURFACE_BASE_BLUR, prefs.getStatusBarBlurRadius());

        // Base moves and every place moves with it: the third layer is empty, so the second and
        // first are the whole answer.
        prefs.setSurfaceBaseValue(SurfaceProperty.BLUR, 7);
        assertEquals(7, prefs.getStatusBarBlurRadius());
        look.setRenderPlace(null);
        assertEquals(7, prefs.getStatusBarBlurRadius());
    }

    @Test
    public void aPlaceOverrideBeatsTheSharedValueWhichBeatsBase() {
        prefs.setSurfaceBaseValue(SurfaceProperty.BLUR, 7);
        // The shared surface takes its own value: layer two.
        prefs.detachSurfaceValue(SurfaceSlot.STATUS, SurfaceProperty.BLUR, 11);
        assertEquals(11, prefs.getStatusBarBlurRadius());

        // One place takes its own: layer three.
        look.beginEdit(PaneWallPage.DISPLAY);
        prefs.detachSurfaceValue(SurfaceSlot.STATUS, SurfaceProperty.BLUR, 3);
        assertEquals(3, prefs.getStatusBarBlurRadius());
        look.endEdit();

        // Everyone else is where they were.
        look.setRenderPlace(PaneWallPage.TERMINAL);
        assertEquals(11, prefs.getStatusBarBlurRadius());
        look.setRenderPlace(null);
        assertEquals(11, prefs.getStatusBarBlurRadius());
        look.setRenderPlace(PaneWallPage.DISPLAY);
        assertEquals(3, prefs.getStatusBarBlurRadius());
    }

    @Test
    public void aPlaceOverrideIsReadEvenWhileTheSharedSurfaceFollowsBase() {
        // The shared status surface still follows Base; the place's own value must not be hidden
        // behind the shared link, which is why the link is scoped alongside the number.
        prefs.setSurfaceBaseValue(SurfaceProperty.BLUR, 7);
        assertTrue(prefs.isSurfaceInheriting(SurfaceSlot.STATUS, SurfaceProperty.BLUR));

        look.beginEdit(PaneWallPage.WIDGETS);
        prefs.detachSurfaceValue(SurfaceSlot.STATUS, SurfaceProperty.BLUR, 2);
        assertEquals(2, prefs.getStatusBarBlurRadius());
        look.endEdit();

        look.setRenderPlace(null);
        assertEquals(7, prefs.getStatusBarBlurRadius());
        assertTrue(prefs.isSurfaceInheriting(SurfaceSlot.STATUS, SurfaceProperty.BLUR));
    }

    @Test
    public void everyKindOfScopableRowResolvesThroughThePlace() {
        prefs.setAppLauncherDockStyle(TERMUX_APP.APP_LAUNCHER_DOCK_STYLE_DEFAULT);
        prefs.setWallpaperBackdropDim(10);
        prefs.setAppLauncherButtonCount(5);
        prefs.setTerminalPaneGap(4);

        look.beginEdit(PaneWallPage.DISPLAY);
        prefs.setAppLauncherDockStyle(TERMUX_APP.APP_LAUNCHER_DOCK_STYLE_ROUNDED);
        prefs.setWallpaperBackdropDim(60);
        prefs.setAppLauncherButtonCount(9);
        prefs.setTerminalPaneGap(12);
        assertEquals(TERMUX_APP.APP_LAUNCHER_DOCK_STYLE_ROUNDED, prefs.getAppLauncherDockStyle());
        assertEquals(60, prefs.getWallpaperBackdropDim());
        assertEquals(9, prefs.getAppLauncherButtonCount());
        assertEquals(12, prefs.getTerminalPaneGap());
        look.endEdit();

        look.setRenderPlace(PaneWallPage.TERMINAL);
        assertEquals(TERMUX_APP.APP_LAUNCHER_DOCK_STYLE_DEFAULT, prefs.getAppLauncherDockStyle());
        assertEquals(10, prefs.getWallpaperBackdropDim());
        assertEquals(5, prefs.getAppLauncherButtonCount());
        assertEquals(4, prefs.getTerminalPaneGap());
    }

    // ------------------------------------------------------------------ what a scoped write touches

    @Test
    public void aScopedWriteTouchesOnlyThatPlacesKeys() {
        prefs.detachSurfaceValue(SurfaceSlot.STATUS, SurfaceProperty.BLUR, 11);

        look.beginEdit(PaneWallPage.DISPLAY);
        prefs.detachSurfaceValue(SurfaceSlot.STATUS, SurfaceProperty.BLUR, 3);
        look.endEdit();

        assertEquals(11, store.getInt(KEY_STATUS_BLUR, -1));
        assertEquals(3, store.getInt(
            PlaceLookPreferences.lookKey(PaneWallPage.DISPLAY, KEY_STATUS_BLUR), -1));
        assertFalse(store.contains(
            PlaceLookPreferences.lookKey(PaneWallPage.TERMINAL, KEY_STATUS_BLUR)));
        assertFalse(store.contains(
            PlaceLookPreferences.lookKey(PaneWallPage.WIDGETS, KEY_STATUS_BLUR)));
    }

    @Test
    public void baseAndTheMaterialAreSharedEvenWhileAPlaceIsOpen() {
        look.beginEdit(PaneWallPage.DISPLAY);
        prefs.setSurfaceBaseValue(SurfaceProperty.BLUR, 9);
        prefs.setSurfaceMaterial(TERMUX_APP.SURFACE_MATERIAL_FROST);
        look.endEdit();

        assertEquals(9, store.getInt(SurfaceProperty.BLUR.baseKey, -1));
        assertFalse(store.contains(PlaceLookPreferences.lookKey(
            PaneWallPage.DISPLAY, SurfaceProperty.BLUR.baseKey)));
        assertFalse(store.contains(PlaceLookPreferences.lookKey(
            PaneWallPage.DISPLAY, TERMUX_APP.KEY_SURFACE_MATERIAL)));
        look.setRenderPlace(PaneWallPage.DISPLAY);
        assertEquals(9, prefs.getSurfaceBaseValue(SurfaceProperty.BLUR));
    }

    @Test
    public void runSharedLiftsThePlaceForOneAction() {
        look.beginEdit(PaneWallPage.DISPLAY);
        look.runShared(() -> prefs.detachSurfaceValue(SurfaceSlot.STATUS, SurfaceProperty.BLUR, 8));
        look.endEdit();

        assertEquals(8, store.getInt(KEY_STATUS_BLUR, -1));
        assertFalse(store.contains(
            PlaceLookPreferences.lookKey(PaneWallPage.DISPLAY, KEY_STATUS_BLUR)));
    }

    // ------------------------------------------------------------------ the layer as a whole

    @Test
    public void clearingEveryOverridePutsEveryPlaceBackOnTheSharedLook() {
        prefs.detachSurfaceValue(SurfaceSlot.STATUS, SurfaceProperty.BLUR, 11);
        for (PaneWallPage place : PaneWallPage.values()) {
            look.beginEdit(place);
            prefs.detachSurfaceValue(SurfaceSlot.STATUS, SurfaceProperty.BLUR, 1);
            prefs.setWallpaperBackdropDim(30);
            look.endEdit();
        }
        assertTrue(look.hasAnyOverrides());

        look.clearAllOverrides();

        assertFalse(look.hasAnyOverrides());
        for (PaneWallPage place : PaneWallPage.values()) {
            look.setRenderPlace(place);
            assertEquals(place + " back on the shared look", 11, prefs.getStatusBarBlurRadius());
        }
        // The shared layer itself is untouched by the clear.
        assertEquals(11, store.getInt(KEY_STATUS_BLUR, -1));
    }

    @Test
    public void theWholeLayerCanBeCapturedAndPutBack() {
        look.beginEdit(PaneWallPage.DISPLAY);
        prefs.detachSurfaceValue(SurfaceSlot.STATUS, SurfaceProperty.BLUR, 3);
        prefs.setAppLauncherDockStyle(TERMUX_APP.APP_LAUNCHER_DOCK_STYLE_ROUNDED);
        look.endEdit();
        Map<String, Object> captured = look.capture();
        String signature = look.signature();

        look.clearAllOverrides();
        look.beginEdit(PaneWallPage.TERMINAL);
        prefs.detachSurfaceValue(SurfaceSlot.STATUS, SurfaceProperty.BLUR, 25);
        look.endEdit();
        assertNotEquals(signature, look.signature());

        look.restore(captured);

        assertEquals(signature, look.signature());
        look.setRenderPlace(PaneWallPage.DISPLAY);
        assertEquals(3, prefs.getStatusBarBlurRadius());
        assertEquals(TERMUX_APP.APP_LAUNCHER_DOCK_STYLE_ROUNDED, prefs.getAppLauncherDockStyle());
        look.setRenderPlace(PaneWallPage.TERMINAL);
        assertEquals(TERMUX_APP.DEFAULT_SURFACE_BASE_BLUR, prefs.getStatusBarBlurRadius());
    }

    @Test
    public void aScopedWriteMovesOnlyThatPlacesSignature() {
        String before = look.signature();
        look.beginEdit(PaneWallPage.DISPLAY);
        prefs.detachSurfaceValue(SurfaceSlot.STATUS, SurfaceProperty.BLUR, 3);
        look.endEdit();
        assertNotEquals(before, look.signature());

        // A shared write leaves the layer alone, so the shared card's own dirtiness is not the
        // place layer's.
        String afterScoped = look.signature();
        prefs.setSurfaceBaseValue(SurfaceProperty.BLUR, 9);
        assertEquals(afterScoped, look.signature());
    }

    // ------------------------------------------------------------------ the marks the editor draws

    @Test
    public void aRowKnowsWhichPlacesHaveTakenItForThemselves() {
        List<String> keys = statusBlurKeys();
        assertEquals(Collections.emptyList(), look.placesOverriding(keys));

        look.beginEdit(PaneWallPage.TERMINAL);
        prefs.detachSurfaceValue(SurfaceSlot.STATUS, SurfaceProperty.BLUR, 3);
        look.endEdit();
        look.beginEdit(PaneWallPage.DISPLAY);
        prefs.detachSurfaceValue(SurfaceSlot.STATUS, SurfaceProperty.BLUR, 5);
        look.endEdit();

        assertEquals(Arrays.asList(PaneWallPage.TERMINAL, PaneWallPage.DISPLAY),
            look.placesOverriding(keys));
        assertTrue(look.hasOverride(PaneWallPage.TERMINAL, keys));
        assertFalse(look.hasOverride(PaneWallPage.WIDGETS, keys));

        // Tapping the mark gives the row back.
        look.clearOverride(PaneWallPage.TERMINAL, keys);
        assertFalse(look.hasOverride(PaneWallPage.TERMINAL, keys));
        assertEquals(Collections.singletonList(PaneWallPage.DISPLAY), look.placesOverriding(keys));
    }

    @Test
    public void onlyTheKeysTheEditorOwnsCanBeScoped() {
        assertTrue(PlaceLookPreferences.isScopable(KEY_STATUS_BLUR));
        assertTrue(PlaceLookPreferences.isScopable(TERMUX_APP.KEY_SURFACE_INHERIT_PREFIX
            + SurfaceSlot.STATUS.key + "_" + SurfaceProperty.BLUR.key));
        assertTrue(PlaceLookPreferences.isScopable(TERMUX_APP.KEY_WALLPAPER_BACKDROP_DIM));
        assertFalse(PlaceLookPreferences.isScopable(SurfaceProperty.BLUR.baseKey));
        assertFalse(PlaceLookPreferences.isScopable(TERMUX_APP.KEY_SURFACE_MATERIAL));
        assertFalse(PlaceLookPreferences.isScopable(TERMUX_APP.KEY_TOP_PANE_CLOCK_STYLE));
        assertFalse(PlaceLookPreferences.isScopable(TERMUX_APP.KEY_IN_APP_KEYBOARD_HEIGHT_SCALE));
        assertNull(PlaceLookPreferences.placeOfLookKey(KEY_STATUS_BLUR));
        assertEquals(PaneWallPage.WIDGETS, PlaceLookPreferences.placeOfLookKey(
            PlaceLookPreferences.lookKey(PaneWallPage.WIDGETS, KEY_STATUS_BLUR)));
    }

    @Test
    public void theHomePlaceStoresUnderTheNameTheUserSeesItBy() {
        assertEquals("home", PlaceLookPreferences.placeKey(PaneWallPage.WIDGETS));
        assertEquals("terminal", PlaceLookPreferences.placeKey(PaneWallPage.TERMINAL));
        assertEquals("display", PlaceLookPreferences.placeKey(PaneWallPage.DISPLAY));
        assertEquals("place.display.look." + KEY_STATUS_BLUR,
            PlaceLookPreferences.lookKey(PaneWallPage.DISPLAY, KEY_STATUS_BLUR));
    }
}
