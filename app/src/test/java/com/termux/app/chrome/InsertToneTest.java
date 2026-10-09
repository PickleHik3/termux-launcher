package com.termux.app.chrome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class InsertToneTest {

    private static final int FRAME = 0xFF405060;

    private static float lumaOver(int tint, int frame) {
        return InsertTone.luma(InsertTone.over(tint, frame | 0xFF000000));
    }

    private static void assertOneStepDarker(int tint) {
        int floored = InsertTone.floorTint(tint, FRAME);
        float frame = InsertTone.luma(FRAME);
        // One byte of rounding in the alpha and each channel.
        assertTrue("tint " + Integer.toHexString(tint),
            lumaOver(floored, FRAME) <= frame * (1f - InsertTone.STEP) + 0.01f);
    }

    @Test
    public void anInvisibleTintGetsExactlyTheStepOfBlackUnderIt() {
        int floored = InsertTone.floorTint(0x00000000, FRAME);
        assertEquals("black", 0, floored & 0x00FFFFFF);
        assertEquals(Math.round(InsertTone.STEP * 255f), floored >>> 24, 1);
        assertOneStepDarker(0x00000000);
    }

    @Test
    public void everyTintEndsOneStepDarkerThanTheFrameGlass() {
        int[] tints = {0x00000000, 0x20000000, 0x40FFFFFF, 0x99101820, 0xFFFFFFFF, 0x80808080};
        for (int tint : tints) assertOneStepDarker(tint);
    }

    @Test
    public void aTintAlreadyFurtherDownIsLeftAlone() {
        int dark = 0xCC000000;
        assertEquals(dark, InsertTone.floorTint(dark, FRAME));
    }

    @Test
    public void moreDarknessStaysDarkerThanLess() {
        int less = InsertTone.floorTint(0x40000000, FRAME);
        int more = InsertTone.floorTint(0xA0000000, FRAME);
        assertTrue(lumaOver(more, FRAME) <= lumaOver(less, FRAME));
    }

    @Test
    public void theStepIsARatioSoBrightAndDarkFramesAskForTheSameBlack() {
        int onDark = InsertTone.floorTint(0x00000000, 0xFF202020);
        int onLight = InsertTone.floorTint(0x00000000, 0xFFE0E0E0);
        assertEquals(onDark >>> 24, onLight >>> 24, 1);
    }
}
