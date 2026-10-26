package com.termux.app.surfaces.cards;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.place.EdgeStackPolicy;
import com.termux.app.place.Element;
import com.termux.app.place.PlaceLayout;
import com.termux.app.place.PlaceLayout.Edge;
import com.termux.app.place.Slot;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The Dock card's Bars list as policy (editor-redesign §2 Dock, §9): which bars it lists, the
 * order they read in, where each one stands, and what each opens.
 *
 * <p>Every bar is placed on its own edge, so the list order means something on screen only for
 * bars that share an edge (and, along the bottom, the same side of the keyboard). There the list
 * reads like the screen: top to bottom, and left to right for the side columns. On the top and
 * left edges the first of them is the outermost band; on the bottom and right edges it is the
 * innermost. The status bar is not in the list and keeps its place in a stack it shares.</p>
 *
 * <p>No views and no {@code Context}, so it is held by JVM tests.</p>
 */
public final class BarOrder {

    private BarOrder() {}

    /** The list as a fresh install has it: Apps, A–Z, Extra keys. */
    @NonNull
    public static final List<Element> DEFAULT = Collections.unmodifiableList(Arrays.asList(
        Element.APPS, Element.AZ, Element.EXTRA_KEYS));

    /** Where a bar can stand, as its Position row offers it. */
    public enum Position {
        TOP, BOTTOM, LEFT, RIGHT,
        /** On the bottom edge, between the content and the keyboard. */
        ABOVE_KEYBOARD,
        /** The A–Z strip along the bottom, riding the dock's apps row. */
        RIDES_APPS_ROW
    }

    /** What a bar's sliders glyph opens, in the order its settings stand. */
    public enum Setting {
        POSITION, APP_ICONS, MOST_USED, ICON_PACK, GLASS_BACKGROUND, SHOW, EXTRA_KEYS_EDITOR
    }

    /** Whether {@code element} is one of the Dock's bars (the status bar is not). */
    public static boolean isBar(@Nullable Element element) {
        return element == Element.APPS || element == Element.AZ || element == Element.EXTRA_KEYS;
    }

    // ------------------------------------------------------------------------------ the list

    /** The list as stored: the bars' storage keys, top to bottom, comma separated. */
    @NonNull
    public static String format(@NonNull List<Element> order) {
        StringBuilder out = new StringBuilder();
        for (Element bar : normalized(order)) {
            if (out.length() > 0)
                out.append(',');
            out.append(bar.storageKey());
        }
        return out.toString();
    }

    /**
     * The list a stored value names: unknown names and repeats dropped, and any bar it leaves out
     * added at the end in the default order. Null or empty is the default list.
     */
    @NonNull
    public static List<Element> parse(@Nullable String stored) {
        List<Element> out = new ArrayList<>(DEFAULT.size());
        if (stored != null) {
            for (String name : stored.split(",")) {
                for (Element bar : DEFAULT) {
                    if (bar.storageKey().equals(name.trim()) && !out.contains(bar))
                        out.add(bar);
                }
            }
        }
        return normalized(out);
    }

    /** {@code order} with only bars, each once, every bar present (missing ones last). */
    @NonNull
    static List<Element> normalized(@NonNull List<Element> order) {
        List<Element> out = new ArrayList<>(DEFAULT.size());
        for (Element element : order) {
            if (isBar(element) && !out.contains(element))
                out.add(element);
        }
        for (Element bar : DEFAULT) {
            if (!out.contains(bar))
                out.add(bar);
        }
        return out;
    }

    /** {@code order} with the bar at {@code from} moved to {@code to}. */
    @NonNull
    public static List<Element> moved(@NonNull List<Element> order, int from, int to) {
        List<Element> out = new ArrayList<>(order);
        if (from < 0 || from >= out.size())
            return out;
        Element bar = out.remove(from);
        out.add(Math.max(0, Math.min(to, out.size())), bar);
        return out;
    }

    /**
     * {@code full} with the bars of {@code visible} put in that order, every other bar left where
     * it was: what a drag among the rows the page shows does to the whole list.
     */
    @NonNull
    public static List<Element> mergeVisible(@NonNull List<Element> full,
                                             @NonNull List<Element> visible) {
        List<Element> out = new ArrayList<>(full.size());
        int next = 0;
        for (Element bar : full) {
            if (visible.contains(bar) && next < visible.size())
                out.add(visible.get(next++));
            else
                out.add(bar);
        }
        return out;
    }

