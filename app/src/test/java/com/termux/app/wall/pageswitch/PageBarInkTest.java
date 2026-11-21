package com.termux.app.wall.pageswitch;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class PageBarInkTest {

    @Test public void theKeysPopUpLaysADarkCardUnderPaleInkAndAPaleOneUnderDarkInk() {
        assertEquals(0xFF1F1F1F, PageBarGlass.cardFor(0xFFF2F0E4));
        assertEquals(0xFFF2F2F2, PageBarGlass.cardFor(0xFF1F2A2D));
    }
}
