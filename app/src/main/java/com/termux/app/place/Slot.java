package com.termux.app.place;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.place.PlaceLayout.Edge;

/**
 * Where one {@link Element} stands on a place in one orientation: put away, or on an edge at a
 * position in that edge's stack. Immutable and value-equal, so a layout holding four of them can
 * be compared rather than re-derived.
 *
 * <p>{@code order} counts from the screen edge inwards — 0 is the outermost band on that edge,
 * the one against the glass — and is kept while the element is hidden so that bringing it back
 * puts it where it was. Orders are per element, not allocated, so two elements can hold the same
 * number; {@link EdgeStackPolicy#stack} breaks that tie with {@link Element#defaultOrder}, which
 * is a total order, so a stack is always deterministic.
 */
public final class Slot {

    /** The element is put away. The status bar never is. */
    public final boolean hidden;
    /** The edge it stands on, and the one it comes back to while it is hidden. */
    @NonNull public final Edge edge;
    /** Its position in that edge's stack, 0 outermost. */
    public final int order;

    public Slot(boolean hidden, @NonNull Edge edge, int order) {
        this.hidden = hidden;
        this.edge = edge;
        this.order = Math.max(0, order);
    }

    /** Standing on an edge, at the position the launcher has always drawn it. */
    @NonNull
    public static Slot on(@NonNull Edge edge, @NonNull Element element) {
        return new Slot(false, edge, element.defaultOrder(edge));
    }

    /** Standing on an edge at a position of its own. */
    @NonNull
    public static Slot on(@NonNull Edge edge, int order) {
        return new Slot(false, edge, order);
    }

    /** Put away, remembering the edge and position it would come back to. */
    @NonNull
    public static Slot hiddenFrom(@NonNull Edge edge, int order) {
        return new Slot(true, edge, order);
    }

    @NonNull
    public Slot withEdge(@NonNull Edge newEdge) {
        return new Slot(hidden, newEdge, order);
    }

    @NonNull
    public Slot withOrder(int newOrder) {
        return new Slot(hidden, edge, newOrder);
    }

    @NonNull
    public Slot withHidden(boolean nowHidden) {
        return new Slot(nowHidden, edge, order);
    }

    @Override
    public boolean equals(@Nullable Object other) {
        if (this == other) return true;
        if (!(other instanceof Slot)) return false;
        Slot that = (Slot) other;
        return hidden == that.hidden && order == that.order && edge == that.edge;
    }

    @Override
    public int hashCode() {
        return (edge.hashCode() * 31 + order) * 31 + (hidden ? 1 : 0);
    }

    @NonNull
    @Override
    public String toString() {
        return "Slot{" + (hidden ? "hidden@" : "") + edge + "#" + order + "}";
    }
}
