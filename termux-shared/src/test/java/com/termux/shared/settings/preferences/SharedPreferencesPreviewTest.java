package com.termux.shared.settings.preferences;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

/**
 * A preview answers {@link SharedPreferenceUtils}' reads of its own store and of nothing else,
 * writes nothing, and once cleared every reader is back on what is stored.
 */
@RunWith(RobolectricTestRunner.class)
public class SharedPreferencesPreviewTest {

    @After
    public void tearDown() {
        SharedPreferencesPreview.clear();
    }

    @Test
    public void aPreviewAnswersReadsOfItsStoreUntilCleared() {
        SharedPreferences store = store("preview-store");
        SharedPreferences shown = store("preview-shown");
        store.edit().putInt("blur", 4).putString("tint", "scheme").putBoolean("soft", false)
            .putFloat("radius", 2f).commit();
        shown.edit().putInt("blur", 25).putString("tint", "obsidian").putBoolean("soft", true)
            .putFloat("radius", 6f).commit();

        SharedPreferencesPreview.show(store, shown);
        assertTrue(SharedPreferencesPreview.isShowing());
        assertEquals(25, SharedPreferenceUtils.getInt(store, "blur", 0));
        assertEquals("obsidian", SharedPreferenceUtils.getString(store, "tint", null, true));
        assertTrue(SharedPreferenceUtils.getBoolean(store, "soft", false));
        assertEquals(6f, SharedPreferenceUtils.getFloat(store, "radius", 0f), 0f);
        // Nothing was written: the store's own methods still read what it holds.
        assertEquals(4, store.getInt("blur", 0));

        SharedPreferencesPreview.clear();
        assertFalse(SharedPreferencesPreview.isShowing());
        assertEquals(4, SharedPreferenceUtils.getInt(store, "blur", 0));
        assertEquals("scheme", SharedPreferenceUtils.getString(store, "tint", null, true));
    }

    @Test
    public void anotherStoreIsNotPreviewed() {
        SharedPreferences store = store("preview-own");
        SharedPreferences other = store("preview-other");
        SharedPreferences shown = store("preview-over");
        other.edit().putInt("blur", 9).commit();
        shown.edit().putInt("blur", 25).commit();
        SharedPreferencesPreview.show(store, shown);
        assertEquals(9, SharedPreferenceUtils.getInt(other, "blur", 0));
        assertSame(other, SharedPreferencesPreview.readsFor(other));
        assertSame(shown, SharedPreferencesPreview.readsFor(store));
        assertNull(SharedPreferencesPreview.readsFor(null));
    }

    private static SharedPreferences store(String name) {
        SharedPreferences store = RuntimeEnvironment.getApplication()
            .getSharedPreferences(name, Context.MODE_PRIVATE);
        store.edit().clear().commit();
        return store;
    }
}
