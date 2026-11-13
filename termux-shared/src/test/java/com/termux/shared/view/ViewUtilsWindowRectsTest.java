package com.termux.shared.view;

import static org.junit.Assert.assertEquals;

import android.graphics.Rect;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class ViewUtilsWindowRectsTest {

    @Test
    public void fullScreenPortraitKeepsFrameAndView() {
        Rect[] r = ViewUtils.buildWindowAndViewRects(new Rect(0, 60, 1000, 2000), 0, 0, 1900, 1000, 100, false);
        assertEquals(new Rect(0, 60, 1000, 2000), r[0]);
        assertEquals(new Rect(0, 1900, 1000, 2000), r[1]);
    }

    @Test
    public void fullScreenLandscapeExtendsRightToViewForNavbar() {
        Rect[] r = ViewUtils.buildWindowAndViewRects(new Rect(0, 0, 2700, 1000), 0, 0, 900, 2800, 100, true);
        assertEquals(new Rect(0, 0, 2800, 1000), r[0]);
        assertEquals(new Rect(0, 900, 2800, 1000), r[1]);
    }

    @Test
    public void rightHalfSplitKeepsBothInScreenSpace() {
        Rect[] r = ViewUtils.buildWindowAndViewRects(new Rect(1368, 0, 2736, 1824), 0, 1368, 1724, 1368, 100, true);
        assertEquals(new Rect(1368, 0, 2736, 1824), r[0]);
        assertEquals(new Rect(1368, 1724, 2736, 1824), r[1]);
        assertEquals(r[0].bottom, r[1].bottom);
    }

    @Test
    public void bottomHalfSplitInLandscapeKeepsBothInScreenSpace() {
        Rect[] r = ViewUtils.buildWindowAndViewRects(new Rect(0, 912, 2736, 1824), 0, 0, 1724, 2736, 100, true);
        assertEquals(new Rect(0, 912, 2736, 1824), r[0]);
        assertEquals(new Rect(0, 1724, 2736, 1824), r[1]);
        assertEquals(r[0].bottom, r[1].bottom);
    }
}
