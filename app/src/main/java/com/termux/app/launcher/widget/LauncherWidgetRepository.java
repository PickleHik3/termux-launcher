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
    /**
     * Written instead of {@link #SCHEMA_VERSION} while the wall holds a built-in widget: the same
     * payload with {@code builtin} records in it, which a build without built-ins would reject
     * record by record. Naming it a newer version makes that build go read-only as a whole, so a
     * downgrade keeps the wall rather than half of it.
     */
    private static final int BUILTIN_VERSION = 5;
    /**
     * Written while a Home page holds an app icon: every record names its {@code kind}, and an
     * app icon carries its {@code app}. A v5 build would read an app icon as an app widget with a
     * negative ID and refuse the wall, so the newer number makes it go read-only instead. v5 and
     * older carry no kind and load unchanged: a {@code builtin} field makes a built-in, anything
     * else an app widget.
     */
    private static final int APP_ITEM_VERSION = 6;
    /**
     * Written while a Home page holds a folder: the v6 payload, with each folder carrying its name
     * and its apps. A v6 build would drop the folders one by one and keep the rest, which would
     * lose the apps in them on its next write; the newer number makes it go read-only instead.
     * v6 and older load unchanged.
     */
    private static final int FOLDER_VERSION = 7;
    /**
     * Written while a Home folder holds more apps than the old cap of 16 (Settings › Home ›
     * Folders › Max folder size): the v7 payload, which a v7 build would read back cut to 16 apps
     * a folder and then write over. The newer number makes it go read-only instead.
     */
    private static final int LARGE_FOLDER_VERSION = 8;
    /** v3 is read as v4 with no orientation named and no shelf: identical bytes, nothing moves. */
    private static final int LEGACY_PAGED_VERSION = 3;
    private static final String PREFS = "launcher_widget_repository";
    private static final String KEY_STATE = "state";

    /**
     * Who owns the Home pages: the ordered Home page ids, and the changes to them. The repository
     * keys what stands on a page by that id (an index is only its position in this list) and asks
     * the owner to grow, remove or reorder the pages when its own edits do so. The wall's page
     * store is the owner in the app; without one the repository keeps a list of its own.
     */
    public interface HomePageOwner {
        /** The Home page ids in order; never empty. */
        @NonNull List<Integer> homePageIds();
        /** Appends {@code count} Home pages after the last one; answers their ids in order. */
        @NonNull List<Integer> addHomePages(int count);
        /** Removes these Home pages (the owner never removes the last one). */
        void removeHomePages(@NonNull Collection<Integer> ids);
        /** Puts the same Home pages in this order. */
        void reorderHomePages(@NonNull List<Integer> order);
    }

    /** The owner a repository built without one keeps for itself. */
    private static final class SelfOwner implements HomePageOwner {
        private List<Integer> list = new ArrayList<>(Collections.singletonList(1));
        private int next = 2;
        @NonNull @Override public List<Integer> homePageIds() { return new ArrayList<>(list); }
        @NonNull @Override public List<Integer> addHomePages(int count) {
            ArrayList<Integer> added = new ArrayList<>();
            for (int i = 0; i < count; i++) added.add(next++);
            list.addAll(added);
            return added;
        }
        @Override public void removeHomePages(@NonNull Collection<Integer> ids) {
            ArrayList<Integer> kept = new ArrayList<>(list);
            kept.removeAll(ids);
            if (!kept.isEmpty()) list = kept;
        }
        @Override public void reorderHomePages(@NonNull List<Integer> order) {
            if (order.size() == list.size() && list.containsAll(order)) list = new ArrayList<>(order);
        }
        void adopt(@NonNull List<Integer> stored) {
            list = new ArrayList<>(stored);
            for (Integer id : stored) next = Math.max(next, id + 1);
        }
    }

    private final Storage storage;
    @NonNull private final HomePageOwner owner;
    @Nullable private final SelfOwner selfOwner;
    /** The Home page ids in order as this repository last saw them: index in, id out. */
    @NonNull private List<Integer> ids = new ArrayList<>();
    /** Records of pages the owner no longer lists (a removal that may be undone); page holds the id. */
    @NonNull private LinkedHashMap<Integer, LauncherWidgetRecord> orphans = new LinkedHashMap<>();
    /** Set while this repository is itself calling the owner, so the owner's echo is ignored. */
    private boolean ownerBusy;
    /** The ids the next commit leaves the pages with, when it removes or reorders them. */
    @Nullable private List<Integer> plannedIds;
    /** Ids the owner grew for the commit in flight, taken back if the write fails. */
    @NonNull private List<Integer> preGrown = new ArrayList<>();
    private boolean legacyWriteBack;
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
    /**
     * The wall as it stood before {@link #shrinkGridRows} ran, kept beside the layout in the same
     * payload so the two commit together. Null when no shrink is outstanding.
     */
    @Nullable private RowSnapshot rowSnapshot;
    private long revision;
    private boolean migrationWritePending;
    private boolean readOnlyUnknownVersion;

    public LauncherWidgetRepository(@NonNull Storage storage) {
        this(storage, null);
    }

    public LauncherWidgetRepository(@NonNull Storage storage, @Nullable HomePageOwner homeOwner) {
        this.storage = storage;
        this.selfOwner = homeOwner == null ? new SelfOwner() : null;
        this.owner = homeOwner != null ? homeOwner : selfOwner;
        ids = new ArrayList<>(owner.homePageIds());
        if (ids.isEmpty()) ids = new ArrayList<>(owner.addHomePages(1));
        pageCount = ids.size();
        load(storage.read());
        if (legacyWriteBack && !migrationWritePending && !readOnlyUnknownVersion) {
            // An index-keyed wall is written back keyed by id at once: the owner may reorder the
            // pages before the next edit, and a position would then name the wrong page.
            storage.write(encode(records, orphans, ids, pending, grid, freshPages, orientation,
                layouts, rowSnapshot, revision));
        }
        // An empty grid carries no placements worth preserving: adopt the current default
        // dimensions so a stale persisted grid (e.g. the old 6x4) cannot outlive its widgets.
        if (records.isEmpty() && pending == null
            && !grid.equals(WidgetGridDefinition.DEFAULT)) {
            commitValidated(records, null, WidgetGridDefinition.DEFAULT, pageCount, revision + 1);
        }
    }

    @NonNull public static LauncherWidgetRepository create(@NonNull Context context) {
        return create(context, null);
    }

    @NonNull public static LauncherWidgetRepository create(@NonNull Context context,
                                                           @Nullable HomePageOwner homeOwner) {
        SharedPreferences preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return new LauncherWidgetRepository(new Storage() {
            @Override public String read() { return preferences.getString(KEY_STATE, null); }
            @Override public boolean write(@NonNull String value) {
                return preferences.edit().putString(KEY_STATE, value).commit();
            }
        }, homeOwner);
    }

    /**
     * The owner's Home pages changed (an Edit pages add, move or removal, an undo): lines the
     * index view up with the new list by id. What stood on a page that is no longer listed is
     * kept aside, unseen, until {@link #pageRemoved} says it is gone for good or the page comes
     * back. Nothing is written; the next commit carries the new order.
     */
    public synchronized void onHomePagesChanged() {
        if (ownerBusy) return;
        List<Integer> now = owner.homePageIds();
        if (now.isEmpty() || now.equals(ids)) return;
        LinkedHashMap<Integer, LauncherWidgetRecord> live = new LinkedHashMap<>();
        LinkedHashMap<Integer, LauncherWidgetRecord> aside = new LinkedHashMap<>();
        for (LauncherWidgetRecord record : records.values()) {
            int id = record.page < ids.size() ? ids.get(record.page) : -1;
            int at = now.indexOf(id);
            if (at >= 0) live.put(record.appWidgetId, record.withPage(at));
            else aside.put(record.appWidgetId, record.withPage(Math.max(0, id)));
        }
        for (LauncherWidgetRecord record : orphans.values()) {
            int at = now.indexOf(record.page);
            if (at >= 0) live.put(record.appWidgetId, record.withPage(at));
            else aside.put(record.appWidgetId, record);
        }
        if (pending != null) {
            int id = pending.page < ids.size() ? ids.get(pending.page) : -1;
            int at = now.indexOf(id);
            pending = at < 0 ? null : pending.withPage(at);
        }
        LinkedHashSet<Integer> nextFresh = new LinkedHashSet<>();
        for (Integer page : freshPages) {
            int at = page < ids.size() ? now.indexOf(ids.get(page)) : -1;
            if (at >= 0) nextFresh.add(at);
        }
        freshPages = nextFresh;
        records = live;
        orphans = aside;
        ids = new ArrayList<>(now);
        pageCount = ids.size();
    }

    /**
     * The owner dropped Home page {@code id} for good: what stood on it, here and in the shelved
     * layouts, goes with it. One commit when there was anything.
     */
    public synchronized void pageRemoved(int id) {
        if (ownerBusy || migrationWritePending || readOnlyUnknownVersion) return;
        boolean changed = false;
        LinkedHashMap<Integer, LauncherWidgetRecord> keptAside = new LinkedHashMap<>();
        for (LauncherWidgetRecord record : orphans.values()) {
            if (record.page == id) changed = true; else keptAside.put(record.appWidgetId, record);
        }
        LinkedHashMap<String, OrientationLayout> shelf = new LinkedHashMap<>();
        for (Map.Entry<String, OrientationLayout> entry : layouts.entrySet()) {
            OrientationLayout cut = withoutPage(entry.getValue(), id);
            changed |= cut != entry.getValue();
            shelf.put(entry.getKey(), cut);
        }
        RowSnapshot cutSnapshot = rowSnapshot;
        if (rowSnapshot != null) {
            OrientationLayout before = withoutPage(rowSnapshot.before, id);
            OrientationLayout after = withoutPage(rowSnapshot.after, id);
            if (before != rowSnapshot.before || after != rowSnapshot.after) {
                cutSnapshot = new RowSnapshot(rowSnapshot.orientation, rowSnapshot.rows, before, after);
                changed = true;
            }
        }
        if (!changed) return;
        LinkedHashMap<Integer, LauncherWidgetRecord> was = orphans;
        orphans = keptAside;
        if (!commitValidated(records, pending, grid, pageCount, freshPages, orientation, shelf,
            cutSnapshot, revision + 1)) {
            orphans = was;
        }
    }

    /** The layout without its cells on page {@code id}; the same object when it had none. */
    @NonNull
    private static OrientationLayout withoutPage(@NonNull OrientationLayout layout, int id) {
        boolean any = false;
        for (Placement placed : layout.cells.values()) any |= placed.page == id;
        if (!any && (layout.pageIds == null || !layout.pageIds.contains(id))
            && !layout.freshPages.contains(id)) return layout;
        LinkedHashMap<Integer, Placement> cells = new LinkedHashMap<>();
        for (Map.Entry<Integer, Placement> cell : layout.cells.entrySet()) {
            if (cell.getValue().page != id) cells.put(cell.getKey(), cell.getValue());
        }
        List<Integer> pageIds = null;
        if (layout.pageIds != null) {
            pageIds = new ArrayList<>(layout.pageIds);
            pageIds.remove(Integer.valueOf(id));
        }
        LinkedHashSet<Integer> fresh = new LinkedHashSet<>(layout.freshPages);
        fresh.remove(id);
        return new OrientationLayout(layout.grid, pageIds, fresh, cells);
    }

    /** The Home page ids in the order the pager shows them. */
    @NonNull public synchronized List<Integer> pageIds() { return new ArrayList<>(ids); }

    /** The id of the page at {@code index}, or -1 when there is none. */
    public synchronized int pageIdAt(int index) {
        return index >= 0 && index < ids.size() ? ids.get(index) : -1;
    }

    /** Where page {@code id} stands in the pager, or -1 when it is not a Home page now. */
    public synchronized int indexOfPage(int id) { return ids.indexOf(id); }

    @NonNull public synchronized List<LauncherWidgetRecord> records() {
        return Collections.unmodifiableList(new ArrayList<>(records.values()));
    }
    @Nullable public synchronized LauncherWidgetRecord get(int appWidgetId) {
        return records.get(appWidgetId);
    }
    /**
     * The next free key for a built-in widget: negative, below every built-in the wall holds on
     * any page or in any shelved orientation, so a key is never reused while a layout still
     * names it.
     */
    public synchronized int allocateBuiltinId() {
        int lowest = 0;
        for (int id : records.keySet()) lowest = Math.min(lowest, id);
        for (int id : orphans.keySet()) lowest = Math.min(lowest, id);
        for (OrientationLayout layout : layouts.values()) {
            for (int id : layout.cells.keySet()) lowest = Math.min(lowest, id);
        }
        return lowest - 1;
    }

    /**
     * Puts an app icon on Home: on {@code preferredPage} (the page in front) when it has room, else
     * the first page that has, else on a new page after the last ({@link HomeItemPlacementPolicy}).
     * One commit, the page and the icon together. Returns the icon as stored, or null when the
     * write failed or the store is read-only, with nothing changed.
     */
    @Nullable
    public synchronized LauncherWidgetRecord addApp(@NonNull com.termux.app.launcher.model.AppRef app,
                                                    int preferredPage, @Nullable Bundle options) {
        HomeItemPlacementPolicy.Choice choice = HomeItemPlacementPolicy.place(grid, pageCount,
            preferredPage, this::occupancyOnPage, 1, 1);
        if (choice == null) return null;
        LauncherWidgetRecord record = LauncherWidgetRecord.app(allocateBuiltinId(), app,
            choice.cell, choice.page, options);
        LinkedHashMap<Integer, LauncherWidgetRecord> next = new LinkedHashMap<>(records);
        next.put(record.appWidgetId, record);
        int nextPages = choice.appendsPage ? pageCount + 1 : pageCount;
        return commitValidated(next, pending, grid, nextPages, revision + 1) ? record : null;
    }

    /**
     * Puts a shortcut on Home like {@link #addApp}: on {@code preferredPage} when it has room, else
     * the first page that has, else on a new page after the last. One commit. Returns the record as
     * stored, or null when the write failed or the store is read-only, with nothing changed.
     */
    @Nullable
    public synchronized LauncherWidgetRecord addShortcut(@NonNull String owner, long profileSerial,
                                                         int preferredPage,
                                                         @Nullable Bundle options) {
        HomeItemPlacementPolicy.Choice choice = HomeItemPlacementPolicy.place(grid, pageCount,
            preferredPage, this::occupancyOnPage, 1, 1);
        if (choice == null) return null;
        LauncherWidgetRecord record = LauncherWidgetRecord.shortcut(allocateBuiltinId(), owner,
            profileSerial, choice.cell, choice.page, options);
        LinkedHashMap<Integer, LauncherWidgetRecord> next = new LinkedHashMap<>(records);
        next.put(record.appWidgetId, record);
        int nextPages = choice.appendsPage ? pageCount + 1 : pageCount;
        return commitValidated(next, pending, grid, nextPages, revision + 1) ? record : null;
    }

    /**
     * Puts a shortcut on Home at {@code cell} of {@code page}, as {@link #addAppAt} puts an app: the
     * cell a drag chose, so no other page or cell is tried. One commit. Returns the record as
     * stored, or null when the page is not there, the cell is taken or off the grid, or the write
     * failed, with nothing changed.
     */
    @Nullable
    public synchronized LauncherWidgetRecord addShortcutAt(@NonNull String owner,
                                                           long profileSerial, int page,
                                                           @NonNull WidgetCellRect cell,
                                                           @Nullable Bundle options) {
        if (page < 0 || page >= pageCount) return null;
        LauncherWidgetRecord record = LauncherWidgetRecord.shortcut(allocateBuiltinId(), owner,
            profileSerial, cell, page, options);
        LinkedHashMap<Integer, LauncherWidgetRecord> next = new LinkedHashMap<>(records);
        next.put(record.appWidgetId, record);
        return commitValidated(next, pending, grid, pageCount, revision + 1) ? record : null;
    }

    /**
     * Puts an app icon on Home at {@code cell} of {@code page}: the cell a drag chose, so no other
     * page or cell is tried. One commit. Returns the icon as stored, or null when the page is not
     * there, the cell is taken or off the grid, or the write failed, with nothing changed.
     */
    @Nullable
    public synchronized LauncherWidgetRecord addAppAt(@NonNull com.termux.app.launcher.model.AppRef app,
                                                      int page, @NonNull WidgetCellRect cell,
                                                      @Nullable Bundle options) {
        if (page < 0 || page >= pageCount) return null;
        LauncherWidgetRecord record = LauncherWidgetRecord.app(allocateBuiltinId(), app, cell, page,
            options);
        LinkedHashMap<Integer, LauncherWidgetRecord> next = new LinkedHashMap<>(records);
        next.put(record.appWidgetId, record);
        return commitValidated(next, pending, grid, pageCount, revision + 1) ? record : null;
    }

    /**
     * Drops the app icon {@code draggedId} on {@code targetId} ({@link HomeFolderPolicy}): onto
     * another app icon, the two become one folder in the target's cell, under the target's key (so
     * a turned screen's shelved layout still places it), named after the dropped app ({@code fallbackName} when it has no label); onto a folder, the app
     * joins it at the end. The dropped icon goes in the same commit. Returns the folder as stored,
     * or null when the drop is not a merge (see {@link HomeFolderPolicy#canTarget}) or the write
     * failed, with nothing changed.
     */
    @Nullable
    public synchronized LauncherWidgetRecord mergeIntoFolder(int draggedId, int targetId,
                                                             @NonNull String fallbackName) {
        return mergeIntoFolder(draggedId, targetId, fallbackName, HomeFolderPolicy.MAX_APPS);
    }

    /** {@link #mergeIntoFolder} with a folder taking up to {@code capacity} apps. */
    @Nullable
    public synchronized LauncherWidgetRecord mergeIntoFolder(int draggedId, int targetId,
                                                             @NonNull String fallbackName,
                                                             int capacity) {
        LauncherWidgetRecord dragged = records.get(draggedId);
        LauncherWidgetRecord target = records.get(targetId);
        if (dragged == null || !HomeFolderPolicy.canTarget(dragged, target, capacity)) return null;
        LauncherWidgetRecord.FolderApp app = HomeFolderPolicy.entryOf(dragged);
        if (app == null) return null;
        LauncherWidgetRecord folder;
        if (target.isFolder()) {
            List<LauncherWidgetRecord.FolderApp> apps = HomeFolderPolicy.add(target.folderApps(),
                app, capacity);
            if (apps == null) return null;
            folder = target.withFolder(target.folderName(), apps);
        } else {
            LauncherWidgetRecord.FolderApp other = HomeFolderPolicy.entryOf(target);
            HomeFolderPolicy.Contents contents = other == null ? null
                : HomeFolderPolicy.merge(app, other, fallbackName);
            if (contents == null) return null;
            folder = LauncherWidgetRecord.folder(targetId, contents.name, contents.apps,
                target.cell, target.page);
        }
        LinkedHashMap<Integer, LauncherWidgetRecord> next = new LinkedHashMap<>(records);
        next.remove(draggedId);
        next.remove(targetId);
        next.put(folder.appWidgetId, folder);
        return commitValidated(next, pending, grid, pageCount, revision + 1) ? folder : null;
    }

    /**
     * Takes the app {@code stableId} out of the folder {@code folderId} and puts it on Home as an
     * icon: the page in front when it has room, else the first page that has, else a new page.
     * What is left of the folder follows {@link HomeFolderPolicy#remove}: still a folder, the last
     * app's own icon in the folder's cell under the folder's key, or nothing. One commit. Returns the
     * icon as placed, or null when the app was not there or the write failed, with nothing
     * changed.
     */
    @Nullable
    public synchronized LauncherWidgetRecord removeFromFolder(int folderId,
                                                              @NonNull String stableId,
                                                              int preferredPage) {
        LauncherWidgetRecord folder = records.get(folderId);
        if (folder == null || !folder.isFolder()) return null;
        HomeFolderPolicy.Removal removal = HomeFolderPolicy.remove(folder.folderApps(), stableId);
        if (removal.taken == null) return null;
        LinkedHashMap<Integer, LauncherWidgetRecord> next = new LinkedHashMap<>(records);
        switch (removal.outcome) {
            case FOLDER:
                next.put(folderId, folder.withFolder(folder.folderName(), removal.remaining));
                break;
            case ICON:
                next.put(folderId, appRecord(folderId, removal.lastApp, folder.cell, folder.page));
                break;
            case GONE:
            default:
                next.remove(folderId);
                break;
        }
        int key = allocateBuiltinId();
        final Map<Integer, LauncherWidgetRecord> settled = next;
        HomeItemPlacementPolicy.Choice choice = HomeItemPlacementPolicy.place(grid, pageCount,
            preferredPage, page -> occupancyOnPage(settled, page), 1, 1);
        if (choice == null) return null;
        LauncherWidgetRecord placed = appRecord(key, removal.taken, choice.cell, choice.page);
        next.put(placed.appWidgetId, placed);
        int nextPages = choice.appendsPage ? pageCount + 1 : pageCount;
        return commitValidated(next, pending, grid, nextPages, revision + 1) ? placed : null;
    }

    /**
     * Renames the folder {@code folderId}: {@code name} trimmed and capped
     * ({@link HomeFolderPolicy#name}), {@code fallbackName} when blank. False when there is no
     * such folder or the write failed.
     */
    public synchronized boolean renameFolder(int folderId, @Nullable String name,
                                             @NonNull String fallbackName) {
        LauncherWidgetRecord folder = records.get(folderId);
        if (folder == null || !folder.isFolder()) return false;
        String settledName = HomeFolderPolicy.name(name, fallbackName);
        if (settledName.equals(folder.folderName())) return true;
        LinkedHashMap<Integer, LauncherWidgetRecord> next = new LinkedHashMap<>(records);
        next.put(folderId, folder.withFolder(settledName, folder.folderApps()));
        return commitValidated(next, pending, grid, pageCount, revision + 1);
    }

    @NonNull
    private static LauncherWidgetRecord appRecord(int id, @NonNull LauncherWidgetRecord.FolderApp app,
                                                  @NonNull WidgetCellRect cell, int page) {
        Bundle options = new Bundle();
        options.putString(LauncherWidgetRecord.APP_LABEL, app.label);
        return LauncherWidgetRecord.app(id, app.app(), cell, page, options);
    }

    /**
     * What stands on a page for a placement to avoid: its records and, on its page, the cell an
     * add in flight has reserved, which no record holds yet.
     */
    @NonNull private List<LauncherWidgetRecord> occupancyOnPage(int page) {
        return occupancyOnPage(records, page);
    }

    /** The same, measured against {@code values} rather than the records as they stand. */
    @NonNull private List<LauncherWidgetRecord> occupancyOnPage(
            @NonNull Map<Integer, LauncherWidgetRecord> values, int page) {
        ArrayList<LauncherWidgetRecord> out = new ArrayList<>(recordsOnPage(values, page));
        WidgetAddTransaction held = pending;
        if (held != null && held.page == page && held.appWidgetId > 0
            && !records.containsKey(held.appWidgetId)) {
            out.add(new LauncherWidgetRecord(held.appWidgetId, held.provider, held.profileSerial,
                LauncherWidgetRecord.State.DELETING, held.cell, held.page, null, null));
        }
        return out;
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
            && WidgetGridPlacementPolicy.canPlace(grid, recordsOnPage(page), cell,
                WidgetGridPlacementPolicy.IGNORE_NONE);
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
     * Takes {@code rows} rows off the grid (the room an always-on keyboard needs) with today's
     * reflow, after keeping the wall as it stands so {@link #restoreGridRows()} can put it back.
     * Returns what moved or shrank, empty when nothing did, or null when refused (an add is in
     * flight, the grid cannot lose that many rows, or the write failed) with nothing changed.
     *
     * <p>A second shrink while the first snapshot is still untouched by edits widens that
     * snapshot rather than replacing it, so one restore undoes both. After an edit it starts a
     * new snapshot of the edited wall. The report is always against the wall before this call.</p>
     */
    @Nullable public synchronized WidgetReflowReport shrinkGridRows(int rows) {
        if (rows < 1 || pending != null || migrationWritePending || readOnlyUnknownVersion) {
            return null;
        }
        int nextRows = grid.rows - rows;
        if (nextRows < WidgetGridDefinition.MIN_ROWS) return null;
        WidgetGridDefinition next = new WidgetGridDefinition(nextRows, grid.columns);
        int[] pages = { pageCount };
        LinkedHashMap<Integer, LauncherWidgetRecord> relaid = reflow(records, next, pages);
        if (relaid == null) return null;
        List<Integer> afterIds = idsGrownTo(pages[0]);
        RowSnapshot previous = rowSnapshot != null && rowSnapshot.isUntouched(this)
            ? rowSnapshot : null;
        OrientationLayout before = previous != null ? previous.before
            : layoutOf(records, grid, ids, freshPages);
        int taken = (previous != null ? previous.rows : 0) + rows;
        OrientationLayout after = layoutOf(relaid, next, afterIds, freshPages);
        RowSnapshot nextSnapshot = new RowSnapshot(orientation, taken, before, after);
        ArrayList<WidgetReflowReport.Entry> moved = new ArrayList<>();
        for (LauncherWidgetRecord old : records.values()) {
            LauncherWidgetRecord now = relaid.get(old.appWidgetId);
            if (now != null && (now.page != old.page || !now.cell.equals(old.cell))) {
                moved.add(new WidgetReflowReport.Entry(old.appWidgetId, old.page, old.cell,
                    now.page, now.cell));
            }
        }
        plannedIds = afterIds;
        if (!commitValidated(relaid, null, next, pages[0], freshPages, orientation, layouts,
            nextSnapshot, revision + 1)) {
            return null;
        }
        return moved.isEmpty() ? WidgetReflowReport.EMPTY : new WidgetReflowReport(moved);
    }

    /** True while a {@link #shrinkGridRows} snapshot is held, edited since or not. */
    public synchronized boolean hasGridRowSnapshot() { return rowSnapshot != null; }

    /**
     * Gives the rows back. When nothing was edited since the shrink (no add, move, resize, remove
     * or page change, and the same orientation and grid are still on screen) the snapshot is
     * replayed exactly and true is returned. Otherwise the snapshot is dropped and only the grid
     * grows, the regained rows left empty, and false is returned. Refused with false and nothing
     * changed while an add is in flight; false also when there was no snapshot, in which case the
     * grid is left alone.
     */
    public synchronized boolean restoreGridRows() {
        RowSnapshot held = rowSnapshot;
        if (held == null || pending != null || migrationWritePending || readOnlyUnknownVersion) {
            return false;
        }
        if (held.isUntouched(this)) {
            OrientationLayout before = held.before;
            LinkedHashMap<Integer, LauncherWidgetRecord> replayed = new LinkedHashMap<>();
            for (LauncherWidgetRecord record : records.values()) {
                Placement placed = before.cells.get(record.appWidgetId);
                int at = placed == null ? -1 : ids.indexOf(placed.page);
                replayed.put(record.appWidgetId,
                    at < 0 ? record : record.withPage(at).withCell(placed.cell));
            }
            // The pages the shrink added at the end go again; any other page change stands.
            int count = pageCount;
            if (before.pageIds != null && before.pageIds.size() <= ids.size()
                && ids.subList(0, before.pageIds.size()).equals(before.pageIds)) {
                count = before.pageIds.size();
            }
            LinkedHashSet<Integer> fresh = new LinkedHashSet<>();
            for (Integer id : before.freshPages) {
                int at = ids.indexOf(id);
                if (at >= 0 && at < count) fresh.add(at);
            }
            return commitValidated(replayed, null, before.grid, count, fresh, orientation, layouts,
                null, revision + 1);
        }
        int rows = Math.min(WidgetGridDefinition.MAX_ROWS, grid.rows + held.rows);
        WidgetGridDefinition grown = new WidgetGridDefinition(rows, grid.columns);
        commitValidated(records, null, grown, pageCount, freshPages, orientation, layouts, null,
            revision + 1);
        return false;
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
            if (WidgetGridPlacementPolicy.canPlace(next, onPage, kept,
                WidgetGridPlacementPolicy.IGNORE_NONE)) {
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
     * <p>Returns true when a layout was swapped in, so a caller can redraw on exactly those turns.
     * The swap may leave every widget where it was — the same grid on both sides is the common
     * case — so this is "the wall was re-laid", not "something looks different".
     * Refused, with nothing changed, while an add is in flight — its reservation was made against
     * the layout on screen.</p>
     */
    /**
     * The same, for a caller holding raw preference numbers: the grid is clamped into its safety
     * bounds here rather than at each call site.
     */
    public synchronized boolean applyOrientation(@NonNull String key, int rows, int columns) {
        return applyOrientation(key, new WidgetGridDefinition(
            Math.max(WidgetGridDefinition.MIN_ROWS, Math.min(WidgetGridDefinition.MAX_ROWS, rows)),
            Math.max(WidgetGridDefinition.MIN_COLUMNS,
                Math.min(WidgetGridDefinition.MAX_COLUMNS, columns))));
    }

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
        int[] pages = { pageCount };
        LinkedHashMap<Integer, LauncherWidgetRecord> desired = new LinkedHashMap<>();
        for (LauncherWidgetRecord record : records.values()) {
            Placement placed = incoming == null ? null : incoming.cells.get(record.appWidgetId);
            int at = placed == null ? -1 : ids.indexOf(placed.page);
            LauncherWidgetRecord seeded = at < 0 ? record
                : record.withPage(at).withCell(placed.cell);
            desired.put(seeded.appWidgetId, seeded);
            pages[0] = Math.max(pages[0], seeded.page + 1);
        }
        LinkedHashMap<Integer, LauncherWidgetRecord> relaid = reflow(desired, next, pages);
        if (relaid == null) return false;
        if (!commitValidated(relaid, null, next, pages[0], freshPages, key, shelf, revision + 1)) {
            return false;
        }
        return true;
    }

    /** What the orientation on screen looks like right now, for the shelf. */
    private OrientationLayout snapshot() {
        return layoutOf(records, grid, null, null);
    }

    /**
     * The cells of {@code values} by page id. With {@code pageIds} given the layout also names the
     * pages it was made against and which of them were fresh ({@code fresh} are indices into it).
     */
    private OrientationLayout layoutOf(@NonNull Map<Integer, LauncherWidgetRecord> values,
                                       @NonNull WidgetGridDefinition definition,
                                       @Nullable List<Integer> pageIds,
                                       @Nullable Collection<Integer> fresh) {
        LinkedHashMap<Integer, Placement> cells = new LinkedHashMap<>();
        List<Integer> named = pageIds == null ? ids : pageIds;
        for (LauncherWidgetRecord record : values.values()) {
            int id = record.page < named.size() ? named.get(record.page) : record.page;
            cells.put(record.appWidgetId, new Placement(record.cell, id));
        }
        LinkedHashSet<Integer> freshIds = new LinkedHashSet<>();
        if (pageIds != null && fresh != null) {
            for (Integer page : fresh) if (page >= 0 && page < pageIds.size()) freshIds.add(pageIds.get(page));
        }
        return new OrientationLayout(definition,
            pageIds == null ? null : new ArrayList<>(pageIds), freshIds, cells);
    }

    /** The page ids with the owner grown, if need be, to {@code count} pages; kept for rollback. */
    @NonNull
    private List<Integer> idsGrownTo(int count) {
        ArrayList<Integer> out = new ArrayList<>(ids);
        if (count > out.size()) {
            List<Integer> grown = ownerAdd(count - out.size());
            preGrown = new ArrayList<>(grown);
            out.addAll(grown);
        }
        return out;
    }

    /** The fresh pages as ids. */
    @NonNull
    private LinkedHashSet<Integer> freshIds() {
        LinkedHashSet<Integer> out = new LinkedHashSet<>();
        for (Integer page : freshPages) if (page >= 0 && page < ids.size()) out.add(ids.get(page));
        return out;
    }

    @NonNull private List<Integer> ownerAdd(int count) {
        ownerBusy = true;
        try { return owner.addHomePages(count); } finally { ownerBusy = false; }
    }

    private void ownerRemove(@NonNull Collection<Integer> gone) {
        ownerBusy = true;
        try { owner.removeHomePages(gone); } finally { ownerBusy = false; }
    }

    private void ownerReorder(@NonNull List<Integer> order) {
        ownerBusy = true;
        try { owner.reorderHomePages(order); } finally { ownerBusy = false; }
    }

    /**
     * The wall before a row shrink and the wall the shrink left. "Edited since" is a comparison of
     * the live wall with {@code after}, so it needs no flag to keep in step with every mutator
     * (add, move, resize, remove, page add or remove all change it) and it survives a restart
     * because both layouts are persisted. A turn of the screen swaps the records, so it reads as
     * an edit too and the snapshot is never replayed into the wrong orientation.
     */
    private static final class RowSnapshot {
        @Nullable final String orientation;
        final int rows;
        @NonNull final OrientationLayout before;
        @NonNull final OrientationLayout after;
        RowSnapshot(@Nullable String orientation, int rows, @NonNull OrientationLayout before,
                    @NonNull OrientationLayout after) {
            this.orientation = orientation;
            this.rows = rows;
            this.before = before;
            this.after = after;
        }

        boolean isUntouched(@NonNull LauncherWidgetRepository live) {
            if (!java.util.Objects.equals(orientation, live.orientation)
                || !after.grid.equals(live.grid) || after.pageIds == null
                || !after.pageIds.equals(live.ids)
                || !after.freshPages.equals(live.freshIds())
                || after.cells.size() != live.records.size()) return false;
            for (LauncherWidgetRecord record : live.records.values()) {
                Placement placed = after.cells.get(record.appWidgetId);
                if (placed == null || record.page >= live.ids.size()
                    || placed.page != live.ids.get(record.page)
                    || !placed.cell.equals(record.cell)) return false;
            }
            return true;
        }
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
     * not what {@link #applyOrientation} lays the cells out on — the caller's grid is. Cells name
     * their page by id. {@code pageIds} and {@code freshPages} (ids) are only held by a row
     * snapshot, to tell whether the pages changed since; a shelf has neither, since every
     * orientation shares the Home pages.
     */
    private static final class OrientationLayout {
        @NonNull final WidgetGridDefinition grid;
        @Nullable final List<Integer> pageIds;
        @NonNull final LinkedHashSet<Integer> freshPages;
        @NonNull final LinkedHashMap<Integer, Placement> cells;
        OrientationLayout(@NonNull WidgetGridDefinition grid, @Nullable List<Integer> pageIds,
                          @NonNull LinkedHashSet<Integer> freshPages,
                          @NonNull LinkedHashMap<Integer, Placement> cells) {
            this.grid = grid;
            this.pageIds = pageIds;
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
        trimEmptyPageIds();
        return !trimFailed;
    }

    /** Whether the last trim had pages to take away and could not write it. */
    private boolean trimFailed;

    /**
     * {@link #trimEmptyPages()}, answering which pages it took away: their ids, in order; empty
     * when nothing changed or the write failed.
     */
    @NonNull
    public synchronized List<Integer> trimEmptyPageIds() {
        trimFailed = false;
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
        if (kept == pageCount) return Collections.emptyList();
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
        List<Integer> before = new ArrayList<>(ids);
        if (kept > 0) {
            ArrayList<Integer> plan = new ArrayList<>();
            for (int page = 0; page < pageCount; page++) if (keep[page]) plan.add(ids.get(page));
            plannedIds = plan;
        }
        if (!commitValidated(next, nextPending, grid, target, nextFresh, revision + 1)) {
            trimFailed = true;
            return Collections.emptyList();
        }
        before.removeAll(ids);
        return before;
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
        ArrayList<Integer> plan = new ArrayList<>(ids);
        plan.remove(page);
        plannedIds = plan;
        return commitValidated(next, nextPending, grid, pageCount - 1, nextFresh, revision + 1);
    }

    /**
     * Moves page {@code from} to {@code to} (Edit pages), carrying everything on it, and renumbers
     * the pages between ({@link com.termux.app.launcher.home.HomePageEdits#moveMapping}). One
     * commit; refused for a page that is not there or a move that goes nowhere.
     */
    public synchronized boolean movePage(int from, int to) {
        if (!com.termux.app.launcher.home.HomePageEdits.canMove(pageCount, from, to)) return false;
        int[] mapping = com.termux.app.launcher.home.HomePageEdits.moveMapping(pageCount, from, to);
        LinkedHashMap<Integer, LauncherWidgetRecord> next = new LinkedHashMap<>();
        for (LauncherWidgetRecord record : records.values()) {
            boolean onPage = record.page >= 0 && record.page < pageCount;
            next.put(record.appWidgetId, onPage ? record.withPage(mapping[record.page]) : record);
        }
        WidgetAddTransaction nextPending = pending != null && pending.page >= 0 && pending.page < pageCount
            ? pending.withPage(mapping[pending.page]) : pending;
        LinkedHashSet<Integer> nextFresh = new LinkedHashSet<>();
        for (Integer fresh : freshPages) {
            if (fresh >= 0 && fresh < pageCount) nextFresh.add(mapping[fresh]);
        }
        // The shelved orientations name pages by id, so they follow the move on their own.
        ArrayList<Integer> order = new ArrayList<>(ids);
        for (int i = 0; i < pageCount; i++) order.set(mapping[i], ids.get(i));
        plannedIds = order;
        return commitValidated(next, nextPending, grid, pageCount, nextFresh, revision + 1);
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
            recordsOnPage(transaction.page), transaction.cell,
            WidgetGridPlacementPolicy.IGNORE_NONE)) {
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
            transaction.cell, WidgetGridPlacementPolicy.IGNORE_NONE)) return false;
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

    /**
     * Empties the wall in one commit: no records, no reservation, a single page, and nothing left
     * on the shelf for the orientations that are not on screen. The grid and the orientation on
     * screen are how the wall is drawn rather than what is on it, so both stay as they are.
     *
     * <p>Shelved layouts go with the records because they are placements of those same widget
     * IDs; keeping them would leave the other orientation holding positions for widgets that no
     * longer exist, ready to resurface at the next rotation.
     *
     * @return true when the empty wall was written.
     */
    public synchronized boolean resetToEmptyWall() {
        LinkedHashMap<Integer, LauncherWidgetRecord> aside = orphans;
        orphans = new LinkedHashMap<>();
        if (commitValidated(new LinkedHashMap<Integer, LauncherWidgetRecord>(), null, grid, 1,
            Collections.<Integer>emptySet(), orientation,
            new LinkedHashMap<String, OrientationLayout>(), null, revision + 1)) {
            return true;
        }
        orphans = aside;
        return false;
    }

    @NonNull public synchronized String serialize() {
        return encode(records, orphans, ids, pending, grid, freshPages, orientation, layouts,
            rowSnapshot, revision);
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
        return commitValidated(next, nextPending, nextGrid, nextPageCount, nextFresh,
            nextOrientation, nextLayouts, rowSnapshot, nextRevision);
    }

    private boolean commitValidated(Map<Integer, LauncherWidgetRecord> next,
                                    @Nullable WidgetAddTransaction nextPending,
                                    WidgetGridDefinition nextGrid, int nextPageCount,
                                    @NonNull Collection<Integer> nextFresh,
                                    @Nullable String nextOrientation,
                                    @NonNull Map<String, OrientationLayout> nextLayouts,
                                    @Nullable RowSnapshot nextSnapshot, long nextRevision) {
        List<Integer> planned = plannedIds;
        plannedIds = null;
        List<Integer> already = preGrown;
        preGrown = new ArrayList<>();
        if (migrationWritePending || readOnlyUnknownVersion
            || !validatePaged(nextGrid, next, nextPending, nextPageCount)) {
            if (!already.isEmpty()) ownerRemove(already);
            return false;
        }
        LinkedHashSet<Integer> fresh = settledFresh(nextFresh, next, nextPageCount);
        // The page ids after this commit: the plan of a removal or a move, the owner's new pages
        // for a grown count, else the first pages as they stand.
        List<Integer> nextIds;
        List<Integer> grown = new ArrayList<>();
        if (planned != null && planned.size() == nextPageCount) {
            nextIds = new ArrayList<>(planned);
        } else if (nextPageCount > ids.size()) {
            int want = nextPageCount - ids.size();
            grown = ownerAdd(want);
            nextIds = new ArrayList<>(ids);
            nextIds.addAll(grown);
            if (grown.size() != want) {
                ownerRemove(grown);
                if (!already.isEmpty()) ownerRemove(already);
                return false;
            }
        } else {
            nextIds = new ArrayList<>(ids.subList(0, nextPageCount));
        }
        List<Integer> removed = new ArrayList<>(ids);
        removed.removeAll(nextIds);
        LinkedHashMap<String, OrientationLayout> shelf = new LinkedHashMap<>(nextLayouts);
        if (nextOrientation != null) shelf.remove(nextOrientation);
        // A page leaving takes the shelved cells on it along.
        for (Integer gone : removed) {
            for (Map.Entry<String, OrientationLayout> entry : shelf.entrySet()) {
                entry.setValue(withoutPage(entry.getValue(), gone));
            }
        }
        String encoded = encode(next, orphans, nextIds, nextPending, nextGrid, fresh,
            nextOrientation, shelf, nextSnapshot, nextRevision);
        if (!storage.write(encoded)) {
            List<Integer> back = new ArrayList<>(grown);
            back.addAll(already);
            if (!back.isEmpty()) ownerRemove(back);
            return false;
        }
        records = new LinkedHashMap<>(next);
        pending = nextPending;
        grid = nextGrid;
        ids = nextIds;
        pageCount = nextIds.size();
        freshPages = fresh;
        orientation = nextOrientation;
        layouts = shelf;
        rowSnapshot = nextSnapshot;
        revision = nextRevision;
        if (!removed.isEmpty()) {
            ownerRemove(removed);
        } else {
            List<Integer> held = owner.homePageIds();
            if (!held.equals(nextIds) && held.size() == nextIds.size()
                && held.containsAll(nextIds)) {
                ownerReorder(nextIds);
            }
        }
        // The owner may have declined part of it; take its list as the truth.
        onHomePagesChanged();
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

    /**
     * A built-in widget that no longer exists (the shell widget went in 2026-10) leaves its cell
     * empty rather than refusing the wall; the next commit writes the wall without it.
     */
    private static void dropRetiredBuiltins(@NonNull LinkedHashMap<Integer, LauncherWidgetRecord> loaded) {
        java.util.Iterator<LauncherWidgetRecord> it = loaded.values().iterator();
        while (it.hasNext()) {
            LauncherWidgetRecord record = it.next();
            if (record.builtinKind != null
                && com.termux.app.launcher.widget.builtin.BuiltinWidgetKind.fromId(record.builtinKind) == null) {
                android.util.Log.w("LauncherWidgets", "dropping retired built-in widget "
                    + record.builtinKind);
                it.remove();
            }
        }
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
            if (version != SCHEMA_VERSION && version != LEGACY_PAGED_VERSION
                && version != BUILTIN_VERSION && version != APP_ITEM_VERSION
                && version != FOLDER_VERSION && version != LARGE_FOLDER_VERSION) {
                readOnlyUnknownVersion = true;
                return;
            }
            WidgetGridDefinition loadedGrid = decodeGrid(root.getJSONObject("grid"));
            // A payload with the marker names pages by id. One without it names them by position:
            // the i-th Home page the owner lists, which the owner grows to cover what it names.
            boolean idKeyed = "id".equals(root.optString("pageKey", ""));
            if (idKeyed && selfOwner != null) {
                List<Integer> stored = decodeIdList(root.optJSONArray("pageIds"));
                if (!stored.isEmpty()) selfOwner.adopt(stored);
            }
            if (!idKeyed) {
                int have = owner.homePageIds().size();
                int need = pagesNamed(root);
                if (need > have) owner.addHomePages(need - have);
            }
            ids = new ArrayList<>(owner.homePageIds());
            int loadedPages = ids.size();
            LinkedHashMap<Integer, LauncherWidgetRecord> loaded = decodeRecords(root, true);
            dropRetiredBuiltins(loaded);
            WidgetAddTransaction loadedPending = root.has("pending")
                ? decodeTransaction(root.getJSONObject("pending"), true, 0, 0) : null;
            LinkedHashMap<Integer, LauncherWidgetRecord> loadedAside = new LinkedHashMap<>();
            LinkedHashSet<Integer> loadedFresh = new LinkedHashSet<>();
            JSONArray freshArray = root.optJSONArray("freshPages");
            if (idKeyed) {
                LinkedHashMap<Integer, LauncherWidgetRecord> projected = new LinkedHashMap<>();
                for (LauncherWidgetRecord record : loaded.values()) {
                    int at = ids.indexOf(record.page);
                    if (at >= 0) projected.put(record.appWidgetId, record.withPage(at));
                    else loadedAside.put(record.appWidgetId, record);
                }
                loaded = projected;
                if (loadedPending != null) {
                    int at = ids.indexOf(loadedPending.page);
                    loadedPending = at < 0 ? null : loadedPending.withPage(at);
                }
                for (Integer id : decodePages(freshArray)) {
                    int at = ids.indexOf(id);
                    if (at >= 0) loadedFresh.add(at);
                }
            } else {
                loadedFresh = decodePages(freshArray);
                legacyWriteBack = true;
            }
            if (!validatePaged(loadedGrid, loaded, loadedPending, loadedPages)) {
                // Refused silently once, and the wall simply looked empty: say so.
                android.util.Log.e("LauncherWidgets", "stored wall refused: " + loaded.size()
                    + " records on " + loadedPages + " page(s), grid " + loadedGrid);
                legacyWriteBack = false;
                return;
            }
            records = loaded;
            orphans = loadedAside;
            pending = loadedPending;
            grid = loadedGrid;
            pageCount = loadedPages;
            // A save written before hand-added pages existed simply has none of them.
            freshPages = settledFresh(loadedFresh, loaded, loadedPages);
            String loadedOrientation = root.optString("orientation", null);
            orientation = loadedOrientation == null || loadedOrientation.isEmpty()
                ? null : loadedOrientation;
            List<Integer> byPosition = idKeyed ? null : ids;
            layouts = decodeLayouts(root.optJSONObject("layouts"), orientation, byPosition);
            rowSnapshot = decodeRowSnapshot(root.optJSONObject("rowSnapshot"), byPosition);
            revision = Math.max(0, root.optLong("revision", 0));
        } catch (JSONException | IllegalArgumentException exception) {
            android.util.Log.e("LauncherWidgets", "stored wall unreadable", exception);
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
        ids = new ArrayList<>(owner.homePageIds());
        pageCount = ids.size();
        revision = Math.max(0, root.optLong("revision", 0));
        migrationWritePending = !storage.write(encode(loaded, orphans, ids, loadedPending,
            loadedGrid, Collections.emptySet(), null, Collections.emptyMap(), null, revision));
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
            if (legacy == null) throw new JSONException("unknown kind in v1");
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
        ids = new ArrayList<>(owner.homePageIds());
        pageCount = ids.size();
        revision = 0;
        migrationWritePending = !storage.write(encode(migrated, orphans, ids, migratedPending,
            migratedGrid, Collections.emptySet(), null, Collections.emptyMap(), null, 0));
    }

    @NonNull
    private static List<Integer> decodeIdList(@Nullable JSONArray array) {
        ArrayList<Integer> out = new ArrayList<>();
        for (int i = 0; array != null && i < array.length(); i++) {
            int id = array.optInt(i, -1);
            if (id >= 0 && !out.contains(id)) out.add(id);
        }
        return out;
    }

    /** How many pages a position-keyed payload names, anywhere in it. */
    private static int pagesNamed(@NonNull JSONObject root) {
        int need = Math.max(1, root.optInt("pages", 1));
        need = Math.max(need, pageAfter(root.optJSONArray("records")));
        JSONObject held = root.optJSONObject("pending");
        if (held != null) need = Math.max(need, held.optInt("page", 0) + 1);
        JSONObject shelf = root.optJSONObject("layouts");
        if (shelf != null) {
            Iterator<String> keys = shelf.keys();
            while (keys.hasNext()) need = Math.max(need, layoutPages(shelf.optJSONObject(keys.next())));
        }
        JSONObject snapshot = root.optJSONObject("rowSnapshot");
        if (snapshot != null) {
            need = Math.max(need, layoutPages(snapshot.optJSONObject("before")));
            need = Math.max(need, layoutPages(snapshot.optJSONObject("after")));
        }
        return need;
    }

    private static int layoutPages(@Nullable JSONObject layout) {
        if (layout == null) return 0;
        return Math.max(Math.max(1, layout.optInt("pages", 1)), pageAfter(layout.optJSONArray("cells")));
    }

    private static int pageAfter(@Nullable JSONArray items) {
        int out = 0;
        for (int i = 0; items != null && i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            if (item != null) out = Math.max(out, item.optInt("page", 0) + 1);
        }
        return out;
    }

    private static int idAt(@Nullable List<Integer> byPosition, int position) {
        if (byPosition == null) return position;
        return position < byPosition.size() ? byPosition.get(position) : position;
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
            // A kind this build cannot hold, or a folder that cannot be read, leaves its cell
            // empty and the rest of the wall stands.
            if (record == null) continue;
            if (loaded.put(record.appWidgetId, record) != null) throw new JSONException("duplicate widget ID");
        }
        return loaded;
    }

    private static String encode(Map<Integer, LauncherWidgetRecord> live,
                                 @NonNull Map<Integer, LauncherWidgetRecord> aside,
                                 @NonNull List<Integer> pageIds,
                                 @Nullable WidgetAddTransaction pendingTransaction,
                                 WidgetGridDefinition definition,
                                 @NonNull Collection<Integer> freshIndices,
                                 @Nullable String orientation,
                                 @NonNull Map<String, OrientationLayout> layouts,
                                 @Nullable RowSnapshot snapshot, long revision) {
        try {
            // Everything is written under its page id; the root says so, so a payload without the
            // marker is read as positions once (and written back as ids).
            LinkedHashMap<Integer, LauncherWidgetRecord> values = new LinkedHashMap<>();
            for (LauncherWidgetRecord record : live.values()) {
                values.put(record.appWidgetId, record.page < pageIds.size()
                    ? record.withPage(pageIds.get(record.page)) : record);
            }
            values.putAll(aside);
            WidgetAddTransaction transaction = pendingTransaction != null
                && pendingTransaction.page < pageIds.size()
                ? pendingTransaction.withPage(pageIds.get(pendingTransaction.page))
                : pendingTransaction;
            LinkedHashSet<Integer> fresh = new LinkedHashSet<>();
            for (Integer page : freshIndices) {
                if (page >= 0 && page < pageIds.size()) fresh.add(pageIds.get(page));
            }
            int pages = pageIds.size();
            JSONObject root = new JSONObject();
            boolean holdsFolder = holdsFolder(values.values());
            boolean writeKind = holdsFolder || holdsApp(values.values());
            root.put("version", holdsLargeFolder(values.values()) ? LARGE_FOLDER_VERSION
                : holdsFolder ? FOLDER_VERSION : writeKind ? APP_ITEM_VERSION
                : holdsBuiltin(values.values()) ? BUILTIN_VERSION : SCHEMA_VERSION);
            root.put("revision", revision);
            root.put("grid", encodeGrid(definition));
            root.put("pageKey", "id");
            root.put("pages", Math.max(1, pages));
            JSONArray idArray = new JSONArray();
            for (Integer id : pageIds) idArray.put((int) id);
            root.put("pageIds", idArray);
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
            if (snapshot != null) {
                JSONObject held = new JSONObject().put("rows", snapshot.rows)
                    .put("before", encodeLayout(snapshot.before))
                    .put("after", encodeLayout(snapshot.after));
                if (snapshot.orientation != null) held.put("orientation", snapshot.orientation);
                root.put("rowSnapshot", held);
            }
            JSONArray array = new JSONArray();
            for (LauncherWidgetRecord record : values.values()) {
                array.put(encodeRecord(record, writeKind));
            }
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
        if (value.pageIds != null) {
            JSONArray named = new JSONArray();
            for (Integer id : value.pageIds) named.put((int) id);
            out.put("pageIds", named);
        }
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
            @Nullable JSONObject value, @Nullable String active, @Nullable List<Integer> byPosition) {
        LinkedHashMap<String, OrientationLayout> shelf = new LinkedHashMap<>();
        if (value == null) return shelf;
        Iterator<String> keys = value.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            if (key.isEmpty() || key.equals(active)) continue;
            try {
                shelf.put(key, decodeLayout(value.getJSONObject(key), byPosition));
            } catch (JSONException | IllegalArgumentException ignored) {
                // Drop this orientation's shelf entry; the rest of the payload stands.
            }
        }
        return shelf;
    }

    /** {@code byPosition} is set for a layout keyed by page position: position i is that list's i-th id. */
    private static OrientationLayout decodeLayout(@NonNull JSONObject entry,
                                                  @Nullable List<Integer> byPosition)
        throws JSONException {
        LinkedHashMap<Integer, Placement> cells = new LinkedHashMap<>();
        JSONArray array = entry.optJSONArray("cells");
        for (int i = 0; array != null && i < array.length(); i++) {
            JSONObject item = array.getJSONObject(i);
            cells.put(item.getInt("id"), new Placement(decodeCell(item.getJSONObject("cell")),
                idAt(byPosition, Math.max(0, item.optInt("page", 0)))));
        }
        List<Integer> pageIds = null;
        JSONArray named = entry.optJSONArray("pageIds");
        if (named != null) {
            pageIds = new ArrayList<>();
            for (int i = 0; i < named.length(); i++) pageIds.add(named.optInt(i, -1));
        } else if (byPosition != null && entry.has("pages")) {
            pageIds = new ArrayList<>();
            int count = Math.max(1, entry.optInt("pages", 1));
            for (int i = 0; i < count; i++) pageIds.add(idAt(byPosition, i));
        }
        LinkedHashSet<Integer> fresh = new LinkedHashSet<>();
        for (Integer page : decodePages(entry.optJSONArray("freshPages"))) {
            fresh.add(idAt(byPosition, page));
        }
        return new OrientationLayout(decodeGrid(entry.getJSONObject("grid")), pageIds, fresh,
            cells);
    }

    /** An unreadable snapshot is dropped: the wall stands as it is and nothing is replayed. */
    @Nullable
    private static RowSnapshot decodeRowSnapshot(@Nullable JSONObject value,
                                                 @Nullable List<Integer> byPosition) {
        if (value == null) return null;
        try {
            String key = value.optString("orientation", null);
            return new RowSnapshot(key == null || key.isEmpty() ? null : key,
                Math.max(1, value.getInt("rows")),
                decodeLayout(value.getJSONObject("before"), byPosition),
                decodeLayout(value.getJSONObject("after"), byPosition));
        } catch (JSONException | IllegalArgumentException ignored) {
            return null;
        }
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

    private static boolean holdsBuiltin(@NonNull Collection<LauncherWidgetRecord> values) {
        for (LauncherWidgetRecord record : values) if (record.isBuiltin()) return true;
        return false;
    }

    private static boolean holdsApp(@NonNull Collection<LauncherWidgetRecord> values) {
        for (LauncherWidgetRecord record : values) if (record.isApp() || record.isShortcut()) return true;
        return false;
    }

    /** Whether a folder holds more apps than a v7 build reads back. */
    private static boolean holdsLargeFolder(@NonNull Collection<LauncherWidgetRecord> values) {
        for (LauncherWidgetRecord record : values) {
            if (record.isFolder() && record.folderApps().size() > HomeFolderPolicy.MAX_APPS) return true;
        }
        return false;
    }

    private static boolean holdsFolder(@NonNull Collection<LauncherWidgetRecord> values) {
        for (LauncherWidgetRecord record : values) if (record.isFolder()) return true;
        return false;
    }

    @NonNull
    private static JSONObject encodeAppRef(@NonNull com.termux.app.launcher.model.AppRef app)
        throws JSONException {
        return new JSONObject().put("package", app.packageName)
            .put("activity", app.activityName).put("user", app.userId)
            .put("serial", app.userSerialNumber).put("cloned", app.clonedProfile)
            .put("profileLabel", app.profileLabel);
    }

    @NonNull
    private static com.termux.app.launcher.model.AppRef decodeAppRef(@NonNull JSONObject app)
        throws JSONException {
        return new com.termux.app.launcher.model.AppRef(app.getString("package"),
            app.getString("activity"), app.optInt("user", -1), app.optLong("serial", -1L),
            app.optBoolean("cloned", false), app.optString("profileLabel", ""));
    }

    /**
     * A stored folder, read leniently: an app entry that cannot be read is left out, a folder of
     * one app comes back as that app's icon under the folder's key (the rule a removal follows),
     * and a folder with no readable app, or no folder block at all, is null so its cell is left
     * empty and the rest of the wall stands.
     */
    @Nullable
    private static LauncherWidgetRecord decodeFolder(@NonNull JSONObject value,
                                                     @NonNull WidgetCellRect cell) {
        try {
            int id = value.getInt("id");
            int page = Math.max(0, value.optInt("page", 0));
            JSONObject folder = value.getJSONObject("folder");
            JSONArray array = folder.getJSONArray("apps");
            List<LauncherWidgetRecord.FolderApp> apps = new ArrayList<>();
            java.util.HashSet<String> seen = new java.util.HashSet<>();
            for (int i = 0; i < array.length() && apps.size() < HomeFolderPolicy.HARD_MAX_APPS; i++) {
                JSONObject entry = array.optJSONObject(i);
                if (entry == null) continue;
                try {
                    LauncherWidgetRecord.FolderApp app = new LauncherWidgetRecord.FolderApp(
                        decodeAppRef(entry), entry.optString("label", ""));
                    if (seen.add(app.stableId())) apps.add(app);
                } catch (JSONException | IllegalArgumentException unreadable) {
                    // This app is left out; the folder keeps the others.
                }
            }
            if (apps.isEmpty()) throw new JSONException("a folder with no app");
            if (apps.size() == 1) return appRecord(id, apps.get(0), cell, page);
            return LauncherWidgetRecord.folder(id,
                HomeFolderPolicy.name(folder.optString("name", ""), HomeFolderPolicy.DEFAULT_NAME),
                apps, cell, page);
        } catch (JSONException | IllegalArgumentException malformed) {
            android.util.Log.w("LauncherWidgets", "dropping a Home folder that cannot be read: "
                + malformed.getMessage());
            return null;
        }
    }

    /** {@code writeKind}: a v6 payload, where every record names its kind. */
    private static JSONObject encodeRecord(LauncherWidgetRecord record, boolean writeKind)
        throws JSONException {
        JSONObject value = new JSONObject();
        value.put("id", record.appWidgetId);
        if (writeKind) value.put("kind", record.kind.name());
        value.put("provider", record.provider.flattenToString());
        if (record.builtinKind != null) value.put("builtin", record.builtinKind);
        com.termux.app.launcher.model.AppRef app = record.appRef();
        if (app != null) value.put("app", encodeAppRef(app));
        if (record.isFolder()) {
            JSONArray apps = new JSONArray();
            for (LauncherWidgetRecord.FolderApp member : record.folderApps()) {
                apps.put(encodeAppRef(member.app()).put("label", member.label));
            }
            value.put("folder", new JSONObject().put("name", record.folderName())
                .put("apps", apps));
        }
        value.put("profile", record.profileSerial);
        value.put("state", record.state.name());
        value.put("cell", encodeCell(record.cell));
        value.put("page", record.page);
        value.put("options", encodeBundle(record.sizeOptions()));
        if (record.lastRenderFailure != null) value.put("failure", record.lastRenderFailure);
        return value;
    }

    /**
     * One stored record, or null for a kind this build cannot hold or a folder that cannot be
     * read ({@link #decodeFolder}), which the caller leaves out. A record with no {@code kind} (v5 and older) is a built-in when
     * it names one and an app widget otherwise, exactly as those versions read it.
     */
    @Nullable
    private static LauncherWidgetRecord decodeRecord(JSONObject value, boolean hasCell,
                                                     int legacyColumn, int legacyRow)
        throws JSONException {
        ComponentName provider = ComponentName.unflattenFromString(value.getString("provider"));
        if (provider == null) throw new JSONException("invalid provider");
        WidgetCellRect cell = hasCell ? decodeCell(value.getJSONObject("cell"))
            : new WidgetCellRect(legacyColumn, legacyRow, legacyColumn + 1, legacyRow + 1);
        String kindName = value.optString("kind", null);
        if (kindName != null && !kindName.isEmpty()) {
            LauncherWidgetRecord.Kind kind;
            try {
                kind = LauncherWidgetRecord.Kind.valueOf(kindName);
            } catch (IllegalArgumentException unknown) {
                kind = null;
            }
            if (kind == null) {
                android.util.Log.w("LauncherWidgets", "dropping Home item of kind " + kindName);
                return null;
            }
            if (kind == LauncherWidgetRecord.Kind.FOLDER) return decodeFolder(value, cell);
            if (kind == LauncherWidgetRecord.Kind.SHORTCUT) {
                return LauncherWidgetRecord.shortcut(value.getInt("id"), provider.getPackageName(),
                    value.optLong("profile", 0L), cell, Math.max(0, value.optInt("page", 0)),
                    decodeBundle(value.optJSONObject("options")));
            }
            if (kind == LauncherWidgetRecord.Kind.APP) {
                com.termux.app.launcher.model.AppRef ref = decodeAppRef(value.getJSONObject("app"));
                return LauncherWidgetRecord.app(value.getInt("id"), ref, cell,
                    Math.max(0, value.optInt("page", 0)),
                    decodeBundle(value.optJSONObject("options")));
            }
        }
        String builtin = value.optString("builtin", null);
        if (builtin != null && !builtin.isEmpty()) {
            return LauncherWidgetRecord.builtin(value.getInt("id"), builtin, cell,
                Math.max(0, value.optInt("page", 0)), decodeBundle(value.optJSONObject("options")));
        }
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
