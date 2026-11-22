package com.termux.app.surfaces;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.termux.shared.termux.settings.preferences.LookGrid;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

/** The upgrade nudge's eligibility (overhaul §6). */
public class GlassResetNudgeTest {

    private static final LookGrid SHIPPED = new LookGrid(new LookGrid.MemoryStore());

    private static boolean custom(Map<String, ?> stored) {
        return GlassResetNudge.glassCustom(stored, SHIPPED, "system", "default", false);
    }

    @Test
    public void aFreshInstallIsSettledAtOnce() {
        boolean usedBefore = GlassResetNudge.usedBefore(0, 0, false, false, false);
        assertFalse(usedBefore);
        assertEquals(GlassResetNudge.SETTLED,
            GlassResetNudge.evaluate(GlassResetNudge.UNDECIDED, usedBefore, true));
    }

    @Test
    public void anUpgradeWithShippedGlassIsSettled() {
        Map<String, Object> stored = new HashMap<>();
        // A cell holding the value that ships (the start-up seeds write these) is not custom.
        LookGrid.Key cell = new LookGrid.Key(LookGrid.Property.OPACITY, null, LookGrid.NO_PAGE);
        stored.put(LookGrid.key(LookGrid.Property.OPACITY, null, LookGrid.NO_PAGE),
            SHIPPED.resolve(cell).value);
        stored.put("place.dress.home.portrait.az", false);
        assertFalse(custom(stored));
        boolean usedBefore = GlassResetNudge.usedBefore(1, 0, false, false, true);
        assertTrue(usedBefore);
        assertEquals(GlassResetNudge.SETTLED,
            GlassResetNudge.evaluate(GlassResetNudge.UNDECIDED, usedBefore, custom(stored)));
    }

    @Test
    public void anUpgradeWithCustomGlassIsDue() {
        Map<String, Object> stored = new HashMap<>();
        LookGrid.Key cell = new LookGrid.Key(LookGrid.Property.OPACITY, null, LookGrid.NO_PAGE);
        int shipped = SHIPPED.resolve(cell).value;
        stored.put(LookGrid.key(LookGrid.Property.OPACITY, null, LookGrid.NO_PAGE),
            shipped == 0 ? 50 : 0);
        assertTrue(custom(stored));
        assertEquals(GlassResetNudge.DUE,
            GlassResetNudge.evaluate(GlassResetNudge.UNDECIDED, true, true));
        // A stable 1.x install: no wizard, but the old tour was finished.
        assertTrue(GlassResetNudge.usedBefore(0, 1, false, false, false));
    }

    @Test
    public void colourAwayFromDefaultCountsAsCustom() {
        Map<String, Object> none = new HashMap<>();
        assertFalse(custom(none));
        assertTrue(GlassResetNudge.glassCustom(none, SHIPPED, "true", "default", false));
        assertTrue(GlassResetNudge.glassCustom(none, SHIPPED, "system", "harder", false));
        assertTrue(GlassResetNudge.glassCustom(none, SHIPPED, null, null, true));
    }

    @Test
    public void declinedOrAcceptedNeverAsksAgain() {
        assertEquals(GlassResetNudge.SETTLED,
            GlassResetNudge.evaluate(GlassResetNudge.SETTLED, true, true));
        assertEquals(GlassResetNudge.DUE,
            GlassResetNudge.evaluate(GlassResetNudge.DUE, true, false));
    }
}
