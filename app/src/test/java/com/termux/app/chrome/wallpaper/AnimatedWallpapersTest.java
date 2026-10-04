package com.termux.app.chrome.wallpaper;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** The registry knows living stills only; the retired pre-made ids read as unknown. */
public class AnimatedWallpapersTest {

    @Test public void livingIdsAreTheOnlyKnownOnes() {
        assertTrue(AnimatedWallpapers.isKnownId("living:0123456789abcdef"));
        assertTrue(AnimatedWallpapers.isLivingId("living:fedcba9876543210"));
        for (String retired : new String[] {"mesh", "aurora", "tide", "rain", "contour", "drift", "lava", "silk",
            "caustics", "chrome"}) {
            assertFalse(retired, AnimatedWallpapers.isKnownId(retired));
            assertFalse(retired, AnimatedWallpapers.isLivingId(retired));
            assertNull(retired, AnimatedWallpapers.byId(retired));
        }
    }

    @Test public void lookupsNeedAResolvedLivingStill() {
        assertNull(AnimatedWallpapers.byId("nope"));
        assertNull(AnimatedWallpapers.byId(null));
        assertNull("not resolved from disk yet", AnimatedWallpapers.byId("living:0123456789abcdef"));
    }
}
