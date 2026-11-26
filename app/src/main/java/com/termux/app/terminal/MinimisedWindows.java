package com.termux.app.terminal;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The Terminal windows taken out of the row by Minimise: still open, their shells still running,
 * waiting in Overview as cards. Each keeps the place it stood at among the row's terminal
 * windows, so bringing it back puts it there again (held to the row's ends if the row has
 * shrunk since). Pure: the activity holds one and saves it with the row.
 *
 * @param <T> the window
 */
public final class MinimisedWindows<T> {

    /** One minimised window and the terminal index it stood at. */
    public static final class Item<T> {
        @NonNull public final T window;
        public final int index;

        public Item(@NonNull T window, int index) {
            this.window = window;
            this.index = index;
        }
    }

    @NonNull private final List<Item<T>> mItems = new ArrayList<>();

    /** {@code window} leaves the row from terminal index {@code index}; it goes last in Overview. */
    public void add(@NonNull T window, int index) {
        mItems.add(new Item<>(window, Math.max(0, index)));
    }

    /** Takes minimised window {@code i} out to bring it back; null when there is none. */
    public Item<T> remove(int i) {
        return i < 0 || i >= mItems.size() ? null : mItems.remove(i);
    }

    /** Forgets {@code window} (it closed while minimised); whether it was held. */
    public boolean forget(@NonNull T window) {
        for (int i = 0; i < mItems.size(); i++) {
            if (mItems.get(i).window == window) {
                mItems.remove(i);
                return true;
            }
        }
        return false;
    }

    public boolean contains(@NonNull T window) {
        for (Item<T> item : mItems) if (item.window == window) return true;
        return false;
    }

    public int size() {
        return mItems.size();
    }

    @NonNull
    public Item<T> get(int i) {
        return mItems.get(i);
    }

    @NonNull
    public List<Item<T>> items() {
        return Collections.unmodifiableList(mItems);
    }

    public void clear() {
        mItems.clear();
    }

    /** Where a window that stood at {@code index} goes back in a row of {@code count} terminal windows. */
    public static int restoreIndex(int index, int count) {
        return Math.max(0, Math.min(index, Math.max(0, count)));
    }
}
