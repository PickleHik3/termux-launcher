package com.termux.app.place;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.place.PlaceLayout.Edge;
import com.termux.app.place.PlaceLayout.RowPlacement;
import com.termux.app.wall.PaneWallPage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Where a bar lifted off the live screen inside the surface editor may be dropped, which of those
 * targets the finger is over, and the write a release makes. Pure and view-free — the same
 * relationship {@code DockLayoutPolicy} has with the dock — so the rules can be read and tested in
 * one place while the editor stays dumb enough to just draw the answer.
 *
 * <p>The legal set is the per-place layout model: a bar that stands along the bottom in portrait
 * gains the two side edges once there is width for a column, the status bar is never hidden, and
 * the A&#8211;Z index picks an edge of its own only while it is not riding the pinned apps row.
 * The Layout page's {@code MiniatureDragPolicy} answers the same questions for the page's
 * miniatures; this is the live screen's copy of them, and the tests hold both to the model table.
 */
public final class PlaceArrangePolicy {

    private PlaceArrangePolicy() {}

    /** A bar the user may lift. Everything else the editor outlines has no placement to change. */
    public enum Bar { STATUS_BAR, APPS_ROW, AZ_INDEX, EXTRA_KEYS }

    /**
     * One drop target: an edge of the screen, or the tray when {@link #edge} is null. Carries its
     * own rectangle so hit-testing is the policy's job too and the editor only reports where the
     * finger is.
     */
    public static final class Slot {
        @Nullable public final Edge edge;
        public final float left;
        public final float top;
        public final float right;
        public final float bottom;

        public Slot(@Nullable Edge edge, float left, float top, float right, float bottom) {
            this.edge = edge;
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
        }

        /** The tray: dropping here puts the bar away. */
        public boolean isTray() {
            return edge == null;
        }

        public boolean contains(float x, float y) {
            return x >= left && x <= right && y >= top && y <= bottom;
        }

        public float centerX() {
            return (left + right) / 2f;
        }

        public float centerY() {
            return (top + bottom) / 2f;
        }

        @NonNull
        @Override
        public String toString() {
            return "Slot{" + (edge == null ? "tray" : edge) + "}";
        }
    }

    /** Everywhere one lifted bar may land: the edges it may stand on, and whether it may hide. */
    public static final class Targets {
        @NonNull public final List<Edge> edges;
        public final boolean tray;

        Targets(@NonNull List<Edge> edges, boolean tray) {
            this.edges = Collections.unmodifiableList(edges);
            this.tray = tray;
        }

        /** A bar with nowhere to go is not liftable at all. */
        public boolean isEmpty() {
            return edges.isEmpty() && !tray;
        }

        public boolean offers(@NonNull Edge edge) {
            return edges.contains(edge);
        }
    }

    private static final Targets NOTHING = new Targets(new ArrayList<>(), false);

    /**
     * Where {@code bar} may be dropped on this place in this orientation, given what the
     * arrangement looks like right now.
     *
     * <p>{@code place} is part of the question by design — the model scopes every arrangement value
     * to a place — even though no place currently withholds a bar from an edge.
     */
    @NonNull
    public static Targets targets(@NonNull PaneWallPage place, @NonNull PlaceOrientation orientation,
                                  @NonNull PlaceLayout layout, @NonNull Bar bar) {
        boolean landscape = orientation == PlaceOrientation.LANDSCAPE;
        switch (bar) {
            case STATUS_BAR:
                // Never hidden: the wall's paging gesture rides it, so it only ever moves.
                return new Targets(edges(landscape, true), false);
            case APPS_ROW:
            case EXTRA_KEYS:
                return new Targets(edges(landscape, false), true);
            case AZ_INDEX:
                // Riding under the pinned apps the index goes wherever they go, so the only thing
                // a drag can do with it there is put it away.
                boolean standsAlone = !PlaceChromePolicy.appsRowShown(layout);
                return new Targets(standsAlone ? edges(landscape, true) : new ArrayList<>(), true);
            default:
                return NOTHING;
        }
    }

    /**
     * The edges a bar may stand on: the bottom always, the top only for a bar that has a top
     * position at all, and a column down either side only where there is width for one.
     */
    @NonNull
    private static List<Edge> edges(boolean landscape, boolean top) {
        List<Edge> edges = new ArrayList<>(4);
        if (top) edges.add(Edge.TOP);
        edges.add(Edge.BOTTOM);
        if (landscape) {
            edges.add(Edge.LEFT);
            edges.add(Edge.RIGHT);
        }
        return edges;
    }

    /**
     * The slot the finger is over, or null when it is over none of them. Two edge slots meet at a
     * corner, so the nearest centre wins rather than whichever was offered first.
     */
    @Nullable
    public static Slot slotUnder(@NonNull List<Slot> slots, float x, float y) {
        Slot best = null;
        float bestDistance = Float.MAX_VALUE;
        for (Slot slot : slots) {
            if (!slot.contains(x, y)) continue;
            float dx = slot.centerX() - x;
            float dy = slot.centerY() - y;
            float distance = dx * dx + dy * dy;
            if (distance < bestDistance) {
                bestDistance = distance;
                best = slot;
            }
        }
        return best;
    }

    /**
     * The write a release makes: the same keys the Layout page's pills write, for this place and
     * this orientation. A null slot is a release over nothing, which writes nothing and says so, so
     * the caller can spring the ghost back on the same answer.
     */
    public static boolean dropOn(@NonNull PlaceLayoutStore places, @NonNull PaneWallPage place,
                                 @NonNull PlaceOrientation orientation, @NonNull Bar bar,
                                 @Nullable Slot slot) {
        if (slot == null) return false;
        PlaceLayout layout = places.resolve(place, orientation);
        Targets targets = targets(place, orientation, layout, bar);
        if (slot.isTray()) {
            if (!targets.tray) return false;
        } else if (slot.edge == null || !targets.offers(slot.edge)) {
            return false;
        }
        switch (bar) {
            case STATUS_BAR:
                places.setStatusBarEdge(place, orientation, slot.edge);
                return true;
            case APPS_ROW:
                places.setAppsRow(place, orientation, rowFor(slot));
                return true;
            case EXTRA_KEYS:
                places.setExtraKeys(place, orientation, rowFor(slot));
                return true;
            case AZ_INDEX:
                if (slot.isTray()) {
                    places.setAzRowShown(place, orientation, false);
                    return true;
                }
                places.setAzRowShown(place, orientation, true);
                places.setAzBarEdge(place, orientation, slot.edge);
                return true;
            default:
                return false;
        }
    }

    /** A row's placement for a slot: the tray is away, an edge is that edge. */
    @NonNull
    private static RowPlacement rowFor(@NonNull Slot slot) {
        if (slot.isTray()) return RowPlacement.HIDDEN;
        switch (slot.edge) {
            case LEFT: return RowPlacement.LEFT;
            case RIGHT: return RowPlacement.RIGHT;
            default: return RowPlacement.BOTTOM;
        }
    }
}
