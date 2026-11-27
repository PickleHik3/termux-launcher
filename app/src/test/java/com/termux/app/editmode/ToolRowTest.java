package com.termux.app.editmode;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** Where a dropped tool lands, in either reading direction. */
public class ToolRowTest {

    @Test public void leftToRightLandsAfterTheChipsItHasPassed() {
        float[] others = {50f, 150f};
        assertEquals(0, ToolRow.dropIndex(10f, others, false));
        assertEquals(1, ToolRow.dropIndex(100f, others, false));
        assertEquals(2, ToolRow.dropIndex(300f, others, false));
    }

    @Test public void rightToLeftReadsTheRowFromTheRight() {
        // The first chip stands at the right; held and released in place it stays first.
        float[] others = {50f};
        assertEquals(0, ToolRow.dropIndex(150f, others, true));
        assertEquals(1, ToolRow.dropIndex(10f, others, true));
    }
}
