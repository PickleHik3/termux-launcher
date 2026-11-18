package com.termux.app.place;

import androidx.annotation.NonNull;

import com.termux.app.page.Side;
import com.termux.app.place.PlaceLayout.Edge;

import java.util.ArrayList;
import java.util.List;

/**
 * The bottom stack's side lanes (ADR 0030 section 4), as pure functions. The stack's
 * band over the keyboard is laid out once for both sides' rests: a row both sides show in the same
 * place is the stack's ({@link #STACK}), a row only Home's layout has stands in Home's lane and a
 * row only the Workspace's has in the Workspace's ({@link SideRows#carrier}). The lanes overlap in
 * one band, so the band is the shared rows plus the taller lane ({@link #layoutBand}), and each
 * row has one parent for both sides' layouts ({@link #resolve}) so no landing re-parents it.
 */
public final class StackLanes {

    private StackLanes() {}

    /** A row child's lane in the band: the shared stack's, Home's or the Workspace's. */
    public static final int STACK = 0;
    public static final int HOME = 1;
    public static final int WORKSPACE = 2;

    /** The dock's rows, the elements whose hosts stand in the band. */
    public static final Element[] ROWS = {Element.APPS, Element.AZ, Element.EXTRA_KEYS};

    /** Where a layout stands an element. */
    public enum Where {
        NONE, TOP, BOTTOM_OVER, BOTTOM_UNDER, LEFT, RIGHT
    }

    /** The lane for a {@link SideRows.Carrier}: none is the stack's, which draws nothing for it. */
    public static int laneOf(@NonNull SideRows.Carrier carrier) {
        switch (carrier) {
            case HOME: return HOME;
            case WORKSPACE: return WORKSPACE;
            default: return STACK;
        }
    }

    /** The lane of a side's own rows. */
    public static int laneOf(@NonNull Side side) {
        return side == Side.HOME ? HOME : WORKSPACE;
    }

    /** {@code element}'s lane from Home's layout and the Workspace's, both read at once. */
    public static int laneOf(@NonNull Element element, @NonNull PlaceLayout home,
                             @NonNull PlaceLayout workspace) {
        return laneOf(SideRows.carrier(element, Side.HOME, home, Side.WORKSPACE, workspace));
    }

    /** Where {@code layout} stands {@code element}: the edge, and for the bottom which side of the keyboard. */
    @NonNull
    public static Where where(@NonNull PlaceLayout layout, @NonNull Element element) {
        for (Edge edge : Edge.values()) {
            if (!EdgeStackPolicy.stack(layout, edge).contains(element)) continue;
            switch (edge) {
                case TOP: return Where.TOP;
                case LEFT: return Where.LEFT;
                case RIGHT: return Where.RIGHT;
                default:
                    return EdgeStackPolicy.standsUnderKeyboard(layout, element)
                        ? Where.BOTTOM_UNDER : Where.BOTTOM_OVER;
            }
        }
        return Where.NONE;
    }

    /**
     * The one place {@code element}'s host stands for both layouts. Where the front layout stands
     * it, there; where only the other layout stands it in the band over the keyboard, there too (a
     * lane of the other side), and a row the other layout stands anywhere else is left to the
     * front's. Where the two layouts stand it in two different places ({@link #conflicts}) the
     * front layout decides, as it did before lanes.
     */
    @NonNull
    public static Where resolve(@NonNull Element element, @NonNull PlaceLayout front,
                                @NonNull PlaceLayout other) {
        Where mine = where(front, element);
        if (mine != Where.NONE) return mine;
        Where theirs = where(other, element);
        return theirs == Where.BOTTOM_OVER ? theirs : Where.NONE;
    }

    /** Whether both layouts stand {@code element}, in two different places. */
    public static boolean conflicts(@NonNull Element element, @NonNull PlaceLayout front,
                                    @NonNull PlaceLayout other) {
        Where mine = where(front, element);
        Where theirs = where(other, element);
        return mine != Where.NONE && theirs != Where.NONE && mine != theirs;
    }

    /**
     * The hosts standing in {@code where}, outermost first, for both layouts: the front layout's
     * own order, then the rows only the other layout stands there, each put after the row it
     * follows in the other layout's order (first when none of its predecessors is here). The
     * status bar is shared chrome that keeps today's per-page behaviour: the front layout's alone.
     */
    @NonNull
    public static List<Element> hostsOn(@NonNull PlaceLayout front, @NonNull PlaceLayout other,
                                        @NonNull Where where) {
        List<Element> out = new ArrayList<>(4);
        for (Element element : listFor(front, where)) {
            if (element == Element.STATUS || resolve(element, front, other) == where)
                out.add(element);
        }
        List<Element> theirs = listFor(other, where);
        for (int i = 0; i < theirs.size(); i++) {
            Element element = theirs.get(i);
            if (element == Element.STATUS || out.contains(element)
                || resolve(element, front, other) != where) continue;
            int at = 0;
            for (int j = i - 1; j >= 0; j--) {
                int found = out.indexOf(theirs.get(j));
                if (found >= 0) {
                    at = found + 1;
                    break;
                }
            }
            out.add(at, element);
        }
        return out;
    }

