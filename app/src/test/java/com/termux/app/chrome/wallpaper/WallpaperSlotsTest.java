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

    @Test
    public void lockChoiceDefaultsToSameAsHomeAndMotionToOn() {
        assertEquals("same_as_home", preferences.getWallpaperLockChoice());
        assertTrue(preferences.isWallpaperLockMotionEnabled());
    }

    @Test
    public void lockChoiceRoundTrips() {
        preferences.setWallpaperLockChoice("animated:aurora");
        assertEquals("animated:aurora", preferences.getWallpaperLockChoice());
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
        preferences.setManagedWallpaperAnimatedId("mesh");
        preferences.setWallpaperLockChoice("animated:tide");
        preferences.setWallpaperLockMotionEnabled(false);
        assertEquals("mesh", preferences.getManagedWallpaperAnimatedId());
    }

    @Test
    public void stateMapsTheStoredValues() {
        WallpaperSlots.State s = WallpaperSlots.stateFrom("aurora", "animated:tide", false, true);
        assertEquals("aurora", s.home.animatedId);
        assertFalse(s.home.photo);
        assertEquals("tide", s.lock.animatedId);
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
    public void lockSlotNamesForTheStatusRoute() {
        assertEquals("same_as_home", WallpaperSlots.lockSlotName(WallpaperSlots.Choice.sameAsHome()));
        assertEquals("photo", WallpaperSlots.lockSlotName(WallpaperSlots.Choice.photo()));
        assertEquals("rain", WallpaperSlots.lockSlotName(WallpaperSlots.Choice.animated("rain")));
    }

    @Test
    public void readBelow34HasNoLockLive() {
        WallpaperSlots.State s = WallpaperSlots.read(RuntimeEnvironment.getApplication());
        assertFalse(s.lockLiveActive);
    }
}
