package com.termux.app.surfaces;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.termux.shared.termux.settings.preferences.PageLookKeys;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.SurfaceSlot;
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_APP;

import org.junit.Test;

import java.util.Set;

/** L6: a page's Reset drops every value the page holds, not only the surface numbers. */
public class SurfaceEditorPageResetTest {

    @Test
    public void aWholePageResetDropsTheMaterialAndTheBackdrop() {
        Set<String> keys = SurfaceEditorController.pageResetKeys(null);
        assertTrue(keys.containsAll(PageLookKeys.DETACHABLE));
        assertTrue(keys.contains(TERMUX_APP.KEY_SURFACE_MATERIAL));
        assertTrue(keys.contains(TERMUX_APP.KEY_SURFACE_MATERIAL_INTENSITY));
        assertTrue(keys.contains(TERMUX_APP.KEY_IN_APP_KEYBOARD_BACKDROP_OPACITY));
    }

    @Test
    public void theKeyboardCardsResetDropsItsBackdropButNotTheDocks() {
        Set<String> keys = SurfaceEditorController.pageResetKeys(SurfaceSlot.KEYBOARD);
        assertTrue(keys.contains(TERMUX_APP.KEY_IN_APP_KEYBOARD_BACKDROP_OPACITY));
        assertFalse(keys.contains(TERMUX_APP.KEY_DOCK_GLASS_GRAIN));
        assertFalse(SurfaceEditorController.pageResetKeys(SurfaceSlot.DOCK)
            .contains(TERMUX_APP.KEY_IN_APP_KEYBOARD_BACKDROP_OPACITY));
    }
}
