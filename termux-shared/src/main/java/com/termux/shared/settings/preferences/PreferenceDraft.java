package com.termux.shared.settings.preferences;

import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The values an editing session holds over a store: every read of the store through
 * {@link SharedPreferenceUtils} sees the draft on top, and nothing reaches the store until
 * {@link #commit}. One draft at a time, for the whole process, so a session that outlives its
 * activity (a relaunch into another theme) goes on with the same draft.
 *
 * <p>Writes go into the draft only through a handle made over it (a {@code SharedPreferences}
 * that is this draft); a write to the store itself, from anywhere else, is an outside write. An
 * outside write to a key the draft does not hold shows at once and is neither undone by
 * {@link #discard} nor overwritten by {@link #commit}; for a key both touched, the draft wins until
 * it is committed.</p>
 *
 * <p>Dirty is the draft against the store: a held value equal to the stored one is not a change,
 * so setting a value back to what is saved clears it. Nothing is written to disk, no change
 * listener fires, and a launcher killed mid-session loses only the draft. After {@link #commit} or
 * {@link #discard} a handle that outlived the session reads and writes the store.</p>
 *
 * <p>Thread-safe for reads off the main thread: held values live in a concurrent map and are never
 * changed in place.</p>
 */
public final class PreferenceDraft implements SharedPreferences {

    /** Marks a held removal. */
    private static final Object REMOVED = new Object();

    @Nullable private static volatile PreferenceDraft sCurrent;

    @NonNull private final SharedPreferences mStore;
    @NonNull private final ConcurrentHashMap<String, Object> mHeld = new ConcurrentHashMap<>();
    private volatile boolean mEnded;

    private PreferenceDraft(@NonNull SharedPreferences store) {
        mStore = store;
    }

    /**
     * Opens the draft over {@code store} and shows it to every reader of that store, replacing a
     * draft left open (which is dropped, unstored).
     */
    @NonNull
    public static PreferenceDraft begin(@NonNull SharedPreferences store) {
        PreferenceDraft previous = sCurrent;
        if (previous != null)
            previous.discard();
        PreferenceDraft draft = new PreferenceDraft(store);
        sCurrent = draft;
        SharedPreferencesPreview.show(store, draft);
        return draft;
    }

    /** The open draft, or null. */
    @Nullable
    public static PreferenceDraft current() {
        return sCurrent;
    }

    /** Drops an open draft, unstored: a session that ended without Done or Discard. */
    public static void discardCurrent() {
        PreferenceDraft draft = sCurrent;
        if (draft != null)
            draft.discard();
    }

    // ------------------------------------------------------------------------- listeners

    /** One listener registered through {@link #listen}: weakly held, as a store holds its own. */
    private static final class Listening {
        @NonNull final WeakReference<OnSharedPreferenceChangeListener> listener;
        @NonNull final SharedPreferences store;

        Listening(@NonNull OnSharedPreferenceChangeListener listener,
                  @NonNull SharedPreferences store) {
            this.listener = new WeakReference<>(listener);
            this.store = store;
        }
    }

    private static final ArrayList<Listening> sListening = new ArrayList<>();

    /**
     * Registers {@code listener} on {@code store} and remembers it, so that while a draft sits over
     * {@code store} a change the draft makes to what readers see is told to it as a change of the
     * store would be: a listener that restyles a view from a key keeps restyling while the key is
     * edited in a draft. Use this in place of the store's own register call for a store a draft
     * may sit over; the listener is held weakly.
     */
    public static void listen(@NonNull SharedPreferences store,
                              @NonNull OnSharedPreferenceChangeListener listener) {
        store.registerOnSharedPreferenceChangeListener(listener);
        synchronized (sListening) {
            for (Listening each : sListening)
                if (each.listener.get() == listener && each.store == store)
                    return;
            sListening.add(new Listening(listener, store));
        }
    }

    /** Undoes {@link #listen}. */
    public static void unlisten(@NonNull SharedPreferences store,
                                @NonNull OnSharedPreferenceChangeListener listener) {
        store.unregisterOnSharedPreferenceChangeListener(listener);
        synchronized (sListening) {
            for (int i = sListening.size() - 1; i >= 0; i--) {
                Listening each = sListening.get(i);
                OnSharedPreferenceChangeListener held = each.listener.get();
                if (held == null || (held == listener && each.store == store))
                    sListening.remove(i);
            }
        }
    }

    /** Tells every listener of this draft's store that {@code keys} now read differently. */
    private void notifyChanged(@NonNull Set<String> keys) {
        if (keys.isEmpty())
            return;
        ArrayList<OnSharedPreferenceChangeListener> targets = new ArrayList<>();
        synchronized (sListening) {
            for (int i = sListening.size() - 1; i >= 0; i--) {
                Listening each = sListening.get(i);
                OnSharedPreferenceChangeListener held = each.listener.get();
                if (held == null)
                    sListening.remove(i);
                else if (each.store == mStore)
                    targets.add(held);
            }
        }
        for (String key : keys)
            for (OnSharedPreferenceChangeListener target : targets)
                target.onSharedPreferenceChanged(mStore, key);
    }

    /** Held keys whose held value differs from the store's: what readers lose when they are dropped. */
    @NonNull
    private Set<String> heldDifferences() {
        HashSet<String> out = new HashSet<>();
        if (mHeld.isEmpty())
            return out;
        Map<String, ?> stored = mStore.getAll();
        for (Map.Entry<String, Object> entry : mHeld.entrySet()) {
            Object held = entry.getValue();
            Object was = stored.get(entry.getKey());
            if (held == REMOVED ? was != null : !Objects.equals(held, was))
                out.add(entry.getKey());
        }
        return out;
    }

    /** Whether this draft is the open one. */
    public boolean isOpen() {
        return !mEnded;
    }

    /** The store this draft sits over. */
    @NonNull
    public SharedPreferences store() {
        return mStore;
    }

    /** Whether the draft holds a value, or a removal, for {@code key}. */
    public boolean holds(@NonNull String key) {
        return mHeld.containsKey(key);
    }

    /** Whether the draft holds anything at all, changed from the store or not. */
    public boolean isEmpty() {
        return mHeld.isEmpty();
    }

    /** Whether a held value differs from what the store holds now. */
    public boolean isDirty() {
        return isDirtyIgnoring(java.util.Collections.<String>emptySet());
    }

    /**
     * {@link #isDirty} leaving out {@code ignored} keys: bookkeeping a session writes beside what
     * the user changed, which Done stores and Discard drops but which is nothing to save.
     */
    public boolean isDirtyIgnoring(@NonNull Set<String> ignored) {
        if (mHeld.isEmpty())
            return false;
        Map<String, ?> stored = mStore.getAll();
        for (Map.Entry<String, Object> entry : mHeld.entrySet()) {
            if (ignored.contains(entry.getKey()))
                continue;
            Object held = entry.getValue();
            Object was = stored.get(entry.getKey());
            if (held == REMOVED ? was != null : !Objects.equals(held, was))
                return true;
        }
        return false;
    }

    /** Writes everything held to the store as one editor, then ends the session. */
    public void commit() {
        if (mEnded)
            return;
        if (!mHeld.isEmpty()) {
            Editor editor = mStore.edit();
            for (Map.Entry<String, Object> entry : mHeld.entrySet())
                put(editor, entry.getKey(), entry.getValue());
            editor.apply();
        }
        end();
    }

    /** Drops everything held and goes on: Undo inside a session. The store is not written. */
    public void reset() {
        Set<String> reverted = heldDifferences();
        mHeld.clear();
        notifyChanged(reverted);
    }

    /** Drops everything held, writing nothing: the store is as it was, apart from outside writes. */
    public void discard() {
        if (mEnded)
            return;
        Set<String> reverted = heldDifferences();
        end();
        notifyChanged(reverted);
    }

    /** What readers see for {@code key} now: the held value, or the store's from {@code stored}. */
    @Nullable
    private Object visible(String key, @Nullable Map<String, ?> stored) {
        Object held = mHeld.get(key);
        if (held != null)
            return held == REMOVED ? null : held;
        return stored == null ? null : stored.get(key);
    }

    private void end() {
        mEnded = true;
        if (sCurrent == this)
            sCurrent = null;
        SharedPreferencesPreview.clear(this);
        mHeld.clear();
    }

    @SuppressWarnings("unchecked")
    private static void put(Editor editor, String key, Object value) {
        if (value == REMOVED) editor.remove(key);
        else if (value instanceof String) editor.putString(key, (String) value);
        else if (value instanceof Integer) editor.putInt(key, (Integer) value);
        else if (value instanceof Boolean) editor.putBoolean(key, (Boolean) value);
        else if (value instanceof Float) editor.putFloat(key, (Float) value);
        else if (value instanceof Long) editor.putLong(key, (Long) value);
        else if (value instanceof Set) editor.putStringSet(key, (Set<String>) value);
    }

    // ------------------------------------------------------------------------------ reads

    /** The held value, {@link #REMOVED}, or null when the draft has not touched the key. */
    @Nullable
    private Object held(String key) {
        return mHeld.get(key);
    }

    @Override
    public Map<String, ?> getAll() {
        HashMap<String, Object> all = new HashMap<>(mStore.getAll());
        for (Map.Entry<String, Object> entry : mHeld.entrySet()) {
            if (entry.getValue() == REMOVED) all.remove(entry.getKey());
            else all.put(entry.getKey(), entry.getValue());
        }
        return all;
    }

    @Nullable
    @Override
    public String getString(String key, @Nullable String defValue) {
        Object value = held(key);
        if (value == null) return mStore.getString(key, defValue);
        return value == REMOVED ? defValue : (String) value;
    }

    @SuppressWarnings("unchecked")
    @Nullable
    @Override
    public Set<String> getStringSet(String key, @Nullable Set<String> defValues) {
        Object value = held(key);
        if (value == null) return mStore.getStringSet(key, defValues);
        return value == REMOVED ? defValues : (Set<String>) value;
    }

    @Override
    public int getInt(String key, int defValue) {
        Object value = held(key);
        if (value == null) return mStore.getInt(key, defValue);
        return value == REMOVED ? defValue : (Integer) value;
    }

    @Override
    public long getLong(String key, long defValue) {
        Object value = held(key);
        if (value == null) return mStore.getLong(key, defValue);
        return value == REMOVED ? defValue : (Long) value;
    }

    @Override
    public float getFloat(String key, float defValue) {
        Object value = held(key);
        if (value == null) return mStore.getFloat(key, defValue);
        return value == REMOVED ? defValue : (Float) value;
    }

    @Override
    public boolean getBoolean(String key, boolean defValue) {
        Object value = held(key);
        if (value == null) return mStore.getBoolean(key, defValue);
        return value == REMOVED ? defValue : (Boolean) value;
    }

    @Override
    public boolean contains(String key) {
        Object value = held(key);
        if (value == null) return mStore.contains(key);
        return value != REMOVED;
    }

    // ----------------------------------------------------------------------------- writes

    @Override
    public Editor edit() {
        return mEnded ? mStore.edit() : new DraftEditor();
    }

    /** Listeners are the store's: the draft changes nothing a listener could be told of. */
    @Override
    public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {
        listen(mStore, listener);
    }

    @Override
    public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {
        unlisten(mStore, listener);
    }

    /** Collects one editor's changes and folds them into the draft on apply or commit. */
    private final class DraftEditor implements Editor {
        private final HashMap<String, Object> mChanges = new HashMap<>();
        private boolean mClear;

        private Editor change(String key, @Nullable Object value) {
            // A null value removes the key, as it does on a real editor.
            mChanges.put(key, value == null ? REMOVED : value);
            return this;
        }

        @Override public Editor putString(String key, @Nullable String value) { return change(key, value); }
        @Override public Editor putStringSet(String key, @Nullable Set<String> values) { return change(key, values); }
        @Override public Editor putInt(String key, int value) { return change(key, value); }
        @Override public Editor putLong(String key, long value) { return change(key, value); }
        @Override public Editor putFloat(String key, float value) { return change(key, value); }
        @Override public Editor putBoolean(String key, boolean value) { return change(key, value); }
        @Override public Editor remove(String key) { return change(key, null); }

        @Override
        public Editor clear() {
            mClear = true;
            mChanges.clear();
            return this;
        }

        @Override
        public boolean commit() {
            if (mEnded) {
                // The session ended between edit() and here: the store takes it.
                Editor editor = mStore.edit();
                if (mClear)
                    editor.clear();
                for (Map.Entry<String, Object> entry : mChanges.entrySet())
                    put(editor, entry.getKey(), entry.getValue());
                editor.apply();
                return true;
            }
            Map<String, ?> stored = null;
            HashSet<String> changed = new HashSet<>();
            if (mClear) {
                // As on a real editor, a clear empties the draft's view before this editor's own
                // changes: every key the store or the draft holds is held as removed.
                stored = mStore.getAll();
                Set<String> keys = new HashSet<>(stored.keySet());
                keys.addAll(mHeld.keySet());
                for (String key : keys) {
                    if (visible(key, stored) != null)
                        changed.add(key);
                    mHeld.put(key, REMOVED);
                }
            }
            for (Map.Entry<String, Object> entry : mChanges.entrySet()) {
                String key = entry.getKey();
                if (stored == null && !mHeld.containsKey(key))
                    stored = mStore.getAll();
                Object before = visible(key, stored);
                Object after = entry.getValue() == REMOVED ? null : entry.getValue();
                if (Objects.equals(before, after))
                    changed.remove(key);
                else
                    changed.add(key);
                mHeld.put(key, entry.getValue());
            }
            mChanges.clear();
            mClear = false;
            notifyChanged(changed);
            return true;
        }

        @Override
        public void apply() {
            commit();
        }
    }
}
