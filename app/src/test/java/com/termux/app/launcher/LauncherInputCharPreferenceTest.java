package com.termux.app.launcher;

import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_APP;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class LauncherInputCharPreferenceTest {

    @Test
    public void blankInputCharFallsBackToDefault() {
        assertEquals(TERMUX_APP.DEFAULT_APP_LAUNCHER_INPUT_CHAR,
            TermuxAppSharedPreferences.normalizeAppLauncherInputChar(""));
    }

    @Test
    public void nullInputCharFallsBackToDefault() {
        assertEquals(TERMUX_APP.DEFAULT_APP_LAUNCHER_INPUT_CHAR,
            TermuxAppSharedPreferences.normalizeAppLauncherInputChar(null));
    }

    @Test
    public void whitespaceOnlyInputCharFallsBackToDefault() {
        assertEquals(TERMUX_APP.DEFAULT_APP_LAUNCHER_INPUT_CHAR,
            TermuxAppSharedPreferences.normalizeAppLauncherInputChar("   "));
    }

    @Test
    public void nonBlankInputCharIsPreserved() {
        assertEquals(";", TermuxAppSharedPreferences.normalizeAppLauncherInputChar(";"));
    }
}
