package com.termux.app.controlbar;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** Round 4: at font 1.3 the Workspace list read "Workspac…" and the window menu "Move to Works…". */
public class SheetWidthTest {

    @Test public void aSheetWidensWithTheFontScale() {
        assertEquals(280, SheetWidth.px(280f, 1f, 1f, 1000));
        assertEquals(364, SheetWidth.px(280f, 1f, 1.3f, 1000));
        assertEquals("a smaller font keeps the sheet's own width", 280, SheetWidth.px(280f, 1f, 0.85f, 1000));
    }

    @Test public void aSheetNeverOutgrowsItsRoom() {
        assertEquals(330, SheetWidth.px(280f, 1f, 2f, 330));
        assertEquals("before the room is known", 560, SheetWidth.px(280f, 2f, 1f, 0));
    }
}
