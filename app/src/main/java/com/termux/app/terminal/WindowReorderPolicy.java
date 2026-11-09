package com.termux.app.terminal;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;
import java.util.Locale;

/**
 * Reordering the Workspace's row: by dragging a window's chip along the window bar, and by name
 * ({@code window.move}). Arithmetic only — the bar measures its chips and moves its views, the
 * activity moves the windows; this decides where everything goes.
 *
 * <p>Coordinates run along the row in reading order: a right-to-left bar mirrors its x before it
 * asks, so chip 0 is always the one with the smallest {@code lefts} value here.
 *
 * <p>The rules:
 * <ul>
 *   <li>A lifted chip lands among the others by its centre. It passes a neighbour only once its
 *   centre is past that neighbour's resting centre by {@code margin}, and passes back only once it
 *   is that far on the other side, so a finger resting on the boundary does not flicker the order.
 *   <li>Only chips that can move take part. A chip that cannot (a Home float's) never lifts, never
 *   shifts, and the lifted chip stays inside the run of movable chips around it.
 *   <li>While the lifted chip hovers at an index, the others stand where they would stand with it
 *   dropped there: packed in the new order, each slot keeping the gap that slot had at rest.
 *   <li>Near either end of an overflowing bar the strip scrolls that way, faster the deeper the
 *   finger is into the edge, never past either end.
 * </ul>
 */
public final class WindowReorderPolicy {

    private WindowReorderPolicy() {}

    /** Sentinel of {@link #targetIndex}: the argument names no place in the row. */
    public static final int INVALID = -1;

    /** Whether chip {@code index} may be lifted: it exists, it can move, and it has company. */
    public static boolean canLift(int index, int count, @Nullable boolean[] movable) {
        if (index < 0 || index >= count || !isMovable(index, movable)) return false;
        int[] run = runAround(index, count, movable);
        return run[1] > run[0];
    }

    /**
     * The index the lifted chip would drop at.
     *
     * @param lefts the chips' resting left edges, in row order
     * @param rights the chips' resting right edges, in row order
     * @param from the lifted chip's index at rest
     * @param liftedCentre where the lifted chip's centre is now
     * @param previous the index it was hovering at last time; {@code from} at the lift
     * @param margin how far past a neighbour's centre the lifted centre has to go to pass it
     * @param movable which chips can move, or null when every chip can
     */
    public static int dropIndex(@NonNull int[] lefts, @NonNull int[] rights, int from,
                                float liftedCentre, int previous, float margin,
                                @Nullable boolean[] movable) {
        int count = Math.min(lefts.length, rights.length);
        if (!canLift(from, count, movable)) return from;
        int[] run = runAround(from, count, movable);
        int target = Math.max(run[0], Math.min(previous, run[1]));
        float slack = Math.max(0f, margin);
        // On: the chip the lifted one passes next is the one standing just after its slot. Back:
        // the one standing just before it. Both are judged by where they rest.
        while (target < run[1]
            && liftedCentre > centre(lefts, rights, otherAt(target + 1, from, target)) + slack) {
            target++;
        }
        while (target > run[0]
            && liftedCentre < centre(lefts, rights, otherAt(target - 1, from, target)) - slack) {
            target--;
        }
        return target;
    }

    /**
     * Where each chip's left edge stands while the chip lifted from {@code from} hovers at
     * {@code to}: the run packed in that order from its first left edge, every slot keeping its
     * resting gap. The lifted chip's own entry is its drop slot, where it settles on release.
     * Chips outside the movable run keep their resting place.
     */
    @NonNull
    public static int[] shiftedLefts(@NonNull int[] lefts, @NonNull int[] rights, int from, int to,
                                     @Nullable boolean[] movable) {
        int count = Math.min(lefts.length, rights.length);
        int[] out = new int[count];
        System.arraycopy(lefts, 0, out, 0, count);
        if (!canLift(from, count, movable)) return out;
        int[] run = runAround(from, count, movable);
        int target = Math.max(run[0], Math.min(to, run[1]));
        int x = lefts[run[0]];
        for (int slot = run[0]; slot <= run[1]; slot++) {
            int chip = slot == target ? from : otherAt(slot, from, target);
            out[chip] = x;
            x += rights[chip] - lefts[chip];
            if (slot < run[1]) x += lefts[slot + 1] - rights[slot];
        }
        return out;
    }

