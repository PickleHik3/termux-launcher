package com.termux.app.wall;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Edit pages' rules over the wall's {@link WallPages}: what the page overview reads of the page
 * list (the Home pages' order and names, where the Terminal page stands, the Display pages it
 * lists) and the edits it makes (moves, Set as home, bindings, a new bound Display page). It
 * reads and writes the {@link WallPagesStore}; the few things only the launcher can do go
 * through {@link Launcher}. The activity builds one per opened overview and hands it to
 * {@link PageOverviewView}.
 */
public final class PageOverviewPages {

    /** The launcher's side of these rules: each call one step only the launcher can take. */
    public interface Launcher {
        /** The widget repository moves Home page {@code id} to Home position {@code to}. */
        void moveHomePage(int id, int to);
        /** The widget repository removes Home page {@code id}, at once. */
        void removeHomePage(int id);
        /** Writes a choice of Home that is Start in's ({@link StartInPolicy#choose}). */
        void applyStartIn(@NonNull StartInPolicy.Choice choice);
        /** The name of the Linux app {@code binding} names, as the catalog read it; null when unknown. */
        @Nullable String displayAppName(@NonNull String binding);
        /** The page a new page goes after: the page the wall stands on (-1 when there is none). */
        int pageAfterCurrent();
        /** The catalog and the bound Display pages' cards are read again. */
        void refreshDisplayCards();
        /** The wall rebuilds its cells from the page list. */
        void refreshPages();
    }

    @NonNull private final WallPagesStore store;
    /** The Assistant page's card name when it has none of its own. */
    @NonNull private final String assistantName;
    @NonNull private final Launcher launcher;

    public PageOverviewPages(@NonNull WallPagesStore store, @NonNull String assistantName,
                             @NonNull Launcher launcher) {
        this.store = store;
        this.assistantName = assistantName;
        this.launcher = launcher;
    }

    @NonNull
    private WallPages wallPages() {
        return store.current();
    }

    private void editWallPages(@NonNull WallPagesStore.Edit edit) {
        store.edit(edit);
    }

    // Home's pages (Edit pages' operations).

    /** The home page's place among the Home pages ({@link WallPages#homeIndex}). */
    public int defaultPage() {
        return wallPages().homeIndex();
    }

    // Each edit of Home's pages is mirrored in the wall's pages, so their order stays
    // the widget repository's. Removal is final at once: the undo window comes later.
    // The overview shows the Home pages in order; its cards are positions in that
    // order, and the page operations name the page by id.
    /** The Home page at position {@code from} moves to Home position {@code to}. */
    public void movePage(int from, int to) {
        List<Integer> ids = wallPages().homePageIdsForRepository();
        if (from < 0 || from >= ids.size()) return;
        launcher.moveHomePage(ids.get(from), to);
    }

    /** The Home page at position {@code page} goes, at once. */
    public void removePage(int page) {
        List<Integer> ids = wallPages().homePageIdsForRepository();
        if (page < 0 || page >= ids.size()) return;
        launcher.removeHomePage(ids.get(page));
    }

    /**
     * The card of {@code kind} at {@code index} (a Home page's index, an Assistant page's id, or
     * a window's row place) becomes home: a Home page or the Assistant page in the page list, any
     * other card through Start in ({@link StartInPolicy#choose}).
     */
    public void chooseHome(@NonNull PageOverviewPolicy.Kind kind, int index) {
        if (kind == PageOverviewPolicy.Kind.HOME_PAGE) {
            editWallPages(pages -> pages.chooseHomePage(index));
            return;
        }
        if (kind == PageOverviewPolicy.Kind.ASSISTANT) {
            editWallPages(pages -> pages.setHome(index));
            return;
        }
        launcher.applyStartIn(StartInPolicy.choose(kind));
    }

    /** The name Home page {@code page} was given in Edit pages; null for its default. */
    @Nullable
    public String homePageName(int page) {
        List<WallPages.Page> homes = wallPages().homePages();
        return page >= 0 && page < homes.size() ? homes.get(page).name : null;
    }

    // The wall's order (ADR 0015, A2a): the Terminal page anywhere among Home's.

    /** How many Home pages stand before the Terminal page; all of them when there is none. */
    public int homePagesBeforeTerminal() {
        WallPages pages = wallPages();
        WallPages.Page terminal = pages.terminal();
        if (terminal == null) return Integer.MAX_VALUE;
        int n = 0;
        for (WallPages.Page page : pages.pages()) {
            if (page.id == terminal.id) return n;
            if (page.kind == WallPages.PageKind.HOME) n++;
        }
        return n;
    }

    /**
     * The Home page at {@code homeIndex} moves to page index {@code wallIndex} on the wall, the
     * Terminal page counted as one page ({@link WallPages#move}).
     */
    public void moveHomePageOnWall(int homeIndex, int wallIndex) {
        List<WallPages.Page> homes = wallPages().homePages();
        if (homeIndex < 0 || homeIndex >= homes.size()) return;
        int id = homes.get(homeIndex).id;
        // The overview counts the listed pages; the wall also holds a temporary one.
        editWallPages(pages -> pages.move(id, pages.wallIndexOfListed(wallIndex)));
    }

    /** The Terminal page, with every window card, moves to page index {@code wallIndex}. */
    public void moveTerminalPage(int wallIndex) {
        WallPages.Page terminal = wallPages().terminal();
        if (terminal == null) return;
        int id = terminal.id;
        editWallPages(pages -> pages.move(id, pages.wallIndexOfListed(wallIndex)));
    }

