package com.termux.app.wall.pageswitch;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class PageBarInkTest {

    @Test public void theMocksPairStaysFull() {
        assertEquals(1f, PageBarInk.neutralAlpha(0xFFF4B79E, 0xFFF2F0E4), 0f);
    }

    @Test public void navyOnNavyDims() {
        assertEquals(PageBarInk.DIMMED, PageBarInk.neutralAlpha(0xFF3F5AA0, 0xFF42568C), 0f);
    }

    @Test public void identicalColoursDim() {
        assertEquals(PageBarInk.DIMMED, PageBarInk.neutralAlpha(0xFFABCDEF, 0xFFABCDEF), 0f);
    }

    @Test public void whiteAgainstBlackStaysFull() {
        assertEquals(1f, PageBarInk.neutralAlpha(0xFFFFFFFF, 0xFF000000), 0f);
    }

    @Test public void theKeysPopUpLaysADarkCardUnderPaleInkAndAPaleOneUnderDarkInk() {
        assertEquals(0xFF1F1F1F, PageBarGlass.cardFor(0xFFF2F0E4));
        assertEquals(0xFFF2F2F2, PageBarGlass.cardFor(0xFF1F2A2D));
    }
}
