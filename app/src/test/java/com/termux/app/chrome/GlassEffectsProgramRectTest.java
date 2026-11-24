package com.termux.app.chrome;

import static org.junit.Assert.assertArrayEquals;

import org.junit.Test;

/**
 * The dock program's rect: the slab's cap with no Peek card, the card's rect while one stands. No
 * crossing blends it (#305: Home's card travels with Home, the Terminal's on a host of its own).
 */
public class GlassEffectsProgramRectTest {

    private static final float[] CAP = {0f, 10f, 1080f, 900f, 40f};
    private static final float[] PEEK = {32f, 95f, 1048f, 1100f, 60f};

    private static float[] rect(float[] peek) {
        float[] out = new float[RoundCut.SIZE];
        GlassEffects.programRect(CAP, peek, out);
        return out;
    }

    @Test public void noCardIsTheCap() {
        assertArrayEquals(CAP, rect(null), 0f);
    }

    @Test public void aCardIsTheCard() {
        assertArrayEquals(PEEK, rect(PEEK), 0f);
    }
}
