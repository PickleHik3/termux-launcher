package com.termux.shared.termux.settings.registry;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.HashMap;
import java.util.Map;

/** A {@link SettingStore} held in a map, for tests. */
public final class InMemorySettingStore implements SettingStore {

    private final Map<String, Object> mValues = new HashMap<>();

    @Nullable
    @Override
    public synchronized String getString(@NonNull String key) {
        Object value = mValues.get(key);
        return value instanceof String ? (String) value : null;
    }

    @Override
    public synchronized int getInt(@NonNull String key, int defaultValue) {
        Object value = mValues.get(key);
        return value instanceof Integer ? (Integer) value : defaultValue;
    }

    @Override
    public synchronized void putString(@NonNull String key, @NonNull String value) {
        mValues.put(key, value);
    }

    @Override
    public synchronized void putInt(@NonNull String key, int value) {
        mValues.put(key, value);
    }
}
