package com.termux.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.termux.app.wall.PaneWallPage;

import org.junit.Test;

/**
 * Which place a surface-editor deep link opens on. The value is the name the wall already uses for
 * its places, and anything else — a missing extra, an old section name, a place this build has no
 * page for — is the shared look every place wears.
 */
public class SurfaceEditorPlaceLinkTest {

    @Test
    public void aLinkNamesItsPlaceTheWayTheWallDoes() {
        for (PaneWallPage place : PaneWallPage.values())
            assertEquals(place, TermuxActivity.parseSurfaceEditorPlace(place.toolName()));
        assertEquals(PaneWallPage.WIDGETS, TermuxActivity.parseSurfaceEditorPlace("widgets"));
        assertEquals(PaneWallPage.DISPLAY, TermuxActivity.parseSurfaceEditorPlace(" Display "));
    }

    @Test
    public void anUnknownOrAbsentPlaceOpensOnTheSharedLayer() {
        assertNull(TermuxActivity.parseSurfaceEditorPlace(null));
        assertNull(TermuxActivity.parseSurfaceEditorPlace(""));
        assertNull(TermuxActivity.parseSurfaceEditorPlace("home"));
        assertNull(TermuxActivity.parseSurfaceEditorPlace("sessions"));
    }
}
