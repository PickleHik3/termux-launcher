package com.termux.app.chrome.wallpaper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_APP;

import org.junit.Test;

import java.io.File;
import java.util.List;

public class LiveRestorePlanTest {

    private static final String SAME = TERMUX_APP.VALUE_WALLPAPER_LOCK_SAME_AS_HOME;
    private static final String PHOTO = TERMUX_APP.VALUE_WALLPAPER_LOCK_PHOTO;

    private static LiveRestorePlan.Memory mem(boolean home, boolean appOnly, String lock, boolean lockFile) {
        return new LiveRestorePlan.Memory(home, appOnly, lock, lockFile, 7, 8);
    }

    // --- remember ---

    @Test
    public void remember_nothingWhenThePhoneIsNotTouched() {
        assertEquals(LiveRestorePlan.Remember.NOTHING, LiveRestorePlan.remember(false, null, 7, 8));
        assertEquals(LiveRestorePlan.Remember.NOTHING, LiveRestorePlan.remember(false, mem(true, false, SAME, false), 7, 8));
    }

    @Test
    public void remember_firstStillTakesAMemory() {
        assertEquals(LiveRestorePlan.Remember.TAKE, LiveRestorePlan.remember(true, null, 7, 8));
    }

    @Test
    public void remember_aSceneChangeWhileOurStillIsOnThePhoneKeepsTheMemory() {
        // Scene to scene: Home and Lock still show the previous still, so the photo behind it is the one to keep.
        assertEquals(LiveRestorePlan.Remember.KEEP, LiveRestorePlan.remember(true, mem(true, false, SAME, false), 7, 8));
    }

    @Test
    public void remember_aWallpaperSetSinceMakesTheMemoryStale() {
        assertEquals(LiveRestorePlan.Remember.TAKE, LiveRestorePlan.remember(true, mem(true, false, SAME, false), 9, 8));
        assertEquals(LiveRestorePlan.Remember.TAKE, LiveRestorePlan.remember(true, mem(true, false, SAME, false), 7, 9));
    }

    @Test
    public void remember_unknownIdsAreNeverTrusted() {
        LiveRestorePlan.Memory unknown = new LiveRestorePlan.Memory(true, false, SAME, false, 0, 0);
        assertEquals(LiveRestorePlan.Remember.TAKE, LiveRestorePlan.remember(true, unknown, 0, 0));
        LiveRestorePlan.Memory bad = new LiveRestorePlan.Memory(true, false, SAME, false, -1, 8);
        assertEquals(LiveRestorePlan.Remember.TAKE, LiveRestorePlan.remember(true, bad, -1, 8));
    }

    @Test
    public void photoChosen_dropsAMemoryTheNewWallpaperMadeStale() {
        LiveRestorePlan.Memory m = mem(true, false, SAME, false);
        assertTrue(LiveRestorePlan.dropOnPhotoChosen(m, 9, 8));
        assertTrue(LiveRestorePlan.dropOnPhotoChosen(m, 7, 9));
        assertFalse("the phone still shows the still (a launcher-only photo)", LiveRestorePlan.dropOnPhotoChosen(m, 7, 8));
        assertFalse("no memory", LiveRestorePlan.dropOnPhotoChosen(null, 9, 8));
    }

    // --- restore ---

    @Test
    public void restore_onlyWhileOurStillIsOnThePhoneAndThereIsSomethingToPutBack() {
        LiveRestorePlan.Memory m = mem(true, false, SAME, false);
        assertTrue(LiveRestorePlan.shouldRestore(m, 7, 8));
        assertFalse("another wallpaper was set since", LiveRestorePlan.shouldRestore(m, 9, 8));
        assertFalse("no memory", LiveRestorePlan.shouldRestore(null, 7, 8));
        assertFalse("nothing known", LiveRestorePlan.shouldRestore(mem(false, false, PHOTO, false), 7, 8));
    }

