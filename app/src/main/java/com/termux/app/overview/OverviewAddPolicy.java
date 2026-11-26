package com.termux.app.overview;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

/**
 * The + card's rows (spec overview-2026-11-24, "The + button"): which of Home page, Workspace and
 * Assistant it lists, which needs a deliberate press, and which card a new page is found as after
 * the wall is read again. Pure.
 */
public final class OverviewAddPolicy {

    private OverviewAddPolicy() {}

    /** One row of the card. */
    public enum Row { HOME_PAGE, WORKSPACE, ASSISTANT }

    /** Where a new page goes when the card in focus has no wall page of its own. */
    public static final int END = -1;

    /**
     * The rows in order. Home page goes while the wall shows no Home pages (Use as Terminal only);
     * Assistant goes while the wall has one, as only one can stand; Workspace is always offered.
     */
    @NonNull
    public static List<Row> rows(boolean homePagesShown, boolean hasAssistant) {
        List<Row> rows = new ArrayList<>(3);
        if (homePagesShown) rows.add(Row.HOME_PAGE);
        rows.add(Row.WORKSPACE);
        if (!hasAssistant) rows.add(Row.ASSISTANT);
        return rows;
    }

    /**
     * Whether the row is added by holding it rather than a tap: a Home page changes what the
     * launcher is made of, so it asks for more than a new terminal or an Assistant does.
     */
    public static boolean requiresHold(@NonNull Row row) {
        return row == Row.HOME_PAGE;
    }

    /**
     * The wall page a new Home page or Assistant goes after: the page of the card in focus, or
     * {@link #END} when that card stands for no wall page.
     */
    public static int afterPageId(int focusPageId) {
        return focusPageId >= 0 ? focusPageId : END;
    }

    /** One card's identity across two readings of the wall: its wall page and window place. */
    public static long key(int pageId, int index) {
        return ((long) pageId << 32) | (index & 0xFFFFFFFFL);
    }

    /**
     * The index in {@code after} of the card that was not in {@code before}, where the strip
     * scrolls to; with more than one new, the last (a Workspace's windows land after the ones
     * before). -1 when nothing new stands.
     */
    public static int addedIndex(@NonNull long[] before, @NonNull long[] after) {
        int found = -1;
        for (int i = 0; i < after.length; i++) {
            boolean known = false;
            for (long old : before) {
                if (old == after[i]) {
                    known = true;
                    break;
                }
            }
            if (!known) found = i;
        }
        return found;
    }
}
