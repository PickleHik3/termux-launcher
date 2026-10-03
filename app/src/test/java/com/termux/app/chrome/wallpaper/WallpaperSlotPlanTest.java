package com.termux.app.chrome.wallpaper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.termux.app.chrome.wallpaper.WallpaperSlotPlan.Inputs;
import com.termux.app.chrome.wallpaper.WallpaperSlotPlan.Kind;
import com.termux.app.chrome.wallpaper.WallpaperSlots.Choice;
import com.termux.app.chrome.wallpaper.WallpaperSlots.Slot;

import org.junit.Test;

/** What applying a slot choice or the Motion toggle does (lock-live-wallpaper.md decisions 2, 4, 5, 6). */
public class WallpaperSlotPlanTest {

    private static final int SYSTEM = WallpaperSlotPlan.FLAG_SYSTEM;
    private static final int LOCK = WallpaperSlotPlan.FLAG_LOCK;

    private static Inputs in(String homeId, Choice lock, boolean motion, boolean live) {
        return new Inputs(35, homeId, lock, motion, live);
    }

    // --- stored values ---

    @Test public void lockValuesRoundTrip() {
        assertEquals("same_as_home", WallpaperSlotPlan.lockValue(Choice.sameAsHome()));
        assertEquals("photo", WallpaperSlotPlan.lockValue(Choice.photo()));
        assertEquals("animated:aurora", WallpaperSlotPlan.lockValue(Choice.animated("aurora")));
        assertTrue(WallpaperSlotPlan.lockChoice("same_as_home").sameAsHome);
        assertTrue(WallpaperSlotPlan.lockChoice("photo").photo);
        assertEquals("tide", WallpaperSlotPlan.lockChoice("animated:tide").animatedId);
    }

    @Test public void unknownLockValuesReadAsSameAsHome() {
        assertTrue(WallpaperSlotPlan.lockChoice(null).sameAsHome);
        assertTrue(WallpaperSlotPlan.lockChoice("").sameAsHome);
        assertTrue(WallpaperSlotPlan.lockChoice("animated:nope").sameAsHome);
        assertTrue(WallpaperSlotPlan.lockChoice("animated:").sameAsHome);
        assertTrue(WallpaperSlotPlan.lockChoice("both").sameAsHome);
    }

    @Test public void homeChoiceIsAKnownIdElseAPhoto() {
        assertEquals("mesh", WallpaperSlotPlan.homeChoice("mesh").animatedId);
        assertTrue(WallpaperSlotPlan.homeChoice(null).photo);
        assertTrue(WallpaperSlotPlan.homeChoice("gone").photo);
    }

    @Test public void sameAsHomeResolvesToTheHomeBackground() {
        assertEquals("rain", WallpaperSlotPlan.resolveLockId(Choice.sameAsHome(), "rain"));
        assertNull("home photo", WallpaperSlotPlan.resolveLockId(Choice.sameAsHome(), null));
        assertNull(WallpaperSlotPlan.resolveLockId(Choice.sameAsHome(), "gone"));
        assertEquals("silk", WallpaperSlotPlan.resolveLockId(Choice.animated("silk"), "rain"));
        assertNull(WallpaperSlotPlan.resolveLockId(Choice.photo(), "rain"));
    }

    // --- Home ---

    @Test public void homeAnimatedSetsTheHomeStillOnlyWhenTheLockIsLiveAndFollows() {
        WallpaperSlotPlan p = WallpaperSlotPlan.forApply(Slot.HOME, Choice.animated("aurora"),
            in(null, Choice.sameAsHome(), true, true));
        assertEquals(Kind.SET_STILL, p.kind);
        assertEquals(SYSTEM, p.flags);
        assertEquals("aurora", p.stillId);
        assertTrue(p.recordHome);
        assertNull(p.recordLock);
    }

    @Test public void homeAnimatedAlsoUpdatesAStillSameAsHomeLock() {
        WallpaperSlotPlan off = WallpaperSlotPlan.forApply(Slot.HOME, Choice.animated("aurora"),
            in(null, Choice.sameAsHome(), false, false));
        assertEquals(SYSTEM | LOCK, off.flags);
        WallpaperSlotPlan notLive = WallpaperSlotPlan.forApply(Slot.HOME, Choice.animated("aurora"),
            in(null, Choice.sameAsHome(), true, false));
        assertEquals(SYSTEM | LOCK, notLive.flags);
        assertNull("the lock stays Same as Home", notLive.recordLock);
    }

    @Test public void homeAnimatedLeavesAnOwnLockChoiceAlone() {
        WallpaperSlotPlan p = WallpaperSlotPlan.forApply(Slot.HOME, Choice.animated("aurora"),
            in(null, Choice.animated("tide"), false, false));
        assertEquals(SYSTEM, p.flags);
    }

