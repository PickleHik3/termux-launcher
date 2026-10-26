package com.termux.app.surfaces;

import androidx.annotation.NonNull;

import com.termux.app.dock.PeekGlassGeometry;
import com.termux.app.dock.PeekGlassPreferences;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.SurfaceProperty;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.WallHalf;
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_APP;

/**
 * What a card's header Reset puts back in the preference store, so that every key the card's rows
 * show is covered: the glass (the screens' own half of Base, a surface's own values and its Glass
 * position), and the dock's and keyboard's own keys. What lives in the layout session (the dock
 * height) or in the Home settings store is reset by the controller.
 */
final class EditorCardReset {
    private EditorCardReset() {}

    /** The glass rows' stored values back as they ship, then the card's other store keys. */
    static void resetStore(@NonNull TermuxAppSharedPreferences prefs, @NonNull EditorCard card,
                           @NonNull WallHalf half) {
        switch (card) {
            case HOME:
                screenGlass(prefs, WallHalf.HOME);
                break;
            case WORKSPACE:
                screenGlass(prefs, WallHalf.WORKSPACE);
                break;
            case APPEARANCE:
                for (TermuxAppSharedPreferences.SurfaceSlot slot
                        : TermuxAppSharedPreferences.SurfaceSlot.values()) {
                    prefs.resetSurfaceToLook(slot, half);
                    prefs.setSurfaceGlassPosition(slot, -1);
                }
                prefs.setSurfaceGlassPosition(null, -1);
                break;
            default:
                if (card.slot != null) {
                    prefs.resetSurfaceToLook(card.slot, half);
                    prefs.setSurfaceGlassPosition(card.slot, -1);
                }
                break;
        }
        if (card == EditorCard.KEYBOARD)
            prefs.setInAppKeyboardKeyOpacity(TERMUX_APP.DEFAULT_IN_APP_KEYBOARD_KEY_OPACITY);
        if (card == EditorCard.DOCK) {
            prefs.setLayoutCornersDp(false, TERMUX_APP.DEFAULT_SURFACE_BASE_CORNER_RADIUS);
            prefs.setLayoutMarginDp(false, TERMUX_APP.DEFAULT_SURFACE_BASE_SIDE_GAP);
            prefs.setAppLauncherButtonCount(TERMUX_APP.DEFAULT_APP_LAUNCHER_BUTTON_COUNT);
            PeekGlassPreferences.setReflection(prefs.getSharedPreferences(),
                PeekGlassGeometry.REFLECTION_DEFAULT);
        }
    }

    /** A screen's own half of Base back to the values it ships with. */
    private static void screenGlass(@NonNull TermuxAppSharedPreferences prefs,
                                    @NonNull WallHalf half) {
        prefs.setSurfaceBaseValue(SurfaceProperty.BLUR, half, TERMUX_APP.DEFAULT_SURFACE_BASE_BLUR);
        prefs.setSurfaceBaseValue(SurfaceProperty.GRAIN, half,
            TERMUX_APP.DEFAULT_SURFACE_BASE_GRAIN);
        prefs.setSurfaceBaseValue(SurfaceProperty.OPACITY, half,
            TERMUX_APP.DEFAULT_SURFACE_BASE_OPACITY);
        prefs.setSurfaceBaseValue(SurfaceProperty.TINT, half, TERMUX_APP.DEFAULT_SURFACE_BASE_TINT);
    }
}
