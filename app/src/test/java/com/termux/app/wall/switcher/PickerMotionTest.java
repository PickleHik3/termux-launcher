package com.termux.app.wall.switcher;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class PickerMotionTest {

    @Test public void totalOpenIsOneWindowPlusTheLastStagger() {
        assertEquals(220L, PickerMotion.totalOpenMs(0));
        assertEquals(220L + 30L * 4, PickerMotion.totalOpenMs(4));
        assertEquals(220L, PickerMotion.totalOpenMs(-2));
    }

    @Test public void eachElementWaitsForItsStagger() {
        assertEquals(0f, PickerMotion.windowProgress(0, 0), 0f);
        assertEquals(0f, PickerMotion.windowProgress(60, 2), 0f);
        assertEquals(110f / 220f, PickerMotion.windowProgress(170, 2), 1e-6f);
        assertEquals(1f, PickerMotion.windowProgress(280, 2), 0f);
    }

    @Test public void theLastElementFinishesWhenTheOpeningDoes() {
        int last = 4;
        assertEquals(1f, PickerMotion.windowProgress(PickerMotion.totalOpenMs(last), last), 0f);
    }

    @Test public void alphaIsFullAfterSeventyPercentOfTheWindow() {
        assertEquals(0.5f, PickerMotion.alphaOf(0.35f), 1e-6f);
        assertEquals(1f, PickerMotion.alphaOf(0.7f), 1e-6f);
        assertEquals(1f, PickerMotion.alphaOf(1f), 0f);
        assertEquals(0f, PickerMotion.alphaOf(-1f), 0f);
    }
}
