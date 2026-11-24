package com.termux.app.statusbar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Status bar card › Place: the strip or a thin row in the pane; one global value. */
public class StatusPlaceTest {

    @Test
    public void theEnumReadsItsValuesAndFallsBackToTheStrip() {
        assertEquals(StatusPlace.STRIP, StatusPlace.fromValue("strip"));
        assertEquals(StatusPlace.PANE, StatusPlace.fromValue("pane"));
        assertEquals(StatusPlace.STRIP, StatusPlace.fromValue(null));
        assertEquals(StatusPlace.STRIP, StatusPlace.fromValue("elsewhere"));
        assertEquals(StatusPlace.STRIP, StatusPlace.DEFAULT);
        assertTrue(StatusPlace.PANE.inPane());
        assertFalse(StatusPlace.STRIP.inPane());
    }
}
