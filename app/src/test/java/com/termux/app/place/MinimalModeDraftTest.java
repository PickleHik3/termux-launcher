package com.termux.app.place;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import com.termux.shared.settings.preferences.PreferenceDraft;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/**
 * Minimal mode, turned on or off while the Appearance editor holds a draft, is the draft's: the
 * store keeps its own value until Done, and Discard puts the stored mode back without a restore.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class MinimalModeDraftTest {

    private SharedPreferences mStore;
    private PlaceLayoutStore mPlaces;

    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.getApplication();
        mStore = context.getSharedPreferences("minimal-draft", Context.MODE_PRIVATE);
        mStore.edit().clear().commit();
        mPlaces = new PlaceLayoutStore(new TermuxAppSharedPreferences(context, mStore, null));
    }

    @After
    public void tearDown() {
        PreferenceDraft.discardCurrent();
    }

    @Test
    public void theModeTurnedOnInASessionShowsAtOnceAndIsNotStored() {
        PreferenceDraft draft = PreferenceDraft.begin(mStore);

        mPlaces.setMinimal(true);

        assertTrue("the window sees it", mPlaces.isMinimal());
        assertFalse("the store does not", mStore.getBoolean(PlaceLayoutStore.minimalKey(), false));
        assertTrue(draft.isDirty());
    }

    @Test
    public void doneStoresIt() {
        PreferenceDraft draft = PreferenceDraft.begin(mStore);
        mPlaces.setMinimal(true);

        draft.commit();

        assertTrue(mStore.getBoolean(PlaceLayoutStore.minimalKey(), false));
        assertTrue(mPlaces.isMinimal());
    }

    @Test
    public void discardBringsTheStoredModeBackWithNothingToRestore() {
        PreferenceDraft draft = PreferenceDraft.begin(mStore);
        mPlaces.setMinimal(true);
        assertTrue(mPlaces.isMinimal());

        draft.discard();

        assertFalse(mPlaces.isMinimal());
        assertEquals(false, mStore.getBoolean(PlaceLayoutStore.minimalKey(), false));
    }

    @Test
    public void outsideOfASessionTheModeIsStoredAsBefore() {
        mPlaces.setMinimal(true);

        assertTrue(mStore.getBoolean(PlaceLayoutStore.minimalKey(), false));
    }
}
