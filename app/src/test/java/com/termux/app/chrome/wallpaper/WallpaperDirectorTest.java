package com.termux.app.chrome.wallpaper;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.termux.app.chrome.wallpaper.WallpaperDirector.Conditions;
import com.termux.app.chrome.wallpaper.WallpaperDirector.Frame;
import com.termux.app.chrome.wallpaper.WallpaperDirector.Moment;
import com.termux.app.chrome.wallpaper.WallpaperDirector.Thermal;

import org.junit.Test;

/** The pure rules behind a generated background: when it plays, at what rate, and what it shows. */
public class WallpaperDirectorTest {

    private static final long MS = 1_000_000L;
    private static final long T0 = 5_000 * MS;
    private static final int[] PALETTE = {0xFF102030, 0xFF203040, 0xFF304050, 0xFF405060};
    private static final float EPS = 1e-4f;

    private WallpaperDirector playing() {
        WallpaperDirector d = new WallpaperDirector(60f, PALETTE);
        d.setConditions(Conditions.playing().build(), T0);
        return d;
    }

    private static Frame at(WallpaperDirector d, long ms) {
        return d.frame(T0 + ms * MS);
    }

    /** Frames every 33 ms up to and including {@code toMs}; returns the last one. */
    private static Frame run(WallpaperDirector d, long fromMs, long toMs) {
        Frame f = null;
        for (long t = fromMs; t <= toMs; t += 33) f = at(d, t);
        return f;
    }

    private static int momentsShown(Frame f) {
        int n = 0;
        for (Moment m : f.moments) if (m.kind != WallpaperDirector.KIND_NONE) n++;
        return n;
    }

    private static Moment firstOfKind(Frame f, int kind) {
        for (Moment m : f.moments) if (m.kind == kind) return m;
        return null;
    }

    @Test
    public void playsAtThirtyFramesWhenEverythingHolds() {
        WallpaperDirector d = playing();
        Frame f = at(d, 0);
        assertEquals(WallpaperDirector.MAX_FPS, f.fps);
        assertEquals(1f, f.energy, EPS);
        assertEquals(0f, f.dim, EPS);
        assertFalse(f.lockDue);
        assertNull(d.reason());
    }

    @Test
    public void eachGateConditionOffGivesNoFrameAndNoMoments() {
        for (int i = 0; i < 5; i++) {
            WallpaperDirector d = new WallpaperDirector(60f, PALETTE);
            Conditions.Builder b = Conditions.playing();
            switch (i) {
                case 0: b.fancierGlassActive = false; break;
                case 1: b.sdkSupported = false; break;
                case 2: b.animatedIdStored = false; break;
                case 3: b.managedPictureOnScreen = false; break;
                default: b.selfDrawnBackdrop = false; break;
            }
            d.setConditions(b.build(), T0);
            d.touch(1f, 2f, T0);
            Frame f = at(d, 10);
            assertEquals("gate row " + i, 0, f.fps);
            assertEquals("gate row " + i, 0, momentsShown(f));
        }
    }

    @Test
    public void everyPauseRowStopsTheClock() {
        for (int i = 0; i < 8; i++) {
            WallpaperDirector d = new WallpaperDirector(60f, PALETTE);
            Conditions.Builder b = Conditions.playing();
            switch (i) {
                case 0: b.killSwitch = true; break;
                case 1: b.rendererHealthy = false; break;
                case 2: b.visible = false; break;
                case 3: b.screenOn = false; break;
                case 4: b.lazyMode = true; break;
                case 5: b.powerSave = true; break;
                case 6: b.thermal = Thermal.MODERATE_OR_WORSE; break;
                default: b.reducedMotion = true; break;
            }
            d.setConditions(b.build(), T0);
            assertEquals("pause row " + i, 0, at(d, 0).fps);
        }
    }

    @Test
    public void lightThermalDropsToFifteen() {
        WallpaperDirector d = new WallpaperDirector(60f, PALETTE);
        Conditions.Builder b = Conditions.playing();
        b.thermal = Thermal.LIGHT;
        d.setConditions(b.build(), T0);
        assertEquals(WallpaperDirector.PRESSURE_FPS, at(d, 0).fps);
    }

