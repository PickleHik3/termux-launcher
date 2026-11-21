package com.termux.app.launcher.drawer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AppDrawerSectionSwitchGlassTest {

    @Test
    public void glassIsWantedOnlyForDefaultWhileOpen() {
        assertTrue(AppDrawerSectionSwitchGlass.wanted(AppDrawerViewType.DEFAULT, true));
        assertFalse(AppDrawerSectionSwitchGlass.wanted(AppDrawerViewType.DEFAULT, false));
        for (AppDrawerViewType type : AppDrawerViewType.values()) {
            if (type == AppDrawerViewType.DEFAULT) continue;
            assertFalse(type.name(), AppDrawerSectionSwitchGlass.wanted(type, true));
        }
    }

    @Test
    public void radiusIsHalfTheHeight() {
        assertEquals(60f, AppDrawerSectionSwitchGlass.radiusPx(120), 0f);
        assertEquals(0f, AppDrawerSectionSwitchGlass.radiusPx(-4), 0f);
    }
}
