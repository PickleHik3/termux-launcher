package com.termux.app.surfaces;

import androidx.annotation.NonNull;

import com.termux.app.dock.PeekGlassGeometry;
import com.termux.app.dock.PeekGlassPreferences;
import com.termux.app.surfaces.cards.DockCard;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.SurfaceProperty;
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_APP;

/**
 * What a card's header Reset puts back in the preference store, so that every key the card's rows
 * show is covered: the glass (the screens' own half of Base, a surface's own values and its Glass
 * position), and each card's own keys: the dock's, the keyboard's, the status bar's clock and
 * tiles, the terminal's contrast, trail, effect and sizes. What lives in the layout session (the
 * dock's height, style and type, the keyboard's type, height and bottom padding, Home's grid) or
 * in Settings' store (Home's rows, the dock's options) is reset by the controller. Where each bar
 * stands, whether it shows and the Bars list order are the arrangement's, which Undo and the Match
 * rows own; Reset leaves them.
 */
final class EditorCardReset {
    private EditorCardReset() {}

    /** The glass rows' stored values back as they ship, then the card's other store keys. */
    static void resetStore(@NonNull TermuxAppSharedPreferences prefs, @NonNull EditorCard card,
                           int pageId) {
        switch (card) {
            case HOME:
                screenGlass(prefs, TermuxAppSharedPreferences.PAGE_WALL);
                break;
            case WORKSPACE:
                screenGlass(prefs, TermuxAppSharedPreferences.terminalPageId());
                break;
            case APPEARANCE:
                for (TermuxAppSharedPreferences.SurfaceSlot slot
                        : TermuxAppSharedPreferences.SurfaceSlot.values()) {
                    prefs.resetSurfaceToLook(slot, pageId);
                    prefs.setSurfaceGlassPosition(slot, -1);
                }
                prefs.setSurfaceGlassPosition(null, -1);
                break;
            default:
                if (card.slot != null) {
                    prefs.resetSurfaceToLook(card.slot, pageId);
                    prefs.setSurfaceGlassPosition(card.slot, -1);
                }
                break;
        }
        switch (card) {
            case KEYBOARD:
                prefs.setInAppKeyboardKeyOpacity(TERMUX_APP.DEFAULT_IN_APP_KEYBOARD_KEY_OPACITY);
                prefs.setInAppKeyboardOpacityLinked(TERMUX_APP.DEFAULT_IN_APP_KEYBOARD_OPACITY_LINKED);
                // Absent, as shipped: the keyboard theme's own radius and spacing apply.
                remove(prefs, TERMUX_APP.KEY_IN_APP_KEYBOARD_KEY_CORNER_RADIUS_DP);
                remove(prefs, TERMUX_APP.KEY_IN_APP_KEYBOARD_KEY_MARGIN_SCALE);
                break;
            case DOCK:
                prefs.setLayoutCornersDp(false, TERMUX_APP.DEFAULT_SURFACE_BASE_CORNER_RADIUS);
                prefs.setLayoutMarginDp(false, TERMUX_APP.DEFAULT_SURFACE_BASE_SIDE_GAP);
                prefs.setAppLauncherButtonCount(TERMUX_APP.DEFAULT_APP_LAUNCHER_BUTTON_COUNT);
                PeekGlassPreferences.setReflection(prefs.getSharedPreferences(),
                    PeekGlassGeometry.REFLECTION_DEFAULT);
                remove(prefs, DockCard.KEY_AZ_GLASS_BACKGROUND);
                remove(prefs, DockCard.KEY_AZ_SHOW);
                remove(prefs, DockCard.KEY_COMMAND_PALETTE_BUTTON);
                break;
            case STATUS:
                prefs.setTopPaneClockStyle(TERMUX_APP.DEFAULT_TOP_PANE_CLOCK_STYLE);
                prefs.setTopPaneClockAlignment(TERMUX_APP.DEFAULT_TOP_PANE_CLOCK_ALIGNMENT);
                prefs.setTopPaneClockAmPmEnabled(TERMUX_APP.DEFAULT_TOP_PANE_CLOCK_AM_PM);
                prefs.setStatusClockSecondsEnabled(TERMUX_APP.DEFAULT_STATUS_CLOCK_SECONDS);
                prefs.setStatusWidgetWeatherFahrenheit(
                    TERMUX_APP.DEFAULT_STATUS_WIDGET_WEATHER_FAHRENHEIT);
                prefs.setStatusWidgetCpuEnabled(TERMUX_APP.DEFAULT_STATUS_WIDGET_CPU);
                prefs.setStatusWidgetRamEnabled(TERMUX_APP.DEFAULT_STATUS_WIDGET_RAM);
                prefs.setStatusWidgetWeatherEnabled(TERMUX_APP.DEFAULT_STATUS_WIDGET_WEATHER);
                prefs.setStatusWidgetNotificationsEnabled(
                    TERMUX_APP.DEFAULT_STATUS_WIDGET_NOTIFICATIONS);
                prefs.setStatusWidgetMediaEnabled(TERMUX_APP.DEFAULT_STATUS_WIDGET_MEDIA);
                break;
            case TERMINAL:
                prefs.setTerminalContrastLevel(TERMUX_APP.DEFAULT_VALUE_TERMINAL_CONTRAST_LEVEL);
                prefs.setTerminalCursorTrailEnabled(TERMUX_APP.DEFAULT_TERMINAL_CURSOR_TRAIL);
                prefs.setTerminalCursorTrailStyle(TERMUX_APP.DEFAULT_TERMINAL_CURSOR_TRAIL_STYLE);
                prefs.setTerminalRetroEffect(TERMUX_APP.DEFAULT_TERMINAL_RETRO_EFFECT);
                prefs.setTextSizePercent(TERMUX_APP.DEFAULT_TEXT_SIZE_PERCENT);
                prefs.setTerminalPaneGap(TERMUX_APP.DEFAULT_TERMINAL_PANE_GAP);
                break;
            default:
                break;
        }
    }

    /** Drops {@code key}, so its getter answers the shipped default. */
    private static void remove(@NonNull TermuxAppSharedPreferences prefs, @NonNull String key) {
        android.content.SharedPreferences store = prefs.getSharedPreferences();
        if (store != null && store.contains(key))
            store.edit().remove(key).apply();
    }

    /** A screen's own half of Base back to the values it ships with. */
    private static void screenGlass(@NonNull TermuxAppSharedPreferences prefs,
                                    int pageId) {
        prefs.setSurfaceBaseValue(SurfaceProperty.BLUR, pageId, TERMUX_APP.DEFAULT_SURFACE_BASE_BLUR);
        prefs.setSurfaceBaseValue(SurfaceProperty.GRAIN, pageId,
            TERMUX_APP.DEFAULT_SURFACE_BASE_GRAIN);
        prefs.setSurfaceBaseValue(SurfaceProperty.OPACITY, pageId,
            TERMUX_APP.DEFAULT_SURFACE_BASE_OPACITY);
        prefs.setSurfaceBaseValue(SurfaceProperty.TINT, pageId, TERMUX_APP.DEFAULT_SURFACE_BASE_TINT);
    }
}
