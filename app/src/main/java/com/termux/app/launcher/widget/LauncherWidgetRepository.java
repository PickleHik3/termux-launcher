package com.termux.app.launcher.widget;

import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.SizeF;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Synchronous v4 durable store. Storage commits before the in-memory snapshot changes.
 *
 * <p>The records are always the layout of the orientation on screen: every reader and every
 * mutation sees one set of cells and pages, exactly as before. What v4 adds is a shelf of the
 * <em>other</em> orientations' layouts, put down and picked up by {@link #applyOrientation}, so a
 * turn of the screen and back finds the wall as the user left it.</p>
 */
public final class LauncherWidgetRepository {
    public interface Storage {
        @Nullable String read();
        boolean write(@NonNull String value);
    }

    private static final int SCHEMA_VERSION = 4;
    /** v3 is read as v4 with no orientation named and no shelf: identical bytes, nothing moves. */
    private static final int LEGACY_PAGED_VERSION = 3;
    private static final String PREFS = "launcher_widget_repository";
    private static final String KEY_STATE = "state";

    private final Storage storage;
    private LinkedHashMap<Integer, LauncherWidgetRecord> records = new LinkedHashMap<>();
    @Nullable private WidgetAddTransaction pending;
    @NonNull private WidgetGridDefinition grid = WidgetGridDefinition.DEFAULT;
    private int pageCount = 1;
    /** The pages added by hand that have not held a widget yet; empty, and still not trimmed. */
    @NonNull private LinkedHashSet<Integer> freshPages = new LinkedHashSet<>();
    /** The orientation the live records belong to, or null before one has been named. */
    @Nullable private String orientation;
    /** The layouts of the orientations that are not on screen; never holds {@link #orientation}. */
    @NonNull private LinkedHashMap<String, OrientationLayout> layouts = new LinkedHashMap<>();
    private long revision;
    private boolean migrationWritePending;
    private boolean readOnlyUnknownVersion;

    public LauncherWidgetRepository(@NonNull Storage storage) {
        this.storage = storage;
        load(storage.read());
        // An empty grid carries no placements worth preserving: adopt the current default
        // dimensions so a stale persisted grid (e.g. the old 6x4) cannot outlive its widgets.
        if (records.isEmpty() && pending == null
            && !grid.equals(WidgetGridDefinition.DEFAULT)) {
            commitValidated(records, null, WidgetGridDefinition.DEFAULT, pageCount, revision + 1);
        }
    }

    @NonNull public static LauncherWidgetRepository create(@NonNull Context context) {
        SharedPreferences preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return new LauncherWidgetRepository(new Storage() {
            @Override public String read() { return preferences.getString(KEY_STATE, null); }
            @Override public boolean write(@NonNull String value) {
                return preferences.edit().putString(KEY_STATE, value).commit();
            }
        });
    }

