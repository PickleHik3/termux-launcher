package com.termux.app.statusbar;

import com.termux.app.wall.PaneWallPage;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

/** The place icons: where each sits and how present it is as the wall moves. */
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

    @Test public void allThreeIconsAreWholeWithinAWidthAndTheLeavingOneDissolves() {
        assertEquals(1f, StatusBarLensPolicy.alpha(0f), 0.001f);
        assertEquals(1f, StatusBarLensPolicy.alpha(-1f), 0.001f);
        assertEquals(1f, StatusBarLensPolicy.alpha(1f), 0.001f);
        assertEquals(0.5f, StatusBarLensPolicy.alpha(1.25f), 0.001f);
        assertEquals(0f, StatusBarLensPolicy.alpha(-1.5f), 0.001f);
        assertEquals(1f, StatusBarLensPolicy.scale(0f), 0.001f);
        assertEquals(0.86f, StatusBarLensPolicy.scale(1f), 0.001f);
        assertEquals(1f, StatusBarLensPolicy.tintWeight(0f), 0.001f);
        assertEquals(0.5f, StatusBarLensPolicy.tintWeight(0.5f), 0.001f);
        assertEquals(0f, StatusBarLensPolicy.tintWeight(1f), 0.001f);
    }

    @Test public void aDragCarriesTheArrivingIconHomeAsTheHomeIconLeaves() {
        float home = 12f, leftPeek = -18f, rightPeek = 382f, size = 36f;
        // At rest: home, and the two neighbours half past their edges.
        assertEquals(12f, StatusBarLensPolicy.iconX(0f, home, leftPeek, rightPeek, size), 0.001f);
        assertEquals(-18f, StatusBarLensPolicy.iconX(-1f, home, leftPeek, rightPeek, size), 0.001f);
        assertEquals(382f, StatusBarLensPolicy.iconX(1f, home, leftPeek, rightPeek, size), 0.001f);
        // Dragging left by half a width: the right neighbour is halfway home, the home icon
        // halfway to the left edge, and the left neighbour half a size past its edge and fading.
        assertEquals(197f, StatusBarLensPolicy.iconX(0.5f, home, leftPeek, rightPeek, size), 0.001f);
        assertEquals(-3f, StatusBarLensPolicy.iconX(-0.5f, home, leftPeek, rightPeek, size), 0.001f);
        assertEquals(-45f, StatusBarLensPolicy.iconX(-1.5f, home, leftPeek, rightPeek, size), 0.001f);
    }
}
