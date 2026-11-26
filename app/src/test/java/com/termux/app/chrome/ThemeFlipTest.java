package com.termux.app.chrome;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class ThemeFlipTest {

    @Test
    public void firstReadOnlyRecords() {
        ThemeFlip flip = new ThemeFlip();
        assertFalse(flip.moved(ThemeFlip.signature(true, true, false, false)));
    }

    @Test
    public void sameLookDoesNotMove() {
        ThemeFlip flip = new ThemeFlip();
        int look = ThemeFlip.signature(false, false, true, true);
        flip.moved(look);
        assertFalse(flip.moved(look));
        assertFalse(flip.moved(look));
    }

    @Test
    public void systemFlipMovesEvenWhenTheWindowIsPinned() {
        // The repro: night pinned off in the app, `cmd uimode night yes` on the system.
        ThemeFlip flip = new ThemeFlip();
        flip.moved(ThemeFlip.signature(false, false, false, false));
        assertTrue(flip.moved(ThemeFlip.signature(false, true, false, false)));
        // And back: `night no` is a flip too.
        assertTrue(flip.moved(ThemeFlip.signature(false, false, false, false)));
    }

    @Test
    public void everyPartOfTheLookMoves() {
        int base = ThemeFlip.signature(false, false, false, false);
        assertNotEquals(base, ThemeFlip.signature(true, false, false, false));
        assertNotEquals(base, ThemeFlip.signature(false, true, false, false));
        assertNotEquals(base, ThemeFlip.signature(false, false, true, false));
        assertNotEquals(base, ThemeFlip.signature(false, false, false, true));
    }

    @Test
    public void noLookIsUnread() {
        for (int i = 0; i < 16; i++) {
            int look = ThemeFlip.signature((i & 1) != 0, (i & 2) != 0, (i & 4) != 0, (i & 8) != 0);
            assertNotEquals(ThemeFlip.UNREAD, look);
        }
    }
}
