package com.termux.app.fragments.settings;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.place.EdgeStackPolicy;
import com.termux.app.place.Element;
import com.termux.app.place.PlaceLayout;
import com.termux.app.place.PlaceLayout.Edge;

import java.util.List;

/**
 * Which arrangement moves the layout canvas offers a screen reader or a keyboard for one bar,
 * read off the same legal set a drag is ({@link MiniatureDragPolicy#targets}). Pure and view-free
 * like that class: it never invents a target the drag would not offer, it only picks one of them,
 * so a move made from an accessibility action lands exactly where a drop on that gap would.
 *
 * <p>A move to another edge takes that edge's innermost gap (over the keyboard along the bottom),
 * so the bands already standing there keep their places; a step along the bar's own edge moves it
 * one place outward (toward the screen edge) or inward (toward the middle) within its side of the
 * keyboard. Sizes step by a fixed amount inside the store's own range.
 */
public final class LayoutCanvasA11yPolicy {

    private LayoutCanvasA11yPolicy() {}

    /** One step of the pinned apps' height, as a multiple of its unscaled height. */
    public static final float DOCK_SCALE_STEP = 0.1f;
    /** One step of the keyboard's height, as a multiple of its unscaled height. */
    public static final float KEYBOARD_SCALE_STEP = 0.05f;
    /** One step of the space under the keyboard's last row, in dp. */
    public static final int CHIN_STEP_DP = 2;

    /**
     * Where a shown bar stands: its edge, which side of the keyboard along the bottom, its place
     * in that group (0 outermost) and how many bands share the group.
     */
    public static final class Position {
        @NonNull public final Edge edge;
        public final boolean underKeyboard;
        public final int index;
        public final int count;

        Position(@NonNull Edge edge, boolean underKeyboard, int index, int count) {
            this.edge = edge;
            this.underKeyboard = underKeyboard;
            this.index = index;
            this.count = count;
        }
    }

    /** Where a bar stands, or null while the arrangement leaves it hidden. */
    @Nullable
    public static Position positionOf(@NonNull PlaceLayout layout, @NonNull Element element) {
        if (!EdgeStackPolicy.isShown(layout, element)) return null;
        Edge edge = EdgeStackPolicy.edgeOf(layout, element);
        boolean under = edge == Edge.BOTTOM && EdgeStackPolicy.standsUnderKeyboard(layout, element);
        List<Element> group = groupOf(layout, edge, under);
        int index = Math.max(0, group.indexOf(element));
        return new Position(edge, under, index, Math.max(1, group.size()));
    }

    @NonNull
    private static List<Element> groupOf(@NonNull PlaceLayout layout, @NonNull Edge edge,
                                         boolean underKeyboard) {
        if (edge != Edge.BOTTOM) return EdgeStackPolicy.stack(layout, edge);
        return underKeyboard ? EdgeStackPolicy.underKeyboard(layout)
            : EdgeStackPolicy.overKeyboard(layout);
    }

    /**
     * The drop that puts a shown bar on another edge: that edge's innermost gap, over the keyboard
     * along the bottom. Null for the edge the bar already stands on, for a hidden bar, and for an
     * edge the targets do not offer.
     */
    @Nullable
    public static EdgeStackPolicy.Drop toEdge(@NonNull MiniatureDragPolicy.Targets targets,
                                              @NonNull PlaceLayout layout,
                                              @NonNull Element element, @NonNull Edge edge) {
        Position at = positionOf(layout, element);
        if (at == null || at.edge == edge) return null;
        int gaps = targets.gapsOn(edge);
        if (gaps <= 0) return null;
        return find(targets.drops, edge, gaps - 1, false);
    }

    /**
     * The drop that moves a shown bar one place along its own edge: {@code outward} toward the
     * screen edge, otherwise toward the middle. It stays on its side of the keyboard. Null where
     * there is no place that way, or the targets do not offer it.
     */
    @Nullable
    public static EdgeStackPolicy.Drop step(@NonNull MiniatureDragPolicy.Targets targets,
                                            @NonNull PlaceLayout layout,
                                            @NonNull Element element, boolean outward) {
        Position at = positionOf(layout, element);
        if (at == null) return null;
        int index = outward ? at.index - 1 : at.index + 1;
        if (index < 0 || index >= at.count) return null;
        return find(at.underKeyboard ? targets.underKeyboardDrops : targets.drops, at.edge, index,
            at.underKeyboard);
    }

    /**
     * What an arrow key toward {@code edge} does to a selected bar: a bar already on that edge
     * steps outward along it, any other moves there. Null where neither is legal.
     */
    @Nullable
    public static EdgeStackPolicy.Drop toward(@NonNull MiniatureDragPolicy.Targets targets,
                                              @NonNull PlaceLayout layout,
                                              @NonNull Element element, @NonNull Edge edge) {
        Position at = positionOf(layout, element);
        if (at == null) return null;
        return at.edge == edge ? step(targets, layout, element, true)
            : toEdge(targets, layout, element, edge);
    }

    /** Whether a shown bar may be put away: the same rule as the tray during a drag. */
    public static boolean canHide(@NonNull MiniatureDragPolicy.Targets targets,
                                  @NonNull PlaceLayout layout, @NonNull Element element) {
        return targets.tray && EdgeStackPolicy.isShown(layout, element);
    }

    /** Whether a bar is put away now, which is when it can be brought back. */
    public static boolean canShow(@NonNull PlaceLayout layout, @NonNull Element element) {
        return !EdgeStackPolicy.isShown(layout, element);
    }

    /**
     * A scale moved one step up ({@code direction} positive) or down, kept in range and rounded
     * to hundredths so repeated steps do not drift.
     */
    public static float stepScale(float value, float step, float min, float max, int direction) {
        float next = value + Math.signum(direction) * step;
        next = Math.round(next * 100f) / 100f;
        return Math.max(min, Math.min(max, next));
    }

    /** A whole number moved one step up or down, kept in range. */
    public static int stepInt(int value, int step, int min, int max, int direction) {
        int next = value + (direction > 0 ? step : direction < 0 ? -step : 0);
        return Math.max(min, Math.min(max, next));
    }

    @Nullable
    private static EdgeStackPolicy.Drop find(@NonNull List<EdgeStackPolicy.Drop> drops,
                                             @NonNull Edge edge, int index, boolean underKeyboard) {
        for (EdgeStackPolicy.Drop drop : drops) {
            if (drop.edge == edge && drop.index == index && drop.underKeyboard == underKeyboard)
                return drop;
        }
        return null;
    }
}
