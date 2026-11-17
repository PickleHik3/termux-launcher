package com.termux.app.dock;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;
import com.termux.app.page.Side;
import com.termux.app.place.Element;

/**
 * Where each side's rows are drawn (ADR 0030 section 2): the one place readers find a row by id,
 * through the side that owns it. A row has its view, the host it stands in along the bottom (in the
 * stack's row stack), and the portable host it is lent to on any other edge. 0 where the side has
 * no such view. Home's own extra-keys bar is the Home pill, a view of its own; the Workspace's is
 * the toolbar pager. The apps row is Home's; a Workspace page that shows it shares Home's view, and
 * the stack keeps it put on such a crossing ({@code SideRows}).
 */
public final class SideRowViews {

    private SideRowViews() {}

    /** Which of a row's views. */
    public enum Part {
        /** The row itself. */
        ROW,
        /** The host it stands in along the bottom. */
        BOTTOM_HOST,
        /** The portable host it is lent to on another edge. */
        EDGE_HOST
    }

    /** The id of {@code side}'s {@code row} as {@code part}; 0 where it has none. */
    public static int id(@NonNull Side side, @NonNull Side.Row row, @NonNull Part part) {
        if (!side.owns(row)) return 0;
        switch (row) {
            case APPS:
                return part == Part.ROW ? R.id.apps_bar_viewpager
                    : part == Part.BOTTOM_HOST ? R.id.apps_bar_row_host : R.id.place_apps_bar_host;
            case AZ:
                return part == Part.ROW ? R.id.apps_bar_az_row
                    : part == Part.BOTTOM_HOST ? R.id.apps_bar_az_host : R.id.place_az_bar_host;
            case EXTRA_KEYS:
                if (side == Side.HOME) return part == Part.ROW ? R.id.home_pill_layer : 0;
                return part == Part.ROW ? R.id.terminal_toolbar_view_pager
                    : part == Part.BOTTOM_HOST ? R.id.terminal_toolbar_host
                    : R.id.place_extra_keys_host;
            case WINDOW_CHIPS:
                return part == Part.ROW ? R.id.terminal_window_bar : 0;
            default:
                return 0;
        }
    }

    /** The side whose view the stack's {@code element} is: the Workspace's extra keys, Home's others. */
    @NonNull
    public static Side sideOf(@NonNull Element element) {
        return element == Element.EXTRA_KEYS ? Side.WORKSPACE : Side.HOME;
    }

    /** The row a stack's {@code element} is; null for the status bar, which no side owns. */
    @Nullable
    public static Side.Row rowOf(@NonNull Element element) {
        switch (element) {
            case APPS: return Side.Row.APPS;
            case AZ: return Side.Row.AZ;
            case EXTRA_KEYS: return Side.Row.EXTRA_KEYS;
            default: return null;
        }
    }

    /** The id of the stack's {@code element} as {@code part}, through its side; 0 for the status bar. */
    public static int id(@NonNull Element element, @NonNull Part part) {
        Side.Row row = rowOf(element);
        return row == null ? 0 : id(sideOf(element), row, part);
    }
}
