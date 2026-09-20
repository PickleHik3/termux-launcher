package com.termux.app.fragments.settings;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.place.EdgeStackPolicy;
import com.termux.app.place.Element;
import com.termux.app.place.PlaceLayout;
import com.termux.app.place.PlaceLayout.Edge;
import com.termux.app.place.PlaceOrientation;
import com.termux.app.wall.PaneWallPage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Where a bar lifted off the Layout editor's miniature may be dropped, which of those targets the
 * finger is over, and how much of the picture the canvas keeps once the side columns have taken
 * theirs. Pure and view-free — the same relationship {@code DockLayoutPolicy} has with the dock —
 * so the rules can be read and tested in one place while the miniature stays dumb enough to just
 * draw the answer.
 *
 * <p>The legal set is {@link EdgeStackPolicy#targets}: every edge in both orientations, one drop
 * per gap in that edge's stack, the status bar never hidden, and the A&#8211;Z index only picking
 * an edge of its own while it is not riding the pinned apps row. This class adds nothing to that
 * but the tray — which is the view's word for hidden — and the rectangles a finger is hit-tested
 * against.
 */
public final class MiniatureDragPolicy {

    private MiniatureDragPolicy() {}

    /** A bar the user may lift. Everything else the miniature draws has no placement to change. */
    public enum Bar {
        STATUS_BAR(Element.STATUS),
        APPS_ROW(Element.APPS),
        AZ_INDEX(Element.AZ),
        EXTRA_KEYS(Element.EXTRA_KEYS);

        @NonNull private final Element mElement;

        Bar(@NonNull Element element) {
            mElement = element;
        }

        /** The model's name for this bar; everything about where it may stand is asked of it. */
        @NonNull
        public Element element() {
            return mElement;
        }

        /** The bar one element is drawn as. */
        @NonNull
        public static Bar of(@NonNull Element element) {
            for (Bar bar : values()) {
                if (bar.mElement == element) return bar;
            }
            return STATUS_BAR;
        }
    }

    /**
     * One drop target: a gap in one edge's stack, or the tray under the phone when {@link #edge}
     * is null. Carries its own rectangle so hit-testing is the policy's job too and the view only
     * reports where the finger is.
     *
     * <p>{@link #index} is the position in that edge's stack the drop would give the bar, 0
     * outermost, and {@link #line} is the coordinate of the gap itself — the y of a row's
     * insertion line, the x of a column's — so the picture can draw where the band would land
     * rather than only which edge it would land on.
     */
    public static final class Slot {
        @Nullable public final Edge edge;
        public final int index;
        public final float line;
        public final float left;
        public final float top;
        public final float right;
        public final float bottom;

        public Slot(@Nullable Edge edge, int index, float line, float left, float top, float right,
                    float bottom) {
            this.edge = edge;
            this.index = Math.max(0, index);
            this.line = line;
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
        }

        /** An edge's outermost gap, for a caller that has a rectangle and no stack to place in. */
        public Slot(@Nullable Edge edge, float left, float top, float right, float bottom) {
            this(edge, 0, edge == Edge.BOTTOM ? bottom : edge == Edge.RIGHT ? right
                : edge == Edge.LEFT ? left : top, left, top, right, bottom);
        }

        /** The tray: dropping here hides the bar. */
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
            return "Slot{" + (edge == null ? "tray" : edge + "#" + index) + "}";
        }
    }

    /** Everywhere one lifted bar may land: the gaps it may stand in, and whether it may hide. */
    public static final class Targets {
        @NonNull public final List<EdgeStackPolicy.Drop> drops;
        public final boolean tray;

        Targets(@NonNull List<EdgeStackPolicy.Drop> drops, boolean tray) {
            this.drops = Collections.unmodifiableList(drops);
            this.tray = tray;
        }

        /** A bar with nowhere to go is not liftable at all. */
        public boolean isEmpty() {
            return drops.isEmpty() && !tray;
        }

        public boolean offers(@NonNull Edge edge) {
            return gapsOn(edge) > 0;
        }

        /** How many gaps this edge offers: one more than it has bands the lifted bar is not. */
        public int gapsOn(@NonNull Edge edge) {
            int gaps = 0;
            for (EdgeStackPolicy.Drop drop : drops) {
                if (drop.edge == edge) gaps++;
            }
            return gaps;
        }

        /** The edges offered, in {@link Edge} order; a drag outlines one region per edge. */
        @NonNull
        public List<Edge> edges() {
            List<Edge> edges = new ArrayList<>(4);
            for (EdgeStackPolicy.Drop drop : drops) {
                if (!edges.contains(drop.edge)) edges.add(drop.edge);
            }
            return edges;
        }
    }

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
        Element element = bar.element();
        // The tray is the bar's own rule, not a gap's: the A-Z index riding the pinned apps row is
        // offered no gap at all, and putting it away is still the one thing a drag can do with it.
        return new Targets(new ArrayList<>(EdgeStackPolicy.targets(layout, element, orientation)),
            element.hideAllowed());
    }

    /**
     * The slot the finger is over, or null when it is over none of them. Two slots meet at a
     * corner and every gap in an edge touches the next, so the nearest centre wins rather than
     * whichever was offered first.
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

    // ---------------------------------------------------------------- how wide the canvas stays

    /** The share of the frame one band claims of whatever the bands outside it have left. */
    private static final float STATUS_FRACTION = 0.11f;
    private static final float ROW_FRACTION = 0.15f;
    private static final float ALPHABETS_FRACTION = 0.08f;

    /**
     * How narrow the canvas has to get before the editor says so: under this share of the picture's
     * width, in portrait. It stands in for the rule it replaces — a portrait screen used to refuse
     * a side column outright — so the freedom is kept and the warning is what is left of the
     * refusal. Nothing is blocked at any width.
     */
    public static final float NARROW_CANVAS_FRACTION = 0.60f;

    /** The share of its edge one band claims, which is what the miniature draws it at. */
    public static float bandFraction(@NonNull Element element) {
        switch (element) {
            case STATUS: return STATUS_FRACTION;
            case AZ: return ALPHABETS_FRACTION;
            case APPS:
            case EXTRA_KEYS:
            default: return ROW_FRACTION;
        }
    }

    /**
     * What is left of the picture's width for the canvas once both side stacks have claimed their
     * columns. Each band takes its share of what the bands outside it left, which is how the
     * miniature claims them, so this is the width the user is looking at rather than an estimate.
     */
    public static float canvasWidthFraction(@NonNull PlaceLayout layout) {
        float remaining = 1f;
        for (Edge edge : new Edge[] {Edge.LEFT, Edge.RIGHT}) {
            for (Element element : EdgeStackPolicy.stack(layout, edge))
                remaining -= remaining * bandFraction(element);
        }
        return Math.max(0f, remaining);
    }

    /**
     * Whether this arrangement leaves the canvas narrow enough to be worth a word: columns down the
     * side of a portrait screen, with little width left beside them. Landscape has width to spare,
     * so it is never warned about.
     */
    public static boolean warnsNarrowCanvas(@NonNull PlaceLayout layout,
                                            @NonNull PlaceOrientation orientation) {
        return orientation == PlaceOrientation.PORTRAIT
            && canvasWidthFraction(layout) < NARROW_CANVAS_FRACTION;
    }
}
