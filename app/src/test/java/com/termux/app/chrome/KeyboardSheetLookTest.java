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

    @Test
    public void aDockedSliceUnderTheSheetIsRoundedAtItsOwnTop() {
        // Docked: the frame's card starts 98px above the keyboard (the extra keys), its top
        // corners 63px, square at the screen's bottom.
        LiveChromeShape.Clip slice = new LiveChromeShape.Clip(0, 98, 0, 0, 63f, 63f, 0f, 0f);
        LiveChromeShape.Clip own = KeyboardSheetLook.outline(slice, true);
        assertEquals(0, own.reachTop);
        assertEquals(63f, own.topLeft, EPS);
        assertEquals(63f, own.topRight, EPS);
        assertEquals(0f, own.bottomLeft, EPS);
        assertEquals(0f, own.bottomRight, EPS);
        assertEquals(slice.reachBottom, own.reachBottom);
    }

    @Test
    public void theKeyboardKeepsItsOwnClipWithoutTheSheet() {
        LiveChromeShape.Clip slice = new LiveChromeShape.Clip(0, 98, 0, 0, 63f, 63f, 0f, 0f);
        org.junit.Assert.assertSame(slice, KeyboardSheetLook.outline(slice, false));
        LiveChromeShape.Clip card = LiveChromeShape.cardClip(40f);
        org.junit.Assert.assertSame(card, KeyboardSheetLook.outline(card, true));
    }
}
