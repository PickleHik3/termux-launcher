package com.termux.app.dock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** The reflection's look numbers: depth curves, ripple, specular line and the A-Z band. */
public class PeekReflectionLookTest {

    @Test
    public void theMirrorIsClearAtTheIconAndSofterAndFainterWithDepth() {
        assertEquals(0f, PeekReflectionLook.blurFraction(0f), 0f);
        assertEquals(1f, PeekReflectionLook.blurFraction(1f), 1e-6f);
        assertEquals(1f, PeekReflectionLook.fadeFactor(0f), 0f);
        assertEquals(0f, PeekReflectionLook.fadeFactor(1f), 0f);
        float lastBlur = -1f;
        float lastFade = 2f;
        for (float t = 0f; t <= 1f; t += 0.05f) {
            assertTrue(PeekReflectionLook.blurFraction(t) > lastBlur);
            assertTrue(PeekReflectionLook.fadeFactor(t) < lastFade);
            lastBlur = PeekReflectionLook.blurFraction(t);
            lastFade = PeekReflectionLook.fadeFactor(t);
        }
        assertEquals(PeekReflectionLook.blurFraction(1f), PeekReflectionLook.blurFraction(3f), 0f);
    }

    @Test
    public void theRippleGrowsFromNothingAtTheIcon() {
        assertEquals(0f, PeekReflectionLook.rippleAmplitudePx(0f, 2.625f), 0f);
        assertEquals(1.4f * 2.625f, PeekReflectionLook.rippleAmplitudePx(1f, 2.625f), 1e-4f);
    }

    @Test
    public void theSpecularLineHugsTheTopAndLetsGoDownTheSides() {
        float th = PeekReflectionLook.specularThicknessPx(2.625f);
        assertEquals(1.5f * 2.625f, th, 1e-4f);
        assertEquals(1f, PeekReflectionLook.specularThicknessPx(0.1f), 0f);
        float onEdge = PeekReflectionLook.specular(0f, 0f, th, 74f);
        assertEquals(PeekReflectionLook.SPECULAR_STRENGTH, onEdge, 1e-5f);
        assertEquals(0f, PeekReflectionLook.specular(th, 0f, th, 74f), 1e-5f);
        assertEquals(0f, PeekReflectionLook.specular(-1f, 0f, th, 74f), 0f);
        assertTrue(PeekReflectionLook.specular(0f, 40f, th, 74f) < onEdge);
        assertEquals(0f, PeekReflectionLook.specular(0f, 74f * 1.5f, th, 74f), 1e-5f);
    }

    /**
     * Apart, Home's lane and the host are drawn Home's drop lower alike, so the mirror stands
     * over the host as laid out and over its fade band exactly as Home's rest drew it before the
     * cards stood apart (no drop): the host's drop is never taken off the y. Across, the lane's
     * slide is drawn in and the host's taken off once, so the two cancel.
     */
    @Test
    public void apartTheMirrorStandsOnItsBandAsBeforeTheCardsStoodApart() {
        float drop = 31f;
        float laneTy = com.termux.app.place.SideCards.laneTyPx(
            com.termux.app.place.StackLanes.HOME, true, drop, 0, 0f);
        assertEquals("Home's lane carries the host's drop", drop, laneTy, 0f);
        // The row's y over the host as drawn less as laid out: the lane's drop less the host's.
        float rowOverHostMoved = laneTy - drop;
        for (float origin : new float[] {0f, 120f}) {
            float base = PeekGlassGeometry.drawOriginY(origin, 0f);
            assertEquals(origin + rowOverHostMoved, PeekGlassGeometry.drawOriginY(origin, drop), 0f);
            assertEquals(base, PeekGlassGeometry.drawOriginY(origin, drop), 0f);
        }
        float slide = -1080f;
        // drawOn translates by rowDrawX(-originX, laneTx): the lane's slide less the host's.
        assertEquals(0f, PeekGlassGeometry.rowDrawX(-PeekGlassGeometry.drawOriginX(0f, slide),
            slide), 0f);
    }
}
