package com.termux.app.launcher.drawer;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * Terminal open on pong (2026-11-20): the grid's icons faded in over the Terminal pane a few
 * frames before the card's glass picture landed under them. The content waits for the material,
 * with a backstop.
 */
public class AppDrawerContentGateTest {

    private static final float FRAME = 1f / 90f;

    @Test public void staysShutUntilTheMaterialIsUp() {
        assertEquals(0f, AppDrawerContentGate.step(0f, false, 0.05f, FRAME), 0f);
    }

    @Test public void opensOverItsFadeOnceTheMaterialIsUp() {
        float gate = AppDrawerContentGate.step(0f, true, 0.05f, FRAME);
        assertEquals(FRAME / AppDrawerContentGate.FADE_S, gate, 1e-6f);
        for (int i = 0; i < 20; i++) gate = AppDrawerContentGate.step(gate, true, 0.1f, FRAME);
        assertEquals(1f, gate, 0f);
    }

    @Test public void theBackstopOpensItWithoutTheMaterial() {
        float gate = AppDrawerContentGate.step(0f, false, AppDrawerContentGate.BACKSTOP_S, FRAME);
        assertEquals(FRAME / AppDrawerContentGate.FADE_S, gate, 1e-6f);
    }

    @Test public void anOpenGateStaysOpen() {
        assertEquals(1f, AppDrawerContentGate.step(1f, false, 0f, FRAME), 0f);
    }
}
