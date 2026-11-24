package com.termux.app.terminal;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * A session's row as the Terminal page walks it (spec #384, "The window contract"; #392): its
 * terminal windows, in the session's own list, with its Linux windows standing between them, so
 * {@code [t1] [Firefox] [t2]} is one row of three windows. Pure; the activity keeps the terminal
 * windows in {@code WSession.windows} as before and asks this for every place in the row (the
 * wall's inner stops, the window chips, the page bar, {@code window.next}, launcherctl).
 *
 * <p>A Linux window stands after a terminal window (its anchor), or at the row's start; several
 * after the same terminal window keep their order. The terminal list is never touched except by
 * {@link #move}, so saving a session saves only its terminal windows, in their order, and a
 * restore never shifts them. A terminal window leaving the list hands its Linux windows to the
 * terminal window before it, as a list entry would close up. Main thread only.
 *
 * @param <T> the terminal window type ({@code TerminalPaneController.Window}; anything in tests)
 */
public final class SessionRow<T> {

    /** No Linux window: a terminal window is in front. */
    public static final long NONE = 0L;

    /** Where a Linux window stood, kept while it is minimised: the window before it. */
    public static final class Place<T> {
        /** The terminal window it stood after; null at the row's start. */
        @Nullable public final T after;
        /** The Linux window directly before it after the same terminal window; {@link #NONE} for none. */
        public final long afterLinux;

        Place(@Nullable T after, long afterLinux) {
            this.after = after;
            this.afterLinux = afterLinux;
        }
    }

    private static final class Entry<T> {
        final long id;
        @Nullable T after;

        Entry(long id, @Nullable T after) {
            this.id = id;
            this.after = after;
        }
    }

    /** The Linux windows in row order: by anchor's place, then list order within one anchor. */
    private final ArrayList<Entry<T>> mLinux = new ArrayList<>();
    /** The terminal order last seen, to hand a leaving terminal window's Linux windows on. */
    private final ArrayList<T> mLastOrder = new ArrayList<>();
    private long mFront = NONE;

    /** Whether the row holds any Linux window. */
    public boolean hasLinux() {
        return !mLinux.isEmpty();
    }

    public boolean contains(long id) {
        return find(id) >= 0;
    }

    /** The Linux window in front, or {@link #NONE} when a terminal window is. */
    public long front() {
        return mFront;
    }

    /** How many windows the row holds, of both kinds. */
    public int count(@NonNull List<T> terminals) {
        heal(terminals);
        return terminals.size() + mLinux.size();
    }

    /** The terminal window index at row place {@code i}; -1 when a Linux window stands there or out of range. */
    public int terminalAt(@NonNull List<T> terminals, int i) {
        heal(terminals);
        int place = 0;
        int linuxHead = 0;
        // Linux windows at the row's start.
        for (; linuxHead < mLinux.size() && mLinux.get(linuxHead).after == null; linuxHead++) {
            if (place++ == i) return -1;
        }
        for (int t = 0; t < terminals.size(); t++) {
            if (place++ == i) return t;
            T term = terminals.get(t);
            for (int k = 0; k < mLinux.size(); k++) {
                if (mLinux.get(k).after != term) continue;
                if (place++ == i) return -1;
            }
        }
        return -1;
    }

    /** The Linux window at row place {@code i}; {@link #NONE} when a terminal window stands there. */
    public long linuxAt(@NonNull List<T> terminals, int i) {
        heal(terminals);
        int place = 0;
        for (int k = 0; k < mLinux.size(); k++) {
            if (mLinux.get(k).after != null) continue;
            if (place++ == i) return mLinux.get(k).id;
        }
        for (int t = 0; t < terminals.size(); t++) {
            if (place++ == i) return NONE;
            T term = terminals.get(t);
            for (int k = 0; k < mLinux.size(); k++) {
                if (mLinux.get(k).after != term) continue;
                if (place++ == i) return mLinux.get(k).id;
            }
        }
        return NONE;
    }

    /** The row place of terminal window {@code terminalIndex}; -1 when out of range. */
    public int indexOfTerminal(@NonNull List<T> terminals, int terminalIndex) {
        if (terminalIndex < 0 || terminalIndex >= terminals.size()) return -1;
        heal(terminals);
        int place = 0;
        for (int k = 0; k < mLinux.size(); k++) if (mLinux.get(k).after == null) place++;
        for (int t = 0; t < terminalIndex; t++) {
            place++;
            T term = terminals.get(t);
            for (int k = 0; k < mLinux.size(); k++) if (mLinux.get(k).after == term) place++;
        }
        return place;
    }

    /** The row place of Linux window {@code id}; -1 when the row does not hold it. */
    public int indexOfLinux(@NonNull List<T> terminals, long id) {
        heal(terminals);
        int count = count(terminals);
        for (int i = 0; i < count; i++) if (linuxAt(terminals, i) == id) return i;
        return -1;
    }

    /**
     * The row place in front: the Linux window in front, else terminal window
     * {@code terminalCurrent} (clamped); -1 for an empty row.
     */
    public int frontIndex(@NonNull List<T> terminals, int terminalCurrent) {
        heal(terminals);
        if (mFront != NONE) {
            int at = indexOfLinux(terminals, mFront);
            if (at >= 0) return at;
            mFront = NONE;
        }
        if (terminals.isEmpty()) return mLinux.isEmpty() ? -1 : 0;
        return indexOfTerminal(terminals,
            Math.max(0, Math.min(terminalCurrent, terminals.size() - 1)));
    }

    /**
     * Brings row place {@code i} in front: a Linux window becomes {@link #front()} and -1 is
     * returned; a terminal window clears it and its terminal index is returned for the caller to
     * show. Out of range: nothing changes and -2 is returned.
     */
    public int select(@NonNull List<T> terminals, int i) {
        if (i < 0 || i >= count(terminals)) return -2;
        long linux = linuxAt(terminals, i);
        if (linux != NONE) {
            mFront = linux;
            return -1;
        }
        mFront = NONE;
        return terminalAt(terminals, i);
    }

    /** Brings Linux window {@code id} in front; false when the row does not hold it. */
    public boolean selectLinux(long id) {
        if (find(id) < 0) return false;
        mFront = id;
        return true;
    }

    /** A terminal window is in front again (a chip, a step, a shell opening). */
    public void clearFront() {
        mFront = NONE;
    }

    /**
     * Adds Linux window {@code id} right after the window in front: after the Linux window in
     * front, else right after terminal window {@code terminalCurrent} (before any Linux window
     * already after it), else at the row's start. It does not come in front by itself.
     */
    public void add(@NonNull List<T> terminals, int terminalCurrent, long id) {
        if (id == NONE || find(id) >= 0) return;
        heal(terminals);
        int front = mFront == NONE ? -1 : find(mFront);
        if (front >= 0) {
            mLinux.add(front + 1, new Entry<>(id, mLinux.get(front).after));
            return;
        }
        T after = terminals.isEmpty() ? null
            : terminals.get(Math.max(0, Math.min(terminalCurrent, terminals.size() - 1)));
        mLinux.add(firstAfter(after), new Entry<>(id, after));
    }

    /** Where Linux window {@code id} stands, for putting it back there; null when the row does not hold it. */
    @Nullable
    public Place<T> placeOf(@NonNull List<T> terminals, long id) {
        heal(terminals);
        int at = find(id);
        if (at < 0) return null;
        Entry<T> e = mLinux.get(at);
        long before = at > 0 && mLinux.get(at - 1).after == e.after ? mLinux.get(at - 1).id : NONE;
        return new Place<>(e.after, before);
    }

    /**
     * Puts Linux window {@code id} back at {@code place} (a minimised window restored): after the
     * Linux window it followed while that is still in the row, else first after the terminal
     * window it followed while that is still in the row, else where {@link #add} puts a new one.
     */
    public void restore(@NonNull List<T> terminals, int terminalCurrent, long id, @Nullable Place<T> place) {
        if (id == NONE || find(id) >= 0) return;
        heal(terminals);
        if (place != null) {
            int before = place.afterLinux == NONE ? -1 : find(place.afterLinux);
            if (before >= 0) {
                mLinux.add(before + 1, new Entry<>(id, mLinux.get(before).after));
                return;
            }
            if (place.after != null && terminals.contains(place.after)) {
                mLinux.add(firstAfter(place.after), new Entry<>(id, place.after));
                return;
            }
        }
        add(terminals, terminalCurrent, id);
    }

    /**
     * Takes Linux window {@code id} out of the row. Returns the row place in front afterwards,
     * as a terminal window's close settles it ({@link WindowCloseFocus#afterRemoval}): the same
     * window when another closed, its neighbour when the one in front closed; -1 for an empty
     * row, {@link Integer#MIN_VALUE} when the row did not hold it. When the place in front is
     * a terminal window, {@link #front()} is {@link #NONE} and the caller shows it.
     */
    public int remove(@NonNull List<T> terminals, int terminalCurrent, long id) {
        heal(terminals);
        int removed = indexOfLinux(terminals, id);
        if (removed < 0) return Integer.MIN_VALUE;
        int front = frontIndex(terminals, terminalCurrent);
        mLinux.remove(find(id));
        if (mFront == id) mFront = NONE;
        int next = WindowCloseFocus.afterRemoval(front, removed, count(terminals));
        if (next >= 0) {
            long linux = linuxAt(terminals, next);
            mFront = linux;
        }
        return next;
    }

    /** Every Linux window leaves (the host stopped or died). */
    public void clearLinux() {
        mLinux.clear();
        mFront = NONE;
    }

    /** The Linux windows in row order, into {@code out} (cleared first). */
    public void linuxIds(@NonNull List<T> terminals, @NonNull List<Long> out) {
        out.clear();
        int count = count(terminals);
        for (int i = 0; i < count; i++) {
            long id = linuxAt(terminals, i);
            if (id != NONE) out.add(id);
        }
    }

    /**
     * Moves the window at row place {@code from} to row place {@code to}, either kind among the
     * other, as the chip menu's reorder and {@code window.move} do. {@code terminals} is reordered
     * in place; each Linux window then stands after the terminal window before it. False when
     * either place is out of range or they are the same.
     */
    public boolean move(@NonNull List<T> terminals, int from, int to) {
        int count = count(terminals);
        if (from < 0 || from >= count || to < 0 || to >= count || from == to) return false;
        ArrayList<Object> keys = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            long linux = linuxAt(terminals, i);
            keys.add(linux != NONE ? (Object) Long.valueOf(linux) : terminals.get(terminalAt(terminals, i)));
        }
        keys.add(to, keys.remove(from));
        terminals.clear();
        mLinux.clear();
        T after = null;
        for (Object key : keys) {
            if (key instanceof Long) {
                mLinux.add(new Entry<>((Long) key, after));
            } else {
                @SuppressWarnings("unchecked") T term = (T) key;
                terminals.add(term);
                after = term;
            }
        }
        sortByAnchor(terminals);
        remember(terminals);
        return true;
    }

    // ---- internals --------------------------------------------------------------------------

    private int find(long id) {
        for (int k = 0; k < mLinux.size(); k++) if (mLinux.get(k).id == id) return k;
        return -1;
    }

    /** Where a new entry goes first after {@code after}: before the first one already there. */
    private int firstAfter(@Nullable T after) {
        for (int k = 0; k < mLinux.size(); k++) if (mLinux.get(k).after == after) return k;
        // None yet: the list keeps entries in row order, so it goes before the first entry of a
        // later anchor. Row order of anchors is read from the last order seen.
        int anchorAt = after == null ? -1 : mLastOrder.indexOf(after);
        for (int k = 0; k < mLinux.size(); k++) {
            T other = mLinux.get(k).after;
            int otherAt = other == null ? -1 : mLastOrder.indexOf(other);
            if (otherAt > anchorAt) return k;
        }
        return mLinux.size();
    }

    /**
     * Hands Linux windows whose terminal window left the list to the terminal window that stood
     * before it, then remembers the order. Allocation-free when nothing changed.
     */
    private void heal(@NonNull List<T> terminals) {
        if (sameOrder(terminals)) return;
        for (int k = 0; k < mLinux.size(); k++) {
            Entry<T> e = mLinux.get(k);
            if (e.after == null || terminals.contains(e.after)) continue;
            int at = mLastOrder.indexOf(e.after);
            T heir = null;
            for (int j = at - 1; j >= 0; j--) {
                T before = mLastOrder.get(j);
                if (terminals.contains(before)) {
                    heir = before;
                    break;
                }
            }
            e.after = heir;
        }
        sortByAnchor(terminals);
        remember(terminals);
    }

    /** Keeps the Linux list in row order (stable within one anchor). */
    private void sortByAnchor(@NonNull List<T> terminals) {
        // Insertion sort: stable, tiny lists.
        for (int i = 1; i < mLinux.size(); i++) {
            Entry<T> e = mLinux.get(i);
            int rank = rank(terminals, e.after);
            int j = i - 1;
            while (j >= 0 && rank(terminals, mLinux.get(j).after) > rank) {
                mLinux.set(j + 1, mLinux.get(j));
                j--;
            }
            mLinux.set(j + 1, e);
        }
    }

    private int rank(@NonNull List<T> terminals, @Nullable T after) {
        return after == null ? -1 : terminals.indexOf(after);
    }

    private boolean sameOrder(@NonNull List<T> terminals) {
        if (terminals.size() != mLastOrder.size()) return false;
        for (int i = 0; i < terminals.size(); i++) if (terminals.get(i) != mLastOrder.get(i)) return false;
        return true;
    }

    private void remember(@NonNull List<T> terminals) {
        mLastOrder.clear();
        mLastOrder.addAll(terminals);
    }
}
