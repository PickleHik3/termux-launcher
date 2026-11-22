package com.termux.app.terminal.extrakeys;

import static com.termux.app.terminal.extrakeys.ExtraKeysBarHeight.Step.GROW;
import static com.termux.app.terminal.extrakeys.ExtraKeysBarHeight.Step.NONE;
import static com.termux.app.terminal.extrakeys.ExtraKeysBarHeight.Step.SHRINK;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ExtraKeysBarHeightTest {

    private static final int BASE = 40;
    private static final float SCALE = 1.25f;

    @Test
    public void overlapCaseTwoRowPageIsTwiceTheOneRowPage() {
        int[] pages = {1, 2};
        int h1 = ExtraKeysBarHeight.pageHeightPx(BASE, ExtraKeysBarHeight.rowsOf(pages, 0), SCALE);
        int h2 = ExtraKeysBarHeight.pageHeightPx(BASE, ExtraKeysBarHeight.rowsOf(pages, 1), SCALE);
        assertEquals(2 * h1, h2);
        assertEquals(GROW, ExtraKeysBarHeight.step(h1, h2));
        assertEquals(SHRINK, ExtraKeysBarHeight.step(h2, h1));
        assertEquals(NONE, ExtraKeysBarHeight.step(h1, h1));
    }

    @Test
    public void textInputPageIsOneRow() {
        int[] pages = {1, 2};
        assertEquals(1, ExtraKeysBarHeight.rowsOf(pages, 2));
        assertEquals(0, ExtraKeysBarHeight.rowsOf(pages, 3));
        assertEquals(0, ExtraKeysBarHeight.rowsOf(pages, -1));
        assertEquals(0, ExtraKeysBarHeight.rowsOf(null, 0));
        assertEquals(1, ExtraKeysBarHeight.rowsOf(new int[0], 0));
    }

    @Test
    public void concealedEndpoints() {
        assertEquals(40, ExtraKeysBarHeight.concealedPx(GROW, 50, 90, 0f));
        assertEquals(0, ExtraKeysBarHeight.concealedPx(GROW, 50, 90, 1f));
        assertEquals(0, ExtraKeysBarHeight.concealedPx(SHRINK, 90, 50, 0f));
        assertEquals(40, ExtraKeysBarHeight.concealedPx(SHRINK, 90, 50, 1f));
        assertEquals(20, ExtraKeysBarHeight.concealedPx(GROW, 50, 90, 0.5f));
        assertEquals(0, ExtraKeysBarHeight.concealedPx(NONE, 50, 50, 0.5f));
    }
}
