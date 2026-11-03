package com.termux.app.look;

import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/** A {@link LookStore} over the main SharedPreferences. Writes are applied asynchronously. */
public final class PrefsLookStore implements LookStore {

    private final SharedPreferences prefs;
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();
    /** Held strongly: SharedPreferences keeps its listeners weakly. */
    private final SharedPreferences.OnSharedPreferenceChangeListener prefsListener =
        (sp, key) -> {
            for (Listener listener : listeners) listener.onKeyChanged(key);
        };

    public PrefsLookStore(@NonNull SharedPreferences prefs) {
        this.prefs = prefs;
        prefs.registerOnSharedPreferenceChangeListener(prefsListener);
    }

    @Override
    public int getInt(@NonNull String key, int defaultValue) {
        try {
            return prefs.getInt(key, defaultValue);
        } catch (ClassCastException e) {
            return defaultValue;
        }
    }

    @Override
    public boolean getBoolean(@NonNull String key, boolean defaultValue) {
        try {
            return prefs.getBoolean(key, defaultValue);
        } catch (ClassCastException e) {
            return defaultValue;
        }
    }

    @NonNull
    @Override
    public String getString(@NonNull String key, @NonNull String defaultValue) {
        try {
            String value = prefs.getString(key, defaultValue);
            return value == null ? defaultValue : value;
        } catch (ClassCastException e) {
            return defaultValue;
        }
    }

    @Override
    public boolean has(@NonNull String key) { return prefs.contains(key); }

    @Override
    public void putInt(@NonNull String key, int value) { prefs.edit().putInt(key, value).apply(); }

    @Override
    public void putBoolean(@NonNull String key, boolean value) {
        prefs.edit().putBoolean(key, value).apply();
    }

    @Override
    public void putString(@NonNull String key, @NonNull String value) {
        prefs.edit().putString(key, value).apply();
    }

    @Override
    public void remove(@NonNull String key) { prefs.edit().remove(key).apply(); }

    @NonNull
    @Override
    public Set<String> keys() { return new HashSet<>(prefs.getAll().keySet()); }

    @Override
    public void addListener(@NonNull Listener listener) { listeners.add(listener); }

    @Override
    public void removeListener(@NonNull Listener listener) { listeners.remove(listener); }
}
