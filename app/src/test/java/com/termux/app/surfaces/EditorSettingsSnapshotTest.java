package com.termux.app.surfaces;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.launcher.home.HomeSettings;

import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The app-wide choices the Appearance and Home cards write come back on Undo the way Settings
 * would write them, and only the ones that changed are written again (each write reloads the
 * launcher's styling, the theme's rebuilds the activity).
 */
public class EditorSettingsSnapshotTest {

    /** A store that remembers what was written to it. */
    private static final class MapStore implements EditorSettingsSnapshot.Store {
        final Map<String, Object> values = new HashMap<>();
        final List<String> writes = new ArrayList<>();

        @Nullable @Override public String getString(@NonNull String key) {
            return (String) values.get(key);
        }

        @Override public void putString(@NonNull String key, @Nullable String value) {
            values.put(key, value);
            writes.add(key);
        }

        @Override public boolean getBoolean(@NonNull String key) {
            Object value = values.get(key);
            return value == null ? EditorSettingsSnapshot.defaultOf(key) : (Boolean) value;
        }

        @Override public void putBoolean(@NonNull String key, boolean value) {
            values.put(key, value);
            writes.add(key);
        }
    }

    @Test
    public void undoPutsBackOnlyWhatChanged() {
        MapStore store = new MapStore();
        store.values.put("theme_mode", "system");
        EditorSettingsSnapshot entry = EditorSettingsSnapshot.capture(store);

        store.putString(HomeSettings.KEY_ICON_SCALE, "120");
        store.putBoolean("widgets_on_glass", true);
        store.writes.clear();

        List<String> written = entry.restore(store);

        assertEquals(store.writes, written);
        assertEquals(2, written.size());
        assertTrue(written.contains(HomeSettings.KEY_ICON_SCALE));
        assertEquals(null, store.getString(HomeSettings.KEY_ICON_SCALE));
        assertFalse(store.getBoolean("widgets_on_glass"));
        assertFalse("the theme did not change, so it is not written", written.contains("theme_mode"));
    }

    @Test
    public void theSignatureSeesEveryChoice() {
        MapStore store = new MapStore();
        String atOpen = EditorSettingsSnapshot.signatureOf(store);
        for (String key : EditorSettingsSnapshot.STRING_KEYS) {
            store.putString(key, "x");
            assertNotEquals(key, atOpen, EditorSettingsSnapshot.signatureOf(store));
            store.values.remove(key);
        }
        for (String key : EditorSettingsSnapshot.BOOLEAN_KEYS) {
            store.putBoolean(key, !EditorSettingsSnapshot.defaultOf(key));
            assertNotEquals(key, atOpen, EditorSettingsSnapshot.signatureOf(store));
            store.values.remove(key);
        }
        assertEquals(atOpen, EditorSettingsSnapshot.signatureOf(store));
    }

    @Test
    public void nothingChangedWritesNothing() {
        MapStore store = new MapStore();
        store.putBoolean(HomeSettings.KEY_LABEL_SHADOW, false);
        EditorSettingsSnapshot entry = EditorSettingsSnapshot.capture(store);
        store.writes.clear();
        assertTrue(entry.restore(store).isEmpty());
        assertTrue(store.writes.isEmpty());
    }
}