    /**
     * Where a row dragged from {@code from} lands, its centre now at {@code draggedCentre}: after
     * every other row whose centre it has passed. {@code centres} are the rows' resting centres,
     * top to bottom.
     */
    public static int dropIndex(@NonNull float[] centres, int from, float draggedCentre) {
        int index = 0;
        for (int i = 0; i < centres.length; i++) {
            if (i != from && centres[i] < draggedCentre)
                index++;
        }
        return index;
    }

    /**
     * The list as it reads on screen: {@code stored}, with the bars that share an edge (and a side
     * of the keyboard) put in the order they stand there, in the places the stored list gives
     * them. Bars on edges of their own keep the stored order, so a list stays as it was left
     * when a revert moved its bars back.
     */
    @NonNull
    public static List<Element> reconcile(@NonNull List<Element> stored,
                                          @NonNull PlaceLayout layout) {
        List<Element> out = normalized(stored);
        for (Edge edge : Edge.values()) {
            for (boolean under : sides(edge)) {
                List<Element> reading = readingOrder(layout, edge, under);
                if (reading.size() < 2)
                    continue;
                List<Integer> places = new ArrayList<>(reading.size());
                for (int i = 0; i < out.size(); i++) {
                    if (reading.contains(out.get(i)))
                        places.add(i);
                }
                for (int k = 0; k < places.size(); k++)
                    out.set(places.get(k), reading.get(k));
            }
        }
        return out;
    }

    /**
     * {@code layout} with the bars of every shared stack standing in {@code order}: the bars trade
     * places among the bands they already hold, so the status bar keeps its band, and the stack is
     * numbered 0 outward as a drop numbers it ({@link EdgeStackPolicy#withDrop}). A stack whose
     * bars already read in order is left untouched.
     */
    @NonNull
    public static PlaceLayout restack(@NonNull PlaceLayout layout, @NonNull List<Element> order) {
        List<Element> list = normalized(order);
        PlaceLayout out = layout;
        for (Edge edge : Edge.values()) {
            for (boolean under : sides(edge)) {
                List<Element> stack = new ArrayList<>(stackOf(layout, edge, under));
                List<Integer> places = new ArrayList<>();
                List<Element> bars = new ArrayList<>();
                for (int i = 0; i < stack.size(); i++) {
                    if (isBar(stack.get(i))) {
                        places.add(i);
                        bars.add(stack.get(i));
                    }
                }
                if (bars.size() < 2)
                    continue;
                Collections.sort(bars, (a, b) -> Integer.compare(list.indexOf(a),
                    list.indexOf(b)));
                if (!firstIsOutermost(edge))
                    Collections.reverse(bars);
                List<Element> next = new ArrayList<>(stack);
                for (int k = 0; k < places.size(); k++)
                    next.set(places.get(k), bars.get(k));
                if (next.equals(stack))
                    continue;
                for (int i = 0; i < next.size(); i++)
                    out = out.withSlot(next.get(i), new Slot(false, edge, i, under));
            }
        }
        return out;
    }

    // -------------------------------------------------------------------------- the positions

    /** The positions {@code bar}'s Position row offers, in the order it shows them. */
    @NonNull
    public static List<Position> positionsFor(@NonNull Element bar) {
        if (bar == Element.AZ)
            return Collections.unmodifiableList(Arrays.asList(Position.RIDES_APPS_ROW,
                Position.TOP, Position.LEFT, Position.RIGHT));
        return Collections.unmodifiableList(Arrays.asList(Position.TOP, Position.BOTTOM,
            Position.LEFT, Position.RIGHT, Position.ABOVE_KEYBOARD));
    }

    /**
     * The position a bar's slot reads as. Along the bottom the apps row and the extra keys read
     * Bottom under the keyboard and Above the keyboard over it; the A–Z strip there rides the
     * apps row.
     */
    @NonNull
    public static Position positionOf(@NonNull Element bar, @NonNull Slot slot) {
        switch (slot.edge) {
            case TOP: return Position.TOP;
            case LEFT: return Position.LEFT;
            case RIGHT: return Position.RIGHT;
            case BOTTOM:
            default:
                if (bar == Element.AZ)
                    return Position.RIDES_APPS_ROW;
                return slot.underKeyboard ? Position.BOTTOM : Position.ABOVE_KEYBOARD;
        }
    }

