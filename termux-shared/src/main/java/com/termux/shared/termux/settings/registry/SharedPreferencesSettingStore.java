package com.termux.shared.termux.settings.registry;

import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * The app's shared preferences. A read of a key holding another type answers the default, and
 * with no preferences every read is the default and every write is dropped.
 */
public final class SharedPreferencesSettingStore implements SettingStore {
    @Nullable private final SharedPreferences mPrefs;

    public SharedPreferencesSettingStore(@Nullable SharedPreferences prefs) {
        mPrefs = prefs;
    }

    @Nullable @Override public String getString(@NonNull String key) {
        if (mPrefs == null) return null;
        try { return mPrefs.getString(key, null); } catch (ClassCastException e) { return null; }
    }

    @Override public int getInt(@NonNull String key, int defaultValue) {
        if (mPrefs == null) return defaultValue;
        try { return mPrefs.getInt(key, defaultValue); } catch (ClassCastException e) { return defaultValue; }
    }

    @Override public boolean getBoolean(@NonNull String key, boolean defaultValue) {
        if (mPrefs == null) return defaultValue;
        try { return mPrefs.getBoolean(key, defaultValue); } catch (ClassCastException e) { return defaultValue; }
    }

    @Override public void putBoolean(@NonNull String key, boolean value) {
        if (mPrefs != null) mPrefs.edit().putBoolean(key, value).apply();
    }

    @Override public void putString(@NonNull String key, @NonNull String value) {
        if (mPrefs != null) mPrefs.edit().putString(key, value).apply();
    }

    @Override public void putInt(@NonNull String key, int value) {
        if (mPrefs != null) mPrefs.edit().putInt(key, value).apply();
    }
}