    @Test
    public void steps_homePhotoWithLockSameAsHome_isOneSetOnBoth() {
        List<LiveRestorePlan.Step> steps = LiveRestorePlan.steps(mem(true, false, SAME, false));
        assertEquals(1, steps.size());
        assertEquals(LiveRestorePlan.Step.Kind.HOME_PHONE, steps.get(0).kind);
        assertEquals(LiveRestorePlan.FLAG_HOME | LiveRestorePlan.FLAG_LOCK, steps.get(0).flags);
        assertEquals(SAME, steps.get(0).recordLock);
    }

    @Test
    public void steps_homePhotoAndOwnLockPhoto_isHomeThenLock() {
        List<LiveRestorePlan.Step> steps = LiveRestorePlan.steps(mem(true, false, PHOTO, true));
        assertEquals(2, steps.size());
        assertEquals(LiveRestorePlan.Step.Kind.HOME_PHONE, steps.get(0).kind);
        assertEquals(LiveRestorePlan.FLAG_HOME, steps.get(0).flags);
        assertNull(steps.get(0).recordLock);
        assertEquals(LiveRestorePlan.Step.Kind.LOCK_PHONE, steps.get(1).kind);
        assertEquals(LiveRestorePlan.FLAG_LOCK, steps.get(1).flags);
        assertEquals(PHOTO, steps.get(1).recordLock);
    }

    @Test
    public void steps_unknownLockPictureLeavesTheLockAlone() {
        List<LiveRestorePlan.Step> steps = LiveRestorePlan.steps(mem(true, false, PHOTO, false));
        assertEquals(1, steps.size());
        assertEquals(LiveRestorePlan.FLAG_HOME, steps.get(0).flags);
    }

    @Test
    public void steps_launcherOwnHomePictureGoesBackToTheLauncherAlone_andLockToTheLockScreen() {
        List<LiveRestorePlan.Step> steps = LiveRestorePlan.steps(mem(true, true, SAME, true));
        assertEquals(2, steps.size());
        assertEquals(LiveRestorePlan.Step.Kind.HOME_APP_ONLY, steps.get(0).kind);
        assertEquals(LiveRestorePlan.Step.Kind.LOCK_PHONE, steps.get(1).kind);
        assertEquals(SAME, steps.get(1).recordLock);
    }

    @Test
    public void steps_onlyALockPictureKnown_isOnlyALockSet() {
        List<LiveRestorePlan.Step> steps = LiveRestorePlan.steps(mem(false, false, PHOTO, true));
        assertEquals(1, steps.size());
        assertEquals(LiveRestorePlan.Step.Kind.LOCK_PHONE, steps.get(0).kind);
    }

    @Test
    public void steps_nothingKnown_isEmpty() {
        assertTrue(LiveRestorePlan.steps(mem(false, false, SAME, false)).isEmpty());
        assertFalse(mem(false, false, null, false).hasAnything());
    }

    // --- what is remembered ---

    @Test
    public void memoryOf_homeWithLockSameAsHome() {
        WallpaperSlots.State s = new WallpaperSlots.State(
            WallpaperSlots.Choice.photo(new File("exact.png")), WallpaperSlots.Choice.sameAsHome());
        LiveRestorePlan.Memory m = LiveRestorePlan.memoryOf(s, true, false);
        assertTrue(m.hasHome);
        assertFalse(m.homeAppOnly);
        assertEquals(SAME, m.lockRecord);
        assertFalse(m.hasLockFile);
    }

    @Test
    public void memoryOf_ownLockPhoto() {
        WallpaperSlots.State s = new WallpaperSlots.State(
            WallpaperSlots.Choice.photo(new File("exact.png")), WallpaperSlots.Choice.photo(new File("lock.png")));
        LiveRestorePlan.Memory m = LiveRestorePlan.memoryOf(s, true, true);
        assertEquals(PHOTO, m.lockRecord);
        assertTrue(m.hasLockFile);
    }

