package com.termux.app.wayland;

import java.util.ArrayList;
import java.util.List;

/**
 * The Linux windows screen's tabs: the windows in the order they opened, and the one shown. A new
 * window is shown at once (it is the app the user just opened); closing the shown one shows its
 * neighbour, the next if there is one, else the previous. Pure, so the rules are tested.
 */
final class LinuxWindowTabs {

    /** No window: the id the host never gives a window. */
    static final long NONE = 0L;

    private final List<Long> ids = new ArrayList<>();
    private long shown = NONE;

    int size() {
        return ids.size();
    }

    long idAt(int index) {
        return ids.get(index);
    }

    boolean contains(long id) {
        return ids.contains(id);
    }

    /** The window shown, or {@link #NONE}. */
    long shown() {
        return shown;
    }

    /** A window opened: it joins the end and is shown. False when it was already there. */
    boolean add(long id) {
        if (id == NONE || ids.contains(id)) return false;
        ids.add(id);
        shown = id;
        return true;
    }

    /** A window closed. False when it was not there. */
    boolean remove(long id) {
        int index = ids.indexOf(id);
        if (index < 0) return false;
        ids.remove(index);
        if (shown == id) {
            if (ids.isEmpty()) shown = NONE;
            else shown = ids.get(Math.min(index, ids.size() - 1));
        }
        return true;
    }

    /** Shows {@code id}. False when it is not a tab or is already shown. */
    boolean show(long id) {
        if (shown == id || !ids.contains(id)) return false;
        shown = id;
        return true;
    }

    void clear() {
        ids.clear();
        shown = NONE;
    }
}
