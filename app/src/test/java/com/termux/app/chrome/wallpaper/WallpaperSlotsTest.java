package com.termux.app.chrome.wallpaper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/** The slot preferences and {@link WallpaperSlots#read}'s mapping. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class WallpaperSlotsTest {

    private TermuxAppSharedPreferences preferences;
    private SharedPreferences sp;

    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.getApplication().getApplicationContext();
        sp = context.getSharedPreferences("wallpaper-slots-test", Context.MODE_PRIVATE);
        sp.edit().clear().commit();
        preferences = new TermuxAppSharedPreferences(context, sp, null);
    }

    private static final String HOME_LIVING = "living:0123456789abcdef";
    private static final String LOCK_LIVING = "animated:living:fedcba9876543210";

    @Test
    public void lockChoiceDefaultsToSameAsHome() {
        assertEquals("same_as_home", preferences.getWallpaperLockChoice());
    }

    @Test
    public void lockChoiceRoundTrips() {
        preferences.setWallpaperLockChoice("photo");
        assertEquals("photo", preferences.getWallpaperLockChoice());
        preferences.setWallpaperLockChoice("same_as_home");
        assertEquals("same_as_home", preferences.getWallpaperLockChoice());
    }

    @Test
    public void junkAndRetiredLockChoicesReadAsSameAsHome() {
        preferences.setWallpaperLockChoice(null);
        assertEquals("same_as_home", preferences.getWallpaperLockChoice());
        preferences.setWallpaperLockChoice(LOCK_LIVING);
        assertEquals("same_as_home", preferences.getWallpaperLockChoice());
        sp.edit().putString("wallpaper_lock_choice", "both").commit();
        assertEquals("same_as_home", preferences.getWallpaperLockChoice());
    }

    @Test
    public void stateMapsTheStoredValues() {
        WallpaperSlots.State s = WallpaperSlots.stateFrom("photo", null, null);
        assertTrue(s.home.photo);
        assertTrue(s.lock.photo);
        assertFalse(s.lock.sameAsHome);
        WallpaperSlots.State d = WallpaperSlots.stateFrom(null, null, null);
        assertTrue(d.home.photo);
        assertTrue(d.lock.sameAsHome);
        assertTrue("a retired animated choice", WallpaperSlots.stateFrom(LOCK_LIVING, null, null).lock.sameAsHome);
    }

    @Test
    public void aSlotHoldingAPhotoCarriesItsKeptPicture() {
        java.io.File exact = new java.io.File("/files/managed-wallpaper/system-wallpaper-exact.png");
        java.io.File lockCopy = new java.io.File("/files/wallpaper/slots/lock.png");
        WallpaperSlots.State s = WallpaperSlots.stateFrom("photo", exact, lockCopy);
        assertEquals(exact, s.home.photoFile);
        assertEquals(lockCopy, s.lock.photoFile);
        WallpaperSlots.State follows = WallpaperSlots.stateFrom("same_as_home", exact, lockCopy);
        assertTrue(follows.lock.sameAsHome);
        assertNull(follows.lock.photoFile);
        assertNull("unknown picture", WallpaperSlots.stateFrom("photo", null, null).home.photoFile);
        assertEquals("photo", WallpaperSlots.lockSlotName(s.lock));
    }

    @Test
    public void lockIsElsewhereOnlyWhenABaselineDiffersFromAReadableId() {
        assertFalse("same id", WallpaperSlots.isLockElsewhere(7, 7));
        assertTrue("another id", WallpaperSlots.isLockElsewhere(7, 9));
        assertFalse("no baseline after an upgrade", WallpaperSlots.isLockElsewhere(0, 9));
        assertFalse("unreadable now", WallpaperSlots.isLockElsewhere(7, 0));
        assertTrue("a shared lock then its own wallpaper", WallpaperSlots.isLockElsewhere(-1, 9));
        assertFalse("a shared lock stays shared", WallpaperSlots.isLockElsewhere(-1, -1));
    }

    @Test
    public void theFirstReadableLockIdBecomesTheBaseline() {
        assertTrue(WallpaperSlots.needsLockBaseline(0, 9));
        assertFalse(WallpaperSlots.needsLockBaseline(0, 0));
        assertFalse(WallpaperSlots.needsLockBaseline(7, 9));
    }

    @Test
    public void lockIdRoundTrips() {
        assertEquals(0, preferences.getManagedWallpaperLockId());
        preferences.setManagedWallpaperLockId(12);
        assertEquals(12, preferences.getManagedWallpaperLockId());
    }

    @Test
    public void lockSlotNamesForTheStatusRoute() {
        assertEquals("same_as_home", WallpaperSlots.lockSlotName(WallpaperSlots.Choice.sameAsHome()));
        assertEquals("photo", WallpaperSlots.lockSlotName(WallpaperSlots.Choice.photo()));
    }

    // --- the retired live wallpaper migration ---

    private static WallpaperSlots.RetiredPlan plan(String home, String lock, boolean held, boolean photo,
                                                    boolean moved, boolean exact) {
        return WallpaperSlots.planRetiredLive(home, lock, held, photo, moved, exact);
    }

    @Test
    public void nothingStoredAndNothingHeldDoesNothing() {
        WallpaperSlots.RetiredPlan p = plan(null, "same_as_home", false, false, false, true);
        assertFalse(p.clearHome);
        assertFalse(p.reapplyHome);
        assertEquals(WallpaperSlots.RetiredLockAction.NONE, p.lock);
        assertEquals(WallpaperSlots.RetiredLockAction.NONE, plan("", "photo", false, true, true, true).lock);
    }

    @Test
    public void aStoredLivingHomeIdIsClearedAndThePhotoStays() {
        WallpaperSlots.RetiredPlan p = plan(HOME_LIVING, "photo", false, false, false, true);
        assertTrue(p.clearHome);
        assertFalse("the system wallpaper is already the photo", p.reapplyHome);
        assertEquals(WallpaperSlots.RetiredLockAction.NONE, p.lock);
        assertTrue(plan("some-other-id", null, false, false, false, false).clearHome);
    }

    @Test
    public void theHomeCopyIsSetAgainOnlyWhenTheSystemMovedAndTheCopyExists() {
        assertTrue(plan(HOME_LIVING, "photo", false, false, true, true).reapplyHome);
        assertFalse(plan(HOME_LIVING, "photo", false, false, true, false).reapplyHome);
        assertFalse(plan(HOME_LIVING, "photo", false, false, false, true).reapplyHome);
        assertFalse("no retired Home id", plan(null, "photo", false, false, true, true).reapplyHome);
    }

    @Test
    public void anAnimatedLockChoiceBecomesItsPhotoWhenFound() {
        WallpaperSlots.RetiredPlan p = plan(null, LOCK_LIVING, false, true, false, true);
        assertEquals(WallpaperSlots.RetiredLockAction.APPLY_PHOTO, p.lock);
        assertFalse(p.clearHome);
    }

    @Test
    public void anAnimatedLockChoiceWithNoPhotoFollowsHome() {
        assertEquals(WallpaperSlots.RetiredLockAction.COPY_HOME_AND_STORE_SAME_AS_HOME,
            plan(HOME_LIVING, LOCK_LIVING, true, false, false, true).lock);
        assertEquals(WallpaperSlots.RetiredLockAction.COPY_HOME_AND_STORE_SAME_AS_HOME,
            plan(null, LOCK_LIVING, false, false, false, false).lock);
    }

    @Test
    public void sameAsHomeCopiesTheHomePictureWhileHomeWasLivingOrOurServiceHoldsTheLock() {
        assertEquals(WallpaperSlots.RetiredLockAction.COPY_HOME_PICTURE,
            plan(HOME_LIVING, "same_as_home", false, false, false, true).lock);
        assertEquals(WallpaperSlots.RetiredLockAction.COPY_HOME_PICTURE,
            plan(HOME_LIVING, null, false, false, false, true).lock);
        assertEquals(WallpaperSlots.RetiredLockAction.COPY_HOME_PICTURE,
            plan(null, "same_as_home", true, false, false, true).lock);
        assertEquals(WallpaperSlots.RetiredLockAction.NONE,
            plan(null, "same_as_home", false, false, false, true).lock);
    }

    @Test
    public void anOwnLockPhotoIsKeptUnlessOurServiceHoldsTheLock() {
        assertEquals(WallpaperSlots.RetiredLockAction.NONE,
            plan(HOME_LIVING, "photo", false, true, false, true).lock);
        assertEquals(WallpaperSlots.RetiredLockAction.APPLY_PHOTO,
            plan(null, "photo", true, true, false, true).lock);
        assertEquals(WallpaperSlots.RetiredLockAction.COPY_HOME_PICTURE,
            plan(null, "photo", true, false, false, true).lock);
    }

    @Test
    public void retiredAnimatedLockAndHashParsing() {
        assertTrue(WallpaperSlots.isRetiredAnimatedLock(LOCK_LIVING));
        assertFalse(WallpaperSlots.isRetiredAnimatedLock("animated:"));
        assertFalse(WallpaperSlots.isRetiredAnimatedLock("photo"));
        assertFalse(WallpaperSlots.isRetiredAnimatedLock(null));
        assertEquals("fedcba9876543210", WallpaperSlots.retiredLivingHash(LOCK_LIVING));
        assertEquals("0123456789abcdef", WallpaperSlots.retiredLivingHash(HOME_LIVING));
        assertNull("a path is never a hash", WallpaperSlots.retiredLivingHash("animated:living:../x"));
        assertNull(WallpaperSlots.retiredLivingHash("animated:"));
        assertNull(WallpaperSlots.retiredLivingHash(null));
    }
}
