package com.termux.app.surfaces;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.termux.app.chrome.GlassMotion;

import org.junit.Test;

/**
 * The editor's reveal times: classic plays the editor's motion tokens (EditorMotion: a fade in
 * on effects-default, a fade out on effects-fast), Mist takes its own.
 */
public class SurfaceEditorMotionTest {

    @Test
    public void classicPlaysTheEditorsFadeSprings() {
        GlassMotion classic = GlassMotion.CLASSIC;
        long in = EditorMotion.Token.EFFECTS_DEFAULT.settleMs();
        long out = EditorMotion.Token.EFFECTS_FAST.settleMs();
        assertEquals(in, SurfaceEditorMotion.revealMs(classic, true));
        assertEquals(out, SurfaceEditorMotion.revealMs(classic, false));
        assertEquals(in, SurfaceEditorMotion.overlayFadeMs(classic, true));
        assertEquals(out, SurfaceEditorMotion.overlayFadeMs(classic, false));
        assertTrue("a fade out is quicker than a fade in", out < in);
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
