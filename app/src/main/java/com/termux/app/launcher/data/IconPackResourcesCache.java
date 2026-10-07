package com.termux.app.launcher.data;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One loaded value per icon pack APK — in practice its {@code Resources} — kept for as long as
 * that APK is the one installed.
 *
 * <p>Every drawable taken from a pack used to build a fresh package context, and with it a fresh
 * {@code Resources}: a {@code ResourcesManager#getResources} on whichever thread asked, and a new
 * asset manager whenever nothing else happened to hold the last one. A dock of pinned icons from a
 * pack paid that once or several times per icon (an unmapped icon is composed from three pack
 * layers), on the main thread on a cold start. A pack is one APK; its resources are loaded once.
 *
 * <p>The entry is keyed on the APK's path as the package manager reports it on each use. An
 * update installs the pack under a new path, so the next use loads the new resources; an
 * uninstalled pack reports no path and answers null, exactly as a failed package context did, so
 * the paths that prune overrides for a removed pack still see it gone. Bounded: packs in use are
 * the global one, the pinned one and whatever single icons were picked from others.
 *
 * <p>The load runs outside the lock, so one thread building a pack's resources never holds up
 * another thread reading a different pack (or the same one, which then loads it once more).
 */
final class IconPackResourcesCache<T> {

    interface Loader<T> {
        /** The value for {@code packageName}, or null when it cannot be loaded. */
        @Nullable T load(@NonNull String packageName);
    }

    private static final class Loaded<T> {
        @NonNull final String apkPath;
        @NonNull final T value;

        Loaded(@NonNull String apkPath, @NonNull T value) {
            this.apkPath = apkPath;
            this.value = value;
        }
    }

    private final Map<String, Loaded<T>> mEntries;

    IconPackResourcesCache(int capacity) {
        mEntries = new LinkedHashMap<String, Loaded<T>>(capacity + 1, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Loaded<T>> eldest) {
                return size() > capacity;
            }
        };
    }

    /**
     * The value for {@code packageName} installed at {@code apkPath}; null when the package is not
     * installed ({@code apkPath} null) or cannot be loaded.
     */
    @Nullable
    T get(@NonNull String packageName, @Nullable String apkPath, @NonNull Loader<T> loader) {
        synchronized (mEntries) {
            if (apkPath == null) {
                mEntries.remove(packageName);
                return null;
            }
            Loaded<T> entry = mEntries.get(packageName);
            if (entry != null && entry.apkPath.equals(apkPath)) return entry.value;
        }
        T value = loader.load(packageName);
        synchronized (mEntries) {
            if (value == null) {
                mEntries.remove(packageName);
                return null;
            }
            mEntries.put(packageName, new Loaded<>(apkPath, value));
        }
        return value;
    }

    void clear() {
        synchronized (mEntries) {
            mEntries.clear();
        }
    }

    int size() {
        synchronized (mEntries) {
            return mEntries.size();
        }
    }
}
