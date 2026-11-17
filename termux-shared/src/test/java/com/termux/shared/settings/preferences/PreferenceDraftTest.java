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

import java.util.ArrayList;
import java.util.List;

/**
 * The draft over the store (window restructure step 1): a preview shown to every reader, Done
 * writing it once, Discard dropping it, an outside write surviving both, and dirty meaning the
 * draft against the store.
 */
@RunWith(RobolectricTestRunner.class)
public class PreferenceDraftTest {

    @After
    public void tearDown() {
        PreferenceDraft.discardCurrent();
        SharedPreferencesPreview.clear();
    }

    @Test
    public void everyReaderOfTheStoreSeesTheDraftOnTop() {
        SharedPreferences store = store("draft-preview");
        store.edit().putInt("blur", 4).putString("tint", "scheme").commit();
        PreferenceDraft draft = PreferenceDraft.begin(store);
        draft.edit().putInt("blur", 25).putBoolean("soft", true).remove("tint").apply();

        assertEquals(25, SharedPreferenceUtils.getInt(store, "blur", 0));
        assertTrue(SharedPreferenceUtils.getBoolean(store, "soft", false));
        assertNull(SharedPreferenceUtils.getString(store, "tint", null, true));
        assertSame(draft, SharedPreferencesPreview.readsFor(store));
        // Nothing is stored yet.
        assertEquals(4, store.getInt("blur", 0));
        assertEquals("scheme", store.getString("tint", null));
        assertFalse(store.contains("soft"));
    }

    @Test
    public void doneWritesTheDraftOnceAndEndsTheSession() {
        SharedPreferences store = store("draft-done");
        store.edit().putInt("blur", 4).putString("tint", "scheme").putInt("keep", 1).commit();
        List<String> changed = new ArrayList<>();
        SharedPreferences.OnSharedPreferenceChangeListener listener = (sp, key) -> changed.add(key);
        store.registerOnSharedPreferenceChangeListener(listener);
        PreferenceDraft draft = PreferenceDraft.begin(store);
        draft.edit().putInt("blur", 10).apply();
        draft.edit().putInt("blur", 25).putBoolean("soft", true).remove("tint").apply();
        assertTrue("no write while the draft is open", changed.isEmpty());

        draft.commit();

        assertEquals(25, store.getInt("blur", 0));
        assertTrue(store.getBoolean("soft", false));
        assertFalse(store.contains("tint"));
        assertEquals(1, store.getInt("keep", 0));
        // Each changed key is told of once, not once per edit.
        assertEquals(3, changed.size());
        assertEquals(1, java.util.Collections.frequency(changed, "blur"));
        assertFalse(draft.isOpen());
        assertNull(PreferenceDraft.current());
        assertFalse(SharedPreferencesPreview.isShowing());
        store.unregisterOnSharedPreferenceChangeListener(listener);
    }

    @Test
    public void discardLeavesTheStoreExactlyAsItWas() {
        SharedPreferences store = store("draft-discard");
        store.edit().putInt("blur", 4).putString("tint", "scheme").commit();
        java.util.Map<String, ?> before = new java.util.HashMap<>(store.getAll());
        PreferenceDraft draft = PreferenceDraft.begin(store);
        draft.edit().putInt("blur", 25).putBoolean("soft", true).remove("tint").apply();

        draft.discard();

        assertEquals(before, store.getAll());
        assertEquals(4, SharedPreferenceUtils.getInt(store, "blur", 0));
        assertFalse(SharedPreferencesPreview.isShowing());
        assertNull(PreferenceDraft.current());
    }

    @Test
    public void anOutsideWriteToACellTheDraftDoesNotHoldShowsAtOnceAndSurvivesDoneAndDiscard() {
        for (boolean done : new boolean[] {true, false}) {
            SharedPreferences store = store("draft-outside-" + done);
            store.edit().putInt("blur", 4).putInt("look", 1).commit();
            PreferenceDraft draft = PreferenceDraft.begin(store);
            draft.edit().putInt("blur", 25).apply();

            // launcherctl look: its own handle writes the store itself.
            store.edit().putInt("look", 7).apply();
            assertEquals("shows at once", 7, SharedPreferenceUtils.getInt(store, "look", 0));
            assertEquals(25, SharedPreferenceUtils.getInt(store, "blur", 0));

            if (done) draft.commit();
            else draft.discard();

            assertEquals(7, store.getInt("look", 0));
            assertEquals(done ? 25 : 4, store.getInt("blur", 0));
        }
    }