    @Test public void homePhotoIsRecordOnly() {
        WallpaperSlotPlan p = WallpaperSlotPlan.forApply(Slot.HOME, Choice.photo(), in("mesh", Choice.sameAsHome(), true, true));
        assertEquals(Kind.RECORD_ONLY, p.kind);
        assertNull(p.recordLock);
        assertFalse(p.recordHome);
    }

    @Test public void homeAnimatedBelow34IsUnsupported() {
        WallpaperSlotPlan p = WallpaperSlotPlan.forApply(Slot.HOME, Choice.animated("aurora"),
            new Inputs(33, null, Choice.sameAsHome(), true, false));
        assertEquals(Kind.UNSUPPORTED, p.kind);
    }

    // --- Lock ---

    @Test public void lockAnimatedWithMotionOpensThePreviewTheFirstTime() {
        WallpaperSlotPlan p = WallpaperSlotPlan.forApply(Slot.LOCK, Choice.animated("lava"),
            in("mesh", Choice.sameAsHome(), true, false));
        assertEquals(Kind.OPEN_PREVIEW, p.kind);
        assertEquals("animated:lava", p.recordLock);
    }

    @Test public void lockAnimatedWithMotionOnceLiveOnlyRecords() {
        WallpaperSlotPlan p = WallpaperSlotPlan.forApply(Slot.LOCK, Choice.animated("lava"),
            in("mesh", Choice.sameAsHome(), true, true));
        assertEquals(Kind.RECORD_ONLY, p.kind);
        assertEquals("animated:lava", p.recordLock);
    }

    @Test public void lockSameAsHomeWithMotionFollowsTheHomeBackground() {
        WallpaperSlotPlan first = WallpaperSlotPlan.forApply(Slot.LOCK, Choice.sameAsHome(),
            in("mesh", Choice.photo(), true, false));
        assertEquals(Kind.OPEN_PREVIEW, first.kind);
        assertEquals("mesh", first.stillId);
        assertEquals("same_as_home", first.recordLock);
    }

    @Test public void lockAnimatedWithMotionOffSetsTheLockStill() {
        WallpaperSlotPlan p = WallpaperSlotPlan.forApply(Slot.LOCK, Choice.animated("drift"),
            in("mesh", Choice.sameAsHome(), false, true));
        assertEquals(Kind.SET_STILL, p.kind);
        assertEquals(LOCK, p.flags);
        assertEquals("drift", p.stillId);
        assertFalse("the Home slot is untouched", p.recordHome);
        assertEquals("animated:drift", p.recordLock);
    }

    @Test public void lockSameAsHomeWithAHomePhotoCopiesThePhoto() {
        WallpaperSlotPlan p = WallpaperSlotPlan.forApply(Slot.LOCK, Choice.sameAsHome(),
            in(null, Choice.animated("tide"), true, true));
        assertEquals(Kind.COPY_HOME_PHOTO_TO_LOCK, p.kind);
        assertEquals("same_as_home", p.recordLock);
    }

    @Test public void lockPhotoIsRecordOnly() {
        WallpaperSlotPlan p = WallpaperSlotPlan.forApply(Slot.LOCK, Choice.photo(),
            in("mesh", Choice.sameAsHome(), true, true));
        assertEquals(Kind.RECORD_ONLY, p.kind);
        assertEquals("photo", p.recordLock);
    }

    @Test public void below34MotionIsIgnoredAndStillsNeed34() {
        Inputs old = new Inputs(33, "mesh", Choice.sameAsHome(), true, false);
        assertEquals(Kind.UNSUPPORTED, WallpaperSlotPlan.forApply(Slot.LOCK, Choice.animated("mesh"), old).kind);
        assertFalse(old.motionPlays());
    }

    // --- Motion ---

    @Test public void motionOnOpensThePreviewWhenNotLive() {
        WallpaperSlotPlan p = WallpaperSlotPlan.forMotion(true, in("mesh", Choice.sameAsHome(), false, false));
        assertEquals(Kind.OPEN_PREVIEW, p.kind);
        assertNull(p.recordLock);
    }

    @Test public void motionOnWhenLiveOnlyRecords() {
        assertEquals(Kind.RECORD_ONLY,
            WallpaperSlotPlan.forMotion(true, in("mesh", Choice.sameAsHome(), false, true)).kind);
    }

    @Test public void motionOffSetsTheLockStill() {
        WallpaperSlotPlan p = WallpaperSlotPlan.forMotion(false, in("mesh", Choice.sameAsHome(), true, true));
        assertEquals(Kind.SET_STILL, p.kind);
        assertEquals(LOCK, p.flags);
        assertEquals("mesh", p.stillId);
    }

