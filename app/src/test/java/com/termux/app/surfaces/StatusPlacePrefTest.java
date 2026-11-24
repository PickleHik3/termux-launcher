package com.termux.app.surfaces;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import com.termux.app.statusbar.StatusPlace;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_APP;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/**
 * Status bar card › Place: the strip or the thin row in the pane, one global value. A fresh
 * install and every existing install read the strip.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class StatusPlacePrefTest {

    private SharedPreferences store;
    private TermuxAppSharedPreferences preferences;

    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.getApplication().getApplicationContext();
        store = context.getSharedPreferences("status-place-test", Context.MODE_PRIVATE);
        store.edit().clear().commit();
        preferences = new TermuxAppSharedPreferences(context, store, null);
    }

    @Test
    public void theStripIsTheDefaultForFreshAndExistingInstalls() {
        assertEquals(TERMUX_APP.STATUS_PLACE_STRIP, TERMUX_APP.DEFAULT_STATUS_PLACE);
        assertEquals(StatusPlace.STRIP, StatusPlace.fromValue(preferences.getStatusPlace()));
        preferences.migrateLookGrid(java.util.Collections.singletonList(0), true);
        assertEquals(StatusPlace.STRIP, StatusPlace.fromValue(preferences.getStatusPlace()));
        assertFalse(store.contains(TERMUX_APP.KEY_STATUS_PLACE));
    }

    @Test
    public void theValueIsStoredOnceAndReadBack() {
        preferences.setStatusPlace(TERMUX_APP.STATUS_PLACE_PANE);
        assertTrue(StatusPlace.fromValue(preferences.getStatusPlace()).inPane());
        assertEquals("pane", store.getString(TERMUX_APP.KEY_STATUS_PLACE, null));
        preferences.setStatusPlace(TERMUX_APP.STATUS_PLACE_STRIP);
        assertFalse(StatusPlace.fromValue(preferences.getStatusPlace()).inPane());
    }

    @Test
    public void theAppearanceResetPutsTheStripBack() {
        assertTrue(AppearanceReset.inScope(TERMUX_APP.KEY_STATUS_PLACE));
    }
}