    @NonNull public synchronized List<LauncherWidgetRecord> records() {
        return Collections.unmodifiableList(new ArrayList<>(records.values()));
    }
    @Nullable public synchronized LauncherWidgetRecord get(int appWidgetId) {
        return records.get(appWidgetId);
    }
    @Nullable public synchronized WidgetAddTransaction pending() { return pending; }
    @NonNull public synchronized WidgetGridDefinition gridDefinition() { return grid; }
    public synchronized long revision() { return revision; }
    public synchronized int pageCount() { return pageCount; }
    /** The orientation the live records belong to, or null before {@link #applyOrientation}. */
    @Nullable public synchronized String orientation() { return orientation; }
    /** The orientations with a layout on the shelf, in the order they were last left. */
    @NonNull public synchronized java.util.Set<String> storedOrientations() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(layouts.keySet()));
    }

    /** Snapshot of the records on one page; the per-page collision universe. */
    @NonNull public synchronized List<LauncherWidgetRecord> recordsOnPage(int page) {
        ArrayList<LauncherWidgetRecord> out = new ArrayList<>();
        for (LauncherWidgetRecord record : records.values()) if (record.page == page) out.add(record);
        return Collections.unmodifiableList(out);
    }

    public synchronized boolean canReserve(long expectedRevision, @NonNull WidgetCellRect cell) {
        return canReserve(expectedRevision, cell, 0);
    }

    public synchronized boolean canReserve(long expectedRevision, @NonNull WidgetCellRect cell,
                                           int page) {
        return pending == null && expectedRevision == revision
            && page >= 0 && page < pageCount
            && WidgetGridPlacementPolicy.canPlace(grid, recordsOnPage(page), cell, -1);
    }

    /**
     * Adopts new grid dimensions. Every widget keeps its place while it still fits; one that no
     * longer does is shrunk to the grid and moved to the first free spot on its page, or onto a
     * new page when its page is full — the grid can always change, and no widget is lost for it.
     * Refused while an add is in flight, since its reservation was made against the old grid.
     */
    public synchronized boolean setGridDefinition(@NonNull WidgetGridDefinition next) {
        if (next.equals(grid)) return true;
        if (pending != null) return false;
        int[] pages = { pageCount };
        LinkedHashMap<Integer, LauncherWidgetRecord> relaid = reflow(records, next, pages);
        if (relaid == null) return false;
        return commitValidated(relaid, null, next, pages[0], revision + 1);
    }

    /**
     * Lays a set of records out on a grid: each keeps its place while it still fits, shrunk to the
     * grid and moved to the first free spot on its page when it does not, and onto a new page when
     * its page is full. {@code pages} carries the page count in and out. Null when a widget cannot
     * be placed at all, which leaves the caller to commit nothing.
     */
    @Nullable
    private static LinkedHashMap<Integer, LauncherWidgetRecord> reflow(
            @NonNull Map<Integer, LauncherWidgetRecord> source,
            @NonNull WidgetGridDefinition next, @NonNull int[] pages) {
        LinkedHashMap<Integer, LauncherWidgetRecord> relaid = new LinkedHashMap<>();
        for (LauncherWidgetRecord record : source.values()) {
            WidgetCellRect cell = record.cell;
            int columnSpan = Math.min(cell.columnSpan(), next.columns);
            int rowSpan = Math.min(cell.rowSpan(), next.rows);
            int left = Math.max(0, Math.min(cell.left, next.columns - columnSpan));
            int top = Math.max(0, Math.min(cell.top, next.rows - rowSpan));
            WidgetCellRect kept = new WidgetCellRect(left, top, left + columnSpan, top + rowSpan);
            int page = record.page;
            List<LauncherWidgetRecord> onPage = recordsOnPage(relaid, page);
            if (WidgetGridPlacementPolicy.canPlace(next, onPage, kept, -1)) {
                relaid.put(record.appWidgetId, record.withCell(kept));
                continue;
            }
            WidgetGridPlacementPolicy.Result placement =
                WidgetGridPlacementPolicy.findPlacement(next, onPage, columnSpan, rowSpan);
            if (placement.outcome != WidgetGridPlacementPolicy.Outcome.PLACED) {
                page = pages[0]++;
                placement = WidgetGridPlacementPolicy.findPlacement(next,
                    Collections.emptyList(), columnSpan, rowSpan);
            }
            if (placement.rect == null) return null;
            relaid.put(record.appWidgetId, record.withPage(page).withCell(placement.rect));
        }
        return relaid;
    }

    /**
     * Names the orientation on screen and brings its layout back. The orientation being left is
     * put on the shelf as it stands, and the one arriving is taken off it and laid out on
     * {@code next} — the grid that orientation is arranged for. An orientation seen for the first
     * time keeps the cells it has, so the first turn of the screen still reflows the other side's
     * layout rather than emptying the wall; every turn after that restores.
     *
     * <p>A widget that arrived while another orientation was on screen has no place on the shelf,
     * so it keeps where it is and the layout finds it room. One that left is simply not restored.
     * </p>
     *
     * <p>Returns true only when widgets moved, so a caller can redraw on exactly those turns.
     * Refused, with nothing changed, while an add is in flight — its reservation was made against
     * the layout on screen.</p>
     */
    public synchronized boolean applyOrientation(@NonNull String key,
                                                 @NonNull WidgetGridDefinition next) {
        if (key.isEmpty() || key.equals(orientation)) return false;
        if (migrationWritePending || readOnlyUnknownVersion) return false;
        if (orientation == null) {
            // Nothing has ever been shelved, so there is nothing to restore and nothing to move.
            // The grid stays the caller's to apply; all that is recorded is whose layout this is.
            commitValidated(records, pending, grid, pageCount, freshPages, key, layouts,
                revision + 1);
            return false;
        }
        if (pending != null) return false;
        LinkedHashMap<String, OrientationLayout> shelf = new LinkedHashMap<>(layouts);
        shelf.put(orientation, snapshot());
        OrientationLayout incoming = shelf.remove(key);
        int[] pages = { incoming == null ? pageCount : Math.max(1, incoming.pageCount) };
        LinkedHashMap<Integer, LauncherWidgetRecord> desired = new LinkedHashMap<>();
        for (LauncherWidgetRecord record : records.values()) {
            Placement placed = incoming == null ? null : incoming.cells.get(record.appWidgetId);
            LauncherWidgetRecord seeded = placed == null ? record
                : record.withPage(placed.page).withCell(placed.cell);
            desired.put(seeded.appWidgetId, seeded);
            pages[0] = Math.max(pages[0], seeded.page + 1);
        }
        LinkedHashMap<Integer, LauncherWidgetRecord> relaid = reflow(desired, next, pages);
        if (relaid == null) return false;
        Collection<Integer> fresh = incoming == null ? freshPages : incoming.freshPages;
        if (!commitValidated(relaid, null, next, pages[0], fresh, key, shelf, revision + 1)) {
            return false;
        }
        return true;
    }

    /** What the orientation on screen looks like right now, for the shelf. */
    private OrientationLayout snapshot() {
        LinkedHashMap<Integer, Placement> cells = new LinkedHashMap<>();
        for (LauncherWidgetRecord record : records.values()) {
            cells.put(record.appWidgetId, new Placement(record.cell, record.page));
        }
        return new OrientationLayout(grid, pageCount, new LinkedHashSet<>(freshPages), cells);
    }

    /** One widget's place in an orientation that is not on screen. */
    private static final class Placement {
        @NonNull final WidgetCellRect cell;
        final int page;
        Placement(@NonNull WidgetCellRect cell, int page) { this.cell = cell; this.page = page; }
    }

    /**
     * One orientation's shelved layout. The grid is the one its cells were arranged against; it is
     * kept for readers that need to know whether a layout still matches the grid in force, and is
     * not what {@link #applyOrientation} lays the cells out on — the caller's grid is.
     */
    private static final class OrientationLayout {
        @NonNull final WidgetGridDefinition grid;
        final int pageCount;
        @NonNull final LinkedHashSet<Integer> freshPages;
        @NonNull final LinkedHashMap<Integer, Placement> cells;
        OrientationLayout(@NonNull WidgetGridDefinition grid, int pageCount,
                          @NonNull LinkedHashSet<Integer> freshPages,
                          @NonNull LinkedHashMap<Integer, Placement> cells) {
            this.grid = grid;
            this.pageCount = Math.max(1, pageCount);
            this.freshPages = freshPages;
            this.cells = cells;
        }
    }

    private static List<LauncherWidgetRecord> recordsOnPage(
            Map<Integer, LauncherWidgetRecord> values, int page) {
        ArrayList<LauncherWidgetRecord> out = new ArrayList<>();
        for (LauncherWidgetRecord record : values.values()) if (record.page == page) out.add(record);
        return out;
    }

    /** Appends an empty page after the last one; returns the new page index or -1 on failure. */
    public synchronized int addPage() {
        return appendPage(false);
    }

    /**
     * Appends a page the user asked for by hand. It is <em>fresh</em>: empty as it is, it survives
     * {@link #trimEmptyPages()} until a widget has been on it, so a page added on purpose does not
     * vanish under the finger that asked for it. The moment a widget lands there the freshness is
     * spent, and the page comes and goes with its widgets like every other.
     */
    public synchronized int addFreshPage() {
        return appendPage(true);
    }

    private int appendPage(boolean fresh) {
        int appended = pageCount;
        LinkedHashSet<Integer> nextFresh = new LinkedHashSet<>(freshPages);
        if (fresh) nextFresh.add(appended);
        return commitValidated(records, pending, grid, pageCount + 1, nextFresh, revision + 1)
            ? appended : -1;
    }

    /** The pages added by hand that have not held a widget yet. */
    @NonNull public synchronized java.util.Set<Integer> freshPages() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(freshPages));
    }

    /**
     * Removes every empty page — leading, middle or trailing — and renumbers what is left, the way
     * {@link #removePage(int)} does for one. Two pages are kept although they hold nothing: the
     * single page a layout with no widgets at all is left with, and a page added by hand that has
     * not held a widget yet. The page a reservation is waiting on counts as held.
     */
    public synchronized boolean trimEmptyPages() {
        boolean[] keep = new boolean[pageCount];
        for (LauncherWidgetRecord record : records.values()) {
            if (record.page >= 0 && record.page < pageCount) keep[record.page] = true;
        }
        if (pending != null && pending.page >= 0 && pending.page < pageCount) {
            keep[pending.page] = true;
        }
        for (Integer page : freshPages) if (page >= 0 && page < pageCount) keep[page] = true;
        int[] renumbered = new int[pageCount];
        int kept = 0;
        for (int page = 0; page < pageCount; page++) {
            renumbered[page] = keep[page] ? kept++ : -1;
        }
        if (kept == pageCount) return true;
        // Nothing to keep means nothing to renumber either: one empty page is what is left.
        int target = Math.max(1, kept);
        LinkedHashMap<Integer, LauncherWidgetRecord> next = new LinkedHashMap<>();
        for (LauncherWidgetRecord record : records.values()) {
            next.put(record.appWidgetId, record.withPage(renumbered[record.page]));
        }
        WidgetAddTransaction nextPending = pending == null ? null
            : pending.withPage(renumbered[pending.page]);
        LinkedHashSet<Integer> nextFresh = new LinkedHashSet<>();
        for (Integer page : freshPages) {
            if (page >= 0 && page < pageCount && renumbered[page] >= 0) {
                nextFresh.add(renumbered[page]);
            }
        }
        return commitValidated(next, nextPending, grid, target, nextFresh, revision + 1);
    }

    /**
     * How many pages there are, outright. Only a restore has any business with this — putting the
     * layout back as it was when an edit session opened, page count and all; every other caller
     * goes through {@link #trimEmptyPages()}, which derives the count from the widgets.
     */
    public synchronized boolean setPageCount(int count) {
        return setPages(count, freshPages);
    }

    /** The same restore, putting back which of those pages were added by hand and still empty. */
    public synchronized boolean setPages(int count, @NonNull Collection<Integer> fresh) {
        if (count < 1) return false;
        LinkedHashSet<Integer> nextFresh = new LinkedHashSet<>();
        for (Integer page : fresh) if (page >= 0 && page < count) nextFresh.add(page);
        if (count == pageCount && nextFresh.equals(freshPages)) return true;
        return commitValidated(records, pending, grid, count, nextFresh, revision + 1);
    }

    /**
     * Removes one empty page and renumbers the pages after it. Refused for a populated page,
     * the last remaining page, or a page holding the pending reservation.
     */
    public synchronized boolean removePage(int page) {
        if (page < 0 || page >= pageCount || pageCount <= 1) return false;
        if (!recordsOnPage(page).isEmpty()) return false;
        if (pending != null && pending.page == page) return false;
        LinkedHashMap<Integer, LauncherWidgetRecord> next = new LinkedHashMap<>();
        for (LauncherWidgetRecord record : records.values()) {
            next.put(record.appWidgetId,
                record.page > page ? record.withPage(record.page - 1) : record);
        }
        WidgetAddTransaction nextPending = pending != null && pending.page > page
            ? pending.withPage(pending.page - 1) : pending;
        LinkedHashSet<Integer> nextFresh = new LinkedHashSet<>();
        for (Integer fresh : freshPages) {
            if (fresh == page) continue;
            nextFresh.add(fresh > page ? fresh - 1 : fresh);
        }
        return commitValidated(next, nextPending, grid, pageCount - 1, nextFresh, revision + 1);
    }

    public synchronized boolean putRecord(@NonNull LauncherWidgetRecord record) {
        LinkedHashMap<Integer, LauncherWidgetRecord> next = new LinkedHashMap<>(records);
        LauncherWidgetRecord existing = next.get(record.appWidgetId);
        if (existing != null && (!existing.provider.equals(record.provider)
            || existing.profileSerial != record.profileSerial)) {
            throw new IllegalArgumentException("app-widget ID already belongs to another provider");
        }
        next.put(record.appWidgetId, record);
        return commitValidated(next, pending, grid, pageCount, revision + 1);
    }

    /**
     * Commits several records at once, so a move that pushes its neighbours aside never lands
     * as a sequence of individually invalid layouts. All or nothing: one validation, one
     * revision bump, and nothing changes when the resulting layout would collide.
     */
    public synchronized boolean putRecords(@NonNull Collection<LauncherWidgetRecord> values) {
        if (values.isEmpty()) return true;
        LinkedHashMap<Integer, LauncherWidgetRecord> next = new LinkedHashMap<>(records);
        for (LauncherWidgetRecord record : values) {
            LauncherWidgetRecord existing = records.get(record.appWidgetId);
            if (existing != null && (!existing.provider.equals(record.provider)
                || existing.profileSerial != record.profileSerial)) {
                throw new IllegalArgumentException(
                    "app-widget ID already belongs to another provider");
            }
            next.put(record.appWidgetId, record);
        }
        return commitValidated(next, pending, grid, pageCount, revision + 1);
    }

    public synchronized boolean removeRecord(int appWidgetId) {
        if (!records.containsKey(appWidgetId)) return true;
        LinkedHashMap<Integer, LauncherWidgetRecord> next = new LinkedHashMap<>(records);
        next.remove(appWidgetId);
        return commitValidated(next, pending, grid, pageCount, revision + 1);
    }

    /** Compatibility/stage-update API. Initial A-3 reservations use reservePending(). */
    public synchronized boolean setPending(@NonNull WidgetAddTransaction transaction) {
        if (pending != null && !pending.token.equals(transaction.token)) {
            throw new IllegalStateException("another widget add is pending");
        }
        LauncherWidgetRecord record = records.get(transaction.appWidgetId);
        if (record != null && record.state != LauncherWidgetRecord.State.DELETING) {
            throw new IllegalArgumentException("pending ID is already active");
        }
        if (pending == null && !WidgetGridPlacementPolicy.canPlace(grid,
            recordsOnPage(transaction.page), transaction.cell, -1)) {
            WidgetGridPlacementPolicy.Result fallback = WidgetGridPlacementPolicy.findPlacement(
                grid, recordsOnPage(transaction.page), transaction.cell.columnSpan(),
                transaction.cell.rowSpan());
            if (fallback.outcome != WidgetGridPlacementPolicy.Outcome.PLACED) return false;
            transaction = transaction.withCell(fallback.rect);
        }
        return commitValidated(records, transaction, grid, pageCount, revision + 1);
    }

    /** Compare-and-commit reservation used by the real picker path before any external launch. */
    public synchronized boolean reservePending(long expectedRevision,
                                               @NonNull WidgetAddTransaction transaction) {
        if (pending != null || expectedRevision != revision
            || transaction.gridRevision != expectedRevision) return false;
        if (transaction.page >= pageCount) return false;
        if (!WidgetGridPlacementPolicy.canPlace(grid, recordsOnPage(transaction.page),
            transaction.cell, -1)) return false;
        return commitValidated(records, transaction, grid, pageCount, revision + 1);
    }

    public synchronized boolean clearPending(@NonNull String token) {
        if (pending == null) return true;
        if (!pending.token.equals(token)) return false;
        return commitValidated(records, null, grid, pageCount, revision + 1);
    }

    /** Atomically makes the configured widget active in its exact reservation. */
    public synchronized boolean finalizeActive(@NonNull String token,
                                               @NonNull LauncherWidgetRecord record) {
        if (pending == null || !pending.token.equals(token)
            || pending.appWidgetId != record.appWidgetId || !pending.cell.equals(record.cell)) return false;
        LinkedHashMap<Integer, LauncherWidgetRecord> next = new LinkedHashMap<>(records);
        next.remove(record.appWidgetId); // DELETING self-record, if present, is the same reservation.
        next.put(record.appWidgetId, record);
        return commitValidated(next, null, grid, pageCount, revision + 1);
    }

    public synchronized boolean beginPendingDeletion(@NonNull WidgetAddTransaction transaction) {
        LauncherWidgetRecord deleting = new LauncherWidgetRecord(transaction.appWidgetId,
            transaction.provider, transaction.profileSerial, LauncherWidgetRecord.State.DELETING,
            transaction.cell, transaction.page, transaction.requestedOptions(), null);
        LinkedHashMap<Integer, LauncherWidgetRecord> next = new LinkedHashMap<>(records);
        next.put(deleting.appWidgetId, deleting);
        return commitValidated(next, pending, grid, pageCount, revision + 1);
    }

    /** Durable first half of per-ID deletion for an already placed widget. */
    public synchronized boolean beginRecordDeletion(int appWidgetId) {
        LauncherWidgetRecord record = records.get(appWidgetId);
        if (record == null) return false;
        if (record.state == LauncherWidgetRecord.State.DELETING) return true;
        LinkedHashMap<Integer, LauncherWidgetRecord> next = new LinkedHashMap<>(records);
        next.put(appWidgetId, record.withState(LauncherWidgetRecord.State.DELETING));
        return commitValidated(next, pending, grid, pageCount, revision + 1);
    }

    public synchronized boolean completeDeletion(int appWidgetId, @Nullable String token) {
        LinkedHashMap<Integer, LauncherWidgetRecord> next = new LinkedHashMap<>(records);
        next.remove(appWidgetId);
        WidgetAddTransaction nextPending = pending;
        if (pending != null && pending.appWidgetId == appWidgetId
            && (token == null || pending.token.equals(token))) nextPending = null;
        return commitValidated(next, nextPending, grid, pageCount, revision + 1);
    }

    /** Atomic A-4/A-5 seam; A-3 only consumes its validation and revision semantics. */
    public synchronized boolean updateLayout(long expectedRevision,
                                             @NonNull WidgetGridDefinition definition,
                                             @NonNull List<LauncherWidgetRecord> values) {
        if (expectedRevision != revision) return false;
        LinkedHashMap<Integer, LauncherWidgetRecord> next = new LinkedHashMap<>();
        for (LauncherWidgetRecord record : values) {
            if (next.put(record.appWidgetId, record) != null) return false;
        }
        return commitValidated(next, pending, definition, pageCount, revision + 1);
    }

    @NonNull public synchronized String serialize() {
        return encode(records, pending, grid, pageCount, freshPages, orientation, layouts,
            revision);
    }

    private boolean commitValidated(Map<Integer, LauncherWidgetRecord> next,
                                    @Nullable WidgetAddTransaction nextPending,
                                    WidgetGridDefinition nextGrid, int nextPageCount,
                                    long nextRevision) {
        return commitValidated(next, nextPending, nextGrid, nextPageCount, freshPages,
            nextRevision);
    }

    private boolean commitValidated(Map<Integer, LauncherWidgetRecord> next,
                                    @Nullable WidgetAddTransaction nextPending,
                                    WidgetGridDefinition nextGrid, int nextPageCount,
                                    @NonNull Collection<Integer> nextFresh, long nextRevision) {
        return commitValidated(next, nextPending, nextGrid, nextPageCount, nextFresh, orientation,
            layouts, nextRevision);
    }

    private boolean commitValidated(Map<Integer, LauncherWidgetRecord> next,
                                    @Nullable WidgetAddTransaction nextPending,
                                    WidgetGridDefinition nextGrid, int nextPageCount,
                                    @NonNull Collection<Integer> nextFresh,
                                    @Nullable String nextOrientation,
                                    @NonNull Map<String, OrientationLayout> nextLayouts,
                                    long nextRevision) {
        if (migrationWritePending || readOnlyUnknownVersion) return false;
        if (!validatePaged(nextGrid, next, nextPending, nextPageCount)) return false;
        LinkedHashSet<Integer> fresh = settledFresh(nextFresh, next, nextPageCount);
        LinkedHashMap<String, OrientationLayout> shelf = new LinkedHashMap<>(nextLayouts);
        if (nextOrientation != null) shelf.remove(nextOrientation);
        String encoded = encode(next, nextPending, nextGrid, nextPageCount, fresh, nextOrientation,
            shelf, nextRevision);
        if (!storage.write(encoded)) return false;
        records = new LinkedHashMap<>(next);
        pending = nextPending;
        grid = nextGrid;
        pageCount = nextPageCount;
        freshPages = fresh;
        orientation = nextOrientation;
        layouts = shelf;
        revision = nextRevision;
        return true;
    }

    /**
     * Freshness is spent the moment a widget is on the page: from then on it is an ordinary page,
     * kept by its widgets and taken away with the last of them. A page that is no longer there
     * drops out too.
     */
    private static LinkedHashSet<Integer> settledFresh(@NonNull Collection<Integer> fresh,
                                                       Map<Integer, LauncherWidgetRecord> values,
                                                       int pages) {
        LinkedHashSet<Integer> settled = new LinkedHashSet<>();
        for (Integer page : fresh) {
            if (page == null || page < 0 || page >= pages) continue;
            boolean held = false;
            for (LauncherWidgetRecord record : values.values()) {
                if (record.page == page) { held = true; break; }
            }
            if (!held) settled.add(page);
        }
        return settled;
    }

    /** Page-scoped snapshot validation: collisions only exist between records on one page. */
    private static boolean validatePaged(WidgetGridDefinition definition,
                                         Map<Integer, LauncherWidgetRecord> values,
                                         @Nullable WidgetAddTransaction transaction,
                                         int pages) {
        if (pages < 1) return false;
        LinkedHashMap<Integer, List<LauncherWidgetRecord>> byPage = new LinkedHashMap<>();
        for (LauncherWidgetRecord record : values.values()) {
            if (record.page < 0 || record.page >= pages) return false;
            List<LauncherWidgetRecord> group = byPage.get(record.page);
            if (group == null) { group = new ArrayList<>(); byPage.put(record.page, group); }
            group.add(record);
        }
        for (List<LauncherWidgetRecord> group : byPage.values()) {
            if (!WidgetGridPlacementPolicy.validate(definition, group)) return false;
        }
        if (transaction == null) return true;
        if (transaction.page < 0 || transaction.page >= pages) return false;
        LauncherWidgetRecord same = values.get(transaction.appWidgetId);
        int ignored = same != null && same.state == LauncherWidgetRecord.State.DELETING
            ? same.appWidgetId : -1;
        List<LauncherWidgetRecord> group = byPage.get(transaction.page);
        return WidgetGridPlacementPolicy.canPlace(definition,
            group == null ? Collections.<LauncherWidgetRecord>emptyList() : group,
            transaction.cell, ignored);
    }

    private void load(@Nullable String encoded) {
        if (encoded == null || encoded.trim().isEmpty()) return;
        try {
            JSONObject root = new JSONObject(encoded);
            int version = root.optInt("version", 0);
            if (version == 1) { migrateV1(root); return; }
            if (version == 2) { migrateV2(root); return; }
            // v3 and v4 decode the same way; a v3 payload simply names no orientation and
            // shelves no layout, so it is adopted as it stands and rewritten as v4 on the next
            // commit. Nothing moves for a user who never turns the screen.
            if (version != SCHEMA_VERSION && version != LEGACY_PAGED_VERSION) {
                readOnlyUnknownVersion = true;
                return;
            }
            WidgetGridDefinition loadedGrid = decodeGrid(root.getJSONObject("grid"));
            int loadedPages = Math.max(1, root.optInt("pages", 1));
            LinkedHashMap<Integer, LauncherWidgetRecord> loaded = decodeRecords(root, true);
            WidgetAddTransaction loadedPending = root.has("pending")
                ? decodeTransaction(root.getJSONObject("pending"), true, 0, 0) : null;
            if (!validatePaged(loadedGrid, loaded, loadedPending, loadedPages)) return;
            records = loaded;
            pending = loadedPending;
            grid = loadedGrid;
            pageCount = loadedPages;
            // A save written before hand-added pages existed simply has none of them.
            freshPages = settledFresh(decodePages(root.optJSONArray("freshPages")), loaded,
                loadedPages);
            String loadedOrientation = root.optString("orientation", null);
            orientation = loadedOrientation == null || loadedOrientation.isEmpty()
                ? null : loadedOrientation;
            layouts = decodeLayouts(root.optJSONObject("layouts"), orientation);
            revision = Math.max(0, root.optLong("revision", 0));
        } catch (JSONException | IllegalArgumentException ignored) {
            // Preserve an empty in-memory recovery target; never overwrite an unknown/corrupt value.
        }
    }

    /** v2 → v3: every record and the pending reservation land on page 0 of a one-page pane. */
    private void migrateV2(JSONObject root) throws JSONException {
        WidgetGridDefinition loadedGrid = decodeGrid(root.getJSONObject("grid"));
        LinkedHashMap<Integer, LauncherWidgetRecord> loaded = decodeRecords(root, true);
        WidgetAddTransaction loadedPending = root.has("pending")
            ? decodeTransaction(root.getJSONObject("pending"), true, 0, 0) : null;
        if (!validatePaged(loadedGrid, loaded, loadedPending, 1)) throw new JSONException("invalid v2");
        // Keep the decoded identities in memory even when the one-shot migration write fails.
        records = loaded;
        pending = loadedPending;
        grid = loadedGrid;
        pageCount = 1;
        revision = Math.max(0, root.optLong("revision", 0));
        migrationWritePending = !storage.write(encode(loaded, loadedPending, loadedGrid, 1,
            Collections.emptySet(), null, Collections.emptyMap(), revision));
    }

    private void migrateV1(JSONObject root) throws JSONException {
        JSONArray array = root.optJSONArray("records");
        int count = array == null ? 0 : array.length();
        boolean hasPending = root.has("pending");
        int total = count + (hasPending ? 1 : 0);
        int rows = Math.max(WidgetGridDefinition.DEFAULT_ROWS,
            (total + WidgetGridDefinition.DEFAULT_COLUMNS - 1) / WidgetGridDefinition.DEFAULT_COLUMNS);
        WidgetGridDefinition migratedGrid = new WidgetGridDefinition(rows,
            WidgetGridDefinition.DEFAULT_COLUMNS);
        LinkedHashMap<Integer, LauncherWidgetRecord> migrated = new LinkedHashMap<>();
        for (int i = 0; i < count; i++) {
            LauncherWidgetRecord legacy = decodeRecord(array.getJSONObject(i), false,
                i % migratedGrid.columns, i / migratedGrid.columns);
            if (migrated.put(legacy.appWidgetId, legacy) != null) throw new JSONException("duplicate widget ID");
        }
        WidgetAddTransaction migratedPending = hasPending
            ? decodeTransaction(root.getJSONObject("pending"), false,
                count % migratedGrid.columns, count / migratedGrid.columns) : null;
        if (!validatePaged(migratedGrid, migrated, migratedPending, 1)) {
            throw new JSONException("invalid v1");
        }
        // Keep the decoded identities in memory even when the one-shot migration write fails.
        records = migrated;
        pending = migratedPending;
        grid = migratedGrid;
        pageCount = 1;
        revision = 0;
        migrationWritePending = !storage.write(encode(migrated, migratedPending, migratedGrid, 1,
            Collections.emptySet(), null, Collections.emptyMap(), 0));
    }

    private static LinkedHashSet<Integer> decodePages(@Nullable JSONArray array) {
        LinkedHashSet<Integer> pages = new LinkedHashSet<>();
        if (array == null) return pages;
        for (int i = 0; i < array.length(); i++) {
            int page = array.optInt(i, -1);
            if (page >= 0) pages.add(page);
        }
        return pages;
    }

    private static LinkedHashMap<Integer, LauncherWidgetRecord> decodeRecords(JSONObject root,
                                                                              boolean hasCell)
        throws JSONException {
        LinkedHashMap<Integer, LauncherWidgetRecord> loaded = new LinkedHashMap<>();
        JSONArray array = root.optJSONArray("records");
        if (array == null) return loaded;
        for (int i = 0; i < array.length(); i++) {
            LauncherWidgetRecord record = decodeRecord(array.getJSONObject(i), hasCell, 0, 0);
            if (loaded.put(record.appWidgetId, record) != null) throw new JSONException("duplicate widget ID");
        }
        return loaded;
    }

    private static String encode(Map<Integer, LauncherWidgetRecord> values,
                                 @Nullable WidgetAddTransaction transaction,
                                 WidgetGridDefinition definition, int pages,
                                 @NonNull Collection<Integer> fresh,
                                 @Nullable String orientation,
                                 @NonNull Map<String, OrientationLayout> layouts, long revision) {
        try {
            JSONObject root = new JSONObject();
            root.put("version", SCHEMA_VERSION);
            root.put("revision", revision);
            root.put("grid", encodeGrid(definition));
            root.put("pages", Math.max(1, pages));
            // Left out when there are none, so a save from a build without hand-added pages and a
            // save with none of them left are the same payload.
            if (!fresh.isEmpty()) {
                JSONArray freshArray = new JSONArray();
                for (Integer page : fresh) freshArray.put((int) page);
                root.put("freshPages", freshArray);
            }
            // Left out the same way: a save from a build that knew nothing of orientations and a
            // save whose shelf is empty are the same payload.
            if (orientation != null) root.put("orientation", orientation);
            if (!layouts.isEmpty()) {
                JSONObject shelf = new JSONObject();
                for (Map.Entry<String, OrientationLayout> entry : layouts.entrySet()) {
                    shelf.put(entry.getKey(), encodeLayout(entry.getValue()));
                }
                root.put("layouts", shelf);
            }
            JSONArray array = new JSONArray();
            for (LauncherWidgetRecord record : values.values()) array.put(encodeRecord(record));
            root.put("records", array);
            if (transaction != null) root.put("pending", encodeTransaction(transaction));
            return root.toString();
        } catch (JSONException e) {
            throw new IllegalStateException("Unable to serialize launcher widgets", e);
        }
    }

    private static JSONObject encodeLayout(OrientationLayout value) throws JSONException {
        JSONObject out = new JSONObject();
        out.put("grid", encodeGrid(value.grid));
        out.put("pages", value.pageCount);
        if (!value.freshPages.isEmpty()) {
            JSONArray fresh = new JSONArray();
            for (Integer page : value.freshPages) fresh.put((int) page);
            out.put("freshPages", fresh);
        }
        JSONArray cells = new JSONArray();
        for (Map.Entry<Integer, Placement> entry : value.cells.entrySet()) {
            cells.put(new JSONObject().put("id", (int) entry.getKey())
                .put("cell", encodeCell(entry.getValue().cell))
                .put("page", entry.getValue().page));
        }
        out.put("cells", cells);
        return out;
    }

    /**
     * A shelf entry that cannot be read is dropped rather than failing the load: the orientation
     * it belongs to is then simply seen for the first time again, which seeds instead of losing.
     */
    @NonNull
    private static LinkedHashMap<String, OrientationLayout> decodeLayouts(
            @Nullable JSONObject value, @Nullable String active) {
        LinkedHashMap<String, OrientationLayout> shelf = new LinkedHashMap<>();
        if (value == null) return shelf;
        Iterator<String> keys = value.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            if (key.isEmpty() || key.equals(active)) continue;
            try {
                JSONObject entry = value.getJSONObject(key);
                LinkedHashMap<Integer, Placement> cells = new LinkedHashMap<>();
                JSONArray array = entry.optJSONArray("cells");
                for (int i = 0; array != null && i < array.length(); i++) {
                    JSONObject item = array.getJSONObject(i);
                    cells.put(item.getInt("id"), new Placement(decodeCell(item.getJSONObject("cell")),
                        Math.max(0, item.optInt("page", 0))));
                }
                shelf.put(key, new OrientationLayout(decodeGrid(entry.getJSONObject("grid")),
                    entry.optInt("pages", 1), decodePages(entry.optJSONArray("freshPages")), cells));
            } catch (JSONException | IllegalArgumentException ignored) {
                // Drop this orientation's shelf entry; the rest of the payload stands.
            }
        }
        return shelf;
    }

    private static JSONObject encodeGrid(WidgetGridDefinition value) throws JSONException {
        return new JSONObject().put("rows", value.rows).put("columns", value.columns);
    }
    private static WidgetGridDefinition decodeGrid(JSONObject value) throws JSONException {
        return new WidgetGridDefinition(value.getInt("rows"), value.getInt("columns"));
    }
    private static JSONObject encodeCell(WidgetCellRect value) throws JSONException {
        return new JSONObject().put("left", value.left).put("top", value.top)
            .put("right", value.right).put("bottom", value.bottom);
    }
    private static WidgetCellRect decodeCell(JSONObject value) throws JSONException {
        return new WidgetCellRect(value.getInt("left"), value.getInt("top"),
            value.getInt("right"), value.getInt("bottom"));
    }

    private static JSONObject encodeRecord(LauncherWidgetRecord record) throws JSONException {
        JSONObject value = new JSONObject();
        value.put("id", record.appWidgetId);
        value.put("provider", record.provider.flattenToString());
        value.put("profile", record.profileSerial);
        value.put("state", record.state.name());
        value.put("cell", encodeCell(record.cell));
        value.put("page", record.page);
        value.put("options", encodeBundle(record.sizeOptions()));
        if (record.lastRenderFailure != null) value.put("failure", record.lastRenderFailure);
        return value;
    }

    private static LauncherWidgetRecord decodeRecord(JSONObject value, boolean hasCell,
                                                     int legacyColumn, int legacyRow)
        throws JSONException {
        ComponentName provider = ComponentName.unflattenFromString(value.getString("provider"));
        if (provider == null) throw new JSONException("invalid provider");
        WidgetCellRect cell = hasCell ? decodeCell(value.getJSONObject("cell"))
            : new WidgetCellRect(legacyColumn, legacyRow, legacyColumn + 1, legacyRow + 1);
        return new LauncherWidgetRecord(value.getInt("id"), provider, value.getLong("profile"),
            LauncherWidgetRecord.State.valueOf(value.getString("state")), cell,
            Math.max(0, value.optInt("page", 0)),
            decodeBundle(value.optJSONObject("options")), value.optString("failure", null));
    }

    private static JSONObject encodeTransaction(WidgetAddTransaction value) throws JSONException {
        JSONObject out = new JSONObject();
        out.put("token", value.token);
        out.put("id", value.appWidgetId);
        out.put("provider", value.provider.flattenToString());
        out.put("profile", value.profileSerial);
        out.put("stage", value.stage.name());
        out.put("cell", encodeCell(value.cell));
        out.put("page", value.page);
        out.put("gridRevision", value.gridRevision);
        if (value.originToken != null) out.put("origin", value.originToken);
        out.put("options", encodeBundle(value.requestedOptions()));
        out.put("started", value.startedAtMillis);
        return out;
    }

    private static WidgetAddTransaction decodeTransaction(JSONObject value, boolean hasCell,
                                                          int legacyColumn, int legacyRow)
        throws JSONException {
        ComponentName provider = ComponentName.unflattenFromString(value.getString("provider"));
        if (provider == null) throw new JSONException("invalid provider");
        WidgetCellRect cell = hasCell ? decodeCell(value.getJSONObject("cell"))
            : new WidgetCellRect(legacyColumn, legacyRow, legacyColumn + 1, legacyRow + 1);
        return new WidgetAddTransaction(value.getString("token"), value.getInt("id"), provider,
            value.getLong("profile"), WidgetAddTransaction.Stage.valueOf(value.getString("stage")),
            cell, Math.max(0, value.optInt("page", 0)),
            hasCell ? value.optLong("gridRevision", 0) : 0,
            value.optString("origin", null), decodeBundle(value.optJSONObject("options")),
            value.getLong("started"));
    }

    private static JSONObject encodeBundle(Bundle bundle) throws JSONException {
        JSONObject out = new JSONObject();
        ArrayList<String> keys = new ArrayList<>(bundle.keySet());
        Collections.sort(keys);
        for (String key : keys) {
            Object value = bundle.get(key);
            if (value instanceof Integer || value instanceof Long || value instanceof Double
                || value instanceof Boolean || value instanceof String) out.put(key, value);
            else if (value instanceof Float) out.put(key, ((Float) value).doubleValue());
            else if (AppWidgetManager.OPTION_APPWIDGET_SIZES.equals(key) && value instanceof ArrayList) {
                JSONArray sizes = encodeSizeList((ArrayList<?>) value);
                if (sizes != null) out.put(key, sizes);
            }
        }
        return out;
    }

    @Nullable private static JSONArray encodeSizeList(@NonNull ArrayList<?> values)
        throws JSONException {
        JSONArray out = new JSONArray();
        for (Object value : values) {
            if (!(value instanceof SizeF)) return null;
            SizeF size = (SizeF) value;
            out.put(new JSONObject().put("width", size.getWidth()).put("height", size.getHeight()));
        }
        return out;
    }

    private static Bundle decodeBundle(@Nullable JSONObject value) throws JSONException {
        Bundle out = new Bundle();
        if (value == null) return out;
        Iterator<String> keys = value.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            Object item = value.get(key);
            if (item instanceof Integer) out.putInt(key, (Integer) item);
            else if (item instanceof Long) out.putLong(key, (Long) item);
            else if (item instanceof Double) out.putDouble(key, (Double) item);
            else if (item instanceof Boolean) out.putBoolean(key, (Boolean) item);
            else if (item instanceof String) out.putString(key, (String) item);
            else if (item instanceof JSONArray) {
                JSONArray encodedSizes = (JSONArray) item;
                ArrayList<SizeF> sizes = new ArrayList<>();
                for (int i = 0; i < encodedSizes.length(); i++) {
                    JSONObject size = encodedSizes.getJSONObject(i);
                    sizes.add(new SizeF((float) size.getDouble("width"),
                        (float) size.getDouble("height")));
                }
                out.putParcelableArrayList(key, sizes);
            }
        }
        return out;
    }
}
