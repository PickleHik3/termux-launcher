package com.termux.app.launcher.pin;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.launcher.widget.HomeItemPlacementPolicy;
import com.termux.app.launcher.widget.WidgetGridDefinition;

/**
 * What the confirm card says and where a pinned item goes, with no view or platform object in
 * sight. An app asking to pin a shortcut or a widget is answered here: the card's state (ready,
 * locked, too big for Home), the place it lands ({@link HomeItemPlacementPolicy}: the page in
 * front, else the first page with room, else a new page).
 */
public final class HomePinPolicy {

    /** What the card offers. */
    public enum Card {
        /** "Add to Home" is live. */
        READY,
        /** Home's layout is locked: the card says so and offers Unlock, which adds afterwards. */
        LOCKED,
        /** The item is larger than Home's whole grid, so no page could take it. */
        TOO_BIG
    }

    /** How a finished add is worded. */
    public enum Landed { ON_PAGE, ON_NEW_PAGE, NOWHERE }

    private HomePinPolicy() {}

    /** The card's state for an item of {@code columns}×{@code rows} cells. */
    @NonNull
    public static Card card(boolean layoutLocked, @NonNull WidgetGridDefinition grid, int columns,
                            int rows) {
        if (layoutLocked) return Card.LOCKED;
        if (columns > grid.columns || rows > grid.rows || columns < 1 || rows < 1) {
            return Card.TOO_BIG;
        }
        return Card.READY;
    }

    /**
     * The first free place for the item: {@code currentPage} first, then each other page, then a
     * page after the last. Null only when it is larger than the whole grid.
     */
    @Nullable
    public static HomeItemPlacementPolicy.Choice place(@NonNull WidgetGridDefinition grid,
                                                       int pageCount, int currentPage,
                                                       @NonNull HomeItemPlacementPolicy.Pages pages,
                                                       int columns, int rows) {
        return HomeItemPlacementPolicy.place(grid, pageCount, currentPage, pages, columns, rows);
    }

    /**
     * The cells a provider's minimum of {@code dp} takes by the classic home-screen rule (70dp a
     * cell less 30), for when Home's own grid has not been measured. At least one.
     */
    public static int classicCells(int dp) {
        return Math.max(1, (Math.max(0, dp) + 30) / 70);
    }

    /** Whether the item went to {@code placedPage} of a Home that had {@code pageCountBefore}. */
    @NonNull
    public static Landed landed(int placedPage, int pageCountBefore) {
        if (placedPage < 0) return Landed.NOWHERE;
        return placedPage >= pageCountBefore ? Landed.ON_NEW_PAGE : Landed.ON_PAGE;
    }
}