    @Test
    public void forACellBothTouchedTheDraftWinsUntilDone() {
        SharedPreferences store = store("draft-both");
        store.edit().putInt("blur", 4).commit();
        PreferenceDraft draft = PreferenceDraft.begin(store);
        draft.edit().putInt("blur", 25).apply();
        store.edit().putInt("blur", 9).apply();
        assertEquals(25, SharedPreferenceUtils.getInt(store, "blur", 0));
        assertEquals(9, store.getInt("blur", 0));

        draft.commit();
        assertEquals(25, store.getInt("blur", 0));
    }

    @Test
    public void aDiscardedBothTouchedCellKeepsTheOutsideValue() {
        SharedPreferences store = store("draft-both-discard");
        store.edit().putInt("blur", 4).commit();
        PreferenceDraft draft = PreferenceDraft.begin(store);
        draft.edit().putInt("blur", 25).apply();
        store.edit().putInt("blur", 9).apply();

        draft.discard();
        assertEquals(9, SharedPreferenceUtils.getInt(store, "blur", 0));
    }

    @Test
    public void dirtyIsTheDraftAgainstTheStoreSoSettingAValueBackClearsIt() {
        SharedPreferences store = store("draft-dirty");
        store.edit().putInt("blur", 4).putString("tint", "scheme").commit();
        PreferenceDraft draft = PreferenceDraft.begin(store);
        assertFalse(draft.isDirty());

        draft.edit().putInt("blur", 25).apply();
        assertTrue(draft.isDirty());
        draft.edit().putInt("blur", 4).apply();
        assertFalse("back to the saved value", draft.isDirty());

        draft.edit().remove("tint").apply();
        assertTrue(draft.isDirty());
        draft.edit().putString("tint", "scheme").apply();
        assertFalse(draft.isDirty());

        // A key the store lacks: held as removed is no change, held with a value is.
        draft.edit().remove("absent").apply();
        assertFalse(draft.isDirty());
        draft.edit().putBoolean("absent", false).apply();
        assertTrue(draft.isDirty());
    }

    @Test
    public void anOutsideWriteThatMatchesTheDraftMakesItClean() {
        SharedPreferences store = store("draft-dirty-outside");
        store.edit().putInt("blur", 4).commit();
        PreferenceDraft draft = PreferenceDraft.begin(store);
        draft.edit().putInt("blur", 25).apply();
        assertTrue(draft.isDirty());
        store.edit().putInt("blur", 25).apply();
        assertFalse(draft.isDirty());
    }

    @Test
    public void aClearHoldsEveryKeyAsRemovedAndLaterPutsStand() {
        SharedPreferences store = store("draft-clear");
        store.edit().putInt("a", 1).putInt("b", 2).commit();
        PreferenceDraft draft = PreferenceDraft.begin(store);
        draft.edit().clear().putInt("c", 3).apply();

        assertFalse(SharedPreferencesPreview.readsFor(store).getAll().containsKey("a"));
        assertEquals(3, SharedPreferenceUtils.getInt(store, "c", 0));
        assertTrue(draft.isDirty());
        draft.commit();
        assertEquals(1, store.getAll().size());
        assertEquals(3, store.getInt("c", 0));
    }

    @Test
    public void aHandleThatOutlivesTheSessionReadsAndWritesTheStore() {
        SharedPreferences store = store("draft-ended");
        PreferenceDraft draft = PreferenceDraft.begin(store);
        draft.edit().putInt("blur", 25).apply();
        draft.discard();

        draft.edit().putInt("late", 5).apply();
        assertEquals(5, store.getInt("late", 0));
        assertEquals(0, draft.getInt("blur", 0));
    }

    @Test
    public void aNewDraftDropsOneLeftOpen() {
        SharedPreferences store = store("draft-replace");
        PreferenceDraft first = PreferenceDraft.begin(store);
        first.edit().putInt("blur", 25).apply();
        PreferenceDraft second = PreferenceDraft.begin(store);

        assertFalse(first.isOpen());
        assertSame(second, PreferenceDraft.current());
        assertEquals(0, SharedPreferenceUtils.getInt(store, "blur", 0));
    }

    private static SharedPreferences store(String name) {
        SharedPreferences store = RuntimeEnvironment.getApplication()
            .getSharedPreferences(name, Context.MODE_PRIVATE);
        store.edit().clear().commit();
        return store;
    }
}
