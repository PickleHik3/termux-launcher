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

    private static final long GIB = 1024L * 1024L * 1024L;

    @Test
    public void underTerlLiveBlurIsOnByDefaultAboveSixGib() {
        // Pong reports 11.5 GiB; an 8 GB phone about 7.3; a 6 GB phone about 5.5.
        assertTrue(LiveBlurPolicy.switchOn(null, true, 11L * GIB + GIB / 2, false));
        assertTrue(LiveBlurPolicy.switchOn(null, true, 7L * GIB, false));
        assertFalse(LiveBlurPolicy.switchOn(null, true, 6L * GIB, false));
        assertFalse(LiveBlurPolicy.switchOn(null, true, 5L * GIB, false));
        assertFalse("a low-RAM device keeps it off", LiveBlurPolicy.switchOn(null, true, 12L * GIB, true));
    }

    @Test
    public void theUsersOwnChoiceWins() {
        assertFalse(LiveBlurPolicy.switchOn(false, true, 12L * GIB, false));
        assertTrue(LiveBlurPolicy.switchOn(true, true, 3L * GIB, false));
        assertTrue(LiveBlurPolicy.switchOn(true, false, 3L * GIB, false));
    }

    @Test
    public void v1KeepsItOffByDefault() {
        assertFalse(LiveBlurPolicy.switchOn(null, false, 12L * GIB, false));
    }
}