    /** The Terminal page becomes home ({@link WallPages#setHome}). */
    public void setTerminalHome() {
        WallPages.Page terminal = wallPages().terminal();
        if (terminal == null) return;
        int id = terminal.id;
        editWallPages(pages -> pages.setHome(id));
    }

    // The Terminal page as a whole (ADR 0015 section 1).

    /** Whether the wall has the Terminal page. */
    public boolean hasTerminalPage() {
        return wallPages().terminal() != null;
    }

    /** Whether the Terminal page can leave: another page stays on the wall. */
    public boolean terminalPageRemovable() {
        return wallPages().terminal() != null && wallPages().listedPages().size() > 1;
    }

    // The Assistant page (ADR 0022), listed with the Display pages.

    /** Whether the wall has an Assistant page. */
    public boolean hasAssistantPage() {
        return wallPages().assistant() != null;
    }

    /** Whether the Assistant page is home. */
    public boolean assistantIsHome() {
        WallPages pages = wallPages();
        WallPages.Page assistant = pages.assistant();
        WallPages.Page home = pages.home();
        return assistant != null && home != null && home.id == assistant.id;
    }

    // Use as Terminal only: the Home pages are set aside, and Edit pages offers them back.

    /** How many Home pages are set aside (detached) while the wall shows none. */
    public int detachedHomePages() {
        int n = 0;
        for (WallPages.Page page : wallPages().detached()) {
            if (page.kind == WallPages.PageKind.HOME) n++;
        }
        return n;
    }

    /**
     * How many Home pages stay in the page list while the wall hides them: all of them unless
     * {@code homePagesShown}.
     */
    public int hiddenHomePages(boolean homePagesShown) {
        // Settings' Use as leaves the Home pages in the list, hidden by the wall.
        return homePagesShown ? 0 : wallPages().homePages().size();
    }

    // Display pages stand on the wall like any page: the overview lists them in order.

    /** The wall's Display pages and its Assistant page, in wall order; no temporary page. */
    @NonNull
    public List<PageOverviewView.DisplayPage> displayPages() {
        WallPages pages = wallPages();
        WallPages.Page terminal = pages.terminal();
        List<PageOverviewView.DisplayPage> out = new ArrayList<>();
        int homes = 0;
        boolean afterTerminal = false;
        // A temporary page (a drawer launch's) is not listed: it goes with its display.
        for (WallPages.Page page : pages.listedPages()) {
            if (terminal != null && page.id == terminal.id) afterTerminal = true;
            if (page.kind == WallPages.PageKind.HOME) homes++;
            if (page.kind == WallPages.PageKind.ASSISTANT) {
                out.add(new PageOverviewView.DisplayPage(page.id,
                    PageCardMenuPolicy.cardName(page.name, assistantName), homes,
                    afterTerminal, true));
                continue;
            }
            if (page.kind != WallPages.PageKind.DISPLAY) continue;
            String appName = page.binding == null ? null : launcher.displayAppName(page.binding);
            out.add(new PageOverviewView.DisplayPage(page.id,
                PageCardMenuPolicy.cardName(page.name,
                    DisplayPagePolicy.cardName(page, appName)), homes, afterTerminal));
        }
        return out;
    }

    /** The app Display page {@code id} is bound to; null for the whole display. */
    @Nullable
    public String displayBinding(int id) {
        WallPages.Page page = wallPages().page(id);
        return page == null ? null : page.binding;
    }

    /**
     * Whether the open display stands on a temporary page (a drawer launch's): "Add display" is
     * still offered then, and makes that page a lasting one.
     */
    public boolean displayTemporary() {
        return wallPages().temporaryDisplay() != null;
    }

    /** Display page {@code id} shows {@code app} from now on; null for the whole display. */
    public void bindDisplayPage(int id, @Nullable String app) {
        editWallPages(pages -> pages.bind(id, app));
        launcher.refreshDisplayCards();
        launcher.refreshPages();
    }

    /**
     * A Display page bound to {@code app} after the page the wall stands on
     * ({@link WallPages#add}); its id, or -1 when none was added.
     */
    public int addBoundDisplay(@NonNull String app) {
        final int after = launcher.pageAfterCurrent();
        WallPages.Result added = store.edit(pages ->
            pages.add(WallPages.PageKind.DISPLAY,
                pages.page(after) != null ? after : WallPages.END, app));
        if (!added.ok() || added.page == null) return -1;
        launcher.refreshDisplayCards();
        launcher.refreshPages();
        return added.page.id;
    }

    // The current session's row of windows.

    /** Edit pages really removed a window: Start in falls back if it named it. */
    public void startInAfterRemoval(@NonNull PageOverviewPolicy.Kind kind, int terminalsLeft) {
        String next = StartInPolicy.afterRemoval(wallPages().startIn(), kind, terminalsLeft);
        if (next != null) editWallPages(pages -> pages.chooseStartIn(next));
    }

    /** The overview card kind for a wall position of {@code page}'s kind. */
    @NonNull
    public static PageOverviewPolicy.Kind cardKind(@NonNull PaneWallPage page) {
        if (page == PaneWallPage.WIDGETS) return PageOverviewPolicy.Kind.HOME_PAGE;
        return page == PaneWallPage.DISPLAY ? PageOverviewPolicy.Kind.DISPLAY
            : page == PaneWallPage.ASSISTANT ? PageOverviewPolicy.Kind.ASSISTANT
            : PageOverviewPolicy.Kind.TERMINAL;
    }
}