    @Test
    public void lowBatteryWhileDischargingDropsToFifteen() {
        WallpaperDirector d = new WallpaperDirector(60f, PALETTE);
        Conditions.Builder b = Conditions.playing();
        b.batteryLowDischarging = true;
        d.setConditions(b.build(), T0);
        assertEquals(WallpaperDirector.PRESSURE_FPS, at(d, 0).fps);
    }

    @Test
    public void fancierGlassOffMidPlayPausesAtTheCurrentFrameAndResumesContinuous() {
        WallpaperDirector d = playing();
        Frame before = run(d, 0, 990);
        Conditions.Builder off = Conditions.playing();
        off.fancierGlassActive = false;
        d.setConditions(off.build(), T0 + 1000 * MS);
        Frame paused = at(d, 1033);
        assertEquals(0, paused.fps);
        assertEquals(before.timeSeconds, paused.timeSeconds, EPS);
        d.setConditions(Conditions.playing().build(), T0 + 5000 * MS);
        Frame resumed = run(d, 5000, 5100);
        assertTrue("time keeps counting from where it stopped", resumed.timeSeconds >= before.timeSeconds);
        assertTrue("and does not jump", resumed.timeSeconds < before.timeSeconds + 0.3f);
    }

    @Test
    public void timeAdvancesByRealDeltasOnlyWhilePlaying() {
        WallpaperDirector d = playing();
        at(d, 0);
        assertEquals(0.033f, at(d, 33).timeSeconds, EPS);
        assertEquals(0.066f, at(d, 66).timeSeconds, EPS);
        Conditions.Builder b = Conditions.playing();
        b.visible = false;
        d.setConditions(b.build(), T0 + 70 * MS);
        assertEquals(0.066f, at(d, 2000).timeSeconds, EPS);
        assertEquals(0.066f, at(d, 4000).timeSeconds, EPS);
    }

    @Test
    public void aSingleHugeDeltaIsClampedToOneHundredMilliseconds() {
        WallpaperDirector d = playing();
        at(d, 0);
        assertEquals(0.1f, at(d, 60_000).timeSeconds, EPS);
    }

    @Test
    public void timeWrapsAtOneDay() {
        WallpaperDirector d = playing();
        Frame f = null;
        for (long i = 0; i <= 864_005; i++) f = d.frame(T0 + i * 100 * MS);
        assertTrue(f.timeSeconds >= 0f);
        assertTrue("wrapped: " + f.timeSeconds, f.timeSeconds < 2f);
    }

    @Test
    public void phaseFollowsTimeAtFullEnergy() {
        WallpaperDirector d = playing();
        at(d, 0);
        Frame f = at(d, 33);
        assertEquals(f.timeSeconds, f.phaseSeconds, EPS);
        f = at(d, 66);
        assertEquals(f.timeSeconds, f.phaseSeconds, EPS);
    }

    @Test
    public void phaseSlowsThroughALockAndIsZeroOnceItSettles() {
        WallpaperDirector d = playing();
        run(d, 0, 1000);
        Frame before = at(d, 1000);
        d.lockRequested(T0 + 1000 * MS);
        Frame mid = run(d, 1033, 1165);
        float gained = mid.phaseSeconds - before.phaseSeconds;
        float elapsed = mid.timeSeconds - before.timeSeconds;
        assertTrue("still moving: " + gained, gained > 0f);
        assertTrue("but slower than time: " + gained + " vs " + elapsed, gained < elapsed - 0.01f);
        Frame end = at(d, 1000 + 350);
        assertTrue(end.lockDue);
        assertEquals(0f, end.phaseSeconds, EPS);
        assertEquals("energy 0 holds it there", 0f, at(d, 1000 + 600).phaseSeconds, EPS);
    }

