package com.termux.app.surfaces;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class CursorTrailChoiceTest {

    @Test public void disabledTrailShowsOffWhateverTheStyle() {
        assertEquals("off", CursorTrailChoice.menuId(false, "comet"));
    }

    @Test public void enabledTrailShowsItsStyle() {
        assertEquals("comet", CursorTrailChoice.menuId(true, "comet"));
    }

    @Test public void offTurnsTheTrailOffAndStoresNoStyle() {
        CursorTrailChoice.Resolved r = CursorTrailChoice.resolve("off");
        assertFalse(r.enabled);
        assertNull(r.style);
    }

    @Test public void aStyleTurnsTheTrailBackOn() {
        CursorTrailChoice.Resolved r = CursorTrailChoice.resolve("railgun");
        assertTrue(r.enabled);
        assertEquals("railgun", r.style);
    }
}
