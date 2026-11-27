package com.termux.app.upgrade;

import com.termux.shared.termux.settings.preferences.LookGrid;
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_APP;
import com.termux.shared.termux.settings.registry.SettingDeclarations;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TerlUpgradePlanTest {

    private static final String GRID_CELL =
        LookGrid.key(LookGrid.Property.FROST, null, (LookGrid.Kind) null);

    private static Map<String, Object> v1Store() {
        Map<String, Object> stored = new HashMap<>();
        // The look: an Appearance value, a surface's Global value, a look grid cell, Terl's own.
        stored.put(TERMUX_APP.KEY_TERMINAL_CORNER_RADIUS, 12);
        stored.put(TERMUX_APP.KEY_SURFACE_MATERIAL, "glass");
        stored.put(GRID_CELL, 40);
        stored.put("terl.look.id", "frost");
        stored.put(SettingDeclarations.TEXT_SIZE.key, 120);
        // The layout: the dock's rows, the session in front.
        stored.put(TERMUX_APP.KEY_APP_LAUNCHER_AZ_ROW_ENABLED, false);
        stored.put(TERMUX_APP.KEY_CURRENT_SESSION, "abc");
        // Retiring rows.
        stored.put(SettingDeclarations.QUICK_REPLY_SWIPE.key, true);
        stored.put(SettingDeclarations.STATUS_BATTERY.key, true);
        stored.put(SettingDeclarations.PAGE_TRANSITION.key, "depth");
        // Carried behaviour.
        stored.put(TERMUX_APP.KEY_IN_APP_KEYBOARD_LAYOUTS, "qwerty,dvorak");
        stored.put(SettingDeclarations.KEYBOARD_HAPTICS.key, false);
        stored.put(TERMUX_APP.KEY_APP_NOTIFICATION_HISTORY_RETENTION_DAYS, 3);
        stored.put(TERMUX_APP.KEY_APP_LAUNCHER_PINNED_ITEMS_V2, "[pins]");
        stored.put(TERMUX_APP.KEY_X11_DISPLAY_DPI, 200);
        stored.put(SettingDeclarations.ANIMATION_SPEED.key, "faster");
        stored.put(TERMUX_APP.KEY_FIRST_RUN_CHAIN_DONE, true);
        // A key v1 does not name.
        stored.put("someone_elses_key", 1);
        return stored;
    }

    @Test
    public void removesTheLookTheLayoutAndRetiredRowsAndKeepsTheRest() {
        TerlUpgradePlan plan = TerlUpgradePlan.of(v1Store());
        assertTrue(plan.due);
        assertEquals(new TreeSet<>(Arrays.asList(
            TERMUX_APP.KEY_TERMINAL_CORNER_RADIUS, TERMUX_APP.KEY_SURFACE_MATERIAL,
            GRID_CELL, "terl.look.id", SettingDeclarations.TEXT_SIZE.key,
            TERMUX_APP.KEY_APP_LAUNCHER_AZ_ROW_ENABLED, TERMUX_APP.KEY_CURRENT_SESSION,
            SettingDeclarations.QUICK_REPLY_SWIPE.key, SettingDeclarations.STATUS_BATTERY.key,
            SettingDeclarations.PAGE_TRANSITION.key)), plan.removed);
    }

    @Test
    public void aStoreThatHasRunPlansNothing() {
        Map<String, Object> stored = v1Store();
        stored.put(TerlUpgradePlan.KEY_DONE, TerlUpgradePlan.VERSION);
        TerlUpgradePlan plan = TerlUpgradePlan.of(stored);
        assertFalse(plan.due);
        assertTrue(plan.removed.isEmpty());
    }

    @Test
    public void anEmptyStoreIsDueAndRemovesNothing() {
        TerlUpgradePlan plan = TerlUpgradePlan.of(new HashMap<>());
        assertTrue(plan.due);
        assertTrue(plan.removed.isEmpty());
    }
}
