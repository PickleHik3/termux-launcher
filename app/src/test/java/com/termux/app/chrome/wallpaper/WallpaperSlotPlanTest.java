package com.termux.app.chrome.wallpaper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

import com.termux.app.chrome.wallpaper.WallpaperSlotPlan.Inputs;
import com.termux.app.chrome.wallpaper.WallpaperSlotPlan.Kind;
import com.termux.app.chrome.wallpaper.WallpaperSlots.Choice;
import com.termux.app.chrome.wallpaper.WallpaperSlots.Slot;

import org.junit.Test;

/** What applying a slot choice does. */
public class WallpaperSlotPlanTest {

    private static final int SYSTEM = WallpaperSlotPlan.FLAG_SYSTEM;
    private static final int LOCK = WallpaperSlotPlan.FLAG_LOCK;

    private static final java.io.File PICTURE = new java.io.File("/data/pending/1.png");

    private static Inputs in(Choice lock) {
        return new Inputs(lock);
    }

    // --- stored values ---

    @Test public void lockValuesRoundTrip() {
        assertEquals("same_as_home", WallpaperSlotPlan.lockValue(Choice.sameAsHome()));
        assertEquals("photo", WallpaperSlotPlan.lockValue(Choice.photo()));
        assertTrue(WallpaperSlotPlan.lockChoice("same_as_home").sameAsHome);
        assertTrue(WallpaperSlotPlan.lockChoice("photo").photo);
    }

    @Test public void unknownLockValuesReadAsSameAsHome() {
        assertTrue(WallpaperSlotPlan.lockChoice(null).sameAsHome);
        assertTrue(WallpaperSlotPlan.lockChoice("").sameAsHome);
        assertTrue(WallpaperSlotPlan.lockChoice("animated:living:0123456789abcdef").sameAsHome);
        assertTrue(WallpaperSlotPlan.lockChoice("both").sameAsHome);
    }

    // --- Home ---

    @Test public void homePhotoWithoutAPictureIsRecordOnly() {
        WallpaperSlotPlan p = WallpaperSlotPlan.forApply(Slot.HOME, Choice.photo(), in(Choice.sameAsHome()));
        assertEquals(Kind.RECORD_ONLY, p.kind);
        assertNull(p.recordLock);
        assertFalse(p.recordHome);
    }

    // --- Lock ---

    @Test public void lockPhotoIsRecordOnly() {
        WallpaperSlotPlan p = WallpaperSlotPlan.forApply(Slot.LOCK, Choice.photo(), in(Choice.sameAsHome()));
        assertEquals(Kind.RECORD_ONLY, p.kind);
        assertEquals("photo", p.recordLock);
    }

    // --- photos with their picture ---

    @Test public void photoOnBothScreens() {
        // Apply: Home first, with Lock already Same as Home: one set for both screens...
        WallpaperSlotPlan home = WallpaperSlotPlan.forApply(Slot.HOME, Choice.photo(PICTURE), in(Choice.sameAsHome()));
        assertEquals(Kind.SET_PHOTO, home.kind);
        assertEquals(SYSTEM | LOCK, home.flags);
        assertEquals(PICTURE, home.photo);
        assertTrue(home.recordHome);
        assertNull(home.recordLock);
        // ...then Lock follows, which has nothing left to set.
        WallpaperSlotPlan follows = WallpaperSlotPlan.forApply(Slot.LOCK, Choice.sameAsHome(), in(Choice.sameAsHome()));
        assertEquals(Kind.RECORD_ONLY, follows.kind);
        assertEquals("same_as_home", follows.recordLock);
    }

    @Test public void photoOnHomeOnlyThenLockFollowsByCopying() {
        WallpaperSlotPlan homeOnly = WallpaperSlotPlan.forApply(Slot.HOME, Choice.photo(PICTURE), in(Choice.photo()));
        assertEquals(Kind.SET_PHOTO, homeOnly.kind);
        assertEquals(SYSTEM, homeOnly.flags);
        assertTrue(homeOnly.recordHome);
        assertNull("the Lock slot is left alone", homeOnly.recordLock);
        WallpaperSlotPlan copy = WallpaperSlotPlan.forApply(Slot.LOCK, Choice.sameAsHome(), in(Choice.photo()));
        assertEquals(Kind.COPY_HOME_PHOTO_TO_LOCK, copy.kind);
        assertEquals(LOCK, copy.flags);
        assertEquals("same_as_home", copy.recordLock);
    }

    @Test public void photoOnLockOnly() {
        WallpaperSlotPlan p = WallpaperSlotPlan.forApply(Slot.LOCK, Choice.photo(PICTURE), in(Choice.sameAsHome()));
        assertEquals(Kind.SET_PHOTO, p.kind);
        assertEquals(LOCK, p.flags);
        assertEquals(PICTURE, p.photo);
        assertFalse("Home untouched", p.recordHome);
        assertEquals("photo", p.recordLock);
    }

    @Test public void lockSetElsewhereKeepsItWhenHomeIsApplied() {
        // The Lock slot then shows the system's own picture, a photo with a file.
        Inputs outside = in(Choice.photo(new java.io.File("/files/wallpaper/slots/lock-system.png")));
        WallpaperSlotPlan p = WallpaperSlotPlan.forApply(Slot.HOME, Choice.photo(PICTURE), outside);
        assertEquals(Kind.SET_PHOTO, p.kind);
        assertEquals("no FLAG_LOCK", SYSTEM, p.flags);
        assertNull(p.recordLock);
    }

    @Test public void lockSetElsewhereThenSameAsHomeCopiesHome() {
        Inputs outside = in(Choice.photo(new java.io.File("/files/wallpaper/slots/lock-system.png")));
        WallpaperSlotPlan p = WallpaperSlotPlan.forApply(Slot.LOCK, Choice.sameAsHome(), outside);
        assertEquals(Kind.COPY_HOME_PHOTO_TO_LOCK, p.kind);
        assertEquals(LOCK, p.flags);
        assertEquals("same_as_home", p.recordLock);
    }

    @Test public void lockSetElsewhereThenAPhotoSetsIt() {
        Inputs outside = in(Choice.photo());
        WallpaperSlotPlan p = WallpaperSlotPlan.forApply(Slot.LOCK, Choice.photo(PICTURE), outside);
        assertEquals(Kind.SET_PHOTO, p.kind);
        assertEquals(LOCK, p.flags);
        assertEquals("photo", p.recordLock);
    }
}
