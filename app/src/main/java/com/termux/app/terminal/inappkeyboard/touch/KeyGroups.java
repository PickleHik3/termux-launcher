package com.termux.app.terminal.inappkeyboard.touch;

import juloo.keyboard2.TapGeometry;

import java.util.Arrays;

/**
 * Which correctable keys pool their landing offsets, taken from the geometry alone so it works on
 * any layout: each row splits into the left, middle and right thirds of the keyboard's width, and
 * a key {@link #WIDE_KEY_WIDTHS} or more key widths wide is a group of its own.
 *
 * <p>Also the grid's neighbour rule for take-backs. Pure arithmetic; no Android types.
 */
public final class KeyGroups {

    /** A key at least this many standard key widths wide is wide: its own group, vertical only. */
    static final float WIDE_KEY_WIDTHS = 2f;
    /** Slack, in key widths, under which two key rectangles count as touching. */
    static final float NEIGHBOUR_SLACK = 0.05f;
    private static final float EPSILON = 1e-3f;

    /** Group of each key; -1 for keys that are not correctable. */
    final int[] group;
    /** Whether each key is wide. */
    final boolean[] wide;
    /** Correctable members of each group. */
    final int[] members;
    final int groupCount;

    private KeyGroups(int[] group, boolean[] wide, int[] members, int groupCount) {
        this.group = group;
        this.wide = wide;
        this.members = members;
        this.groupCount = groupCount;
    }

    public static KeyGroups of(TapGeometry geometry) {
        int n = geometry.keyCount;
        float minLeft = Float.MAX_VALUE;
        float maxRight = -Float.MAX_VALUE;
        int rows = 0;
        for (int i = 0; i < n; i++) {
            minLeft = Math.min(minLeft, geometry.left[i]);
            maxRight = Math.max(maxRight, geometry.right[i]);
            rows = Math.max(rows, geometry.row[i] + 1);
        }
        float width = Math.max(EPSILON, maxRight - minLeft);
        // Thirds first, rows * 3 ids; wide keys are numbered after them.
        int[] thirdIds = new int[rows * 3];
        Arrays.fill(thirdIds, -1);
        int[] group = new int[n];
        boolean[] wide = new boolean[n];
        int[] members = new int[n];
        int groupCount = 0;
        for (int i = 0; i < n; i++) {
            wide[i] = isWide(geometry, i);
            if (!geometry.correctable[i]) {
                group[i] = -1;
                continue;
            }
            int id;
            if (wide[i]) {
                id = groupCount++;
            } else {
                int third = (int) ((geometry.centerX(i) - minLeft) / width * 3f);
                third = Math.max(0, Math.min(2, third));
                int slot = Math.max(0, geometry.row[i]) * 3 + third;
                if (thirdIds[slot] < 0)
                    thirdIds[slot] = groupCount++;
                id = thirdIds[slot];
            }
            group[i] = id;
            members[id]++;
        }
        return new KeyGroups(group, wide, members, groupCount);
    }

    /** Whether key {@code i} is wide: its own group, and it learns its vertical offset only. */
    public static boolean isWide(TapGeometry geometry, int i) {
        return geometry.width(i) >= WIDE_KEY_WIDTHS - EPSILON;
    }

    /** Whether keys {@code a} and {@code b} touch on the grid, edges or corners. */
    public static boolean adjacent(TapGeometry geometry, int a, int b) {
        if (a == b)
            return false;
        return geometry.left[a] <= geometry.right[b] + NEIGHBOUR_SLACK
            && geometry.left[b] <= geometry.right[a] + NEIGHBOUR_SLACK
            && geometry.top[a] <= geometry.bottom[b] + NEIGHBOUR_SLACK
            && geometry.top[b] <= geometry.bottom[a] + NEIGHBOUR_SLACK;
    }
}
