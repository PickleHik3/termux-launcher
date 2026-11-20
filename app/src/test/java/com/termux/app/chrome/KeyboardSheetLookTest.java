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
    public void openDrawerGivesTheSheetGlass() {
        assertEquals(1f, KeyboardSheetLook.glassFraction(true, 1f), EPS);
    }

    @Test
    public void theLookFollowsTheDrawersFractionWithNoStepAtEitherEnd() {
        float prev = 0f;
        for (int i = 1; i <= 10; i++) {
            float f = KeyboardSheetLook.glassFraction(true, i / 10f);
            assertTrue(f >= prev);
            assertTrue(f - prev <= 0.1001f);
            prev = f;
        }
        assertEquals(1f, prev, EPS);
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
    public void dimFollowsTheDrawersDimAtTheGlassFraction() {
        // The drawer's own dim at full progress (0x54), none while the glass is not worn.
        assertEquals(0x54, KeyboardSheetLook.dimAlpha(1f, 1f));
        assertEquals(0, KeyboardSheetLook.dimAlpha(0f, 1f));
        assertEquals(0, KeyboardSheetLook.dimAlpha(1f, 0f));
        assertEquals(Math.round(0x54 * 0.5f), KeyboardSheetLook.dimAlpha(0.5f, 1f));
        assertEquals(0x54 / 2, KeyboardSheetLook.dimAlpha(1f, 0.5f), 1);
        assertEquals(0, KeyboardSheetLook.dimAlpha(Float.NaN, 1f));
    }
}
