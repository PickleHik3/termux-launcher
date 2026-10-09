package com.termux.app.launcher.data;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Remembers what a pinned icon resolved to, so a dock rebuild does not load every override and
 * pinned-pack drawable from its pack again. Bounded and least-recently-used: it holds the
 * drawables the pinned rows show anyway, a few dozen at most.
 *
 * <p>A remembered answer is only good while the artwork it was derived from is the very same
 * object ({@code baseline}, compared by identity): the pinned treatment falls back to, and
 * composes over, the global icon, so a new global icon means a new answer. Everything else the
 * answer depends on — the item, its override, the icon packs in force and their versions — goes in
 * the key, which the caller builds.
 */
public final class PinnedArtworkMemo<B, V> {

    private static final class Remembered<B, V> {
        @Nullable final B baseline;
        @NonNull final V value;

        Remembered(@Nullable B baseline, @NonNull V value) {
            this.baseline = baseline;
            this.value = value;
        }
    }

    private final Map<String, Remembered<B, V>> mEntries;

    public PinnedArtworkMemo(int capacity) {
        final int max = Math.max(1, capacity);
        mEntries = new LinkedHashMap<String, Remembered<B, V>>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Remembered<B, V>> eldest) {
                return size() > max;
            }
        };
    }

    /** The answer remembered for {@code key} over this very {@code baseline}, or null. */
    @Nullable
    public V get(@NonNull String key, @Nullable B baseline) {
        Remembered<B, V> remembered = mEntries.get(key);
        if (remembered == null) return null;
        if (remembered.baseline != baseline) {
            mEntries.remove(key);
            return null;
        }
        return remembered.value;
    }

    public void put(@NonNull String key, @Nullable B baseline, @NonNull V value) {
        mEntries.put(key, new Remembered<>(baseline, value));
    }

    public void clear() {
        mEntries.clear();
    }

    public int size() {
        return mEntries.size();
    }
}
