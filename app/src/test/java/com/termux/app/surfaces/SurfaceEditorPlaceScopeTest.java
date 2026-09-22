package com.termux.app.surfaces;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import com.termux.app.place.PlaceLookPreferences;
import com.termux.app.surfaces.SurfaceEditorProperties.Control;
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

/**
 * What the surface editor promises once it can be opened on one place: which rows a place is
 * allowed to take, and that a preset and Reset held give every place back its
 * shared look.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class SurfaceEditorPlaceScopeTest {

    private Application app;
    private SharedPreferences store;
    private PlaceLookPreferences look;
    private TermuxAppSharedPreferences prefs;

    @Before
    public void setUp() {
        app = RuntimeEnvironment.getApplication();
        store = app.getSharedPreferences("surface-editor-place-test", Context.MODE_PRIVATE);
        store.edit().clear().commit();
        look = new PlaceLookPreferences(store);
        prefs = new TermuxAppSharedPreferences(app, look, null);
    }

    /** Gives every place something of its own, the way an editor session on each would. */
    private void overrideEveryPlace() {
        for (PaneWallPage place : PaneWallPage.values()) {
            look.beginEdit(place);
            prefs.detachSurfaceValue(SurfaceSlot.STATUS, SurfaceProperty.BLUR, 2);
            prefs.setWallpaperBackdropDim(40);
            prefs.setTerminalBorderEnabled(false);
            look.endEdit();
        }
        assertTrue(look.hasAnyOverrides());
    }

    // ------------------------------------------------------------------ which rows can be scoped

    @Test
    public void everyRowOnASurfacesCardCanBeTakenByAPlace() {
        for (SurfaceSlot slot : SurfaceSlot.values()) {
            for (Control control : SurfaceEditorProperties.panel(slot)) {
                if (control.kind == SurfaceEditorProperties.Kind.ACTION) {
                    assertTrue(slot + "/" + control.id + " leaves the editor",
                        control.scopeKeys.isEmpty());
                    continue;
                }
                assertFalse(slot + "/" + control.id + " should be scopable",
                    control.scopeKeys.isEmpty());
                for (String key : control.scopeKeys)
                    assertTrue(key, PlaceLookPreferences.isScopable(key));
            }
        }
    }

    @Test
    public void theSharedLayersOwnRowsStaySharedExceptTheWallpaper() {
        for (Control control : SurfaceEditorProperties.global()) {
            if (SurfaceEditorProperties.ID_WALLPAPER.equals(control.id)) {
                assertEquals(1, control.scopeKeys.size());
                assertEquals(TERMUX_APP.KEY_WALLPAPER_BACKDROP_DIM, control.scopeKeys.get(0));
                continue;
            }
            assertTrue(control.id + " writes Base, which every place shares",
                control.scopeKeys.isEmpty());
        }
    }

    @Test
    public void aCellIsScopedAlongWithItsLinkToBase() {
        for (SurfaceEditorRows.Row row : SurfaceEditorRows.rows()) {
            assertEquals(row.slot + "/" + row.property, 2,
                SurfaceEditorRows.scopeKeys(row).size());
            assertEquals(TermuxAppSharedPreferences.surfaceOverrideKey(row.slot, row.property),
                SurfaceEditorRows.scopeKeys(row).get(0));
        }
    }

    // ------------------------------------------------------------------ the two bulk gestures

    @Test
    public void applyingAPresetGivesEveryPlaceBackTheSharedLook() {
        overrideEveryPlace();
        SurfacePresets.Preset preset = SurfacePresets.presets().get(1);

        // Exactly what the editor's preset tap does.
        look.clearAllOverrides();
        look.runShared(() -> SurfacePresets.apply(prefs, preset));

        assertFalse(look.hasAnyOverrides());
        for (PaneWallPage place : PaneWallPage.values()) {
            look.setRenderPlace(place);
            assertTrue(place + " wears the preset", SurfacePresets.matches(prefs, preset));
        }
    }

    @Test
    public void resetHeldGivesEveryPlaceBackTheSharedLook() {
        overrideEveryPlace();

        // Exactly what ↺ held does: every place back on the shared look, and the shared look back
        // on the shipped numbers.
        look.clearAllOverrides();
        look.runShared(() -> {
            for (SurfaceSlot slot : SurfaceSlot.values())
                prefs.reattachSurface(slot);
            for (SurfaceProperty property : SurfaceProperty.values())
                prefs.setSurfaceBaseValue(property, property.baseDefault);
            prefs.setWallpaperBackdropDim(TERMUX_APP.DEFAULT_WALLPAPER_BACKDROP_DIM);
            prefs.setTerminalBorderEnabled(TERMUX_APP.DEFAULT_VALUE_TERMINAL_BORDER_ENABLED);
        });

        assertFalse(look.hasAnyOverrides());
        for (PaneWallPage place : PaneWallPage.values()) {
            look.setRenderPlace(place);
            assertEquals(place + " blur", TERMUX_APP.DEFAULT_SURFACE_BASE_BLUR,
                prefs.getStatusBarBlurRadius());
            assertEquals(place + " wallpaper", TERMUX_APP.DEFAULT_WALLPAPER_BACKDROP_DIM,
                prefs.getWallpaperBackdropDim());
            assertEquals(place + " frame", TERMUX_APP.DEFAULT_VALUE_TERMINAL_BORDER_ENABLED,
                prefs.isTerminalBorderEnabled());
        }
    }

}
