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

    private static final String LIVING = "living:0123456789abcdef";
    private static final String LIVING_B = "living:fedcba9876543210";

    private TermuxAppSharedPreferences preferences;
    private SharedPreferences sp;

    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.getApplication().getApplicationContext();
        sp = context.getSharedPreferences("wallpaper-slots-test", Context.MODE_PRIVATE);
        sp.edit().clear().commit();
        preferences = new TermuxAppSharedPreferences(context, sp, null);
    }

    @Test
    public void lockChoiceDefaultsToSameAsHomeAndMotionToOn() {
        assertEquals("same_as_home", preferences.getWallpaperLockChoice());
        assertTrue(preferences.isWallpaperLockMotionEnabled());
    }

    @Test
    public void lockChoiceRoundTrips() {
        preferences.setWallpaperLockChoice("animated:" + LIVING);
        assertEquals("animated:" + LIVING, preferences.getWallpaperLockChoice());
        preferences.setWallpaperLockChoice("photo");
        assertEquals("photo", preferences.getWallpaperLockChoice());
        preferences.setWallpaperLockChoice("same_as_home");
        assertEquals("same_as_home", preferences.getWallpaperLockChoice());
    }

    @Test
    public void junkLockChoicesReadAsSameAsHome() {
        preferences.setWallpaperLockChoice(null);
        assertEquals("same_as_home", preferences.getWallpaperLockChoice());
        preferences.setWallpaperLockChoice("animated:");
        assertEquals("same_as_home", preferences.getWallpaperLockChoice());
        sp.edit().putString("wallpaper_lock_choice", "both").commit();
        assertEquals("same_as_home", preferences.getWallpaperLockChoice());
    }

    @Test
    public void lockMotionRoundTrips() {
        preferences.setWallpaperLockMotionEnabled(false);
        assertFalse(preferences.isWallpaperLockMotionEnabled());
        preferences.setWallpaperLockMotionEnabled(true);
        assertTrue(preferences.isWallpaperLockMotionEnabled());
    }

    @Test
    public void slotPrefsDoNotTouchTheHomeSlot() {
        preferences.setManagedWallpaperAnimatedId(LIVING);
        preferences.setWallpaperLockChoice("animated:" + LIVING_B);
        preferences.setWallpaperLockMotionEnabled(false);
        assertEquals(LIVING, preferences.getManagedWallpaperAnimatedId());
    }

    @Test
    public void stateMapsTheStoredValues() {
        WallpaperSlots.State s = WallpaperSlots.stateFrom(LIVING, "animated:" + LIVING_B, false, true);
        assertEquals(LIVING, s.home.animatedId);
        assertFalse(s.home.photo);
        assertEquals(LIVING_B, s.lock.animatedId);
        assertFalse(s.lock.sameAsHome);
        assertFalse(s.lockMotion);
        assertTrue(s.lockLiveActive);
    }

    @Test
    public void stateMapsAHomePhotoAndTheDefaults() {
        WallpaperSlots.State s = WallpaperSlots.stateFrom(null, null, true, false);
        assertTrue(s.home.photo);
        assertNull(s.home.animatedId);
        assertTrue(s.lock.sameAsHome);
        assertTrue(s.lockMotion);
        assertFalse(s.lockLiveActive);
        assertTrue(WallpaperSlots.stateFrom("gone", "photo", true, false).home.photo);
        assertTrue(WallpaperSlots.stateFrom(null, "photo", true, false).lock.photo);
        assertTrue("unknown id", WallpaperSlots.stateFrom(null, "animated:gone", true, false).lock.sameAsHome);
    }

    @Test
    public void retiredBackgroundsReadAsAPhotoAndSameAsHome() {
        WallpaperSlots.State s = WallpaperSlots.stateFrom("aurora", "animated:mesh", true, false);
        assertTrue(s.home.photo);
        assertNull(s.home.animatedId);
        assertTrue(s.lock.sameAsHome);
    }

    @Test
    public void dropRetiredBackgroundsSpotsEveryRetiredHomeId() {
        for (String retired : new String[] {"mesh", "aurora", "tide", "rain", "contour", "drift", "lava", "silk",
            "caustics", "chrome"}) {
            assertTrue(retired, WallpaperSlots.isRetiredHomeId(retired));
        }
        assertFalse("a photo", WallpaperSlots.isRetiredHomeId(null));
        assertFalse(WallpaperSlots.isRetiredHomeId(LIVING));
    }

    @Test
    public void dropRetiredBackgroundsHealsARetiredLockChoice() {
        // Our live wallpaper holds the lock screen: follow Home (its picture is copied over it).
        assertEquals("same_as_home", WallpaperSlots.healedLockValue("animated:aurora", true));
        // Otherwise the lock screen already shows that background's still: keep it as a photo.
        assertEquals("photo", WallpaperSlots.healedLockValue("animated:mesh", false));
        assertNull(WallpaperSlots.healedLockValue("animated:" + LIVING, true));
        assertNull(WallpaperSlots.healedLockValue("same_as_home", true));
        assertNull(WallpaperSlots.healedLockValue("photo", false));
        assertNull(WallpaperSlots.healedLockValue(null, true));
    }

    @Test
    public void aSlotHoldingAPhotoCarriesItsKeptPicture() {
        java.io.File exact = new java.io.File("/files/managed-wallpaper/system-wallpaper-exact.png");
        java.io.File lockCopy = new java.io.File("/files/wallpaper/slots/lock.png");
        WallpaperSlots.State s = WallpaperSlots.stateFrom(null, "photo", true, false, exact, lockCopy);
        assertEquals(exact, s.home.photoFile);
        assertEquals(lockCopy, s.lock.photoFile);
        WallpaperSlots.State animated = WallpaperSlots.stateFrom(LIVING, "same_as_home", true, false, exact, lockCopy);
        assertNull("an animated Home has no photo", animated.home.photoFile);
        assertTrue(animated.lock.sameAsHome);
        assertNull("unknown picture", WallpaperSlots.stateFrom(null, "photo", true, false).home.photoFile);
        assertEquals("photo", WallpaperSlots.lockSlotName(s.lock));
    }

    @Test
    public void lockSlotNamesForTheStatusRoute() {
        assertEquals("same_as_home", WallpaperSlots.lockSlotName(WallpaperSlots.Choice.sameAsHome()));
        assertEquals("photo", WallpaperSlots.lockSlotName(WallpaperSlots.Choice.photo()));
        assertEquals(LIVING, WallpaperSlots.lockSlotName(WallpaperSlots.Choice.animated(LIVING)));
    }

    @Test
    public void readBelow34HasNoLockLive() {
        WallpaperSlots.State s = WallpaperSlots.read(RuntimeEnvironment.getApplication());
        assertFalse(s.lockLiveActive);
    }
}
