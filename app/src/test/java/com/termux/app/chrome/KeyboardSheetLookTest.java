package com.termux.app.chrome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class KeyboardSheetLookTest {

    private static final float EPS = 1e-4f;

    @Test
    public void shutDrawerLeavesTheKeyboardItsOwnLook() {
        assertEquals(0f, KeyboardSheetLook.glassFraction(true, 0f), EPS);
            }

    @Test
    public void aKeyboardThatIsDownWearsNothing() {
        assertEquals(0f, KeyboardSheetLook.glassFraction(false, 1f), EPS);
    }

    @Test
    public void nonsenseFractionIsHeldInRange() {
        assertEquals(1f, KeyboardSheetLook.glassFraction(true, 3f), EPS);
        assertEquals(0f, KeyboardSheetLook.glassFraction(true, -1f), EPS);
        assertEquals(0f, KeyboardSheetLook.glassFraction(true, Float.NaN), EPS);
    }

    @Test
    public void theGlassFollowsTheDrawerWithNoStepAtEitherEnd() {
        float prev = 0f;
        for (int i = 1; i <= 10; i++) {
            float f = KeyboardSheetLook.glassFraction(true, i / 10f);
            assertTrue(f >= prev && f - prev <= 0.1001f);
            prev = f;
        }
        assertEquals(1f, prev, EPS);
    }

    /** The keyboard's corners are never rounded to the drawer's: the pockets showed untinted. */
    @Test
    public void theKeyboardTakesNoCornersFromTheDrawer() {
        for (java.lang.reflect.Method m : KeyboardSheetLook.class.getDeclaredMethods())
            assertTrue(m.getName(), !m.getName().toLowerCase().contains("radius"));
        for (java.lang.reflect.Method m : KeyboardSheetSkin.class.getDeclaredMethods())
            assertTrue(m.getName(), !m.getName().toLowerCase().contains("radius"));
    }
}