    @Test
    public void memoryOf_lockSameAsHomeNeverKeepsALockFile() {
        WallpaperSlots.State s = new WallpaperSlots.State(
            WallpaperSlots.Choice.photo(new File("exact.png")), WallpaperSlots.Choice.sameAsHome());
        assertFalse(LiveRestorePlan.memoryOf(s, true, true).hasLockFile);
    }

    @Test
    public void memoryOf_appOnlyHomeWithThePhonesOwnLock() {
        WallpaperSlots.State s = new WallpaperSlots.State(
            WallpaperSlots.Choice.photo(new File("exact.png")), WallpaperSlots.Choice.photo(new File("lock-system.png")),
            false, true, true);
        LiveRestorePlan.Memory m = LiveRestorePlan.memoryOf(s, true, true);
        assertTrue(m.homeAppOnly);
        assertEquals(SAME, m.lockRecord);
        assertTrue(m.hasLockFile);
    }

    @Test
    public void memoryOf_unknownHomePictureIsNotRemembered() {
        WallpaperSlots.State s = new WallpaperSlots.State(
            WallpaperSlots.Choice.photo(), WallpaperSlots.Choice.sameAsHome());
        assertFalse(LiveRestorePlan.memoryOf(s, false, false).hasAnything());
    }

    // --- the marker file ---

    @Test
    public void memory_survivesAWriteAndARead() {
        LiveRestorePlan.Memory m = new LiveRestorePlan.Memory(true, true, PHOTO, true, 41, 42);
        LiveRestorePlan.Memory back = LiveRestorePlan.Memory.parse(m.serialize());
        assertNotNull(back);
        assertTrue(back.hasHome);
        assertTrue(back.homeAppOnly);
        assertEquals(PHOTO, back.lockRecord);
        assertTrue(back.hasLockFile);
        assertEquals(41, back.stillHomeId);
        assertEquals(42, back.stillLockId);
    }

    @Test
    public void memory_withStillIdsKeepsTheRest() {
        LiveRestorePlan.Memory m = new LiveRestorePlan.Memory(true, false, SAME, false, 0, 0).withStillIds(5, 6);
        assertEquals(5, m.stillHomeId);
        assertEquals(6, m.stillLockId);
        assertEquals(SAME, m.lockRecord);
    }

    @Test
    public void memory_junkIsNotAMemory() {
        assertNull(LiveRestorePlan.Memory.parse(null));
        assertNull(LiveRestorePlan.Memory.parse(""));
        assertNull(LiveRestorePlan.Memory.parse("nonsense"));
        LiveRestorePlan.Memory odd = LiveRestorePlan.Memory.parse("home=true\nlock=living:abc\nstillHomeId=x\n");
        assertNotNull(odd);
        assertNull("an unknown lock value is dropped", odd.lockRecord);
        assertEquals(0, odd.stillHomeId);
    }

    // --- the retired live wallpaper migration ---

    @Test
    public void retiredMigration_neverReadsOrClearsLiveState() {
        // The values Live stores in the lock choice are the plain ones, never the retired prefixes.
        assertFalse(WallpaperSlots.isRetiredAnimatedLock(SAME));
        assertFalse(WallpaperSlots.isRetiredAnimatedLock(PHOTO));
        // Its folders are not the retired living-still folder.
        assertNotEquals("living", LiveRestorePlan.DIR);
        assertNotEquals("living", LiveRestorePlan.STILL_DIR);
        // With Live's lock value stored and nothing retired present, the migration plans nothing.
        WallpaperSlots.RetiredPlan plan = WallpaperSlots.planRetiredLive(null, SAME, false, false, false, true);
        assertFalse(plan.clearHome);
        assertFalse(plan.reapplyHome);
        assertEquals(WallpaperSlots.RetiredLockAction.NONE, plan.lock);
        plan = WallpaperSlots.planRetiredLive(null, PHOTO, false, true, false, true);
        assertFalse(plan.clearHome);
        assertEquals(WallpaperSlots.RetiredLockAction.NONE, plan.lock);
    }
}
