package com.termux.app.launcher.widget;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * Where an item put on Home from outside the page lands (an app icon from the drawer or the Home
 * sheet): the page in front when it has room, else the first page that has, else a new page
 * after the last. Pure, so the order is testable without a repository or a view.
 */
public final class HomeItemPlacementPolicy {

    /** What a page holds, for the placement to read. */
    public interface Pages {
        @NonNull List<LauncherWidgetRecord> onPage(int page);
    }

    /** The page and cell an item lands at. {@link #page} equal to the page count is a new page. */
    public static final class Choice {
        public final int page;
        @NonNull public final WidgetCellRect cell;
        /** True when the item needs a page appended after the last one. */
        public final boolean appendsPage;

        Choice(int page, @NonNull WidgetCellRect cell, boolean appendsPage) {
            this.page = page;
            this.cell = cell;
            this.appendsPage = appendsPage;
        }
    }

    private HomeItemPlacementPolicy() {}

    /**
     * The first place for a {@code columnSpan}×{@code rowSpan} item: {@code preferredPage} (clamped
     * into the pages there are), then every other page from the first, then a new page. Null only
     * when the span is larger than the whole grid, so no page anywhere could take it.
     */
    @Nullable
    public static Choice place(@NonNull WidgetGridDefinition grid, int pageCount,
                               int preferredPage, @NonNull Pages pages,
                               int columnSpan, int rowSpan) {
        int count = Math.max(1, pageCount);
        int preferred = Math.max(0, Math.min(count - 1, preferredPage));
        WidgetGridPlacementPolicy.Result first = WidgetGridPlacementPolicy.findPlacement(grid,
            pages.onPage(preferred), columnSpan, rowSpan);
        if (first.outcome == WidgetGridPlacementPolicy.Outcome.SPAN_EXCEEDS_GRID) return null;
        if (first.rect != null) return new Choice(preferred, first.rect, false);
        for (int page = 0; page < count; page++) {
            if (page == preferred) continue;
            WidgetGridPlacementPolicy.Result found = WidgetGridPlacementPolicy.findPlacement(grid,
                pages.onPage(page), columnSpan, rowSpan);
            if (found.rect != null) return new Choice(page, found.rect, false);
        }
        WidgetGridPlacementPolicy.Result fresh = WidgetGridPlacementPolicy.findPlacement(grid,
            Collections.<LauncherWidgetRecord>emptyList(), columnSpan, rowSpan);
        return fresh.rect == null ? null : new Choice(count, fresh.rect, true);
    }
}
