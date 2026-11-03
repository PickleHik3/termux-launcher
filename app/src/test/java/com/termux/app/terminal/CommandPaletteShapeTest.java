package com.termux.app.terminal;

import static org.junit.Assert.assertEquals;

import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.SurfaceSlot;

import org.junit.Test;

public class CommandPaletteShapeTest {

    @Test public void aStoredRadiusIsTakenAsItIs() {
        assertEquals(20, CommandPaletteShape.openRadiusDp(20, true));
        assertEquals(20, CommandPaletteShape.openRadiusDp(20, false));
        assertEquals(0, CommandPaletteShape.openRadiusDp(0, true));
    }

    @Test public void followTheStyleIsTheSheetsSurfacesAutoRadius() {
        assertEquals(TermuxAppSharedPreferences.resolveAutoCornerRadiusDp(SurfaceSlot.SHEETS, true),
            CommandPaletteShape.openRadiusDp(-1, true));
        assertEquals(0, CommandPaletteShape.openRadiusDp(-1, false));
    }

    @Test public void theSproutLerpsFromTheSeedToTheOpenRadius() {
        assertEquals(6f, CommandPaletteShape.radiusPx(6f, 40f, 0f, 500f), 1e-4f);
        assertEquals(23f, CommandPaletteShape.radiusPx(6f, 40f, 0.5f, 500f), 1e-4f);
        assertEquals(40f, CommandPaletteShape.radiusPx(6f, 40f, 1f, 500f), 1e-4f);
        assertEquals(40f, CommandPaletteShape.radiusPx(6f, 40f, 1.4f, 500f), 1e-4f);
    }

    @Test public void neverMoreThanAPill() {
        // The closed filter row, 56 tall: a 40 radius would bulge past it, so it is a pill.
        assertEquals(28f, CommandPaletteShape.radiusPx(6f, 40f, 1f, 56f), 1e-4f);
        assertEquals(0f, CommandPaletteShape.radiusPx(6f, 40f, 1f, -3f), 0f);
        assertEquals(10f, CommandPaletteShape.radiusPx(6f, 10f, 1f, 56f), 1e-4f);
    }
}
