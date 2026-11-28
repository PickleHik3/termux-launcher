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
}
