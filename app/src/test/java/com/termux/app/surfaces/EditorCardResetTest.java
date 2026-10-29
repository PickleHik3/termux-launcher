package com.termux.app.surfaces;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import com.termux.app.dock.PeekGlassGeometry;
import com.termux.app.dock.PeekGlassPreferences;
import com.termux.app.surfaces.cards.DockCard;
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
        com.termux.app.look.TerminalPageIdFixture.give(prefs);
    }

    @Test
    public void homeFrostComesBackEvenWhenTheEditorOpenedOnIt() {
        prefs.setSurfaceBaseValue(SurfaceProperty.BLUR, TermuxAppSharedPreferences.PAGE_WALL, 40);
        prefs.setSurfaceBaseValue(SurfaceProperty.OPACITY, TermuxAppSharedPreferences.PAGE_WALL, 90);
        EditorCardReset.resetStore(prefs, EditorCard.HOME, TermuxAppSharedPreferences.PAGE_WALL);
        assertEquals(TERMUX_APP.DEFAULT_SURFACE_BASE_BLUR,
            prefs.getSurfaceBaseValue(SurfaceProperty.BLUR, TermuxAppSharedPreferences.PAGE_WALL));
        assertEquals(TERMUX_APP.DEFAULT_SURFACE_BASE_OPACITY,
            prefs.getSurfaceBaseValue(SurfaceProperty.OPACITY, TermuxAppSharedPreferences.PAGE_WALL));
    }

    @Test
    public void workspaceResetLeavesHomeAlone() {
        prefs.setSurfaceBaseValue(SurfaceProperty.BLUR, TermuxAppSharedPreferences.PAGE_WALL, 40);
        prefs.setSurfaceBaseValue(SurfaceProperty.BLUR, TermuxAppSharedPreferences.terminalPageId(), 41);
        EditorCardReset.resetStore(prefs, EditorCard.WORKSPACE, TermuxAppSharedPreferences.terminalPageId());
        // Base is wall-wide: the reset is the wall's, so Home reads the default as well.
        assertEquals(TERMUX_APP.DEFAULT_SURFACE_BASE_BLUR,
            prefs.getSurfaceBaseValue(SurfaceProperty.BLUR, TermuxAppSharedPreferences.PAGE_WALL));
        assertEquals(TERMUX_APP.DEFAULT_SURFACE_BASE_BLUR,
            prefs.getSurfaceBaseValue(SurfaceProperty.BLUR, TermuxAppSharedPreferences.terminalPageId()));
    }

    @Test
    public void dockResetCoversItsOwnKeysAndGlassPosition() {
        prefs.setLayoutCornersDp(false, 4);
        prefs.setLayoutMarginDp(false, 30);
        prefs.setAppLauncherButtonCount(12);
        PeekGlassPreferences.setReflection(store, 5);
        prefs.setSurfaceGlassPosition(SurfaceSlot.DOCK, 77);
        EditorCardReset.resetStore(prefs, EditorCard.DOCK, TermuxAppSharedPreferences.terminalPageId());
        assertEquals(TERMUX_APP.DEFAULT_SURFACE_BASE_CORNER_RADIUS, prefs.getLayoutCornersDp(false));
        assertEquals(TERMUX_APP.DEFAULT_SURFACE_BASE_SIDE_GAP, prefs.getLayoutMarginDp(false));
        assertEquals(TERMUX_APP.DEFAULT_APP_LAUNCHER_BUTTON_COUNT, prefs.getAppLauncherButtonCount());
        assertEquals(PeekGlassGeometry.REFLECTION_DEFAULT, PeekGlassPreferences.reflection(store));
        assertEquals(-1, prefs.getSurfaceGlassPosition(SurfaceSlot.DOCK));
    }

    @Test
    public void keyboardResetReturnsKeyOpacityToTheTheme() {
        prefs.setInAppKeyboardKeyOpacity(59);
        EditorCardReset.resetStore(prefs, EditorCard.KEYBOARD, TermuxAppSharedPreferences.terminalPageId());
        assertFalse(prefs.getInAppKeyboardKeyOpacity() >= 0);
    }

    @Test
    public void keyboardResetUnlinksTheOpacitiesAndDropsKeyShape() {
        prefs.setInAppKeyboardOpacityLinked(true);
        prefs.setInAppKeyboardKeyCornerRadiusDp(3f);
        prefs.setInAppKeyboardKeyMarginScale(1.5f);
        EditorCardReset.resetStore(prefs, EditorCard.KEYBOARD, TermuxAppSharedPreferences.terminalPageId());
        assertEquals(TERMUX_APP.DEFAULT_IN_APP_KEYBOARD_OPACITY_LINKED,
            prefs.isInAppKeyboardOpacityLinked());
        assertFalse(store.contains(TERMUX_APP.KEY_IN_APP_KEYBOARD_KEY_CORNER_RADIUS_DP));
        assertFalse(store.contains(TERMUX_APP.KEY_IN_APP_KEYBOARD_KEY_MARGIN_SCALE));
    }

    @Test
    public void dockResetDropsTheAzAndCommandPaletteSwitches() {
        store.edit().putBoolean(DockCard.KEY_AZ_GLASS_BACKGROUND, true)
            .putString(DockCard.KEY_AZ_SHOW, "faint")
            .putBoolean(DockCard.KEY_COMMAND_PALETTE_BUTTON, false).commit();
        EditorCardReset.resetStore(prefs, EditorCard.DOCK, TermuxAppSharedPreferences.terminalPageId());
        assertFalse(store.contains(DockCard.KEY_AZ_GLASS_BACKGROUND));
        assertFalse(store.contains(DockCard.KEY_AZ_SHOW));
        assertFalse(store.contains(DockCard.KEY_COMMAND_PALETTE_BUTTON));
    }

    @Test
    public void statusResetPutsTheClockAndTilesBackAsTheyShip() {
        prefs.setTopPaneClockStyle(TERMUX_APP.TOP_PANE_CLOCK_STYLE_LED);
        prefs.setTopPaneClockAlignment(TERMUX_APP.TOP_PANE_CLOCK_ALIGNMENT_RIGHT);
        prefs.setTopPaneClockAmPmEnabled(!TERMUX_APP.DEFAULT_TOP_PANE_CLOCK_AM_PM);
        prefs.setStatusClockSecondsEnabled(!TERMUX_APP.DEFAULT_STATUS_CLOCK_SECONDS);
        prefs.setStatusWidgetCpuEnabled(!TERMUX_APP.DEFAULT_STATUS_WIDGET_CPU);
        prefs.setStatusWidgetNotificationsEnabled(!TERMUX_APP.DEFAULT_STATUS_WIDGET_NOTIFICATIONS);
        EditorCardReset.resetStore(prefs, EditorCard.STATUS, TermuxAppSharedPreferences.terminalPageId());
        assertEquals(TERMUX_APP.DEFAULT_TOP_PANE_CLOCK_STYLE, prefs.getTopPaneClockStyle());
        assertEquals(TERMUX_APP.DEFAULT_TOP_PANE_CLOCK_ALIGNMENT, prefs.getTopPaneClockAlignment());
        assertEquals(TERMUX_APP.DEFAULT_TOP_PANE_CLOCK_AM_PM, prefs.isTopPaneClockAmPmEnabled());
        assertEquals(TERMUX_APP.DEFAULT_STATUS_CLOCK_SECONDS, prefs.isStatusClockSecondsEnabled());
        assertEquals(TERMUX_APP.DEFAULT_STATUS_WIDGET_CPU, prefs.isStatusWidgetCpuEnabled());
        assertEquals(TERMUX_APP.DEFAULT_STATUS_WIDGET_NOTIFICATIONS,
            prefs.isStatusWidgetNotificationsEnabled());
    }

    @Test
    public void terminalResetPutsContrastTrailAndEffectBack() {
        prefs.setTerminalContrastLevel("harder");
        prefs.setTerminalCursorTrailEnabled(false);
        prefs.setTerminalRetroEffect("crt_amber");
        prefs.setTerminalPaneGap(12);
        EditorCardReset.resetStore(prefs, EditorCard.TERMINAL, TermuxAppSharedPreferences.terminalPageId());
        assertEquals(TERMUX_APP.DEFAULT_VALUE_TERMINAL_CONTRAST_LEVEL,
            prefs.getTerminalContrastLevel().value);
        assertEquals(TERMUX_APP.DEFAULT_TERMINAL_CURSOR_TRAIL, prefs.isTerminalCursorTrailEnabled());
        assertEquals(TERMUX_APP.DEFAULT_TERMINAL_RETRO_EFFECT, prefs.getTerminalRetroEffect());
        assertEquals(TERMUX_APP.DEFAULT_TERMINAL_PANE_GAP, prefs.getTerminalPaneGap());
    }
}
