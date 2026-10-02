package com.termux.app.chrome.wallpaper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.termux.app.chrome.wallpaper.WallpaperDirector.Conditions;
import com.termux.app.chrome.wallpaper.WallpaperDirector.Frame;

import org.junit.Test;

/** The step-down tiers of the render self-check, and leaving the lock rest pose. */
public class WallpaperDirectorTierTest {

    private static final long MS = 1_000_000L;
    private static final long T0 = 5_000 * MS;
    private static final int[] PALETTE = {0xFF102030, 0xFF203040, 0xFF304050, 0xFF405060};
    private static final float EPS = 1e-4f;

    private static WallpaperDirector playing() {
        WallpaperDirector d = new WallpaperDirector(60f, PALETTE);
        d.setConditions(Conditions.playing().build(), T0);
        return d;
    }

    private static Frame at(WallpaperDirector d, long ms) {
        return d.frame(T0 + ms * MS);
    }

    @Test
    public void tierMapsToFpsAndResolution() {
        assertEquals(30, WallpaperDirector.tierFps(0));
        assertEquals(15, WallpaperDirector.tierFps(1));
        assertEquals(15, WallpaperDirector.tierFps(2));
        assertEquals(10, WallpaperDirector.tierFps(3));
        assertFalse(WallpaperDirector.tierLowRes(0));
        assertFalse(WallpaperDirector.tierLowRes(1));
        assertTrue(WallpaperDirector.tierLowRes(2));
        assertTrue(WallpaperDirector.tierLowRes(3));
    }

    @Test
    public void steppingDownLowersTheFrameRateUntilTheLowestTier() {
        WallpaperDirector d = playing();
        assertEquals(0, d.tier());
        assertEquals(30, at(d, 0).fps);
        assertTrue(d.stepDown());
        assertEquals(15, at(d, 33).fps);
        assertTrue(d.stepDown());
        assertEquals(15, at(d, 66).fps);
        assertTrue(d.stepDown());
        assertEquals(10, at(d, 99).fps);
        assertEquals(WallpaperDirector.MAX_TIER, d.tier());
        // The lowest tier failing is a kill: no further step.
        assertFalse(d.stepDown());
        assertEquals(WallpaperDirector.MAX_TIER, d.tier());
    }

    @Test
    public void pressureAndTierTakeTheLowerRate() {
        WallpaperDirector d = new WallpaperDirector(60f, PALETTE);
        Conditions.Builder b = Conditions.playing();
        b.thermal = WallpaperDirector.Thermal.LIGHT;
        d.setConditions(b.build(), T0);
        assertEquals(15, at(d, 0).fps);
        d.stepDown();
        d.stepDown();
        d.stepDown();
        assertEquals(10, at(d, 33).fps);
    }

    @Test
    public void resetTierGoesBackToFullRate() {
        WallpaperDirector d = playing();
        d.stepDown();
        d.stepDown();
        d.resetTier();
        assertEquals(0, d.tier());
        assertEquals(30, at(d, 0).fps);
    }

    @Test
    public void aSteppedDownDirectorStillReportsPlaying() {
        WallpaperDirector d = playing();
        d.stepDown();
        assertNull(d.reason());
    }

    @Test
    public void unlockLeavesTheLockedPoseWhenTheLauncherStartsAfterScreenOff() {
        WallpaperDirector d = playing();
        at(d, 0);
        long delay = d.lockRequested(T0);
        assertEquals(WallpaperDirector.LOCK_SETTLE_MS, delay);
        Frame locked = at(d, WallpaperDirector.LOCK_SETTLE_MS + 40);
        assertTrue(locked.lockDue);
        assertEquals(0f, locked.energy, EPS);
        assertEquals(WallpaperDirector.LOCK_DIM, locked.dim, EPS);

        // Screen off: paused, still at rest. No USER_PRESENT ever arrives.
        Conditions.Builder off = Conditions.playing();
        off.screenOn = false;
        off.visible = false;
        d.setConditions(off.build(), T0 + 1_000 * MS);
        assertEquals(0, at(d, 1_000).fps);
        assertEquals(WallpaperDirector.LOCK_DIM, at(d, 1_033).dim, EPS);

        // The activity starts with the screen on: the host gives the unlock (WallpaperSession).
        WallpaperSession session = new WallpaperSession();
        session.onScreenOff();
        Conditions.Builder on = Conditions.playing();
        d.setConditions(on.build(), T0 + 5_000 * MS);
        assertTrue(session.unlockOnStart(true));
        d.unlock(T0 + 5_000 * MS);
        Frame mid = at(d, 5_000 + WallpaperDirector.UNLOCK_MS / 2);
        assertTrue(mid.energy > 0f && mid.energy < 1f);
        Frame done = at(d, 5_000 + WallpaperDirector.UNLOCK_MS + 100);
        assertEquals(1f, done.energy, EPS);
        assertEquals(0f, done.dim, EPS);
    }
}
