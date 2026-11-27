package com.termux.app.editmode;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.look.TerlLookStore;
import com.termux.app.look.TerlProperty;
import com.termux.app.look.TerlSurface;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * One visit to edit mode: every change applies to the look store at once, and this keeps the
 * history of the visit (spec "Edit mode and Settings", History). One slider drag is one Undo step,
 * from where the finger landed to where it lifted; Follow Global and Revert are one step each, so
 * Undo right after Revert brings the session back. The history ends with the session.
 *
 * <p>The store stays the truth (ADR 0041). A row the session wrote that now holds something else
 * was written from outside (launcherctl, terl.conf, the Assistant) and is the outside's: Undo and
 * Revert skip it, it leaves {@link #changeCount()}, and it is counted in
 * {@link #keptElsewhereCount()} until the session changes it again.
 *
 * <p>Pure: no views, no Android. Used from the main thread.
 */
public final class EditSession {

    /** What a row holds: an own value, or nothing (it follows Global). */
    private static final class State {
        static final State FOLLOW = new State(false, 0);

        final boolean own;
        final int value;

        private State(boolean own, int value) {
            this.own = own;
            this.value = value;
        }

        static State own(int value) {
            return new State(true, value);
        }

        @Override
        public boolean equals(@Nullable Object other) {
            if (!(other instanceof State)) return false;
            State that = (State) other;
            return own == that.own && (!own || value == that.value);
        }

        @Override
        public int hashCode() {
            return own ? 31 + value : 0;
        }
    }

    /** One surface's one property: a row of a card. */
    private static final class Row {
        final TerlSurface surface;
        final TerlProperty property;

        Row(TerlSurface surface, TerlProperty property) {
            this.surface = surface;
            this.property = property;
        }

        @Override
        public boolean equals(@Nullable Object other) {
            if (!(other instanceof Row)) return false;
            Row that = (Row) other;
            return surface == that.surface && property == that.property;
        }

        @Override
        public int hashCode() {
            return Objects.hash(surface, property);
        }
    }

    /** One Undo step: each row it moved, from (index 0) and to (index 1). */
    private static final class Step {
        final Map<Row, State[]> rows = new LinkedHashMap<>();
    }

    @NonNull private final TerlLookStore mStore;
    /** What each row the session touched held when the session (or the outside) last owned it. */
    private final Map<Row, State> mBaseline = new HashMap<>();
    /** What the session last wrote to each row it still owns. */
    private final Map<Row, State> mLastWritten = new HashMap<>();
    /** Rows the session changed that were written from outside since. */
    private final Set<Row> mKeptElsewhere = new HashSet<>();
    private final Deque<Step> mUndo = new ArrayDeque<>();
    /** Drags in progress: where each started. */
    private final Map<Row, State> mDragStart = new HashMap<>();
    private boolean mEnded;

    private EditSession(@NonNull TerlLookStore store) {
        mStore = store;
    }

    /** Opens a session over {@code store}: nothing changed, nothing to undo. */
    @NonNull
    public static EditSession begin(@NonNull TerlLookStore store) {
        return new EditSession(store);
    }

    /** A finger landed on the row's slider: the start of one Undo step. */
    public void dragStarted(@NonNull TerlSurface surface, @NonNull TerlProperty property) {
        if (!editable(surface)) return;
        reconcile();
        Row row = new Row(surface, property);
        mDragStart.put(row, read(row));
    }

    /**
     * Gives the row an own value, at once. Inside a drag it moves the drag along; on its own it is
     * a step of its own.
     */
    public void set(@NonNull TerlSurface surface, @NonNull TerlProperty property, int value) {
        if (!editable(surface)) return;
        Row row = new Row(surface, property);
        boolean alone = !mDragStart.containsKey(row);
        if (alone) dragStarted(surface, property);
        else reconcile();
        write(row, State.own(value));
        if (alone) dragEnded(surface, property);
    }

    /** The finger lifted: the drag becomes one Undo step, unless it ended where it started. */
    public void dragEnded(@NonNull TerlSurface surface, @NonNull TerlProperty property) {
        if (!editable(surface)) return;
        reconcile();
        Row row = new Row(surface, property);
        State from = mDragStart.remove(row);
        if (from == null || !mLastWritten.containsKey(row)) return;
        State to = read(row);
        if (to.equals(from)) return;
        push(row, from, to);
    }

    /** Clears the row's own value, as one step. A row already following Global is left alone. */
    public void followGlobal(@NonNull TerlSurface surface, @NonNull TerlProperty property) {
        if (!editable(surface)) return;
        reconcile();
        Row row = new Row(surface, property);
        State from = read(row);
        if (!from.own) return;
        write(row, State.FOLLOW);
        push(row, from, State.FOLLOW);
    }

    public boolean canUndo() {
        if (mEnded) return false;
        reconcile();
        return !mUndo.isEmpty();
    }

    /** Reverses the latest step still the session's; rows written from outside since are skipped. */
    public void undo() {
        if (mEnded) return;
        reconcile();
        Step step = mUndo.poll();
        if (step == null) return;
        for (Map.Entry<Row, State[]> entry : step.rows.entrySet()) {
            write(entry.getKey(), entry.getValue()[0]);
        }
    }

    /**
     * Puts every row the session changed back to what it held when the session opened, as one step.
     * Rows written from outside since are skipped.
     */
    public void revert() {
        if (mEnded) return;
        reconcile();
        Step step = new Step();
        for (Map.Entry<Row, State> entry : new HashMap<>(mLastWritten).entrySet()) {
            Row row = entry.getKey();
            State base = mBaseline.get(row);
            State now = entry.getValue();
            if (base == null || base.equals(now)) continue;
            step.rows.put(row, new State[] {now, base});
            write(row, base);
        }
        if (!step.rows.isEmpty()) mUndo.push(step);
    }

    /** How many rows this session changed and still holds changed. */
    public int changeCount() {
        if (mEnded) return 0;
        reconcile();
        int count = 0;
        for (Map.Entry<Row, State> entry : mLastWritten.entrySet()) {
            if (!entry.getValue().equals(mBaseline.get(entry.getKey()))) count++;
        }
        return count;
    }

    /** How many rows this session changed were then set elsewhere, and kept as set there. */
    public int keptElsewhereCount() {
        if (mEnded) return 0;
        reconcile();
        return mKeptElsewhere.size();
    }

    /** Edit mode closed: the changes stay, the history goes. */
    public void end() {
        mEnded = true;
        mUndo.clear();
        mBaseline.clear();
        mLastWritten.clear();
        mKeptElsewhere.clear();
        mDragStart.clear();
    }

    private boolean editable(@NonNull TerlSurface surface) {
        return !mEnded && surface.acceptsOwn;
    }

    @NonNull
    private State read(@NonNull Row row) {
        return mStore.holdsOwn(row.surface, row.property)
            ? State.own(mStore.value(row.surface, row.property)) : State.FOLLOW;
    }

    private void write(@NonNull Row row, @NonNull State state) {
        if (!mBaseline.containsKey(row)) mBaseline.put(row, read(row));
        mKeptElsewhere.remove(row);
        if (state.own) mStore.setOwn(row.surface, row.property, state.value);
        else mStore.followGlobal(row.surface, row.property);
        // What the store holds after the write (a clamped number), so it compares equal later.
        mLastWritten.put(row, read(row));
    }

    private void push(@NonNull Row row, @NonNull State from, @NonNull State to) {
        Step step = new Step();
        step.rows.put(row, new State[] {from, to});
        mUndo.push(step);
    }

    /**
     * Hands every row written from outside since the session last wrote it to the outside: its
     * baseline becomes what it holds now, and every step forgets it.
     */
    private void reconcile() {
        Iterator<Map.Entry<Row, State>> written = mLastWritten.entrySet().iterator();
        while (written.hasNext()) {
            Map.Entry<Row, State> entry = written.next();
            Row row = entry.getKey();
            State now = read(row);
            if (now.equals(entry.getValue())) continue;
            written.remove();
            mBaseline.put(row, now);
            mKeptElsewhere.add(row);
            if (mDragStart.containsKey(row)) mDragStart.put(row, now);
            Iterator<Step> steps = mUndo.iterator();
            while (steps.hasNext()) {
                Step step = steps.next();
                step.rows.remove(row);
                if (step.rows.isEmpty()) steps.remove();
            }
        }
    }
}
