package com.termux.app.chrome;

import androidx.annotation.NonNull;

import java.util.Collections;
import java.util.List;

/**
 * The window row's chips on a Display page (#192): one per app open on the display, in the
 * display's order, the front app selected; an empty display has none. A tap raises the app, the
 * close action closes it. {@link WindowChips} stays the terminal pages' chips; the display window
 * itself is never a chip.
 */
public final class DisplayAppChips {
    private final List<Integer> mWindowIds;
    private final int mFront;

    /**
     * @param windowIds the display's app window ids, in order
     * @param front the front app's index into {@code windowIds}, or -1 for none (already resolved,
     *              as DisplayWindowName.frontIndex does)
     */
    public DisplayAppChips(@NonNull List<Integer> windowIds, int front) {
        mWindowIds = Collections.unmodifiableList(windowIds);
        mFront = front >= 0 && front < windowIds.size() ? front : -1;
    }

    public int count() {
        return mWindowIds.size();
    }

    /** The selected chip: the front app's, -1 on an empty display. */
    public int selected() {
        return mFront;
    }

    /** The window id a tap on chip {@code index} raises, or -1 for no such chip. */
    public int tapWindowId(int index) {
        return idAt(index);
    }

    /** The window id the close action on chip {@code index} closes, or -1 for no such chip. */
    public int closeWindowId(int index) {
        return idAt(index);
    }

    private int idAt(int index) {
        return index >= 0 && index < mWindowIds.size() ? mWindowIds.get(index) : -1;
    }
}
