package com.termux.app.chrome;

import android.util.SparseArray;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Each look's dock frost as its own rest last wore it, measured over that rest's own geometry
 * (the unified dock and keyboard glass with the keyboard up is far taller than Home's dock row),
 * for a crossing toward that page to blend into. An entry stands while the page's ground, the
 * wallpaper's frames ({@link ChromeInk#framesGeneration}, which a page change does not move) and
 * the dress it was measured in are the same. Main thread only; a pass that keeps the filter
 * already kept writes nothing.
 *
 * @param <F> the filter put up
 * @param <M> its matrix, what a blend runs on
 */
public final class DockFilterKeep<F, M> {

    private static final class Entry<F, M> {
        F filter;
        M matrix;
        int ground;
        int frames;
        int dress;
    }

    @NonNull private final SparseArray<Entry<F, M>> mEntries = new SparseArray<>();

    /** Whether {@code look}'s entry already keeps {@code filter}: the keep then costs nothing. */
    public boolean holds(int look, @Nullable F filter) {
        Entry<F, M> entry = mEntries.get(look);
        return entry != null && entry.filter == filter;
    }

    /** Keeps {@code filter} ({@code matrix}) as {@code look}'s rest, on what it was measured over. */
    public void keep(int look, @NonNull F filter, @NonNull M matrix, int ground, int frames,
                     int dress) {
        Entry<F, M> entry = mEntries.get(look);
        if (entry == null) {
            entry = new Entry<>();
            mEntries.put(look, entry);
        }
        entry.filter = filter;
        entry.matrix = matrix;
        entry.ground = ground;
        entry.frames = frames;
        entry.dress = dress;
    }

    /** Refreshes what {@code look}'s kept filter stands on, when the same filter was measured again. */
    public void restamp(int look, int ground, int frames, int dress) {
        Entry<F, M> entry = mEntries.get(look);
        if (entry == null) return;
        entry.ground = ground;
        entry.frames = frames;
        entry.dress = dress;
    }

    /** {@code look}'s kept filter, when it stood on the same ground, frames and dress; else null. */
    @Nullable
    public F filter(int look, int ground, int frames, int dress) {
        Entry<F, M> entry = valid(look, ground, frames, dress);
        return entry == null ? null : entry.filter;
    }

    /** {@link #filter}'s matrix. */
    @Nullable
    public M matrix(int look, int ground, int frames, int dress) {
        Entry<F, M> entry = valid(look, ground, frames, dress);
        return entry == null ? null : entry.matrix;
    }

    @Nullable
    private Entry<F, M> valid(int look, int ground, int frames, int dress) {
        Entry<F, M> entry = mEntries.get(look);
        if (entry == null || entry.ground != ground || entry.frames != frames
            || entry.dress != dress) return null;
        return entry;
    }
}
