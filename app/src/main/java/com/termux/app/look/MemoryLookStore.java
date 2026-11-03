package com.termux.app.look;

import androidx.annotation.NonNull;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/** A {@link LookStore} over a map: tests, and the upgrade's dry run. Notifies on every change. */
public final class MemoryLookStore implements LookStore {

    private final Map<String, Object> values = new HashMap<>();
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();

    public MemoryLookStore() { }

    /** A store holding what this one holds now, with no listeners. */
    @NonNull
    public MemoryLookStore copy() {
        MemoryLookStore copy = new MemoryLookStore();
        synchronized (this) {
            copy.values.putAll(values);
        }
        return copy;
    }

    @Override
    public synchronized int getInt(@NonNull String key, int defaultValue) {
        Object value = values.get(key);
        return value instanceof Integer ? (Integer) value : defaultValue;
    }

    @Override
    public synchronized boolean getBoolean(@NonNull String key, boolean defaultValue) {
        Object value = values.get(key);
        return value instanceof Boolean ? (Boolean) value : defaultValue;
    }

    @NonNull
    @Override
    public synchronized String getString(@NonNull String key, @NonNull String defaultValue) {
        Object value = values.get(key);
        return value instanceof String ? (String) value : defaultValue;
    }

    @Override
    public synchronized boolean has(@NonNull String key) {
        return values.containsKey(key);
    }

    @Override
    public void putInt(@NonNull String key, int value) { put(key, value); }

    @Override
    public void putBoolean(@NonNull String key, boolean value) { put(key, value); }

    @Override
    public void putString(@NonNull String key, @NonNull String value) { put(key, value); }

    private void put(String key, Object value) {
        synchronized (this) {
            values.put(key, value);
        }
        notifyKey(key);
    }

    @Override
    public void remove(@NonNull String key) {
        boolean had;
        synchronized (this) {
            had = values.remove(key) != null;
        }
        if (had) notifyKey(key);
    }

    @NonNull
    @Override
    public synchronized Set<String> keys() {
        return new LinkedHashSet<>(values.keySet());
    }

    @Override
    public void addListener(@NonNull Listener listener) { listeners.add(listener); }

    @Override
    public void removeListener(@NonNull Listener listener) { listeners.remove(listener); }

    private void notifyKey(String key) {
        for (Listener listener : listeners) listener.onKeyChanged(key);
    }
}
