package com.termux.app.terminal;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * The sessions of each Workspace's place on the wall (spec overview-2026-11-24, "The model this
 * assumes"; #357). A session is one layout (a terminal window); a place holds one or more, the one
 * in front is the page on the wall and the others wait in that Workspace's row in Overview. The
 * activity keeps the wall's places as the row's window list, one window per place, the session in
 * front; this keeps the waiting ones, keyed by the window in front, so everything that walks the
 * row (the wall, the chips, the page bar, saving) reads the places unchanged.
 *
 * <p>Each session of a place carries a number, the lowest one free in its place when it joined, so
 * the status row can name the session in front ({@link #numberOf}). A place left with one session
 * drops its entry and that session is number 1 again.
 *
 * <p>Swapping keeps every session's shells running: only which window stands in the row changes.
 * Pure; main thread only.
 *
 * @param <T> the window type ({@code TerminalPaneController.Window}; anything in tests)
 */
public final class PlaceSessions<T> {

    /** One place's sessions beyond the one in front. */
    private static final class Place<T> {
        int frontNumber;
        /** The row's order: a swap puts the old front where the chosen card stood. */
        final ArrayList<T> waiting = new ArrayList<>(2);
        final ArrayList<Integer> numbers = new ArrayList<>(2);

        int freeNumber() {
            for (int n = 1; ; n++) {
                if (n != frontNumber && !numbers.contains(n)) return n;
            }
        }
    }

    /** Keyed by the window in front, by identity. */
    @NonNull private final Map<T, Place<T>> mPlaces = new IdentityHashMap<>();
    @Nullable private Runnable mOnChanged;

    /** Runs after every change of a place's sessions (a swap, an add, a removal); null for none. */
    public void setOnChanged(@Nullable Runnable onChanged) {
        mOnChanged = onChanged;
    }

    /** How many sessions the place of {@code front} holds: 1 when none wait. */
    public int count(@NonNull T front) {
        Place<T> place = mPlaces.get(front);
        return place == null ? 1 : 1 + place.waiting.size();
    }

    /** The sessions waiting in {@code front}'s place, in row order; empty for none. */
    @NonNull
    public List<T> waiting(@NonNull T front) {
        Place<T> place = mPlaces.get(front);
        return place == null ? Collections.<T>emptyList()
            : Collections.unmodifiableList(place.waiting);
    }

    /** The number of the session in front of its place: 1 when none wait. */
    public int numberOf(@NonNull T front) {
        Place<T> place = mPlaces.get(front);
        return place == null ? 1 : place.frontNumber;
    }

    /** The number of the {@code slot}th session waiting in {@code front}'s place; 0 for none. */
    public int waitingNumber(@NonNull T front, int slot) {
        Place<T> place = mPlaces.get(front);
        return place == null || slot < 0 || slot >= place.numbers.size() ? 0
            : place.numbers.get(slot);
    }

    /** The window in front of the place {@code session} waits in; null when it waits nowhere. */
    @Nullable
    public T frontOf(@NonNull T session) {
        for (Map.Entry<T, Place<T>> e : mPlaces.entrySet()) {
            if (indexOf(e.getValue().waiting, session) >= 0) return e.getKey();
        }
        return null;
    }

    public boolean isWaiting(@NonNull T session) {
        return frontOf(session) != null;
    }

    /** Whether any place holds more than one session. */
    public boolean isEmpty() {
        return mPlaces.isEmpty();
    }

    /** Every waiting session, in no particular order of places. */
    public void allWaiting(@NonNull List<T> out) {
        for (Place<T> place : mPlaces.values()) out.addAll(place.waiting);
    }

    /**
     * {@code added} joins {@code front}'s place and stands in front of it; {@code front} waits at
     * the row's start. The caller puts {@code added} in the row where {@code front} stood.
     */
    public void addInFront(@NonNull T front, @NonNull T added) {
        Place<T> place = mPlaces.remove(front);
        if (place == null) {
            place = new Place<>();
            place.frontNumber = 1;
        }
        int number = place.freeNumber();
        place.waiting.add(0, front);
        place.numbers.add(0, place.frontNumber);
        place.frontNumber = number;
        mPlaces.put(added, place);
        changed();
    }

    /** {@code added} joins {@code front}'s place waiting, at the row's end. */
    public void addWaiting(@NonNull T front, @NonNull T added, int number) {
        Place<T> place = mPlaces.get(front);
        if (place == null) {
            place = new Place<>();
            place.frontNumber = 1;
            mPlaces.put(front, place);
        }
        place.waiting.add(added);
        place.numbers.add(number > 0 && number != place.frontNumber
            && !place.numbers.contains(number) ? number : place.freeNumber());
        changed();
    }

    /** As {@link #addWaiting(Object, Object, int)} with the lowest free number. */
    public void addWaiting(@NonNull T front, @NonNull T added) {
        addWaiting(front, added, 0);
    }

    /**
     * Waiting {@code session} comes to the front of its place; the session that was in front takes
     * its card in the row. Returns that old front, which the caller replaces with {@code session}
     * in the row; null when {@code session} waits nowhere.
     */
    @Nullable
    public T bringToFront(@NonNull T session) {
        T front = frontOf(session);
        if (front == null) return null;
        Place<T> place = mPlaces.remove(front);
        int slot = indexOf(place.waiting, session);
        int number = place.numbers.get(slot);
        place.waiting.set(slot, front);
        place.numbers.set(slot, place.frontNumber);
        place.frontNumber = number;
        mPlaces.put(session, place);
        changed();
        return front;
    }

    /**
     * {@code front} leaves the row (closed, its last shell gone): the first session waiting in its
     * place comes in front, and the caller puts it in the row where {@code front} stood. Null when
     * none wait; the place goes with {@code front}.
     */
    @Nullable
    public T promote(@NonNull T front) {
        Place<T> place = mPlaces.remove(front);
        if (place == null) return null;
        T next = place.waiting.remove(0);
        place.frontNumber = place.numbers.remove(0);
        if (!place.waiting.isEmpty()) mPlaces.put(next, place);
        changed();
        return next;
    }

    /** Waiting {@code session} leaves its place (closed); false when it waits nowhere. */
    public boolean removeWaiting(@NonNull T session) {
        T front = frontOf(session);
        if (front == null) return false;
        Place<T> place = mPlaces.get(front);
        int slot = indexOf(place.waiting, session);
        place.waiting.remove(slot);
        place.numbers.remove(slot);
        if (place.waiting.isEmpty()) mPlaces.remove(front);
        changed();
        return true;
    }

    /**
     * Keeps every waiting session on the wall: a place whose window in front is no longer in
     * {@code row} (taken out by a path that did not {@link #promote}) puts its first waiting
     * session at the row's end, its others still waiting behind it. Returns how many came onto
     * the row.
     */
    public int heal(@NonNull List<T> row) {
        if (mPlaces.isEmpty()) return 0;
        List<T> gone = null;
        for (T front : mPlaces.keySet()) {
            if (indexOf(row, front) < 0) {
                if (gone == null) gone = new ArrayList<>(1);
                gone.add(front);
            }
        }
        if (gone == null) return 0;
        for (T front : gone) {
            T next = promote(front);
            if (next != null) row.add(next);
        }
        return gone.size();
    }

    /** Forgets every place: the row is being rebuilt from nothing. */
    public void clear() {
        if (mPlaces.isEmpty()) return;
        mPlaces.clear();
        changed();
    }

    private void changed() {
        if (mOnChanged != null) mOnChanged.run();
    }

    private static <T> int indexOf(@NonNull List<T> list, @NonNull T item) {
        for (int i = 0; i < list.size(); i++) if (list.get(i) == item) return i;
        return -1;
    }

    // ---- Upgrade: today's sessions onto the one wall ------------------------------------------

    /** The one wall that several of today's sessions fold into. */
    public static final class Folded<T> {
        /** Every window of every session, sessions in order, each session's windows in order. */
        @NonNull public final List<T> windows;
        /** The current session's current window's place; -1 when there is no window. */
        public final int current;

        Folded(@NonNull List<T> windows, int current) {
            this.windows = windows;
            this.current = current;
        }
    }

    /**
     * The upgrade (#357): every window of every session becomes a Workspace page on the one wall,
     * so nothing is hidden. {@code sessions} are today's sessions' windows, {@code currents} each
     * session's current window, {@code currentSession} the session on screen (-1 for none, the
     * first session's then).
     */
    @NonNull
    public static <T> Folded<T> fold(@NonNull List<? extends List<T>> sessions,
                                     @NonNull int[] currents, int currentSession) {
        List<T> windows = new ArrayList<>();
        int current = -1;
        int fallback = -1;
        for (int s = 0; s < sessions.size(); s++) {
            List<T> own = sessions.get(s);
            if (own.isEmpty()) continue;
            int at = s < currents.length
                ? Math.max(0, Math.min(currents[s], own.size() - 1)) : 0;
            if (s == currentSession) current = windows.size() + at;
            if (fallback < 0) fallback = windows.size() + at;
            windows.addAll(own);
        }
        return new Folded<>(windows, current >= 0 ? current : fallback);
    }
}