    @Test
    public void phaseResumesFromRestOnUnlockWithoutAJump() {
        WallpaperDirector d = playing();
        run(d, 0, 1000);
        d.lockRequested(T0 + 1000 * MS);
        run(d, 1000, 1400);
        d.unlock(T0 + 2000 * MS);
        float prev = at(d, 2000).phaseSeconds;
        for (long t = 2033; t <= 3000; t += 33) {
            float p = at(d, t).phaseSeconds;
            assertTrue("never rewinds", p >= prev);
            assertTrue("never more than a frame's worth", p - prev <= 0.034f);
            prev = p;
        }
        assertTrue("moving again", prev > 0f);
    }

    @Test
    public void lockEasesEnergyAndDimThenFiresOnce() {
        WallpaperDirector d = playing();
        run(d, 0, 100);
        long delay = d.lockRequested(T0 + 100 * MS);
        assertEquals(WallpaperDirector.LOCK_SETTLE_MS, delay);
        Frame mid = at(d, 100 + 175);
        assertTrue("energy falls", mid.energy < 1f && mid.energy > 0f);
        assertTrue("dim rises", mid.dim > 0f && mid.dim < WallpaperDirector.LOCK_DIM);
        assertFalse(mid.lockDue);
        Frame end = at(d, 100 + 350);
        assertTrue(end.lockDue);
        assertEquals(0f, end.energy, EPS);
        assertEquals(WallpaperDirector.LOCK_DIM, end.dim, EPS);
        assertFalse("due exactly once", at(d, 100 + 383).lockDue);
        assertFalse(at(d, 100 + 500).lockDue);
    }

    @Test
    public void lockDelayIsZeroWhilePausedAndNeverDue() {
        WallpaperDirector d = new WallpaperDirector(60f, PALETTE);
        Conditions.Builder b = Conditions.playing();
        b.visible = false;
        d.setConditions(b.build(), T0);
        assertEquals(0L, d.lockRequested(T0));
        for (long t = 0; t < 1000; t += 33) assertFalse(at(d, t).lockDue);
    }

    @Test
    public void lockDelayIsZeroUnderReducedMotion() {
        WallpaperDirector d = new WallpaperDirector(60f, PALETTE);
        Conditions.Builder b = Conditions.playing();
        b.reducedMotion = true;
        d.setConditions(b.build(), T0);
        assertEquals(0L, d.lockRequested(T0));
        assertFalse(at(d, 500).lockDue);
    }

    @Test
    public void lockNowDuringTheSettleMakesItDueOnTheNextFrame() {
        WallpaperDirector d = playing();
        at(d, 0);
        d.lockRequested(T0 + 10 * MS);
        assertFalse(at(d, 43).lockDue);
        d.lockNow(T0 + 50 * MS);
        assertTrue(at(d, 76).lockDue);
        assertFalse(at(d, 109).lockDue);
    }

    @Test
    public void unlockEasesEnergyUpFromTheRestPose() {
        WallpaperDirector d = playing();
        run(d, 0, 100);
        d.lockRequested(T0 + 100 * MS);
        run(d, 133, 600);
        d.unlock(T0 + 1000 * MS);
        Frame start = at(d, 1000);
        assertEquals(0f, start.energy, 0.02f);
        Frame mid = at(d, 1450);
        assertTrue(mid.energy > 0.2f && mid.energy < 0.8f);
        Frame end = at(d, 1000 + WallpaperDirector.UNLOCK_MS + 33);
        assertEquals(1f, end.energy, EPS);
        assertEquals(0f, end.dim, EPS);
    }

    @Test
    public void unlockWhileNotVisibleWaitsForTheFirstVisibleFrame() {
        WallpaperDirector d = new WallpaperDirector(60f, PALETTE);
        Conditions.Builder hidden = Conditions.playing();
        hidden.visible = false;
        d.setConditions(hidden.build(), T0);
        d.unlock(T0 + 100 * MS);
        assertEquals(0, at(d, 200).fps);
        d.setConditions(Conditions.playing().build(), T0 + 500 * MS);
        Frame first = at(d, 500);
        assertEquals(WallpaperDirector.MAX_FPS, first.fps);
        assertEquals("starts from the rest pose, not already finished", 0f, first.energy, 0.02f);
        assertEquals(1f, at(d, 500 + WallpaperDirector.UNLOCK_MS + 33).energy, EPS);
    }

