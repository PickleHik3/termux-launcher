package com.termux.app.chrome.wallpaper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.termux.app.chrome.wallpaper.WallpaperDirector.Frame;
import com.termux.app.chrome.wallpaper.WallpaperDirector.Thermal;

import org.junit.Test;

/** The lock-screen live wallpaper's look and pacing: calm energy, about 20 % dim, 30 or 15 fps, the unlock settle. */
public class LockWallpaperDirectorTest {

    private static final long MS = 1_000_000L;
    private static final int[] PALETTE = {0xFF000001, 0xFF000002, 0xFF000003, 0xFF000004};

    @Test public void lockLookIsCalmerThanTheLauncherAndDimmedAboutAFifth() {
        assertTrue(LockWallpaperDirector.LOCK_ENERGY > 0f && LockWallpaperDirector.LOCK_ENERGY < 1f);
        assertEquals(0.2f, LockWallpaperDirector.LOCK_DIM, 1e-6f);
    }

    @Test public void fpsFollowsTheLaunchersThermalAndBatteryRules() {
        assertEquals(30, LockWallpaperDirector.fps(Thermal.NONE, false, false, false, false));
        assertEquals(15, LockWallpaperDirector.fps(Thermal.LIGHT, false, false, false, false));
        assertEquals(15, LockWallpaperDirector.fps(Thermal.NONE, true, false, false, false));
        assertEquals(0, LockWallpaperDirector.fps(Thermal.MODERATE_OR_WORSE, false, false, false, false));
        assertEquals(0, LockWallpaperDirector.fps(Thermal.NONE, false, true, false, false));
        assertEquals(0, LockWallpaperDirector.fps(Thermal.NONE, false, false, true, false));
        assertEquals(0, LockWallpaperDirector.fps(Thermal.NONE, false, false, false, true));
        assertEquals(WallpaperDirector.MAX_FPS, LockWallpaperDirector.fps(Thermal.NONE, false, false, false, false));
        assertEquals(WallpaperDirector.PRESSURE_FPS, LockWallpaperDirector.fps(Thermal.LIGHT, true, false, false, false));
    }

    @Test public void lookEasesUpOnWakeAndDownOnUnlock() {
        float[] start = LockWallpaperDirector.look(false, 0);
        assertEquals(0f, start[0], 1e-6f);
        assertEquals(LockWallpaperDirector.LOCK_DIM, start[1], 1e-6f);
        float[] woke = LockWallpaperDirector.look(false, LockWallpaperDirector.WAKE_EASE_MS);
        assertEquals(LockWallpaperDirector.LOCK_ENERGY, woke[0], 1e-6f);

        float[] settleStart = LockWallpaperDirector.look(true, 0);
        assertEquals(LockWallpaperDirector.LOCK_ENERGY, settleStart[0], 1e-6f);
        assertEquals(LockWallpaperDirector.LOCK_DIM, settleStart[1], 1e-6f);
        float[] settled = LockWallpaperDirector.look(true, LockWallpaperDirector.UNLOCK_SETTLE_MS);
        assertEquals(0f, settled[0], 1e-6f);
        assertEquals(0f, settled[1], 1e-6f);
    }

    @Test public void hiddenDrawsNothingMoving() {
        LockWallpaperDirector d = new LockWallpaperDirector(60f, PALETTE);
        Frame f = d.frame(10 * MS);
        assertEquals(0, f.fps);
        assertEquals(0f, f.energy, 1e-6f);
    }

    @Test public void visibleReachesTheCalmLookAndTimeRuns() {
        LockWallpaperDirector d = new LockWallpaperDirector(60f, PALETTE);
        d.setFps(30);
        d.becameVisible(0);
        Frame first = d.frame(0);
        assertEquals(30, first.fps);
        long t = 0;
        Frame f = first;
        while (t <= LockWallpaperDirector.WAKE_EASE_MS * MS + 100 * MS) {
            t += 33 * MS;
            f = d.frame(t);
        }
        assertEquals(LockWallpaperDirector.LOCK_ENERGY, f.energy, 1e-6f);
        assertEquals(LockWallpaperDirector.LOCK_DIM, f.dim, 1e-6f);
        assertTrue(f.timeSeconds > first.timeSeconds);
        assertTrue(f.phaseSeconds > 0f);
        for (WallpaperDirector.Moment m : f.moments) assertEquals(WallpaperDirector.KIND_NONE, m.kind);
    }

    @Test public void unlockSettlesToTheRestPoseThenStops() {
        LockWallpaperDirector d = new LockWallpaperDirector(60f, PALETTE);
        d.setFps(30);
        d.becameVisible(0);
        long t = 0;
        while (t < 1000 * MS) {
            t += 33 * MS;
            d.frame(t);
        }
        d.unlockStarted(t);
        assertTrue(d.settling());
        Frame mid = d.frame(t + LockWallpaperDirector.UNLOCK_SETTLE_MS * MS / 2);
        assertTrue(mid.energy > 0f && mid.energy < LockWallpaperDirector.LOCK_ENERGY);
        Frame end = d.frame(t + LockWallpaperDirector.UNLOCK_SETTLE_MS * MS + MS);
        assertEquals(0, end.fps);
        assertEquals(0f, end.energy, 1e-6f);
        assertEquals(0f, end.dim, 1e-6f);
        assertEquals("the rest pose has phase 0, as the home still", 0f, end.phaseSeconds, 1e-6f);
    }

    @Test public void unlockWhilePausedIsTheRestPoseAtOnce() {
        LockWallpaperDirector d = new LockWallpaperDirector(60f, PALETTE);
        d.setFps(0);
        d.becameVisible(0);
        d.unlockStarted(5 * MS);
        Frame f = d.frame(6 * MS);
        assertEquals(0, f.fps);
        assertEquals(0f, f.energy, 1e-6f);
        assertEquals(0f, f.dim, 1e-6f);
    }

    @Test public void unlockWhileHiddenIsIgnoredAndWakeStartsOver() {
        LockWallpaperDirector d = new LockWallpaperDirector(60f, PALETTE);
        d.setFps(30);
        d.unlockStarted(0);
        assertFalse(d.settling());
        d.becameVisible(10 * MS);
        d.unlockStarted(20 * MS);
        assertTrue(d.settling());
        d.becameHidden();
        d.becameVisible(1000 * MS);
        assertFalse(d.settling());
        assertEquals(30, d.frame(1000 * MS).fps);
    }
}