    @NonNull
    private static List<Element> listFor(@NonNull PlaceLayout layout, @NonNull Where where) {
        switch (where) {
            case BOTTOM_OVER: return EdgeStackPolicy.overKeyboard(layout);
            case BOTTOM_UNDER: return EdgeStackPolicy.underKeyboard(layout);
            case TOP: return EdgeStackPolicy.stack(layout, Edge.TOP);
            case LEFT: return EdgeStackPolicy.stack(layout, Edge.LEFT);
            case RIGHT: return EdgeStackPolicy.stack(layout, Edge.RIGHT);
            default: return new ArrayList<>(0);
        }
    }

    /**
     * Whether the band over the keyboard holds a row of one side's own, for the layout standing in
     * front: only then does a lane have anything to do, and nothing else changes from before.
     */
    public static boolean hasLanes(@NonNull PlaceLayout front, @NonNull PlaceLayout other,
                                   @NonNull Side frontSide) {
        PlaceLayout home = frontSide == Side.HOME ? front : other;
        PlaceLayout workspace = frontSide == Side.HOME ? other : front;
        for (Element element : ROWS) {
            if (laneOf(element, home, workspace) == STACK) continue;
            if (resolve(element, front, other) == Where.BOTTOM_OVER) return true;
        }
        return false;
    }

    /**
     * The side whose lane {@code element} stands in over the keyboard, read from Home's layout and
     * the Workspace's alone, so it is the same from either front and whatever layout a crossing
     * composes; null for a row of the shared stack, one the two layouts stand in two places, or
     * one not in the band over the keyboard.
     */
    @androidx.annotation.Nullable
    public static Side laneSide(@NonNull Element element, @NonNull PlaceLayout home,
                                @NonNull PlaceLayout workspace) {
        if (resolve(element, home, workspace) != Where.BOTTOM_OVER
            || conflicts(element, home, workspace)) return null;
        switch (laneOf(element, home, workspace)) {
            case HOME: return Side.HOME;
            case WORKSPACE: return Side.WORKSPACE;
            default: return null;
        }
    }

    /**
     * {@code rest}'s hosts with those of {@code crossing} that it lacks, each put after the row it
     * follows in {@code crossing} (first when none of its predecessors is here): the band a
     * crossing's composite layout stands beside both sides' rests, which it never takes a row from.
     */
    @NonNull
    public static List<Element> standWith(@NonNull List<Element> rest,
                                          @NonNull List<Element> crossing) {
        List<Element> out = new ArrayList<>(rest);
        for (int i = 0; i < crossing.size(); i++) {
            Element element = crossing.get(i);
            if (out.contains(element)) continue;
            int at = 0;
            for (int j = i - 1; j >= 0; j--) {
                int found = out.indexOf(crossing.get(j));
                if (found >= 0) {
                    at = found + 1;
                    break;
                }
            }
            out.add(at, element);
        }
        return out;
    }

    /**
     * How much taller than the rows a pass stands ({@code passRowsPx}) the band is, with a
     * crossing's composite rows in it: the taller of the two sides' rests less them, never
     * negative. At rest the pass stands the front's rest and this is {@link #slackPx}.
     */
    public static int unionSlackPx(int frontRowsPx, int otherRowsPx, int passRowsPx) {
        return Math.max(0, Math.max(frontRowsPx, otherRowsPx) - Math.max(0, passRowsPx));
    }

    /**
     * How much taller the container is laid out than a pass stands the Terminal's flush padding:
     * the padding it rests with less the pass's own, never negative. The padding is part of the
     * Terminal's rest, and its gate flips at the landing (Home on screen stands none), so the
     * container is laid out for it on both sides and no landing changes its height.
     */
    public static int flushSlackPx(int restFlushPx, int passFlushPx) {
        return Math.max(0, restFlushPx - Math.max(0, passFlushPx));
    }

    /**
     * How much taller the band is than the front side's rest: the other side's rows at rest less
     * the front's, never negative. The container stands this much taller and the slab starts this
     * far below its top, so what shows at rest is the front rest's stack.
     */
    public static int slackPx(int frontRowsPx, int otherRowsPx) {
        return Math.max(0, otherRowsPx - frontRowsPx);
    }

    /**
     * The band laid out for one frame of rows, top to bottom ({@code count} of them): each row's
     * top in the band into {@code topOut}, and the band's height returned. A row of a lane stands
     * on the rows below it that its side shows (the shared rows and its own lane), so a lane's
     * rows are stacked bottom-up from the band's bottom; a shared row stands on the rows below it
     * that the front side shows ({@code frontLane}). The band is the taller of the two sides'
     * stacks. With every row shared it is the plain column. A row that is gone passes height 0.
     */
    public static int layoutBand(@NonNull int[] heightPx, @NonNull int[] lane, int count,
                                 int frontLane, @NonNull int[] topOut) {
        int band = Math.max(sideTotal(heightPx, lane, count, HOME),
            sideTotal(heightPx, lane, count, WORKSPACE));
        int homeBelow = 0;
        int workspaceBelow = 0;
        for (int i = count - 1; i >= 0; i--) {
            int own = lane[i] == STACK ? frontLane : lane[i];
            int under = own == HOME ? homeBelow : workspaceBelow;
            topOut[i] = band - under - heightPx[i];
            if (lane[i] != WORKSPACE) homeBelow += heightPx[i];
            if (lane[i] != HOME) workspaceBelow += heightPx[i];
        }
        return band;
    }

    private static int sideTotal(int[] heightPx, int[] lane, int count, int side) {
        int total = 0;
        for (int i = 0; i < count; i++) {
            if (lane[i] == STACK || lane[i] == side) total += heightPx[i];
        }
        return total;
    }
}