    /**
     * How far the strip scrolls this frame while a chip is held at {@code fingerX} (in the bar's
     * viewport, 0 at its start): negative toward the start, positive toward the end, 0 while the
     * finger is clear of both edges or the strip cannot go further that way.
     *
     * @param edge how deep the scrolling zone is at each end (at most half the viewport)
     * @param maxStep the step at the very edge; it shrinks linearly to 1 at the zone's inner side
     */
    public static int autoScrollStep(float fingerX, int viewport, int edge, int maxStep,
                                     int scrollX, int maxScroll) {
        if (viewport <= 0 || edge <= 0 || maxStep <= 0 || maxScroll <= 0) return 0;
        int zone = Math.min(edge, viewport / 2);
        if (zone <= 0) return 0;
        if (fingerX < zone && scrollX > 0) {
            float depth = Math.min(1f, (zone - fingerX) / zone);
            return -Math.min(Math.max(1, Math.round(maxStep * depth)), scrollX);
        }
        if (fingerX > viewport - zone && scrollX < maxScroll) {
            float depth = Math.min(1f, (fingerX - (viewport - zone)) / zone);
            return Math.min(Math.max(1, Math.round(maxStep * depth)), maxScroll - scrollX);
        }
        return 0;
    }

    /**
     * Whether a finger holding a lifted chip has gone off the row rather than along it: further
     * up or down than {@code limit}, and more vertical than horizontal. The chip goes back.
     */
    public static boolean leavesRow(float dx, float dy, float limit) {
        return Math.abs(dy) > limit && Math.abs(dy) > Math.abs(dx);
    }

    /**
     * The index {@code window.move}'s {@code to} names for the current window, counted from 0:
     * {@code left} and {@code right} step one place (and stay put at the row's end), a number is a
     * place in the row counted from 1. {@link #INVALID} for anything else, a number out of range
     * included.
     */
    public static int targetIndex(@Nullable String to, int current, int count) {
        if (to == null || count <= 0 || current < 0 || current >= count) return INVALID;
        String value = to.trim().toLowerCase(Locale.ROOT);
        if (value.equals("left")) return Math.max(0, current - 1);
        if (value.equals("right")) return Math.min(count - 1, current + 1);
        int place;
        try {
            place = Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return INVALID;
        }
        return place >= 1 && place <= count ? place - 1 : INVALID;
    }

    /**
     * Moves the window at {@code from} to {@code to} in {@code row} and returns where the current
     * window ({@code current} before the move) stands afterwards: the same window stays current,
     * its index follows it. Out-of-range indices change nothing and return {@code current}.
     */
    public static <T> int move(@NonNull List<T> row, int from, int to, int current) {
        int count = row.size();
        if (from < 0 || from >= count || to < 0 || to >= count || from == to) return current;
        row.add(to, row.remove(from));
        return currentAfterMove(from, to, current);
    }

    /** Where the window at {@code current} stands once the one at {@code from} moved to {@code to}. */
    public static int currentAfterMove(int from, int to, int current) {
        if (from == to) return current;
        if (current == from) return to;
        if (from < to && current > from && current <= to) return current - 1;
        if (to < from && current >= to && current < from) return current + 1;
        return current;
    }

    private static boolean isMovable(int index, @Nullable boolean[] movable) {
        return movable == null || (index < movable.length && movable[index]);
    }

    /** The first and last index of the run of movable chips containing {@code index}. */
    @NonNull
    private static int[] runAround(int index, int count, @Nullable boolean[] movable) {
        int start = index;
        while (start > 0 && isMovable(start - 1, movable)) start--;
        int end = index;
        while (end < count - 1 && isMovable(end + 1, movable)) end++;
        return new int[]{start, end};
    }

    /**
     * The chip standing in {@code slot} (never {@code target} itself) while the one lifted from
     * {@code from} sits at {@code target}: the row without the lifted chip, read with a hole at
     * {@code target}.
     */
    private static int otherAt(int slot, int from, int target) {
        int withoutLifted = slot < target ? slot : slot - 1;
        return withoutLifted >= from ? withoutLifted + 1 : withoutLifted;
    }

    private static float centre(@NonNull int[] lefts, @NonNull int[] rights, int index) {
        return (lefts[index] + rights[index]) / 2f;
    }
}
