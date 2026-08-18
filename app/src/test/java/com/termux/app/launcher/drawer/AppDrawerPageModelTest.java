package com.termux.app.launcher.drawer;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class AppDrawerPageModelTest {

    @Test public void countsZeroOneExactAndRemainderWithoutAPhantomPage() {
        assertEquals(0, AppDrawerPageModel.pageCount(0, 12));
        assertEquals(1, AppDrawerPageModel.pageCount(1, 12));
        assertEquals(1, AppDrawerPageModel.pageCount(12, 12));
        assertEquals(2, AppDrawerPageModel.pageCount(13, 12));
    }

    @Test public void partitionsRowMajorBounds() {
        assertEquals(0, AppDrawerPageModel.startForPage(0, 29, 12));
        assertEquals(12, AppDrawerPageModel.endForPage(0, 29, 12));
        assertEquals(12, AppDrawerPageModel.startForPage(1, 29, 12));
        assertEquals(24, AppDrawerPageModel.endForPage(1, 29, 12));
        assertEquals(24, AppDrawerPageModel.startForPage(2, 29, 12));
        assertEquals(29, AppDrawerPageModel.endForPage(2, 29, 12));
    }

    @Test public void clampsAfterResultsShrink() {
        assertEquals(0, AppDrawerPageModel.clampPage(4, 0));
        assertEquals(1, AppDrawerPageModel.clampPage(4, 2));
        assertEquals(0, AppDrawerPageModel.clampPage(-4, 2));
    }
}