    @Test public void motionWithALockPhotoChangesNothing() {
        assertEquals(Kind.RECORD_ONLY, WallpaperSlotPlan.forMotion(false, in("mesh", Choice.photo(), true, false)).kind);
        assertEquals(Kind.RECORD_ONLY, WallpaperSlotPlan.forMotion(true, in("mesh", Choice.photo(), false, false)).kind);
        assertEquals("a home photo on a still lock", Kind.RECORD_ONLY,
            WallpaperSlotPlan.forMotion(true, in(null, Choice.sameAsHome(), false, false)).kind);
    }

    // --- photos with their picture (lock-live-wallpaper.md, "Photos"), the same at 33 and 34 ---

    private static final java.io.File PICTURE = new java.io.File("/data/pending/1.png");

    private static Inputs at(int sdk, String homeId, Choice lock, boolean motion, boolean live) {
        return new Inputs(sdk, homeId, lock, motion, live);
    }

    @Test public void photoOnBothScreens() {
        for (int sdk : new int[] {33, 34}) {
            // Apply: Home first, with Lock already Same as Home: one set for both screens…
            WallpaperSlotPlan home = WallpaperSlotPlan.forApply(Slot.HOME, Choice.photo(PICTURE),
                at(sdk, "mesh", Choice.sameAsHome(), true, false));
            assertEquals(sdk + "", Kind.SET_PHOTO, home.kind);
            assertEquals(sdk + "", SYSTEM | LOCK, home.flags);
            assertEquals(PICTURE, home.photo);
            assertTrue(home.recordHome);
            assertNull(home.recordLock);
            // …then Lock follows, which has nothing left to set.
            WallpaperSlotPlan follows = WallpaperSlotPlan.forApply(Slot.LOCK, Choice.sameAsHome(),
                at(sdk, null, Choice.sameAsHome(), true, false));
            assertEquals(sdk + "", Kind.RECORD_ONLY, follows.kind);
            assertEquals("same_as_home", follows.recordLock);

            // Lock had its own choice: Home's set is Home's only, and Lock follows by copying it.
            WallpaperSlotPlan homeOnly = WallpaperSlotPlan.forApply(Slot.HOME, Choice.photo(PICTURE),
                at(sdk, "mesh", Choice.animated("rain"), false, false));
            assertEquals(SYSTEM, homeOnly.flags);
            WallpaperSlotPlan copy = WallpaperSlotPlan.forApply(Slot.LOCK, Choice.sameAsHome(),
                at(sdk, null, Choice.animated("rain"), false, false));
            assertEquals(sdk + "", Kind.COPY_HOME_PHOTO_TO_LOCK, copy.kind);
            assertEquals(LOCK, copy.flags);
            assertEquals("same_as_home", copy.recordLock);
        }
    }

    @Test public void photoOnBothScreensReplacesALiveLock() {
        // At 34 with our live wallpaper on the lock screen and Motion on, Same as Home still
        // takes the photo: the engine cannot draw one.
        WallpaperSlotPlan home = WallpaperSlotPlan.forApply(Slot.HOME, Choice.photo(PICTURE),
            at(34, "mesh", Choice.sameAsHome(), true, true));
        assertEquals(Kind.SET_PHOTO, home.kind);
        assertEquals(SYSTEM | LOCK, home.flags);
        WallpaperSlotPlan follows = WallpaperSlotPlan.forApply(Slot.LOCK, Choice.sameAsHome(),
            at(34, null, Choice.sameAsHome(), true, true));
        assertEquals("still live: copy the photo over it", Kind.COPY_HOME_PHOTO_TO_LOCK, follows.kind);
    }

    @Test public void photoOnHomeOnly() {
        for (int sdk : new int[] {33, 34}) {
            WallpaperSlotPlan p = WallpaperSlotPlan.forApply(Slot.HOME, Choice.photo(PICTURE),
                at(sdk, "mesh", Choice.animated("tide"), true, true));
            assertEquals(sdk + "", Kind.SET_PHOTO, p.kind);
            assertEquals(sdk + "", SYSTEM, p.flags);
            assertTrue(p.recordHome);
            assertNull("the Lock slot is left alone", p.recordLock);
        }
    }

    @Test public void photoOnLockOnly() {
        for (int sdk : new int[] {33, 34}) {
            WallpaperSlotPlan p = WallpaperSlotPlan.forApply(Slot.LOCK, Choice.photo(PICTURE),
                at(sdk, "mesh", Choice.sameAsHome(), true, sdk >= 34));
            assertEquals(sdk + "", Kind.SET_PHOTO, p.kind);
            assertEquals(sdk + "", LOCK, p.flags);
            assertEquals(PICTURE, p.photo);
            assertFalse("Home untouched", p.recordHome);
            assertEquals("photo", p.recordLock);
        }
    }