    @Test
    public void unlockWhileScreenOffWaitsToo() {
        WallpaperDirector d = new WallpaperDirector(60f, PALETTE);
        Conditions.Builder off = Conditions.playing();
        off.screenOn = false;
        d.setConditions(off.build(), T0);
        d.unlock(T0 + 10 * MS);
        d.setConditions(Conditions.playing().build(), T0 + 500 * MS);
        assertEquals(0f, at(d, 500).energy, 0.02f);
    }

    @Test
    public void aQueuedUnlockExpiresAfterOneSecond() {
        WallpaperDirector d = new WallpaperDirector(60f, PALETTE);
        Conditions.Builder hidden = Conditions.playing();
        hidden.visible = false;
        d.setConditions(hidden.build(), T0);
        d.unlock(T0 + 100 * MS);
        d.setConditions(Conditions.playing().build(), T0 + 2000 * MS);
        Frame first = at(d, 2000);
        assertEquals(WallpaperDirector.MAX_FPS, first.fps);
        assertEquals("no bloom from a stale unlock", 1f, first.energy, EPS);
        assertEquals(0f, first.dim, EPS);
    }

    @Test
    public void anUnlockDroppedWhilePausedLeavesLocked() {
        WallpaperDirector d = playing();
        run(d, 0, 100);
        d.lockRequested(T0 + 100 * MS);
        run(d, 133, 600);
        Conditions.Builder lazy = Conditions.playing();
        lazy.lazyMode = true;
        d.setConditions(lazy.build(), T0 + 700 * MS);
        d.unlock(T0 + 800 * MS);
        d.setConditions(Conditions.playing().build(), T0 + 900 * MS);
        Frame f = at(d, 900);
        assertEquals(1f, f.energy, EPS);
        assertEquals(0f, f.dim, EPS);
    }

    @Test
    public void anExpiredQueuedUnlockLeavesLocked() {
        WallpaperDirector d = playing();
        run(d, 0, 100);
        d.lockRequested(T0 + 100 * MS);
        run(d, 133, 600);
        Conditions.Builder hidden = Conditions.playing();
        hidden.visible = false;
        d.setConditions(hidden.build(), T0 + 700 * MS);
        d.unlock(T0 + 800 * MS);
        d.setConditions(Conditions.playing().build(), T0 + 4000 * MS);
        Frame f = at(d, 4000);
        assertEquals(1f, f.energy, EPS);
        assertEquals(0f, f.dim, EPS);
    }

    @Test
    public void aThirdMomentReplacesTheOldest() {
        WallpaperDirector d = playing();
        at(d, 0);
        d.touch(10f, 20f, T0 + 100 * MS);
        d.bell(0f, 0f, 5f, 5f, T0 + 200 * MS);
        d.paneOpened(1f, 2f, 3f, 4f, T0 + 300 * MS);
        Frame f = at(d, 310);
        assertEquals(WallpaperDirector.MAX_MOMENTS, f.moments.length);
        assertEquals(2, momentsShown(f));
        assertNull("the touch was oldest and is gone", firstOfKind(f, WallpaperDirector.KIND_TOUCH));
        assertNotNull(firstOfKind(f, WallpaperDirector.KIND_BELL));
        assertNotNull(firstOfKind(f, WallpaperDirector.KIND_PANE_OPEN));
    }

    @Test
    public void momentProgressRunsZeroToOneThenTheSlotFrees() {
        WallpaperDirector d = playing();
        at(d, 0);
        d.touch(7f, 9f, T0 + 100 * MS);
        Moment start = firstOfKind(at(d, 100), WallpaperDirector.KIND_TOUCH);
        assertEquals(0f, start.progress, EPS);
        assertEquals(7f, start.x, EPS);
        assertEquals(9f, start.y, EPS);
        assertEquals(0f, start.w, EPS);
        Moment mid = firstOfKind(at(d, 100 + WallpaperDirector.TOUCH_MS / 2), WallpaperDirector.KIND_TOUCH);
        assertEquals(0.5f, mid.progress, 0.05f);
        Frame after = at(d, 100 + WallpaperDirector.TOUCH_MS + 50);
        assertEquals(0, momentsShown(after));
    }

