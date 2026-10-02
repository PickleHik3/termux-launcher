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
 * land on the base and put every surface, terminal included, back on it; the terminal's own
 * Opacity detaches it again afterwards.
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
    public void globalOpacityReattachesEverySurfaceTerminalIncluded() {
        preferences.detachSurfaceValue(SurfaceSlot.CANVAS, SurfaceProperty.OPACITY, 81);
        preferences.detachSurfaceValue(SurfaceSlot.DOCK, SurfaceProperty.OPACITY, 70);
        assertFalse(preferences.isSurfaceInheriting(SurfaceSlot.CANVAS, SurfaceProperty.OPACITY));

        assertEquals(42, GlobalGlass.write(preferences, SurfaceProperty.OPACITY, 42));

        for (SurfaceSlot slot : SurfaceSlot.values()) {
            if (TermuxAppSharedPreferences.hasSurfaceProperty(slot, SurfaceProperty.OPACITY))
                assertTrue(slot.key, preferences.isSurfaceInheriting(slot, SurfaceProperty.OPACITY));
        }
        assertEquals(42, preferences.getSurfaceBaseValue(SurfaceProperty.OPACITY));
        assertEquals("the terminal wears the base now", 42,
            preferences.getTerminalBackgroundOpacity());
    }

    @Test
    public void globalGrainReattachesEverySurface() {
        preferences.detachSurfaceValue(SurfaceSlot.CANVAS, SurfaceProperty.GRAIN, 30);
        preferences.detachSurfaceValue(SurfaceSlot.KEYBOARD, SurfaceProperty.GRAIN, 50);

        assertEquals(9, GlobalGlass.write(preferences, SurfaceProperty.GRAIN, 9));

        for (SurfaceSlot slot : SurfaceSlot.values()) {
            if (TermuxAppSharedPreferences.hasSurfaceProperty(slot, SurfaceProperty.GRAIN))
                assertTrue(slot.key, preferences.isSurfaceInheriting(slot, SurfaceProperty.GRAIN));
        }
        assertEquals(9, preferences.getSurfaceBaseValue(SurfaceProperty.GRAIN));
        assertEquals(9, preferences.getInAppKeyboardGrain());
    }

    @Test
    public void globalBlurFollowsTheSameRule() {
        preferences.detachSurfaceValue(SurfaceSlot.STATUS, SurfaceProperty.BLUR, 3);
        assertEquals(AppearanceLooks.BLUR_MAX_DP,
            GlobalGlass.write(preferences, SurfaceProperty.BLUR, 99));
        assertTrue(preferences.isSurfaceInheriting(SurfaceSlot.STATUS, SurfaceProperty.BLUR));
    }

    @Test
    public void theTerminalsOwnOpacityDetachesItAgainAfterwards() {
        GlobalGlass.write(preferences, SurfaceProperty.OPACITY, 40);
        preferences.detachSurfaceValue(SurfaceSlot.CANVAS, SurfaceProperty.OPACITY, 75);
        preferences.setTerminalBackgroundOpacity(75);
        assertEquals(75, preferences.getTerminalBackgroundOpacity());
        assertEquals("the others keep the base", 40,
            preferences.getSurfaceBaseValue(SurfaceProperty.OPACITY));
    }

    @Test(expected = IllegalArgumentException.class)
    public void cornersAndMarginAreNotGlass() {
        GlobalGlass.write(preferences, SurfaceProperty.SIDE_GAP, 4);
    }
}
