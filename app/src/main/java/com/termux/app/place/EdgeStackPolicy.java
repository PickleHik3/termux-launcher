package com.termux.app.place;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.place.PlaceLayout.Edge;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * What stands on each edge of a place, in what order, how thick it is, and how much of the screen
 * the content beside it gives up. Pure and view-free — the relationship {@code DockLayoutPolicy}
 * has with the dock — so the real screen and the Layout page's miniature can read one answer
 * instead of each spelling the arrangement out again.
 *
 * <p>An edge is a stack: {@link #stack} lists what is on it outermost first, and an edge's content
 * inset is simply the sum of those bands' thicknesses. That replaced the hand-written {@code max()}
 * chain in {@code TermuxActivity.applyTerminalOverlayInsets}, where each column carried the whole
 * reach of every column outside it and the widest won. One {@link EdgeStackView} per edge is what
 * draws the answer.
 *
 * <p><b>One deliberate difference from the shipped chain.</b> A status bar standing in a column on
 * the same edge as the apps rail or the extra keys used not to stand <em>beside</em> them: the two
 * shared one column, the bar taking the top half of it, so the edge cost the wider of the two
 * rather than both. They stack like everything else now and the edge costs both. That merge is
 * what the Layout-freedom work replaced with an order the user can set; every other arrangement
 * the old model could express comes out byte-for-byte the same.
 *
 * <p>Two rules are kept from the old model and are not ours to change:
 * the status bar is never hidden (the wall's pager rides it), and the alphabets index riding the
 * pinned apps row ignores its own slot and goes wherever that row goes
 * ({@link PlaceChromePolicy#azBarEdge}).
 *
 * <p>One rule is a renderer fact rather than a policy one, recorded here so the next reader does
 * not look for it: only a status bar standing on {@link Edge#TOP} gets the system-bar glass strip
 * behind it ({@code applyTerminalWindowBarBackdropInsets}). Nothing in this class decides that.
 */
public final class EdgeStackPolicy {

    private EdgeStackPolicy() {}

    // ---------------------------------------------------------------- values

    /** What the content gives up on each edge, in pixels. */
    public static final class Insets {
        public final int left;
        public final int top;
        public final int right;
        public final int bottom;

        public Insets(int left, int top, int right, int bottom) {
            this.left = Math.max(0, left);
            this.top = Math.max(0, top);
            this.right = Math.max(0, right);
            this.bottom = Math.max(0, bottom);
        }

        public int of(@NonNull Edge edge) {
            switch (edge) {
                case TOP: return top;
                case BOTTOM: return bottom;
                case LEFT: return left;
                default: return right;
            }
        }

        @Override public boolean equals(@Nullable Object other) {
            if (this == other) return true;
            if (!(other instanceof Insets)) return false;
            Insets that = (Insets) other;
            return left == that.left && top == that.top && right == that.right
                && bottom == that.bottom;
        }

        @Override public int hashCode() {
            return ((left * 31 + top) * 31 + right) * 31 + bottom;
        }

        @NonNull @Override public String toString() {
            return "Insets{" + left + "," + top + "," + right + "," + bottom + "}";
        }
    }

    /**
     * How thick each bar stands, in pixels, told to the policy by whoever measured it. A bar is a
     * different thickness lying down than standing up, so each one is given twice, and each figure
     * is <em>the band the bar itself claims</em> — its own margins included, but not the cutout or
     * anything outside it, which the stack adds once.
     *
     * <p>Where the activity already knows each of them:
     * <ul>
     *   <li>status row — {@code targetStatusBarHeightPx(capsule, compact)};
     *       status column — the same for a vertical edge plus
     *       {@code statusBarColumnOuterMarginPx()}.</li>
     *   <li>apps row — {@code getDockLayout().appsBarHeightPx}; apps column —
     *       {@code getDockLayout().railWidthPx} minus {@code railEdgeInsetPx}, which is the cutout
     *       this class adds back itself. An empty rail claims nothing: pass 0.</li>
     *   <li>A&#8211;Z row — {@code getDockLayout().azRowHeightPx} on the dock, or
     *       {@code AzBarHostGeometry.rowHeightPx} for a host of its own; A&#8211;Z column —
     *       {@code AzBarHostGeometry.thicknessPx} plus twice the rail's edge margin.</li>
     *   <li>extra keys row — {@code AccessoryStackLayoutPolicy.computeTerminalToolbarHeightPx};
     *       extra keys column — {@code extraKeysColumnKeysWidthPx()} plus twice that margin.</li>
     * </ul>
     *
     * <p>{@code cutoutLeftPx} and {@code cutoutRightPx} are the display cutout the side stacks
     * start past; there is no top or bottom equivalent here because the system bars are inset by
     * the window, not by this.
     */
    public static final class Metrics {
        public final int cutoutLeftPx;
        public final int cutoutRightPx;
        public final int statusRowPx;
        public final int statusColumnPx;
        public final int appsRowPx;
        public final int appsColumnPx;
        public final int azRowPx;
        public final int azColumnPx;
        public final int extraKeysRowPx;
        public final int extraKeysColumnPx;

        private Metrics(@NonNull Builder b) {
            cutoutLeftPx = Math.max(0, b.cutoutLeftPx);
            cutoutRightPx = Math.max(0, b.cutoutRightPx);
            statusRowPx = Math.max(0, b.statusRowPx);
            statusColumnPx = Math.max(0, b.statusColumnPx);
            appsRowPx = Math.max(0, b.appsRowPx);
            appsColumnPx = Math.max(0, b.appsColumnPx);
            azRowPx = Math.max(0, b.azRowPx);
            azColumnPx = Math.max(0, b.azColumnPx);
            extraKeysRowPx = Math.max(0, b.extraKeysRowPx);
            extraKeysColumnPx = Math.max(0, b.extraKeysColumnPx);
        }

        @NonNull
        public static Builder builder() {
            return new Builder();
        }

        /** The cutout a side stack starts past, on one side. */
        public int cutoutPx(@NonNull Edge edge) {
            if (edge == Edge.LEFT) return cutoutLeftPx;
            if (edge == Edge.RIGHT) return cutoutRightPx;
            return 0;
        }

        public static final class Builder {
            private int cutoutLeftPx;
            private int cutoutRightPx;
            private int statusRowPx;
            private int statusColumnPx;
            private int appsRowPx;
            private int appsColumnPx;
            private int azRowPx;
            private int azColumnPx;
            private int extraKeysRowPx;
            private int extraKeysColumnPx;

            public Builder cutout(int leftPx, int rightPx) {
                cutoutLeftPx = leftPx;
                cutoutRightPx = rightPx;
                return this;
            }

            public Builder status(int rowPx, int columnPx) {
                statusRowPx = rowPx;
                statusColumnPx = columnPx;
                return this;
            }

            public Builder apps(int rowPx, int columnPx) {
                appsRowPx = rowPx;
                appsColumnPx = columnPx;
                return this;
            }

            public Builder az(int rowPx, int columnPx) {
                azRowPx = rowPx;
                azColumnPx = columnPx;
                return this;
            }

            public Builder extraKeys(int rowPx, int columnPx) {
                extraKeysRowPx = rowPx;
                extraKeysColumnPx = columnPx;
                return this;
            }

            @NonNull
            public Metrics build() {
                return new Metrics(this);
            }
        }
    }

    /**
     * One place a lifted element may be dropped: an edge, and the position in that edge's stack
     * the drop would give it — {@code 0} outermost, {@code stack().size()} innermost.
     *
     * <p>{@code hideAllowed} is the element's own rule rather than the drop's, so it reads the
     * same on every drop of one call; it is carried here so a caller hit-testing drops has the
     * whole answer in one place.
     */
    public static final class Drop {
        @NonNull public final Edge edge;
        public final int index;
        public final boolean hideAllowed;

        public Drop(@NonNull Edge edge, int index, boolean hideAllowed) {
            this.edge = edge;
            this.index = Math.max(0, index);
            this.hideAllowed = hideAllowed;
        }

        @Override public boolean equals(@Nullable Object other) {
            if (this == other) return true;
            if (!(other instanceof Drop)) return false;
            Drop that = (Drop) other;
            return index == that.index && hideAllowed == that.hideAllowed && edge == that.edge;
        }

        @Override public int hashCode() {
            return (edge.hashCode() * 31 + index) * 31 + (hideAllowed ? 1 : 0);
        }

        @NonNull @Override public String toString() {
            return "Drop{" + edge + "#" + index + (hideAllowed ? ",hideable" : "") + "}";
        }
    }

    // ---------------------------------------------------------------- the stack

    /**
     * The edge an element actually draws on, which is its own slot's except for the alphabets
     * index while it rides the pinned apps row: there it goes wherever that row goes.
     */
    @NonNull
    public static Edge edgeOf(@NonNull PlaceLayout layout, @NonNull Element element) {
        if (element == Element.AZ) return PlaceChromePolicy.azBarEdge(layout);
        return layout.slot(element).edge;
    }

    /** Whether an element is on screen at all: hidden puts it away, and nothing else does. */
    public static boolean isShown(@NonNull PlaceLayout layout, @NonNull Element element) {
        return element == Element.STATUS || !layout.slot(element).hidden;
    }

    /**
     * What stands on one edge, outermost first. Ties in {@code order} — two elements can hold the
     * same number, since each keeps its own — are broken by {@link Element#defaultOrder}, so the
     * answer never depends on which element was asked about first.
     */
    @NonNull
    public static List<Element> stack(@NonNull PlaceLayout layout, @NonNull Edge edge) {
        List<Element> on = new ArrayList<>(4);
        for (Element element : Element.values()) {
            if (!isShown(layout, element)) continue;
            if (edgeOf(layout, element) != edge) continue;
            on.add(element);
        }
        Collections.sort(on, (a, b) -> {
            int byOrder = Integer.compare(orderOf(layout, a, edge), orderOf(layout, b, edge));
            if (byOrder != 0) return byOrder;
            int byDefault = Integer.compare(a.defaultOrder(edge), b.defaultOrder(edge));
            return byDefault != 0 ? byDefault : Integer.compare(a.ordinal(), b.ordinal());
        });
        return Collections.unmodifiableList(on);
    }

    /**
     * Where an element sits in the stack of the edge it actually draws on. That is its own slot's
     * number, except for the alphabets index riding the pinned apps row: its slot is ignored
     * whole — edge and position alike — and it takes the band it has always had under that row.
     */
    public static int orderOf(@NonNull PlaceLayout layout, @NonNull Element element,
                              @NonNull Edge edge) {
        Slot slot = layout.slot(element);
        return slot.edge == edge ? slot.order : element.defaultOrder(edge);
    }

    // ---------------------------------------------------------------- thickness and insets

    /** The band one element claims on one edge: its width in a column, its height in a row. */
    public static int thicknessPx(@NonNull Element element, @NonNull Edge edge,
                                  @NonNull Metrics metrics) {
        boolean column = edge.isOnSide();
        switch (element) {
            case STATUS: return column ? metrics.statusColumnPx : metrics.statusRowPx;
            case APPS: return column ? metrics.appsColumnPx : metrics.appsRowPx;
            case AZ: return column ? metrics.azColumnPx : metrics.azRowPx;
            case EXTRA_KEYS: return column ? metrics.extraKeysColumnPx : metrics.extraKeysRowPx;
            default: return 0;
        }
    }

    /** How thick everything on one edge stands together, without the cutout under it. */
    public static int stackThicknessPx(@NonNull PlaceLayout layout, @NonNull Edge edge,
                                       @NonNull Metrics metrics) {
        int total = 0;
        for (Element element : stack(layout, edge)) total += thicknessPx(element, edge, metrics);
        return total;
    }

    /**
     * What the content gives up on each edge: the display cutout on the two sides, and everything
     * standing on that edge on top of it. A hidden element is not in the stack and so costs
     * nothing at all.
     */
    @NonNull
    public static Insets contentInsets(@NonNull PlaceLayout layout, @NonNull Metrics metrics) {
        return new Insets(
            metrics.cutoutLeftPx + stackThicknessPx(layout, Edge.LEFT, metrics),
            stackThicknessPx(layout, Edge.TOP, metrics),
            metrics.cutoutRightPx + stackThicknessPx(layout, Edge.RIGHT, metrics),
            stackThicknessPx(layout, Edge.BOTTOM, metrics));
    }

    // ---------------------------------------------------------------- drop targets

    /**
     * Everywhere one element may be dropped. Every edge is offered in both orientations: a column
     * down the side of a portrait screen is allowed now, and a canvas too narrow for one is the
     * editor's to warn about rather than the model's to refuse.
     *
     * <p>{@code orientation} is part of the question by design — every arrangement value is scoped
     * to a place and an orientation, and the editor asks per orientation — even though no edge is
     * currently withheld for it.
     *
     * <p>The alphabets index riding the pinned apps row has no edge of its own to pick: the one
     * thing a drag can do with it is put it away, and, once away, bring it back to the row it
     * rides. An element already standing on an edge does not count itself when the indices for
     * that edge are counted, since a drop there is a move within the stack.
     */
    @NonNull
    public static List<Drop> targets(@NonNull PlaceLayout layout, @NonNull Element element,
                                     @NonNull PlaceOrientation orientation) {
        List<Drop> drops = new ArrayList<>(16);
        boolean hideAllowed = element.hideAllowed();
        if (element == Element.AZ && PlaceChromePolicy.appsRowShown(layout)) {
            if (layout.slot(Element.AZ).hidden) {
                drops.add(new Drop(Edge.BOTTOM, Element.AZ.defaultOrder(Edge.BOTTOM), hideAllowed));
            }
            return Collections.unmodifiableList(drops);
        }
        for (Edge edge : Edge.values()) {
            List<Element> on = stack(layout, edge);
            int slots = on.contains(element) ? on.size() - 1 : on.size();
            for (int index = 0; index <= slots; index++) {
                drops.add(new Drop(edge, index, hideAllowed));
            }
        }
        return Collections.unmodifiableList(drops);
    }
}
