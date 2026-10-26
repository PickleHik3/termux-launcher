package com.termux.app.launcher.home;

import androidx.annotation.NonNull;

/**
 * Edit pages: what adding, removing and reordering Home pages does to everything that names a page
 * by its number — the widgets, icons and folders, the floats, and the default page the Home press
 * lands on. Pure: each operation is a mapping from a page's old number to its new one, which every
 * owner applies to its own records.
 *
 * <p>There is no cap on the number of Home pages; the last page is never removed.
 */
public final class HomePageEdits {

    private HomePageEdits() {}

    /** A mapping's value for a page that is gone. */
    public static final int REMOVED = -1;

    /** Whether page {@code from} can move to {@code to} among {@code count} pages. */
    public static boolean canMove(int count, int from, int to) {
        return from >= 0 && from < count && to >= 0 && to < count && from != to;
    }

    /** Where each of {@code count} pages stands after page {@code from} moves to {@code to}. */
    @NonNull
    public static int[] moveMapping(int count, int from, int to) {
        int[] mapping = identity(count);
        if (!canMove(count, from, to)) return mapping;
        for (int page = 0; page < count; page++) {
            if (page == from) mapping[page] = to;
            else if (from < to && page > from && page <= to) mapping[page] = page - 1;
            else if (from > to && page >= to && page < from) mapping[page] = page + 1;
        }
        return mapping;
    }

    /** Whether one of {@code count} pages can be removed: never the last one. */
    public static boolean canRemove(int count) {
        return count > 1;
    }

    /** Whether removing a page holding {@code items} things (widgets, icons, folders, floats) asks first. */
    public static boolean asksBeforeRemoving(int items) {
        return items > 0;
    }

    /** Where each of {@code count} pages stands after page {@code removed} goes; {@link #REMOVED} for it. */
    @NonNull
    public static int[] removeMapping(int count, int removed) {
        int[] mapping = identity(count);
        if (removed < 0 || removed >= count || !canRemove(count)) return mapping;
        for (int page = 0; page < count; page++) {
            if (page == removed) mapping[page] = REMOVED;
            else if (page > removed) mapping[page] = page - 1;
        }
        return mapping;
    }

    /**
     * Where something on page {@code page} stands after {@code mapping}: its page's new number,
     * or, for a page that is gone, the page now in its place (the one after it, or the new last).
     * A shell in a float is never lost with its page.
     */
    public static int remap(int page, @NonNull int[] mapping) {
        if (page < 0 || page >= mapping.length) return page;
        if (mapping[page] != REMOVED) return mapping[page];
        int remaining = 0;
        for (int value : mapping) if (value != REMOVED) remaining++;
        return Math.max(0, Math.min(page, remaining - 1));
    }

    /**
     * A page's new number in a layout that may have more pages than {@code mapping} covers (the
     * other orientation's): its mapped number, {@link #REMOVED} for the page taken away, and a
     * page past the mapping moved down by as many pages as were taken away.
     */
    public static int renumber(int page, @NonNull int[] mapping) {
        if (page < 0) return page;
        if (page < mapping.length) return mapping[page];
        return page - removedCount(mapping);
    }

    /** How many pages a layout of {@code pageCount} keeps after {@code mapping}; at least one. */
    public static int pageCountAfter(int pageCount, @NonNull int[] mapping) {
        int removed = 0;
        for (int page = 0; page < Math.min(pageCount, mapping.length); page++) {
            if (mapping[page] == REMOVED) removed++;
        }
        return Math.max(1, pageCount - removed);
    }

    private static int removedCount(@NonNull int[] mapping) {
        int removed = 0;
        for (int value : mapping) if (value == REMOVED) removed++;
        return removed;
    }

    @NonNull
    private static int[] identity(int count) {
        int[] mapping = new int[Math.max(0, count)];
        for (int page = 0; page < mapping.length; page++) mapping[page] = page;
        return mapping;
    }

    /** The default page, held to the pages there are. */
    public static int clampDefault(int defaultPage, int count) {
        return Math.max(0, Math.min(Math.max(1, count) - 1, defaultPage));
    }
}