    /**
     * {@code layout} with {@code bar} at {@code position}, hidden or shown as it was. On a new
     * edge it takes the band it has always taken there ({@link Slot#on}); on its own edge it
     * keeps its band. The A–Z strip riding the apps row follows the apps row to either side of
     * the keyboard. Restack afterwards to put shared stacks in the list's order.
     */
    @NonNull
    public static PlaceLayout withPosition(@NonNull PlaceLayout layout, @NonNull Element bar,
                                           @NonNull Position position) {
        Slot now = layout.slot(bar);
        Edge edge;
        boolean under = false;
        switch (position) {
            case TOP: edge = Edge.TOP; break;
            case LEFT: edge = Edge.LEFT; break;
            case RIGHT: edge = Edge.RIGHT; break;
            case BOTTOM:
                edge = Edge.BOTTOM;
                under = bar != Element.AZ;
                break;
            case RIDES_APPS_ROW:
            case ABOVE_KEYBOARD:
            default:
                edge = Edge.BOTTOM;
                if (bar == Element.AZ) {
                    Slot apps = layout.slot(Element.APPS);
                    under = apps.edge == Edge.BOTTOM && apps.underKeyboard;
                }
                break;
        }
        int order = edge == now.edge ? now.order : Slot.on(edge, bar).order;
        Slot next = new Slot(now.hidden, edge, order, under);
        PlaceLayout out = layout.withSlot(bar, next);
        if (bar == Element.APPS) {
            Slot az = out.slot(Element.AZ);
            if (az.edge == Edge.BOTTOM)
                out = out.withSlot(Element.AZ,
                    az.withUnderKeyboard(next.edge == Edge.BOTTOM && next.underKeyboard));
        }
        return out;
    }

    // ------------------------------------------------------------------------- the expansion

    /** What {@code bar}'s sliders glyph opens, in order. */
    @NonNull
    public static List<Setting> settingsOf(@NonNull Element bar) {
        switch (bar) {
            case APPS:
                return Collections.unmodifiableList(Arrays.asList(Setting.POSITION,
                    Setting.APP_ICONS, Setting.MOST_USED, Setting.ICON_PACK));
            case AZ:
                return Collections.unmodifiableList(Arrays.asList(Setting.POSITION,
                    Setting.GLASS_BACKGROUND, Setting.SHOW));
            case EXTRA_KEYS:
                return Collections.unmodifiableList(Arrays.asList(Setting.POSITION,
                    Setting.EXTRA_KEYS_EDITOR));
            default:
                return Collections.emptyList();
        }
    }

    /**
     * The bar open after a tap on {@code tapped}'s sliders glyph while {@code expanded} is open:
     * one bar's settings at a time, and a second tap closes them.
     */
    @Nullable
    public static Element toggled(@Nullable Element expanded, @NonNull Element tapped) {
        return tapped == expanded ? null : tapped;
    }

    /**
     * The bar the card opens on for a door that names {@code target}: that bar for one of the
     * three, none for anything else.
     */
    @Nullable
    public static Element expandedOnOpen(@Nullable Element target) {
        return isBar(target) ? target : null;
    }

    // ----------------------------------------------------------------------------- the stacks

    /** Whether the list's first bar on {@code edge} is its outermost band. */
    static boolean firstIsOutermost(@NonNull Edge edge) {
        return edge == Edge.TOP || edge == Edge.LEFT;
    }

    @NonNull
    private static boolean[] sides(@NonNull Edge edge) {
        return edge == Edge.BOTTOM ? new boolean[] {false, true} : new boolean[] {false};
    }

    @NonNull
    private static List<Element> stackOf(@NonNull PlaceLayout layout, @NonNull Edge edge,
                                         boolean under) {
        if (edge != Edge.BOTTOM)
            return EdgeStackPolicy.stack(layout, edge);
        return under ? EdgeStackPolicy.underKeyboard(layout) : EdgeStackPolicy.overKeyboard(layout);
    }

    /** The bars of one stack in the order the list reads them. */
    @NonNull
    static List<Element> readingOrder(@NonNull PlaceLayout layout, @NonNull Edge edge,
                                      boolean under) {
        List<Element> bars = new ArrayList<>();
        for (Element element : stackOf(layout, edge, under)) {
            if (isBar(element))
                bars.add(element);
        }
        if (!firstIsOutermost(edge))
            Collections.reverse(bars);
        return bars;
    }
}