    @Test public void sameAsHomeOverAHomePhotoWorksBelow34() {
        WallpaperSlotPlan p = WallpaperSlotPlan.forApply(Slot.LOCK, Choice.sameAsHome(),
            at(33, null, Choice.photo(), true, false));
        assertEquals(Kind.COPY_HOME_PHOTO_TO_LOCK, p.kind);
        assertEquals("same_as_home", p.recordLock);
    }

    // --- living stills: a photo with motion, id living:<hash> (living-stills.md, Part D.3) ---

    private static final String LIVING = "living:0123456789abcdef";

    @Test public void livingIdsAreStoredAndReadBack() {
        assertEquals("animated:" + LIVING, WallpaperSlotPlan.lockValue(Choice.animated(LIVING)));
        assertEquals(LIVING, WallpaperSlotPlan.lockChoice("animated:" + LIVING).animatedId);
        assertEquals(LIVING, WallpaperSlotPlan.homeChoice(LIVING).animatedId);
        assertTrue("a malformed living id reads as a photo", WallpaperSlotPlan.homeChoice("living:zz").photo);
        assertTrue(WallpaperSlotPlan.lockChoice("animated:living:zz").sameAsHome);
        assertEquals(LIVING, WallpaperSlotPlan.resolveLockId(Choice.sameAsHome(), LIVING));
        assertEquals(LIVING, WallpaperSlotPlan.resolveLockId(Choice.animated(LIVING), null));
    }

    @Test public void homeLivingSetsTheHomeStillAndRecordsTheId() {
        WallpaperSlotPlan p = WallpaperSlotPlan.forApply(Slot.HOME, Choice.animated(LIVING),
            in(null, Choice.sameAsHome(), true, true));
        assertEquals("applied as its photo by WallpaperSlots, planned as any still", Kind.SET_STILL, p.kind);
        assertEquals(LIVING, p.stillId);
        assertEquals("our lock engine keeps the lock screen", SYSTEM, p.flags);
        assertTrue(p.recordHome);
        WallpaperSlotPlan off = WallpaperSlotPlan.forApply(Slot.HOME, Choice.animated(LIVING),
            in(null, Choice.sameAsHome(), false, true));
        assertEquals("Motion off: the lock screen gets the photo too", SYSTEM | LOCK, off.flags);
    }

    @Test public void homeLivingBelow34IsUnsupported() {
        assertEquals(Kind.UNSUPPORTED, WallpaperSlotPlan.forApply(Slot.HOME, Choice.animated(LIVING),
            new Inputs(33, null, Choice.sameAsHome(), true, false)).kind);
    }

    @Test public void lockLivingFollowsTheMotionToggle() {
        WallpaperSlotPlan first = WallpaperSlotPlan.forApply(Slot.LOCK, Choice.animated(LIVING),
            in(null, Choice.sameAsHome(), true, false));
        assertEquals(Kind.OPEN_PREVIEW, first.kind);
        assertEquals("animated:" + LIVING, first.recordLock);
        WallpaperSlotPlan live = WallpaperSlotPlan.forApply(Slot.LOCK, Choice.animated(LIVING),
            in(null, Choice.sameAsHome(), true, true));
        assertEquals(Kind.RECORD_ONLY, live.kind);
        WallpaperSlotPlan still = WallpaperSlotPlan.forApply(Slot.LOCK, Choice.animated(LIVING),
            in(null, Choice.sameAsHome(), false, true));
        assertEquals("Motion off: the plain photo", Kind.SET_STILL, still.kind);
        assertEquals(LIVING, still.stillId);
        assertEquals(LOCK, still.flags);
        assertFalse(still.recordHome);
    }

    @Test public void lockSameAsHomeFollowsAHomeLiving() {
        WallpaperSlotPlan p = WallpaperSlotPlan.forApply(Slot.LOCK, Choice.sameAsHome(),
            in(LIVING, Choice.photo(), true, false));
        assertEquals(Kind.OPEN_PREVIEW, p.kind);
        assertEquals(LIVING, p.stillId);
        WallpaperSlotPlan off = WallpaperSlotPlan.forMotion(false, in(LIVING, Choice.sameAsHome(), true, true));
        assertEquals(Kind.SET_STILL, off.kind);
        assertEquals(LIVING, off.stillId);
    }

    @Test public void lockLiveSupportedFrom34() {
        assertFalse(WallpaperSlots.lockLiveSupported(26));
        assertFalse(WallpaperSlots.lockLiveSupported(33));
        assertTrue(WallpaperSlots.lockLiveSupported(34));
        assertTrue(WallpaperSlots.lockLiveSupported(36));
    }
}
