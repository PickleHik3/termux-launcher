package com.termux.app.surfaces;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.termux.app.chrome.GlassMotion;

import org.junit.Test;

/** The editor's reveal times: classic is what it always ran, Mist takes its own. */
public class SurfaceEditorMotionTest {

    @Test
    public void classicKeepsTheEditorsOwnTimings() {
        GlassMotion classic = GlassMotion.CLASSIC;
        assertEquals(180L, SurfaceEditorMotion.revealMs(classic, true));
        assertEquals(180L, SurfaceEditorMotion.revealMs(classic, false));
        assertEquals(200L, SurfaceEditorMotion.overlayFadeMs(classic, true));
        assertEquals(200L, SurfaceEditorMotion.overlayFadeMs(classic, false));
        assertFalse("classic keeps the rise, not the player", SurfaceEditorMotion.cardPlaysMotion(classic));
    }

    @Test
    public void mistTakesItsArrivalAndDepartureTimes() {
        GlassMotion mist = GlassMotion.MIST;
        assertEquals(280L, SurfaceEditorMotion.revealMs(mist, true));
        assertEquals(200L, SurfaceEditorMotion.revealMs(mist, false));
        assertEquals(280L, SurfaceEditorMotion.overlayFadeMs(mist, true));
        assertEquals(200L, SurfaceEditorMotion.overlayFadeMs(mist, false));
        assertTrue(SurfaceEditorMotion.cardPlaysMotion(mist));
    }
}
