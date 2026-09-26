package com.termux.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.termux.app.wall.PaneWallPage;

import org.junit.Test;

/**
 * Which place a Layout editor deep link opens over. The value is the name the wall already uses for
 * its places, and anything else — a missing extra, an old section name, a place this build has no
 * page for — opens the editor over whatever place is on screen.
 */
public class LayoutEditorPlaceLinkTest {

    @Test
    public void aLinkNamesItsPlaceTheWayTheWallDoes() {
        for (PaneWallPage place : PaneWallPage.values())
            assertEquals(place, TermuxActivity.parseLayoutEditorPlace(place.toolName()));
        assertEquals(PaneWallPage.WIDGETS, TermuxActivity.parseLayoutEditorPlace("widgets"));
        assertEquals(PaneWallPage.DISPLAY, TermuxActivity.parseLayoutEditorPlace(" Display "));
    }

    @Test
    public void anUnknownOrAbsentPlaceNamesNone() {
        assertNull(TermuxActivity.parseLayoutEditorPlace(null));
        assertNull(TermuxActivity.parseLayoutEditorPlace(""));
        assertNull(TermuxActivity.parseLayoutEditorPlace("home"));
        assertNull(TermuxActivity.parseLayoutEditorPlace("sessions"));
    }
}
