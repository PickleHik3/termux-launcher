package com.termux.app.statusbar;

import com.termux.app.wall.PaneWallPage;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

/** The lens edges: where each place's glyph sits and how present it is as the wall moves. */
public class StatusBarLensPolicyTest {

    private static final List<PaneWallPage> RING = Arrays.asList(PaneWallPage.WIDGETS,
        PaneWallPage.TERMINAL, PaneWallPage.DISPLAY);

    @Test public void neighboursRestOneWidthAwayOnTheSideTheySlideInFrom() {
        assertEquals(-1f, StatusBarLensPolicy.distance(RING, PaneWallPage.TERMINAL,
            PaneWallPage.WIDGETS, 0f, 400), 0.001f);
        assertEquals(1f, StatusBarLensPolicy.distance(RING, PaneWallPage.TERMINAL,
            PaneWallPage.DISPLAY, 0f, 400), 0.001f);
        assertEquals(0f, StatusBarLensPolicy.distance(RING, PaneWallPage.TERMINAL,
            PaneWallPage.TERMINAL, 0f, 400), 0.001f);
        // On the ring the far place waits on the near side: from Display, Widgets is to the right.
        assertEquals(1f, StatusBarLensPolicy.distance(RING, PaneWallPage.DISPLAY,
            PaneWallPage.WIDGETS, 0f, 400), 0.001f);
    }

    @Test public void aDragBringsTheArrivingGlyphHomeAndDissolvesIt() {
        // Dragging left by half a width: Display is half a width from the screen's rest.
        float t = StatusBarLensPolicy.distance(RING, PaneWallPage.TERMINAL, PaneWallPage.DISPLAY,
            -200f, 400);
        assertEquals(0.5f, t, 0.001f);
        assertEquals(0.5f, StatusBarLensPolicy.presence(t), 0.001f);
        assertEquals(Math.pow(0.5, 0.7), StatusBarLensPolicy.alpha(t), 0.001f);
        assertEquals(0.9f, StatusBarLensPolicy.scale(t), 0.001f);
        // And the place on screen is fully present at rest, gone at rest, wholly tinted.
        assertEquals(0f, StatusBarLensPolicy.alpha(0f), 0.001f);
        assertEquals(1f, StatusBarLensPolicy.tintWeight(0f), 0.001f);
        assertEquals(0f, StatusBarLensPolicy.tintWeight(1f), 0.001f);
        assertEquals(0.5f, StatusBarLensPolicy.tintWeight(0.5f), 0.001f);
    }

    @Test public void theGlyphTravelsFromItsLensToHome() {
        float home = 15f, inLeg = 30f, outLeg = 370f;
        // A left neighbour at rest sits half past the left edge; a right one half past the right.
        assertEquals(-15f, StatusBarLensPolicy.lensX(-1f, home, inLeg, outLeg), 0.001f);
        assertEquals(385f, StatusBarLensPolicy.lensX(1f, home, inLeg, outLeg), 0.001f);
        // The place on screen would sit at home, where it is not drawn.
        assertEquals(15f, StatusBarLensPolicy.lensX(0f, home, inLeg, outLeg), 0.001f);
        // Halfway in from either side is halfway along the leg.
        assertEquals(0f, StatusBarLensPolicy.lensX(-0.5f, home, inLeg, outLeg), 0.001f);
        assertEquals(200f, StatusBarLensPolicy.lensX(0.5f, home, inLeg, outLeg), 0.001f);
        // Further out keeps going off the bar.
        assertEquals(-60f, StatusBarLensPolicy.lensX(-1.5f, home, inLeg, outLeg), 0.001f);
    }
}
