package com.termux.shared.termux.settings.preferences;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class AzLockMethodNormalizeTest {

    private static String n(String value) {
        return TermuxAppSharedPreferences.normalizeAppLauncherAzLockMethod(value);
    }

    @Test
    public void acceptsEveryMethod() {
        assertEquals("shizuku", n("shizuku"));
        assertEquals("root", n("root"));
        assertEquals("root", n("  Root "));
        assertEquals("accessibility", n("accessibility"));
    }

    @Test
    public void unknownAndNullFallToOff() {
        assertEquals("off", n(null));
        assertEquals("off", n("nonsense"));
        assertEquals("off", n("off"));
    }
}