    @Test
    public void momentDurationsFollowTheirKind() {
        WallpaperDirector d = playing();
        at(d, 0);
        d.paneOpened(0f, 0f, 1f, 1f, T0);
        d.paneClosed(0f, 0f, 1f, 1f, T0 + 1 * MS);
        assertEquals(2, momentsShown(at(d, 600)));
        assertEquals(0, momentsShown(at(d, WallpaperDirector.PANE_MS + 50)));
        d.pageChanged(-1, T0 + 2000 * MS);
        Moment page = firstOfKind(at(d, 2000), WallpaperDirector.KIND_PAGE_CHANGE);
        assertEquals(-1f, page.x, EPS);
        assertEquals(0, momentsShown(at(d, 2000 + WallpaperDirector.PAGE_MS + 40)));
        d.bell(1f, 2f, 3f, 4f, T0 + 4000 * MS);
        Moment bell = firstOfKind(at(d, 4000 + 1000), WallpaperDirector.KIND_BELL);
        assertEquals(1f, bell.x, EPS);
        assertEquals(2f, bell.y, EPS);
        assertEquals(2f, bell.w, EPS);
        assertEquals(2f, bell.h, EPS);
        assertEquals(0, momentsShown(at(d, 4000 + WallpaperDirector.BELL_MS + 40)));
    }

    @Test
    public void eventsWhilePausedAreDropped() {
        WallpaperDirector d = new WallpaperDirector(60f, PALETTE);
        Conditions.Builder b = Conditions.playing();
        b.lazyMode = true;
        d.setConditions(b.build(), T0);
        d.touch(1f, 1f, T0);
        d.bell(0f, 0f, 1f, 1f, T0);
        d.pageChanged(1, T0);
        d.paneOpened(0f, 0f, 1f, 1f, T0);
        d.setConditions(Conditions.playing().build(), T0 + 100 * MS);
        assertEquals(0, momentsShown(at(d, 110)));
    }

    @Test
    public void paletteIsPassedThroughAndOnlyTheHostChangesIt() {
        WallpaperDirector d = playing();
        assertArrayEquals(PALETTE, at(d, 0).palette);
        run(d, 33, 3000);
        d.lockRequested(T0 + 3000 * MS);
        d.unlock(T0 + 4000 * MS);
        assertArrayEquals(PALETTE, at(d, 4100).palette);
        int[] next = {1, 2, 3, 4};
        d.setPalette(next);
        assertArrayEquals(next, at(d, 4200).palette);
    }

    @Test
    public void reasonNamesWhyABackgroundIsNotPlaying() {
        WallpaperDirector d = new WallpaperDirector(60f, PALETTE);
        assertEquals("inactive", d.reason());
        Conditions.Builder b = Conditions.playing();
        b.sdkSupported = false;
        d.setConditions(b.build(), T0);
        assertEquals("api", d.reason());
        b = Conditions.playing();
        b.fancierGlassActive = false;
        d.setConditions(b.build(), T0);
        assertEquals("fancier_glass_off", d.reason());
        b = Conditions.playing();
        b.killSwitch = true;
        d.setConditions(b.build(), T0);
        assertEquals("killed", d.reason());
        b = Conditions.playing();
        b.rendererHealthy = false;
        d.setConditions(b.build(), T0);
        assertEquals("killed", d.reason());
        b = Conditions.playing();
        b.powerSave = true;
        d.setConditions(b.build(), T0);
        assertEquals("paused", d.reason());
        b = Conditions.playing();
        b.animatedIdStored = false;
        d.setConditions(b.build(), T0);
        assertEquals("inactive", d.reason());
        d.setConditions(Conditions.playing().build(), T0);
        assertNull(d.reason());
    }
}
