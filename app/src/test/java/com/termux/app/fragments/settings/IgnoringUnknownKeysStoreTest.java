package com.termux.app.fragments.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import android.app.Application;
import android.os.Build;

import androidx.preference.PreferenceDataStore;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** A screen with no old store falls back to a store that ignores a key it does not hold. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class IgnoringUnknownKeysStoreTest {

    @Test
    public void everyWriteIsIgnoredInsteadOfThrowing() {
        PreferenceDataStore store = RegistryPreferenceDataStore.ignoringUnknownKeys();
        store.putBoolean("k", true);
        store.putInt("k", 1);
        store.putString("k", "v");
        store.putLong("k", 1L);
        store.putFloat("k", 1f);
        store.putStringSet("k", null);
        assertFalse(store.getBoolean("k", false));
        assertEquals("d", store.getString("k", "d"));
    }
}
