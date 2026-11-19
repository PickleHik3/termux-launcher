package com.termux.app.chrome;

import static org.junit.Assert.assertArrayEquals;

import org.junit.Test;

/**
 * The dock program's rect: the slab's cap with no Peek card, the card's rect at Home's rest, and
 * across a crossing the blend between them by Home's share, so the glass's rim moves with the
 * card's outline and the landing finds it where the rest puts it.
 */
public class GlassEffectsProgramRectTest {

    private static final float[] CAP = {0f, 10f, 1080f, 900f, 40f};
    private static final float[] PEEK = {32f, 95f, 1048f, 1100f, 60f};

    private static float[] rect(float[] peek, float share) {
        float[] out = new float[RoundCut.SIZE];
        GlassEffects.programRect(CAP, peek, share, out);
        return out;
    }

    @Test public void noCardIsTheCap() {
        assertArrayEquals(CAP, rect(null, 1f), 0f);
        assertArrayEquals(CAP, rect(null, 0.4f), 0f);
    }

    @Test public void homesRestIsTheCard() {
        assertArrayEquals(PEEK, rect(PEEK, 1f), 0f);
    }

    @Test public void theWorkspacesRestIsTheCap() {
        assertArrayEquals(CAP, rect(PEEK, 0f), 0f);
    }

    @Test public void midCrossingBlends() {
        assertArrayEquals(new float[] {16f, 52.5f, 1064f, 1000f, 50f}, rect(PEEK, 0.5f), 1e-4f);
    }
}
