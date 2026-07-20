package com.termux.shared.termux.settings.preferences;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TermuxAppSharedPreferencesMigrationTest {

    @Test
    public void launcherVisualDefaultsEnableMaterialColorsAndAppNames() {
        assertTrue(TermuxPreferenceConstants.TERMUX_APP.DEFAULT_VALUE_TERMINAL_DYNAMIC_COLORS_ENABLED);
        assertTrue(TermuxPreferenceConstants.TERMUX_APP.DEFAULT_APP_LAUNCHER_DISPLAY_APP_NAMES);
        assertTrue(TermuxPreferenceConstants.TERMUX_APP.DEFAULT_APP_LAUNCHER_ROW_HAPTICS);
    }

    @Test
    public void migrationEnablesWhenNotDoneAndNotStored() {
        assertTrue(TermuxAppSharedPreferences.shouldEnableTerminalMarginAdjustmentOnMigration(false, false, false));
    }

    @Test
    public void migrationEnablesWhenNotDoneAndExplicitlyDisabled() {
        assertTrue(TermuxAppSharedPreferences.shouldEnableTerminalMarginAdjustmentOnMigration(false, true, false));
    }

    @Test
    public void migrationDoesNotEnableWhenAlreadyDone() {
        assertFalse(TermuxAppSharedPreferences.shouldEnableTerminalMarginAdjustmentOnMigration(true, true, false));
    }

    @Test
    public void migrationDoesNotEnableWhenNotDoneButAlreadyEnabledAndStored() {
        assertFalse(TermuxAppSharedPreferences.shouldEnableTerminalMarginAdjustmentOnMigration(false, true, true));
    }
}
