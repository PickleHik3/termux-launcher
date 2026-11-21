package com.termux.app.surfaces;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.SurfaceProperty;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.SurfaceSlot;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/**
 * The global row's writes (layout editor v2, DECISIONS items 13 and 14): Blur, Opacity and Grain
 * land on Global and put every surface back on it, but for a chrome surface's own values and
 * the terminal's Background (its Opacity and Blur), which no Global write reaches (#287).
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class GlobalGlassTest {

    private TermuxAppSharedPreferences preferences;

    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.getApplication().getApplicationContext();
        SharedPreferences store = context.getSharedPreferences("global-glass-test",
            Context.MODE_PRIVATE);
        store.edit().clear().commit();
        preferences = new TermuxAppSharedPreferences(context, store, null);
    }

    @Test
    public void globalOpacityLeavesTheChromesOwnAndNeverReachesTheTerminal() {
        preferences.setTerminalBackgroundOpacity(81);
        preferences.detachSurfaceValue(SurfaceSlot.DOCK, SurfaceProperty.OPACITY, 70);

        assertEquals(42, GlobalGlass.write(preferences, SurfaceProperty.OPACITY, 42));

        // Own stays (appearance-one-look spec §1): the dock keeps its own; the rest follow.
        for (SurfaceSlot slot : SurfaceSlot.values()) {
            if (slot != SurfaceSlot.CANVAS
                    && TermuxAppSharedPreferences.hasSurfaceProperty(slot, SurfaceProperty.OPACITY))
                assertEquals(slot.key, slot != SurfaceSlot.DOCK,
                    preferences.isSurfaceInheriting(slot, SurfaceProperty.OPACITY));
        }
        assertEquals(42, preferences.getSurfaceGlobalValue(SurfaceProperty.OPACITY));
        // The terminal's Background is its own (ADR 0031 §4, #287).
        assertEquals(81, preferences.getTerminalBackgroundOpacity());
    }

    @Test
    public void globalGrainLeavesTheKeyboardsOwn() {
        preferences.detachSurfaceValue(SurfaceSlot.CANVAS, SurfaceProperty.GRAIN, 30);
        preferences.detachSurfaceValue(SurfaceSlot.KEYBOARD, SurfaceProperty.GRAIN, 50);

        assertEquals(9, GlobalGlass.write(preferences, SurfaceProperty.GRAIN, 9));

        for (SurfaceSlot slot : SurfaceSlot.values()) {
            if (TermuxAppSharedPreferences.hasSurfaceProperty(slot, SurfaceProperty.GRAIN))
                assertEquals(slot.key, slot != SurfaceSlot.KEYBOARD,
                    preferences.isSurfaceInheriting(slot, SurfaceProperty.GRAIN));
        }
        assertEquals(9, preferences.getSurfaceGlobalValue(SurfaceProperty.GRAIN));
    }

    @Test
    public void globalBlurFollowsTheSameRule() {
        preferences.detachSurfaceValue(SurfaceSlot.STATUS, SurfaceProperty.BLUR, 3);
        assertEquals(AppearanceLooks.BLUR_MAX_DP,
            GlobalGlass.write(preferences, SurfaceProperty.BLUR, 99));
        assertFalse("the status bar keeps its own",
            preferences.isSurfaceInheriting(SurfaceSlot.STATUS, SurfaceProperty.BLUR));
    }

    @Test
    public void theTerminalsOwnOpacityLeavesGlobalAsItIs() {
        GlobalGlass.write(preferences, SurfaceProperty.OPACITY, 40);
        preferences.setTerminalBackgroundOpacity(75);
        assertEquals(75, preferences.getTerminalBackgroundOpacity());
        assertEquals("the others keep Global", 40,
            preferences.getSurfaceGlobalValue(SurfaceProperty.OPACITY));
    }

    @Test(expected = IllegalArgumentException.class)
    public void cornersAndMarginAreNotGlass() {
        GlobalGlass.write(preferences, SurfaceProperty.SIDE_GAP, 4);
    }

    @Test
    public void globalTintReattachesEverySurface() {
        preferences.detachSurfaceValue(SurfaceSlot.CANVAS, SurfaceProperty.TINT, 30);
        preferences.detachSurfaceValue(SurfaceSlot.DOCK, SurfaceProperty.TINT, 50);

        assertEquals(40, GlobalGlass.write(preferences, SurfaceProperty.TINT, 40));
        assertEquals(100, GlobalGlass.write(preferences, SurfaceProperty.TINT, 400));
        assertEquals(40, GlobalGlass.write(preferences, SurfaceProperty.TINT, 40));

        for (SurfaceSlot slot : SurfaceSlot.values())
            assertTrue(slot.key, preferences.isSurfaceInheriting(slot, SurfaceProperty.TINT));
        assertEquals(40, preferences.getSurfaceGlobalValue(SurfaceProperty.TINT));
        assertEquals(40, preferences.getTerminalTintStrength());
        assertEquals(40, preferences.getDockTintStrength(com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.terminalPageId()));
    }

    /** A Hue picked while the shared Tint is 0 raises it to 40 in the same edit. */
    @Test
    public void aHuePickAtTintZeroRaisesTheTint() {
        GlobalGlass.write(preferences, SurfaceProperty.TINT, 0);
        int tint = GlobalGlass.writeTintHue(preferences,
            com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_APP
                .GLASS_TINT_HUE_TERTIARY);
        assertEquals(GlobalGlass.TINT_ON_HUE_PICK, tint);
        assertEquals(40, GlobalGlass.TINT_ON_HUE_PICK);
        assertEquals(40, preferences.getSurfaceGlobalValue(SurfaceProperty.TINT));
        assertEquals("every surface wears it, as the Tint slider writes it", 40,
            preferences.getDockTintStrength(com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.terminalPageId()));
        assertEquals("tertiary", preferences.getSurfaceGlassTintHue());
    }

    /** With some Tint already on the glass, a Hue pick changes the hue alone. */
    @Test
    public void aHuePickAboveTintZeroLeavesTheTint() {
        GlobalGlass.write(preferences, SurfaceProperty.TINT, 15);
        assertEquals(15, GlobalGlass.writeTintHue(preferences, "secondary"));
        assertEquals(15, preferences.getSurfaceGlobalValue(SurfaceProperty.TINT));
        assertEquals("secondary", preferences.getSurfaceGlassTintHue());
        GlobalGlass.write(preferences, SurfaceProperty.TINT, 100);
        assertEquals(100, GlobalGlass.writeTintHue(preferences, "primary"));
        assertEquals(100, preferences.getSurfaceGlobalValue(SurfaceProperty.TINT));
    }
}
