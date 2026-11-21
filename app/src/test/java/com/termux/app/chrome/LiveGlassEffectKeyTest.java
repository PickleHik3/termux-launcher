package com.termux.app.chrome;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** The live slab's effect is made again only when one of its uniforms moved; at rest it stands. */
public class LiveGlassEffectKeyTest {

    @Test
    public void theSameUniformsKeepTheEffect() {
        float[] key = LiveGlassEffect.newKey();
        LiveGlassEffect.writeKey(key, 400, 300, 24f, 0f, 0f, 0f, 0f);
        assertTrue(LiveGlassEffect.sameKey(key, 400, 300, 24f, 0f, 0f, 0f, 0f));
    }

    @Test
    public void anyMovedUniformMakesANewOne() {
        float[] key = LiveGlassEffect.newKey();
        LiveGlassEffect.writeKey(key, 400, 300, 24f, 1f, 2f, 0.1f, 0.2f);
        assertFalse(LiveGlassEffect.sameKey(key, 401, 300, 24f, 1f, 2f, 0.1f, 0.2f));
        assertFalse(LiveGlassEffect.sameKey(key, 400, 301, 24f, 1f, 2f, 0.1f, 0.2f));
        assertFalse(LiveGlassEffect.sameKey(key, 400, 300, 20f, 1f, 2f, 0.1f, 0.2f));
        assertFalse(LiveGlassEffect.sameKey(key, 400, 300, 24f, 0f, 2f, 0.1f, 0.2f));
        assertFalse(LiveGlassEffect.sameKey(key, 400, 300, 24f, 1f, 0f, 0.1f, 0.2f));
        assertFalse(LiveGlassEffect.sameKey(key, 400, 300, 24f, 1f, 2f, 0f, 0.2f));
        assertFalse(LiveGlassEffect.sameKey(key, 400, 300, 24f, 1f, 2f, 0.1f, 0f));
    }

    @Test
    public void aFreshKeyMatchesNothingDrawn() {
        // A new key reads 0 x 0, a size no laid-out slab draws at.
        assertFalse(LiveGlassEffect.sameKey(LiveGlassEffect.newKey(), 1, 1, 0f, 0f, 0f, 0f, 0f));
    }
}
