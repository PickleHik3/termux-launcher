package com.termux.app.wall;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * The wall's ordered pages and its home (ADR 0015, page list design 2026-10-27). Immutable: every
 * edit returns a {@link Result} holding the new value and what changed, or the value unchanged
 * and the rule that refused it. Pure; {@link WallPagesStore} keeps it.
 *
 * <p>Rules held here: at most one Terminal page (a page pending removal still counts, so an undo
 * can never make a second); removing the home page makes the first page home; the last Home page
 * is not removed while it is home and alone; a Terminal page with windows is removed only once
 * the caller has confirmed closing them. A page marked for removal is <em>pending</em>: it
 * leaves {@link #pages()} and {@link #stops} at once but stays until {@link #commitRemoval}
 * (or {@link #undo}).
 *
 * <p>Transitional (slice 1 only): the display is still a row window, so home may be the display
 * window ({@link #homeIsDisplayWindow()}); then {@link #home()} is null.
 */
public final class WallPages {

    /** What a page is. */
    public enum PageKind { HOME, TERMINAL, DISPLAY }

    /** Why an edit was refused. */
    public enum Error {
        UNKNOWN_PAGE, BAD_INDEX, SECOND_TERMINAL, LAST_HOME, LAST_PAGE, NEEDS_CONFIRM,
        NOT_PENDING, BINDING_NOT_DISPLAY
    }

    /** What an accepted edit did. */
    public enum ChangeType {
        NONE, ADDED, MOVED, REMOVAL_PENDING, UNDONE, REMOVAL_COMMITTED, HOME_SET, RENAMED, BOUND,
        HOMES_DETACHED, HOMES_ATTACHED
    }

    /** The id {@code add} takes for "at the end". */
    public static final int END = -1;

    /** One page: an id never reused, a kind, a name (null = default) and a Display binding. */
    public static final class Page {
        public final int id;
        @NonNull public final PageKind kind;
        @Nullable public final String name;
        /** A Linux app id for a Display page bound to one; else null. */
        @Nullable public final String binding;

        public Page(int id, @NonNull PageKind kind, @Nullable String name,
                    @Nullable String binding) {
            this.id = id;
            this.kind = kind;
            this.name = name;
            this.binding = binding;
        }

        Page withName(@Nullable String value) { return new Page(id, kind, value, binding); }

        Page withBinding(@Nullable String value) { return new Page(id, kind, name, value); }

        @Override public boolean equals(Object o) {
            if (!(o instanceof Page)) return false;
            Page p = (Page) o;
            return id == p.id && kind == p.kind && Objects.equals(name, p.name)
                && Objects.equals(binding, p.binding);
        }

        @Override public int hashCode() { return Objects.hash(id, kind, name, binding); }

        @Override public String toString() { return "Page#" + id + ":" + kind; }
    }

    /** One place the wall can land: a page, plus a window index on the Terminal page. */
    public static final class Stop {
        public final int pageId;
        /** The window index on the Terminal page; -1 on any other page. */
        public final int window;
        /** The Terminal page has no window: landing here starts a shell. */
        public final boolean startsShell;

        public Stop(int pageId, int window, boolean startsShell) {
            this.pageId = pageId;
            this.window = window;
            this.startsShell = startsShell;
        }

        /** A stop of a page that is not the Terminal page. */
        public static Stop of(int pageId) { return new Stop(pageId, -1, false); }

        /** A window stop; the shell flag does not take part in equality. */
        public static Stop window(int pageId, int window) { return new Stop(pageId, window, false); }

        @Override public boolean equals(Object o) {
            if (!(o instanceof Stop)) return false;
            Stop s = (Stop) o;
            return pageId == s.pageId && window == s.window;
        }

        @Override public int hashCode() { return 31 * pageId + window; }

        @Override public String toString() { return "Stop(" + pageId + "," + window + ")"; }
    }

    /** The stops either side of one; null at an end. */
    public static final class Neighbours {
        @Nullable public final Stop previous;
        @Nullable public final Stop next;

        Neighbours(@Nullable Stop previous, @Nullable Stop next) {
            this.previous = previous;
            this.next = next;
        }
    }

    /** The outcome of an edit: the value to use, what changed, or why it was refused. */
    public static final class Result {
        /** The new value; the old one when refused. */
        @NonNull public final WallPages pages;
        @NonNull public final ChangeType change;
        /** The page the change is about; null when refused. */
        @Nullable public final Page page;
        /** Set when refused. */
        @Nullable public final Error error;
        /** The page's index among the pages on the wall before and after; -1 when none. */
        public final int fromIndex;
        public final int toIndex;

        Result(@NonNull WallPages pages, @NonNull ChangeType change, @Nullable Page page,
               @Nullable Error error, int fromIndex, int toIndex) {
            this.pages = pages;
            this.change = change;
            this.page = page;
            this.error = error;
            this.fromIndex = fromIndex;
            this.toIndex = toIndex;
        }

        public boolean ok() { return error == null; }
    }

    /** The empty value: no pages, nothing home. */
    public static final WallPages EMPTY =
        new WallPages(Collections.<Page>emptyList(), Collections.<Integer>emptyList(),
            Collections.<Page>emptyList(), 0, 1, false);

    private final List<Page> all;          // in order, pending ones included
    private final List<Integer> pending;   // ids pending removal
    private final List<Page> detached;
    private final int homeId;
    private final int nextId;
    private final boolean homeIsDisplayWindow;
    /** The Home page that was home last (0: none), so a trip through Terminal only or the
     *  Workspace or the display comes back to it. */
    private final int lastHomePageId;
    /** Home was moved to the Terminal page by {@link #detachHomes}, to be given back. */
    private final boolean homeParked;

    /** Builds a value; used by the upgrade and the store's reader. */
    public WallPages(@NonNull List<Page> all, @NonNull List<Integer> pending,
                     @NonNull List<Page> detached, int homeId, int nextId,
                     boolean homeIsDisplayWindow) {
        this(all, pending, detached, homeId, nextId, homeIsDisplayWindow, 0, false);
    }

    public WallPages(@NonNull List<Page> all, @NonNull List<Integer> pending,
                     @NonNull List<Page> detached, int homeId, int nextId,
                     boolean homeIsDisplayWindow, int lastHomePageId, boolean homeParked) {
        this.lastHomePageId = lastHomePageId;
        this.homeParked = homeParked;
        this.all = Collections.unmodifiableList(new ArrayList<>(all));
        this.pending = Collections.unmodifiableList(new ArrayList<>(pending));
        this.detached = Collections.unmodifiableList(new ArrayList<>(detached));
        this.homeId = homeId;
        this.nextId = nextId;
        this.homeIsDisplayWindow = homeIsDisplayWindow;
    }

    /** [Home 1..n][Terminal] with n Home pages (ids 1..n, the Terminal n + 1): the wall as it is today. */
    @NonNull
    public static WallPages seed(int homeCount) {
        return WallPagesMigration.seed("home", 0, "home", homeCount);
    }

    // ---- state ----------------------------------------------------------------------------

    /** The pages on the wall, in order, without those pending removal. */
    @NonNull
    public List<Page> pages() {
        List<Page> out = new ArrayList<>(all.size());
        for (Page p : all) if (!pending.contains(p.id)) out.add(p);
        return Collections.unmodifiableList(out);
    }

    /** Every page held, pending ones included (the store's view). */
    @NonNull public List<Page> allPages() { return all; }

    /** The ids pending removal, in the order they were marked. */
    @NonNull public List<Integer> pendingIds() { return pending; }

    /** Pages kept in the store but not on the wall. */
    @NonNull public List<Page> detached() { return detached; }

    public int homeId() { return homeId; }

    public int nextId() { return nextId; }

    /** Transitional: home is the display window, which is not a page. */
    public boolean homeIsDisplayWindow() { return homeIsDisplayWindow; }

    /** The Home page that was home last (0 when none was). */
    public int lastHomePageId() { return lastHomePageId; }

    /** Home was parked on the Terminal page by {@link #detachHomes}. */
    public boolean homeParked() { return homeParked; }

    /** The Home page to come back to: home when it is one, else the one remembered. */
    private int rememberedHome() {
        Page h = homeIsDisplayWindow ? null : find(homeId);
        return h != null && h.kind == PageKind.HOME ? homeId : lastHomePageId;
    }

    public boolean isPending(int id) { return pending.contains(id); }

    /** The home page; null when there are no pages or home is the display window. */
    @Nullable
    public Page home() {
        return homeIsDisplayWindow ? null : page(homeId);
    }

    /** The page on the wall with this id, else null. */
    @Nullable
    public Page page(int id) {
        Page p = find(id);
        return p != null && !pending.contains(id) ? p : null;
    }

    /** The Terminal page, when the wall has one. */
    @Nullable
    public Page terminal() {
        for (Page p : pages()) if (p.kind == PageKind.TERMINAL) return p;
        return null;
    }

    // ---- stops ----------------------------------------------------------------------------

    /**
     * Every place the wall can land, in order: a page each, except the Terminal page, one stop per
     * window of the current session, or one stop that starts a shell when there is none.
     */
    @NonNull
    public List<Stop> stops(int windowCount) {
        List<Stop> out = new ArrayList<>();
        for (Page p : pages()) {
            if (p.kind != PageKind.TERMINAL) {
                out.add(Stop.of(p.id));
            } else if (windowCount <= 0) {
                out.add(new Stop(p.id, 0, true));
            } else {
                for (int w = 0; w < windowCount; w++) out.add(Stop.window(p.id, w));
            }
        }
        return out;
    }

    /** The position of {@code stop} among {@link #stops}; -1 when it is not one. */
    public int indexOf(@NonNull Stop stop, int windowCount) {
        return stops(windowCount).indexOf(stop);
    }

    /** The stops either side of {@code stop}; both null when it is not a stop. */
    @NonNull
    public Neighbours neighbours(@NonNull Stop stop, int windowCount) {
        List<Stop> stops = stops(windowCount);
        int i = stops.indexOf(stop);
        if (i < 0) return new Neighbours(null, null);
        return new Neighbours(i > 0 ? stops.get(i - 1) : null,
            i < stops.size() - 1 ? stops.get(i + 1) : null);
    }

    /**
     * What a page is called: its own name, else "Home n" (n counts the Home pages on the wall),
     * "Terminal", the bound app's name ({@code appName}, as the caller resolved it), or "Display".
     */
    @NonNull
    public String displayName(@NonNull Page page, @Nullable String appName) {
        if (page.name != null) return page.name;
        switch (page.kind) {
            case HOME: {
                int n = 0;
                for (Page p : pages()) {
                    if (p.kind != PageKind.HOME) continue;
                    n++;
                    if (p.id == page.id) return "Home " + n;
                }
                return "Home";
            }
            case TERMINAL: return "Terminal";
            case DISPLAY:
            default: return page.binding != null && appName != null ? appName : "Display";
        }
    }

    // ---- edits ----------------------------------------------------------------------------

    /**
     * Adds a page of {@code kind} after the page {@code afterId} ({@link #END}: last).
     * {@code binding} is for a Display page only. The first page added becomes home.
     */
    @NonNull
    public Result add(@NonNull PageKind kind, int afterId, @Nullable String binding) {
        if (binding != null && kind != PageKind.DISPLAY) return refuse(Error.BINDING_NOT_DISPLAY);
        if (kind == PageKind.TERMINAL && hasTerminal()) return refuse(Error.SECOND_TERMINAL);
        int at = all.size();
        if (afterId != END) {
            int i = indexInAll(afterId);
            if (i < 0) return refuse(Error.UNKNOWN_PAGE);
            at = i + 1;
        }
        Page page = new Page(nextId, kind, null, binding);
        List<Page> list = new ArrayList<>(all);
        list.add(at, page);
        boolean first = pages().isEmpty() && !homeIsDisplayWindow;
        WallPages next = new WallPages(list, pending, detached, first ? page.id : homeId,
            nextId + 1, homeIsDisplayWindow);
        return new Result(next, ChangeType.ADDED, page, null, -1, next.visibleIndex(page.id));
    }

    /** Moves a page to position {@code toIndex} among the pages on the wall. */
    @NonNull
    public Result move(int id, int toIndex) {
        Page page = page(id);
        if (page == null) return refuse(Error.UNKNOWN_PAGE);
        List<Page> visible = pages();
        if (toIndex < 0 || toIndex >= visible.size()) return refuse(Error.BAD_INDEX);
        int from = visible.indexOf(page);
        if (from == toIndex) return new Result(this, ChangeType.NONE, page, null, from, from);
        List<Page> list = new ArrayList<>(all);
        list.remove(page);
        List<Page> rest = new ArrayList<>(visible);
        rest.remove(page);
        int at = toIndex >= rest.size() ? list.size() : list.indexOf(rest.get(toIndex));
        list.add(at, page);
        WallPages next = copy(list, pending, detached, homeId, nextId, homeIsDisplayWindow);
        return new Result(next, ChangeType.MOVED, page, null, from, toIndex);
    }

    /** Removes a page that has no windows to close; see {@link #remove(int, int, boolean)}. */
    @NonNull
    public Result remove(int id) {
        return remove(id, 0, false);
    }

    /**
     * Marks a page pending removal: it leaves the wall at once. A Terminal page with
     * {@code openWindows} above 0 is removed only with {@code confirmed} (the caller has asked to
     * close them). The only page left, and the last Home page while it is home and alone, are
     * refused. Removing the home page makes the first page left home.
     */
    @NonNull
    public Result remove(int id, int openWindows, boolean confirmed) {
        Page page = page(id);
        if (page == null) return refuse(Error.UNKNOWN_PAGE);
        List<Page> visible = pages();
        if (visible.size() == 1) {
            return refuse(page.kind == PageKind.HOME ? Error.LAST_HOME : Error.LAST_PAGE);
        }
        if (page.kind == PageKind.TERMINAL && openWindows > 0 && !confirmed) {
            return refuse(Error.NEEDS_CONFIRM);
        }
        int from = visible.indexOf(page);
        List<Integer> marked = new ArrayList<>(pending);
        marked.add(id);
        int home = homeId;
        if (!homeIsDisplayWindow && homeId == id) {
            home = visible.get(from == 0 ? 1 : 0).id;
        }
        WallPages next = copy(all, marked, detached, home, nextId, homeIsDisplayWindow);
        return new Result(next, ChangeType.REMOVAL_PENDING, page, null, from, -1);
    }

    /** Takes a page back from pending removal, to where it stood. Home is not given back. */
    @NonNull
    public Result undo(int id) {
        Page page = find(id);
        if (page == null) return refuse(Error.UNKNOWN_PAGE);
        if (!pending.contains(id)) return refuse(Error.NOT_PENDING);
        List<Integer> marked = new ArrayList<>(pending);
        marked.remove(Integer.valueOf(id));
        WallPages next = copy(all, marked, detached, homeId, nextId, homeIsDisplayWindow);
        return new Result(next, ChangeType.UNDONE, page, null, -1, next.visibleIndex(id));
    }

    /** Deletes a pending page for good; the result's page is the one the owners drop data of. */
    @NonNull
    public Result commitRemoval(int id) {
        Page page = find(id);
        if (page == null) return refuse(Error.UNKNOWN_PAGE);
        if (!pending.contains(id)) return refuse(Error.NOT_PENDING);
        List<Page> list = new ArrayList<>(all);
        list.remove(page);
        List<Integer> marked = new ArrayList<>(pending);
        marked.remove(Integer.valueOf(id));
        WallPages next = copy(list, marked, detached, homeId, nextId, homeIsDisplayWindow);
        return new Result(next, ChangeType.REMOVAL_COMMITTED, page, null, -1, -1);
    }

    /** Makes a page on the wall home. */
    @NonNull
    public Result setHome(int id) {
        Page page = page(id);
        if (page == null) return refuse(Error.UNKNOWN_PAGE);
        WallPages next = withHome(all, detached, id, false,
            page.kind == PageKind.HOME ? id : rememberedHome(), false);
        return new Result(next, ChangeType.HOME_SET, page, null, -1, next.visibleIndex(id));
    }

    /** Transitional: makes the display window home. The pages and the home id stay. */
    @NonNull
    public Result setHomeDisplayWindow() {
        WallPages next = withHome(all, detached, homeId, true, rememberedHome(), false);
        return new Result(next, ChangeType.HOME_SET, null, null, -1, -1);
    }

    /** Names a page; null or blank goes back to its default name. */
    @NonNull
    public Result rename(int id, @Nullable String name) {
        Page page = page(id);
        if (page == null) return refuse(Error.UNKNOWN_PAGE);
        String value = name == null || name.trim().isEmpty() ? null : name.trim();
        return replace(page, page.withName(value), ChangeType.RENAMED);
    }

    /** Binds a Display page to a Linux app id (null: the screen as it is). */
    @NonNull
    public Result bind(int id, @Nullable String binding) {
        Page page = page(id);
        if (page == null) return refuse(Error.UNKNOWN_PAGE);
        if (page.kind != PageKind.DISPLAY) return refuse(Error.BINDING_NOT_DISPLAY);
        return replace(page, page.withBinding(binding), ChangeType.BOUND);
    }

    // ---- Home, Start in and the Home pages as the app's settings speak of them ------------------

    /** Start in as the app's strings name it: where home is (display, workspace or home). */
    @NonNull
    public String startIn() {
        if (homeIsDisplayWindow) return WallRow.START_IN_DISPLAY;
        Page home = home();
        return home != null && home.kind == PageKind.TERMINAL
            ? WallRow.START_IN_WORKSPACE : WallRow.START_IN_HOME;
    }

    /** The Home pages on the wall, in order. */
    @NonNull
    public List<Page> homePages() {
        List<Page> out = new ArrayList<>();
        for (Page p : pages()) if (p.kind == PageKind.HOME) out.add(p);
        return out;
    }

    /** Which Home page home is, counting Home pages from 0; 0 when home is not a Home page. */
    public int homeIndex() {
        Page home = home();
        if (home == null || home.kind != PageKind.HOME) return 0;
        return Math.max(0, homePages().indexOf(home));
    }

    /**
     * Makes home what Start in {@code startIn} names: the Terminal page for the workspace, the
     * display window, or a Home page (the one home already is, else the first). A value that
     * cannot be (no Terminal page, no Home page) leaves home as it is.
     */
    @NonNull
    public Result chooseStartIn(@Nullable String startIn) {
        String value = StartInPolicy.read(startIn);
        if (WallRow.START_IN_DISPLAY.equals(value)) return setHomeDisplayWindow();
        if (WallRow.START_IN_WORKSPACE.equals(value)) {
            Page t = terminal();
            return t == null ? refuse(Error.UNKNOWN_PAGE) : setHome(t.id);
        }
        Page home = home();
        if (home != null && home.kind == PageKind.HOME) return new Result(this, ChangeType.NONE,
            home, null, -1, -1);
        List<Page> homes = homePages();
        if (homes.isEmpty()) return refuse(Error.UNKNOWN_PAGE);
        Page last = page(lastHomePageId);
        return setHome(last != null && last.kind == PageKind.HOME ? last.id : homes.get(0).id);
    }

    /** Makes the {@code index}th Home page (from 0) home. */
    @NonNull
    public Result chooseHomePage(int index) {
        List<Page> homes = homePages();
        if (index < 0 || index >= homes.size()) return refuse(Error.BAD_INDEX);
        return setHome(homes.get(index).id);
    }

    /** Adds a Home page after the {@code afterIndex}th Home page (-1: at the end of the Homes). */
    @NonNull
    public Result addHomePage(int afterIndex) {
        List<Page> homes = homePages();
        if (afterIndex < 0 || afterIndex >= homes.size()) {
            return add(PageKind.HOME, homes.isEmpty() ? END : homes.get(homes.size() - 1).id, null);
        }
        return add(PageKind.HOME, homes.get(afterIndex).id, null);
    }

    /** Moves the Home page at index {@code from} to index {@code to} among the Home pages. */
    @NonNull
    public Result moveHomePage(int from, int to) {
        List<Page> homes = homePages();
        if (from < 0 || from >= homes.size() || to < 0 || to >= homes.size()) {
            return refuse(Error.BAD_INDEX);
        }
        return move(homes.get(from).id, visibleIndex(homes.get(to).id));
    }

    /** Marks the {@code index}th Home page pending removal. */
    @NonNull
    public Result removeHomePage(int index) {
        List<Page> homes = homePages();
        if (index < 0 || index >= homes.size()) return refuse(Error.BAD_INDEX);
        return remove(homes.get(index).id);
    }

    /**
     * Use as Terminal only: every Home page on the wall goes to the detached pages (ids kept) and
     * home becomes the Terminal page, unless home is the display window, which stays. Refused when
     * nothing but Home pages is on the wall.
     */
    @NonNull
    public Result detachHomes() {
        List<Page> homes = homePages();
        if (homes.isEmpty()) return new Result(this, ChangeType.NONE, null, null, -1, -1);
        Page t = terminal();
        if (t == null) return refuse(Error.LAST_PAGE);
        List<Page> list = new ArrayList<>(all);
        list.removeAll(homes);
        List<Page> kept = new ArrayList<>(detached);
        kept.addAll(homes);
        boolean homeWasHome = !homeIsDisplayWindow && find(homeId) != null
            && find(homeId).kind == PageKind.HOME;
        WallPages next = withHome(list, kept, homeWasHome ? t.id : homeId, homeIsDisplayWindow,
            rememberedHome(), homeWasHome || homeParked);
        return new Result(next, ChangeType.HOMES_DETACHED, null, null, -1, -1);
    }

    /** Use as Home screen: the detached Home pages return, in their order, before the Terminal page. */
    @NonNull
    public Result attachDetached() {
        List<Page> back = new ArrayList<>();
        List<Page> rest = new ArrayList<>();
        for (Page p : detached) (p.kind == PageKind.HOME ? back : rest).add(p);
        if (back.isEmpty()) return new Result(this, ChangeType.NONE, null, null, -1, -1);
        List<Page> list = new ArrayList<>(all);
        int at = list.size();
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).kind == PageKind.TERMINAL) { at = i; break; }
        }
        list.addAll(at, back);
        int home = homeId;
        boolean restore = homeParked && !homeIsDisplayWindow;
        if (restore) {
            for (Page p : back) if (p.id == lastHomePageId) home = p.id;
        }
        WallPages next = withHome(list, rest, home, homeIsDisplayWindow, lastHomePageId,
            restore && home != homeId ? false : homeParked);
        return new Result(next, ChangeType.HOMES_ATTACHED, null, null, -1, -1);
    }

    // ---- keeping the Home pages in step with the widget repository -------------------------

    /**
     * The Home pages this value counts for the repository: those on the wall, or, under Terminal
     * only where every one is set aside, the detached ones.
     */
    private boolean countsDetached() {
        return homePages().isEmpty() && !detachedHomes().isEmpty();
    }

    private List<Page> detachedHomes() {
        List<Page> out = new ArrayList<>();
        for (Page p : detached) if (p.kind == PageKind.HOME) out.add(p);
        return out;
    }

    /** How many Home pages the repository would hold for this value. */
    public int homeCount() {
        return countsDetached() ? detachedHomes().size() : homePages().size();
    }

    /**
     * The same pages with exactly {@code count} Home pages (at least 1): missing ones are added
     * after the last Home page, extra ones dropped from the end. A dropped home page hands home to
     * the first page. Pure; what the start-up guard and the repository's page changes apply.
     */
    @NonNull
    public WallPages withHomeCount(int count) {
        int want = Math.max(1, count);
        boolean inDetached = countsDetached();
        List<Page> homes = inDetached ? detachedHomes() : homePages();
        if (homes.size() == want) return this;
        List<Page> list = new ArrayList<>(all);
        List<Page> kept = new ArrayList<>(detached);
        int next = nextId;
        if (homes.size() < want) {
            List<Page> target = inDetached ? kept : list;
            int at = target.size();
            if (!homes.isEmpty()) at = target.indexOf(homes.get(homes.size() - 1)) + 1;
            else if (!inDetached) {
                for (int i = 0; i < list.size(); i++) {
                    if (list.get(i).kind == PageKind.TERMINAL) { at = i; break; }
                }
            }
            for (int i = homes.size(); i < want; i++) {
                target.add(at++, new Page(next++, PageKind.HOME, null, null));
            }
            return new WallPages(list, pending, kept, homeId, next, homeIsDisplayWindow,
                lastHomePageId, homeParked);
        }
        List<Page> dropped = homes.subList(want, homes.size());
        (inDetached ? kept : list).removeAll(dropped);
        return afterDrop(list, kept, dropped);
    }

    /**
     * The Home pages after the repository renumbered its pages: {@code mapping[i]} is where its
     * page i stands now, or {@link #END} (-1) when it is gone. Gone pages are dropped (never the
     * last Home page), the others take their new order. Pure.
     */
    @NonNull
    public WallPages withHomeMapping(@NonNull int[] mapping) {
        boolean inDetached = countsDetached();
        List<Page> homes = inDetached ? detachedHomes() : homePages();
        if (mapping.length != homes.size()) return this;
        List<Page> survivors = new ArrayList<>();
        List<Page> dropped = new ArrayList<>();
        Page[] byNew = new Page[mapping.length];
        int live = 0;
        for (int i = 0; i < mapping.length; i++) if (mapping[i] >= 0) live++;
        for (int i = 0; i < mapping.length; i++) {
            int to = mapping[i];
            if (to >= 0 && to < byNew.length && byNew[to] == null && live > 0) byNew[to] = homes.get(i);
            else dropped.add(homes.get(i));
        }
        for (Page p : byNew) if (p != null) survivors.add(p);
        if (survivors.isEmpty()) {
            survivors.add(homes.get(0));
            dropped.remove(homes.get(0));
        }
        List<Page> source = new ArrayList<>(inDetached ? detached : all);
        List<Page> result = new ArrayList<>(source.size());
        int fill = 0;
        for (Page p : source) {
            if (!homes.contains(p)) result.add(p);
            else if (fill < survivors.size()) result.add(survivors.get(fill++));
        }
        List<Page> list = inDetached ? new ArrayList<>(all) : result;
        List<Page> kept = inDetached ? result : new ArrayList<>(detached);
        return afterDrop(list, kept, dropped);
    }

    private WallPages afterDrop(List<Page> list, List<Page> kept, List<Page> dropped) {
        List<Integer> marked = new ArrayList<>(pending);
        int home = homeId;
        for (Page d : dropped) if (d.id == homeId && !homeIsDisplayWindow) {
            home = firstVisible(list, marked);
        }
        return new WallPages(list, marked, kept, home, nextId, homeIsDisplayWindow,
            lastHomePageId, homeParked);
    }

    private static int firstVisible(List<Page> list, List<Integer> marked) {
        for (Page p : list) if (!marked.contains(p.id)) return p.id;
        return 0;
    }

    // ---- internals ------------------------------------------------------------------------

    private Result replace(Page old, Page now, ChangeType type) {
        List<Page> list = new ArrayList<>(all);
        list.set(list.indexOf(old), now);
        WallPages next = copy(list, pending, detached, homeId, nextId, homeIsDisplayWindow);
        return new Result(next, type, now, null, -1, next.visibleIndex(now.id));
    }

    private Result refuse(Error error) {
        return new Result(this, ChangeType.NONE, null, error, -1, -1);
    }

    private WallPages copy(List<Page> all, List<Integer> pending, List<Page> detached,
                           int homeId, int nextId, boolean display) {
        return new WallPages(all, pending, detached, homeId, nextId, display, lastHomePageId,
            homeParked);
    }

    private WallPages withHome(List<Page> all, List<Page> detached, int homeId, boolean display,
                               int last, boolean parked) {
        return new WallPages(all, pending, detached, homeId, nextId, display, last, parked);
    }

    private boolean hasTerminal() {
        for (Page p : all) if (p.kind == PageKind.TERMINAL) return true;
        return false;
    }

    @Nullable
    private Page find(int id) {
        for (Page p : all) if (p.id == id) return p;
        return null;
    }

    private int indexInAll(int id) {
        for (int i = 0; i < all.size(); i++) if (all.get(i).id == id) return i;
        return -1;
    }

    private int visibleIndex(int id) {
        List<Page> visible = pages();
        for (int i = 0; i < visible.size(); i++) if (visible.get(i).id == id) return i;
        return -1;
    }

    @Override public boolean equals(Object o) {
        if (!(o instanceof WallPages)) return false;
        WallPages w = (WallPages) o;
        return homeId == w.homeId && nextId == w.nextId
            && homeIsDisplayWindow == w.homeIsDisplayWindow && all.equals(w.all)
            && pending.equals(w.pending) && detached.equals(w.detached)
            && lastHomePageId == w.lastHomePageId && homeParked == w.homeParked;
    }

    @Override public int hashCode() { return Objects.hash(all, pending, detached, homeId, nextId); }
}
