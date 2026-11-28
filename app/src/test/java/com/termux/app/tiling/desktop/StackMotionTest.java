package com.termux.app.tiling.desktop;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class StackMotionTest {

    @Test public void normalAnimationsSlide() {
        assertEquals(StackMotion.Kind.SLIDE, StackMotion.kindFor(false));
        assertEquals(StackMotion.MOVE_MS, StackMotion.durationMs(StackMotion.Kind.SLIDE));
    }

    @Test public void removeAnimationsFadeQuickly() {
        assertEquals(StackMotion.Kind.FADE, StackMotion.kindFor(true));
        assertEquals(StackMotion.FADE_MS, StackMotion.durationMs(StackMotion.Kind.FADE));
        assertTrue(StackMotion.FADE_MS > 0 && StackMotion.FADE_MS < StackMotion.MOVE_MS);
    }

    @Test public void easingStaysInsideTheUnitSquare() {
        assertEquals(4, StackMotion.EASE.length);
        for (float p : StackMotion.EASE) assertTrue(p >= 0f && p <= 1f);
    }

    @Test public void aJumpAcrossSeveralWorkspacesFades() {
        assertEquals(StackMotion.Kind.FADE, StackMotion.kindForMove(StackMotion.Kind.SLIDE, 1f, 4));
        assertEquals(StackMotion.Kind.FADE, StackMotion.kindForMove(StackMotion.Kind.SLIDE, 0f, 2));
        assertEquals(StackMotion.Kind.FADE, StackMotion.kindForMove(StackMotion.Kind.SLIDE, 3f, 1));
        // From mid-slide, more than a level away.
        assertEquals(StackMotion.Kind.FADE, StackMotion.kindForMove(StackMotion.Kind.SLIDE, 1.5f, 3));
    }

    @Test public void aMoveToANeighbourSlides() {
        assertEquals(StackMotion.Kind.SLIDE, StackMotion.kindForMove(StackMotion.Kind.SLIDE, 1f, 2));
        assertEquals(StackMotion.Kind.SLIDE, StackMotion.kindForMove(StackMotion.Kind.SLIDE, 1f, 0));
        assertEquals(StackMotion.Kind.SLIDE, StackMotion.kindForMove(StackMotion.Kind.SLIDE, 1.6f, 1));
        assertEquals(StackMotion.Kind.SLIDE, StackMotion.kindForMove(StackMotion.Kind.SLIDE, 2f, 2));
    }

    @Test public void removeAnimationsFadeEveryMove() {
        assertEquals(StackMotion.Kind.FADE, StackMotion.kindForMove(StackMotion.Kind.FADE, 1f, 2));
    }
}
