package com.termux.app.setup;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class CustomizeSectionTest {

    @Test
    public void theWornLookStaysPickableAndShownWithoutWallpaperAccess() {
        // The walk: Mist worn, system wallpaper unreadable. Mist was checked but disabled and
        // dimmed while Solid looked picked; the worn Look now draws as picked.
        assertTrue(CustomizeSection.lookChipEnabled(SetupState.LOOK_MIST, SetupState.LOOK_MIST,
            false));
        assertTrue(CustomizeSection.lookChipEnabled(SetupState.LOOK_SOLID, SetupState.LOOK_MIST,
            false));
        assertFalse(CustomizeSection.lookChipEnabled(SetupState.LOOK_CLEAR, SetupState.LOOK_MIST,
            false));
        assertFalse(CustomizeSection.lookChipEnabled(SetupState.LOOK_TINT, null, false));
    }

    @Test
    public void everyLookIsPickableWhenTheWallpaperReads() {
        assertTrue(CustomizeSection.lookChipEnabled(SetupState.LOOK_CLEAR, SetupState.LOOK_MIST,
            true));
        assertTrue(CustomizeSection.lookChipEnabled(SetupState.LOOK_TINT, null, true));
    }
}
