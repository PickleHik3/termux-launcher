package com.termux.app.chrome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class KeyboardSheetLookTest {

    private static final float EPS = 1e-4f;

    @Test
    public void shutDrawerLeavesTheKeyboardItsOwnLook() {
        assertEquals(0f, KeyboardSheetLook.glassFraction(true, 0f), EPS);
        assertEquals(0f, KeyboardSheetLook.topRadiusPx(false, 0f, 28f, 0f), EPS);
    }

    @Test
    public void openDrawerGivesTheSheetGlassAndTheDrawersTopRadiusDocked() {
        float f = KeyboardSheetLook.glassFraction(true, 1f);
        assertEquals(1f, f, EPS);
        assertEquals(28f, KeyboardSheetLook.topRadiusPx(false, 0f, 28f, f), EPS);
    }

    @Test
    public void theLookFollowsTheDrawersFractionWithNoStepAtEitherEnd() {
        float prevR = 0f;
        float prevG = 0f;
        for (int i = 1; i <= 10; i++) {
            float f = KeyboardSheetLook.glassFraction(true, i / 10f);
            float r = KeyboardSheetLook.topRadiusPx(false, 0f, 28f, f);
            assertTrue(f >= prevG && r >= prevR);
            assertTrue(f - prevG <= 0.1001f && r - prevR <= 2.81f);
            prevG = f;
            prevR = r;
        }
        assertEquals(28f, prevR, EPS);
    }

    @Test
    public void aKeyboardThatIsDownWearsNothing() {
        assertEquals(0f, KeyboardSheetLook.glassFraction(false, 1f), EPS);
    }

    @Test
    public void aFloatingCardKeepsItsOwnCorners() {
        assertEquals(26f, KeyboardSheetLook.topRadiusPx(true, 26f, 40f, 1f), EPS);
        assertEquals(0f, KeyboardSheetLook.topRadiusPx(true, 0f, 40f, 1f), EPS);
    }

    @Test
    public void aDockedKeyboardWithRoundedScreenCornersNeverShrinksThem() {
        assertEquals(44f, KeyboardSheetLook.topRadiusPx(false, 44f, 28f, 1f), EPS);
        assertEquals(10f, KeyboardSheetLook.topRadiusPx(false, 10f, 28f, 0f), EPS);
    }

    @Test
    public void nonsenseFractionIsHeldInRange() {
        assertEquals(1f, KeyboardSheetLook.glassFraction(true, 3f), EPS);
        assertEquals(0f, KeyboardSheetLook.glassFraction(true, -1f), EPS);
        assertEquals(0f, KeyboardSheetLook.glassFraction(true, Float.NaN), EPS);
    }

    @Test
    public void topRadiusClipKeepsTheBottomAndTheSameClipWhenAlreadyThere() {
        assertNull(KeyboardSheetSkin.withTopRadius(null, 0f));
        LiveChromeShape.Clip c = KeyboardSheetSkin.withTopRadius(null, 28f);
        assertEquals(28f, c.topLeft, EPS);
        assertEquals(28f, c.topRight, EPS);
        assertEquals(0f, c.bottomLeft, EPS);
        assertTrue(c.perCorner);
        assertSame(c, KeyboardSheetSkin.withTopRadius(c, 28f));
        LiveChromeShape.Clip card = LiveChromeShape.cardClip(12f);
        LiveChromeShape.Clip r = KeyboardSheetSkin.withTopRadius(card, 12f);
        assertSame(card, r);
    }
}
