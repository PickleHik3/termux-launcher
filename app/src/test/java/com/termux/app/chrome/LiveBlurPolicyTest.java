package com.termux.app.chrome;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Every term of {@link LiveBlurPolicy#live} can put Live out on its own. */
public class LiveBlurPolicyTest {

    private static final int T = 33;

    @Test
    public void liveWhenEveryTermAllows() {
        assertTrue(LiveBlurPolicy.live(T, true, true, true, false, false, false));
    }

    @Test
    public void belowAndroid13IsNeverLive() {
        assertFalse(LiveBlurPolicy.live(32, true, true, true, false, false, false));
        assertFalse(LiveBlurPolicy.offered(32));
        assertTrue(LiveBlurPolicy.offered(33));
    }

    @Test
    public void theSwitchOffIsNotLive() {
        assertFalse(LiveBlurPolicy.live(T, false, true, true, false, false, false));
    }

    @Test
    public void glassRefractionNotInEffectIsNotLive() {
        assertFalse(LiveBlurPolicy.live(T, true, false, true, false, false, false));
    }

    @Test
    public void aWallpaperAndroidDrawsIsNotLive() {
        assertFalse(LiveBlurPolicy.live(T, true, true, false, false, false, false));
    }

    @Test
    public void batterySaverIsNotLive() {
        assertFalse(LiveBlurPolicy.live(T, true, true, true, true, false, false));
    }

    @Test
    public void lowMemoryPresetIsNotLive() {
        assertFalse(LiveBlurPolicy.live(T, true, true, true, false, true, false));
    }

    @Test
    public void reduceIdleActivityIsNotLive() {
        assertFalse(LiveBlurPolicy.live(T, true, true, true, false, false, true));
    }
}
