package com.termux.app.launcher.widget;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.launcher.home.HomePageEdits;
import com.termux.app.launcher.model.AppRef;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Putting back an icon or folder dropped on Remove. The record is kept whole as it was (page,
 * cell, a folder's name and apps), with whether taking it away also took its page away; the Undo
 * puts it back exactly there when it can. When the place has been taken since, it goes to the
 * nearest free cell on its page, and when the page has no room, wherever a new Home item would go
 * ({@link HomeItemPlacementPolicy}).
 */
public final class HomeItemUndo {

    /** How long the Undo stays on offer: Launcher3's snackbar is about this long. */
    public static final long HOLD_MS = 5000L;

    /** What the Remove took away. */
    public static final class Snapshot {
        @NonNull public final LauncherWidgetRecord record;
        /** The item was the last thing on its page, and the page went with it. */
        public final boolean pageRemoved;

        public Snapshot(@NonNull LauncherWidgetRecord record, boolean pageRemoved) {
            this.record = record;
            this.pageRemoved = pageRemoved;
        }
    }

    /** Where the item goes back to. */
    public static final class Plan {
        public final int page;
        @NonNull public final WidgetCellRect cell;
        /** A page is put back at {@link #page} first, the pages from there on moving up one. */
        public final boolean insertsPage;
        /** A page is added after the last for it ({@link #page} is the page count). */
        public final boolean appendsPage;

        Plan(int page, @NonNull WidgetCellRect cell, boolean insertsPage, boolean appendsPage) {
            this.page = page;
            this.cell = cell;
            this.insertsPage = insertsPage;
            this.appendsPage = appendsPage;
        }
    }

    /** What the Undo did: the item as stored again, and how the pages were renumbered, if they were. */
    public static final class Result {
        @NonNull public final LauncherWidgetRecord record;
        /** Each old page's new number when a page was put back in the middle, else null. */
        @Nullable public final int[] renumbered;

        Result(@NonNull LauncherWidgetRecord record, @Nullable int[] renumbered) {
            this.record = record;
            this.renumbered = renumbered;
        }
    }

    private HomeItemUndo() {}

    /** Where {@code snapshot} goes back to on a Home of {@code pageCount} pages; null if nowhere. */
    @Nullable
    public static Plan plan(@NonNull Snapshot snapshot, @NonNull WidgetGridDefinition grid,
                            int pageCount, @NonNull HomeItemPlacementPolicy.Pages pages) {
        WidgetCellRect cell = snapshot.record.cell;
        int columns = cell.columnSpan(), rows = cell.rowSpan();
        int count = Math.max(1, pageCount);
        int page = snapshot.record.page;
        List<LauncherWidgetRecord> none = Collections.emptyList();
        if (snapshot.pageRemoved) {
            // Its page went with it: a page goes back where it stood, and the item into its cell.
            WidgetCellRect fits = WidgetGridPlacementPolicy.canPlace(grid, none, cell,
                WidgetGridPlacementPolicy.IGNORE_NONE) ? cell
                : WidgetGridPlacementPolicy.findPlacement(grid, none, columns, rows).rect;
            if (fits == null) return null;
            int at = Math.max(0, Math.min(count, page));
            return new Plan(at, fits, at < count, at == count);
        }
        if (page >= 0 && page < count) {
            List<LauncherWidgetRecord> onPage = pages.onPage(page);
            if (WidgetGridPlacementPolicy.canPlace(grid, onPage, cell,
                    WidgetGridPlacementPolicy.IGNORE_NONE)) {
                return new Plan(page, cell, false, false);
            }
            WidgetCellRect near = columns == 1 && rows == 1
                ? HomeAppDropPolicy.nearestFreeCell(grid.columns, grid.rows, cellsOf(onPage),
                    cell.left + 0.5f, cell.top + 0.5f)
                : WidgetGridPlacementPolicy.findPlacement(grid, onPage, columns, rows).rect;
            if (near != null) return new Plan(page, near, false, false);
        }
        HomeItemPlacementPolicy.Choice choice = HomeItemPlacementPolicy.place(grid, count, page,
            pages, columns, rows);
        if (choice == null) return null;
        return new Plan(choice.page, choice.cell, false, choice.appendsPage);
    }

    /**
     * Puts {@code snapshot} back in {@code repository} as {@link #plan} says, under its own key
     * unless something has taken that key since. Null, with nothing changed, when there is nowhere
     * to put it or the store refused.
     */
    @Nullable
    public static Result restore(@NonNull LauncherWidgetRepository repository,
                                 @NonNull Snapshot snapshot) {
        LauncherWidgetRecord was = snapshot.record;
        int count = repository.pageCount();
        Plan plan = plan(snapshot, repository.gridDefinition(), count, repository::recordsOnPage);
        if (plan == null) return null;
        int id = repository.get(was.appWidgetId) == null ? was.appWidgetId
            : repository.allocateBuiltinId();
        LauncherWidgetRecord record = rekeyed(was, id).withCell(plan.cell);
        if (!plan.insertsPage && !plan.appendsPage) {
            return repository.putRecord(record.withPage(plan.page)) ? new Result(record.withPage(
                plan.page), null) : null;
        }
        // The page is added after the last, with the item on it, then moved to where it stood: an
        // empty page could be trimmed between the two writes, a page with the item on it cannot.
        int fresh = repository.addPage();
        if (fresh < 0) return null;
        if (!repository.putRecord(record.withPage(fresh))) {
            repository.setPageCount(count);
            return null;
        }
        if (!plan.insertsPage || plan.page >= fresh || !repository.movePage(fresh, plan.page)) {
            return new Result(record.withPage(fresh), null);
        }
        return new Result(record.withPage(plan.page),
            HomePageEdits.moveMapping(fresh + 1, fresh, plan.page));
    }

    /** {@code record} under {@code id}: itself when the key is its own. */
    @NonNull
    static LauncherWidgetRecord rekeyed(@NonNull LauncherWidgetRecord record, int id) {
        if (id == record.appWidgetId) return record;
        if (record.isFolder()) {
            return LauncherWidgetRecord.folder(id, record.folderName(), record.folderApps(),
                record.cell, record.page);
        }
        AppRef app = record.appRef();
        if (record.isApp() && app != null) {
            return LauncherWidgetRecord.app(id, app, record.cell, record.page, record.sizeOptions());
        }
        return record;
    }

    @NonNull
    private static List<WidgetCellRect> cellsOf(@NonNull List<LauncherWidgetRecord> records) {
        List<WidgetCellRect> cells = new ArrayList<>(records.size());
        for (LauncherWidgetRecord record : records) cells.add(record.cell);
        return cells;
    }
}
