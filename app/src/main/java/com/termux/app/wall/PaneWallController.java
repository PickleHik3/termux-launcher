package com.termux.app.wall;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.page.PageKind;
import com.termux.app.terminal.PaneSurfaceStyle;

/**
 * Owns the wall: its row of pages (Home pages, the Terminal's windows, and the Display pages,
 * each a page of its own; ADR 0008, ADR 0015 A3b), which page is in front, and every way in and out
 * of them. The activity hands it the layout and the answers it cannot work out for itself — whether
 * the current session has a window, how many Home pages and windows there are and which is in
 * front — and nothing else needs to know the wall exists.
 */
public final class PaneWallController implements PaneWallLayout.Listener {

    /** Saved-instance-state key for the half the wall rested in ({@link WallRow#HALF_HOME}…). */
    public static final String ARG_HALF = "wall_half";
    /** Saved-instance-state key for the page's index within its half ({@link WallRow#savedIndex}). */
    public static final String ARG_INDEX = "wall_index";

    @NonNull private final PaneWallLayout mWall;
    @NonNull private WallTerminal mTerminal = WallTerminal.NONE;
    @NonNull private WallLanding mLanding = WallLanding.NONE;
    /** The wall's needs-only reads; {@link WallFacts#NONE} until the constructor or {@link #setFacts}. */
    @NonNull private WallFacts mFacts = WallFacts.NONE;
    @NonNull private WallKeyboardSwipe mKeyboardSwipe = WallKeyboardSwipe.NONE;
    @NonNull private WallStatusFold mStatusFold = WallStatusFold.NONE;
    /** The wall's pose, written only here. */
    @NonNull private final WallPose mPose = new WallPose();
    /** Copy-on-write, so telling them allocates nothing. */
    @NonNull private WallPose.Reader[] mPoseReaders = new WallPose.Reader[0];
    @Nullable private WidgetPaneFrame mWidgetsPage;
    @Nullable private com.termux.app.x11.X11PaneFrame mDisplayWindow;
    /** The Assistant page's one view while the wall holds the page. */
    @Nullable private View mAssistantView;
    @Nullable private com.termux.app.page.Page mAssistantPage;
    /** The page registry the host owns (ADR 0030); null until the host sets it. */
    @Nullable private com.termux.app.page.Pages mPages;
    /** The id the Assistant page is registered under in {@link #mPages}; -1 when none. */
    private int mAssistantPageId = -1;
    /** Makes the Home page for a Home page id; null until the host has its Home view ready. */
    @Nullable private java.util.function.IntFunction<com.termux.app.page.Page> mHomePageFactory;
    /** The Home pages registered in {@link #mPages}, by page id. */
    @NonNull private final java.util.Map<Integer, com.termux.app.page.Page> mHomePages =
        new java.util.HashMap<>();
    /** Makes the Display page for a Display page id; null until the host has set it. */
    @Nullable private java.util.function.IntFunction<com.termux.app.page.Page> mDisplayPageFactory;
    /** The Display pages registered in {@link #mPages}, by page id. */
    @NonNull private final java.util.Map<Integer, com.termux.app.page.Page> mDisplayPages =
        new java.util.HashMap<>();
    /** The Linux windows' seam (spec #384, #392); NONE without them. */
    @NonNull private WallLinux mLinux = WallLinux.NONE;
    /** The Terminal page the Linux windows' page size follows; null while none is watched. */
    @Nullable private View mLinuxSizeSource;
    /** Tells the Linux windows the Terminal page's size when its layout changes it. */
    private final View.OnLayoutChangeListener mLinuxSizeWatcher = (v, l, t, r, b, ol, ot, or, ob) -> {
        if (r - l != or - ol || b - t != ob - ot) mLinux.setPageSize(r - l, b - t);
    };
    /** Re-reads where the row's Linux windows stand: run on every landing and cell change. */
    @Nullable private Runnable mLinuxSync;
    /** The cover the Linux window in front shows; null without Linux windows. */
    @Nullable private View mLinuxView;
    @Nullable private PaneSurfaceStyle mStyle;
    /** How much of the pages' outlines the last frame left showing; 1 at rest. */
    private float mOutlineAlpha = 1f;
    /** How much of the terminal's rims the last frame left showing; 1 at rest. */
    private float mTerminalRimAlpha = 1f;
    /**
     * The Terminal was asked for — by Start in or by restored state — before the row had a
     * window: the wall goes there as soon as one arrives, unless something else moved it first.
     */
    private boolean mTerminalPending;
    /**
     * This landing on the empty Terminal page has asked for its first shell: a second settle
     * before the window arrives starts no second. Cleared when the wall lands elsewhere or the
     * window arrives.
     */
    private boolean mLandingShellStarted;

    // ---- The cells and the binding (ADR 0016) -----------------------------------------------
    // The wall lays out one cell per page; each kind's one view is bound to one page.
    /** The page list the cells were laid out from; null where only the kinds are known. */
    @Nullable private WallPages mKnownPages;
    @Nullable private com.termux.app.page.Pages.Stops mTerminalStops;
    /** No page id: kind-only cells hold negative ids, so a sentinel below them. */
    private static final int NO_ID = Integer.MIN_VALUE;
    private int mTerminalStopsId = NO_ID;
    /**
     * The cells hold no Display page and no Home page after the Terminal page ([Home...][Terminal]
     * or less): the wallpaper's row reads by kind, as it always did, not by the pages' order.
     */
    private boolean mDefaultOrder = true;
    /** The row had a window at the last {@link #refreshPages}. */
    private boolean mHadWindow;
    /** Where the display view is bound and lands; NONE until the host sets it. */
    @NonNull private WallDisplay mDisplay = WallDisplay.NONE;
    /** The run last handed to the host ({@link WallHome#bindRun}). */
    @NonNull private WallHome mHome = WallHome.NONE;
    private int mBoundFirst = 0;
    private int mBoundLast = -1;
    /** The Display page the display view was last bound to; -1 for none yet. */
    private int mDisplayPageId = -1;
    /** The Display page last handed to the Display seam ({@link WallDisplay#bind}). */
    private int mBoundDisplayPageId = -1;
    /**
     * The last used Display page: the one the wall last came to rest on, or was sent to by its
     * id. Not the display's first binding, which a new list makes on its own; -1 before any.
     */
    private int mLastDisplayPageId = -1;
    /** The cells hold a Display page: the wallpaper's pan reads the pages' order. */
    private boolean mHasDisplayPage;

    /**
     * The facts come with the constructor: it reads them on the way up, before a setter could
     * hand them over.
     */
    public PaneWallController(@NonNull PaneWallLayout wall, @NonNull WallFacts facts) {
        mWall = wall;
        mFacts = facts;
        mWall.setListener(this);
        mWall.setReducedMotion(facts.reducedMotion());
        refreshPages();
    }

    /** The Terminal page's seam. */
    public void setTerminal(@Nullable WallTerminal terminal) {
        mTerminal = terminal == null ? WallTerminal.NONE : terminal;
    }

    /** The seam told where the wall commits and where it rests. */
    public void setLanding(@Nullable WallLanding landing) {
        mLanding = landing == null ? WallLanding.NONE : landing;
    }

    /** Replace the needs-only reads the constructor was given. */
    public void setFacts(@Nullable WallFacts facts) {
        mFacts = facts == null ? WallFacts.NONE : facts;
    }

    /** The keyboard swipe's seam. */
    public void setKeyboardSwipe(@Nullable WallKeyboardSwipe swipe) {
        mKeyboardSwipe = swipe == null ? WallKeyboardSwipe.NONE : swipe;
    }

    /** The status bar fold's seam. */
    public void setStatusFold(@Nullable WallStatusFold fold) {
        mStatusFold = fold == null ? WallStatusFold.NONE : fold;
    }

    /** The wall's pose; written only by this controller. */
    @NonNull public WallPose pose() { return mPose; }

    /** Hear every change of the pose, on the same call the wall made it. */
    public void addPoseReader(@NonNull WallPose.Reader reader) {
        WallPose.Reader[] grown = java.util.Arrays.copyOf(mPoseReaders, mPoseReaders.length + 1);
        grown[grown.length - 1] = reader;
        mPoseReaders = grown;
    }

    private void tellPoseReaders(int changed) {
        WallPose.Reader[] readers = mPoseReaders;
        for (int i = 0; i < readers.length; i++) readers[i].onWallPose(mPose, changed);
    }

    /**
     * The host's page registry: the Assistant page registers there while the wall holds it, and
     * every landing records the front there. Re-reads the pages so a page built before this call
     * is registered and the current cell is the recorded front.
     */
    public void setPages(@Nullable com.termux.app.page.Pages pages) {
        if (mPages != null && mAssistantPageId >= 0) mPages.remove(mAssistantPageId);
        if (mPages != null) for (Integer id : mHomePages.keySet()) mPages.remove(id);
        mHomePages.clear();
        if (mPages != null) for (Integer id : mDisplayPages.keySet()) mPages.remove(id);
        mDisplayPages.clear();
        if (mPages != null && mTerminalStopsId != NO_ID) mPages.remove(mTerminalStopsId);
        mTerminalStopsId = NO_ID;
        mAssistantPageId = -1;
        mAssistantPage = null;
        // A view built before this registry wears the old host: it goes, and the sync below
        // builds the page again on this registry's host and registers it.
        View assistantView = mAssistantView;
        if (assistantView != null) {
            mAssistantView = null;
            mWall.setPageView(PageKind.ASSISTANT, null);
            mWall.removeView(assistantView);
        }
        mPages = pages;
        syncHomePages();
        syncDisplayPages();
        syncTerminalStops();
        syncAssistantPage();
        recordFront();
    }

    /**
     * The Linux windows (spec #384, #392): {@code cover} is the one view a Linux window in front of
     * the Terminal's row shows, stood in the Terminal page's place ({@link
     * PaneWallLayout#setTerminalCover}); {@code sync} re-reads where the row's Linux windows stand,
     * run on every landing. Nulls take them away.
     */
    public void setLinuxWindows(@Nullable View cover, @Nullable WallLinux linux,
                                @Nullable Runnable sync) {
        mWall.setTerminalCover(cover);
        mLinuxView = cover;
        mLinux = linux == null ? WallLinux.NONE : linux;
        mLinuxSync = sync;
        mLinux.applyStyle(mStyle);
        watchLinuxPageSize();
        syncLinux();
    }

    /** Follows the Terminal page's size for the Linux windows (the cover takes the same frame). */
    private void watchLinuxPageSize() {
        View terminal = mLinux == WallLinux.NONE ? null : mWall.pageView(PageKind.TERMINAL);
        if (terminal == mLinuxSizeSource) return;
        if (mLinuxSizeSource != null) mLinuxSizeSource.removeOnLayoutChangeListener(mLinuxSizeWatcher);
        mLinuxSizeSource = terminal;
        if (terminal == null) return;
        terminal.addOnLayoutChangeListener(mLinuxSizeWatcher);
        if (terminal.getWidth() > 0) mLinux.setPageSize(terminal.getWidth(), terminal.getHeight());
    }

    /** Whether the cover stands in the Terminal page's place: a Linux window is in front of the row. */
    public void setLinuxInFront(boolean front) {
        mWall.setTerminalCoverShown(front);
    }

    private void syncLinux() {
        mLinux.setFrontPage(mWall.currentPage(), mWall.currentPageId());
        if (mLinuxSync != null) mLinuxSync.run();
    }

    /**
     * The seam that makes one Home page per Home page id; the wall controller never names the
     * launcher's classes. Registers the Home pages at once and on every later page-list change.
     */
    public void setHomePageFactory(
            @Nullable java.util.function.IntFunction<com.termux.app.page.Page> factory) {
        mHomePageFactory = factory;
        syncHomePages();
        recordFront();
    }

    /**
     * The Display's seam: told where the one display view is bound and where the wall lands on
     * it. The binding made before this call is handed over at once.
     */
    public void setDisplay(@NonNull WallDisplay display) {
        mDisplay = display;
        if (mBoundDisplayPageId >= 0) display.bind(mBoundDisplayPageId);
    }

    /**
     * The seam that makes one Display page per Display page id, all sharing the one display view;
     * the wall controller never names the display's classes. Registers them at once and on every
     * later page-list change.
     */
    public void setDisplayPageFactory(
            @Nullable java.util.function.IntFunction<com.termux.app.page.Page> factory) {
        mDisplayPageFactory = factory;
        syncDisplayPages();
        recordFront();
    }

    /**
     * The Terminal cell's windows, the pages of the one Terminal wall cell. Registered at once and
     * on every later page-list change.
     */
    public void setTerminalStops(@Nullable com.termux.app.page.Pages.Stops stops) {
        mTerminalStops = stops;
        syncTerminalStops();
        recordFront();
    }

    /** Registers the Terminal stops under the Terminal cell's id; drops the old id's. */
    private void syncTerminalStops() {
        int id = NO_ID;
        if (mKnownPages != null) {
            for (WallPages.Page page : mKnownPages.pages()) {
                if (page.kind == PageKind.TERMINAL) { id = page.id; break; }
            }
        } else if (mFacts.useAs().rowPages().contains(PageKind.TERMINAL)) {
            id = PaneWallLayout.kindPageId(PageKind.TERMINAL);
        }
        if (mPages == null || mTerminalStops == null) id = NO_ID;
        if (mTerminalStopsId != NO_ID && mTerminalStopsId != id && mPages != null) {
            mPages.remove(mTerminalStopsId);
        }
        mTerminalStopsId = id;
        if (id != NO_ID) mPages.putStops(id, mTerminalStops);
    }

    /** Keeps one Display page registered per Display page id the wall holds. */
    private void syncDisplayPages() {
        java.util.List<Integer> ids = new java.util.ArrayList<>();
        if (mKnownPages != null) {
            for (WallPages.Page page : mKnownPages.pages()) {
                if (page.kind == PageKind.DISPLAY) ids.add(page.id);
            }
        } else if (mFacts.useAs().rowPages().contains(PageKind.DISPLAY)) {
            // A row laid out from the kinds alone holds one Display cell under the kind's own id.
            ids.add(PaneWallLayout.kindPageId(PageKind.DISPLAY));
        }
        syncRegistered(mDisplayPageFactory, mDisplayPages, ids);
    }

    /** Registers one page per id from {@code factory} in {@link #mPages}; drops ids that left. */
    private void syncRegistered(
            @Nullable java.util.function.IntFunction<com.termux.app.page.Page> factory,
            @NonNull java.util.Map<Integer, com.termux.app.page.Page> registered,
            @NonNull java.util.List<Integer> ids) {
        if (mPages == null || factory == null) {
            if (mPages != null) for (Integer id : registered.keySet()) mPages.remove(id);
            registered.clear();
            return;
        }
        java.util.Iterator<java.util.Map.Entry<Integer, com.termux.app.page.Page>> it =
            registered.entrySet().iterator();
        while (it.hasNext()) {
            java.util.Map.Entry<Integer, com.termux.app.page.Page> entry = it.next();
            if (ids.contains(entry.getKey())) continue;
            mPages.remove(entry.getKey());
            it.remove();
        }
        for (int i = 0; i < ids.size(); i++) {
            int id = ids.get(i);
            if (registered.containsKey(id)) continue;
            com.termux.app.page.Page page = factory.apply(id);
            registered.put(id, page);
            mPages.put(id, page);
        }
    }

    /** Keeps one Home page registered per Home page id the wall holds; drops those that left. */
    private void syncHomePages() {
        java.util.function.IntFunction<com.termux.app.page.Page> factory = mHomePageFactory;
        if (mPages == null || factory == null) {
            if (mPages != null) for (Integer id : mHomePages.keySet()) mPages.remove(id);
            mHomePages.clear();
            return;
        }
        // A row laid out from the kinds alone holds one Home cell under the kind's own id.
        java.util.List<Integer> homes = new java.util.ArrayList<>();
        if (mKnownPages != null) {
            for (WallPages.Page home : mKnownPages.homePages()) homes.add(home.id);
        } else if (mFacts.useAs().rowPages().contains(PageKind.HOME)) {
            homes.add(PaneWallLayout.kindPageId(PageKind.HOME));
        }
        // The new pages go in before the old ones leave, so a switch between ids never empties
        // the declared glass for a moment.
        for (int i = 0; i < homes.size(); i++) {
            int id = homes.get(i);
            if (mHomePages.containsKey(id)) continue;
            com.termux.app.page.Page page = factory.apply(id);
            mHomePages.put(id, page);
            mPages.put(id, page);
        }
        java.util.Iterator<java.util.Map.Entry<Integer, com.termux.app.page.Page>> it =
            mHomePages.entrySet().iterator();
        while (it.hasNext()) {
            java.util.Map.Entry<Integer, com.termux.app.page.Page> entry = it.next();
            boolean kept = homes.contains(entry.getKey());
            if (kept) continue;
            mPages.remove(entry.getKey());
            it.remove();
        }
    }

    /** Records the cell the wall rests on as the front, when it is not moving. */
    private void recordFront() {
        if (mPages == null || mWall.isMoving()) return;
        mPages.landed(mWall.currentPageId(), mWall.currentPage());
    }

    @NonNull
    public PaneWallLayout wall() {
        return mWall;
    }

    /** The terminal's pane host is the Terminal's page while a terminal window is in front. */
    public void attachTerminalPage(@NonNull View paneHost) {
        mWall.setPageView(PageKind.TERMINAL, paneHost);
        watchLinuxPageSize();
    }

    /**
     * The terminal's pane host as the wall's middle page, with where its panes stand along its
     * bottom border, so that border's glow lies under the pane pressed ({@link PaneEdges}).
     */
    public void attachTerminalPage(@NonNull View paneHost, @Nullable PaneEdges edges) {
        mWall.setTerminalPaneEdges(edges);
        attachTerminalPage(paneHost);
    }

    /**
     * Re-read the page list (ADR 0016): the wall lays out one cell per page, and keeps the page
     * it is on. Harmless and cheap when nothing changed. A page that leaves while the wall rests
     * on it hands the wall to the page on its left, which is bound and shown where it stands.
     */
    public void refreshPages() {
        mWall.setReducedMotion(mFacts.reducedMotion());
        boolean hasWindow = mFacts.hasWindow();
        // A window opened on a wall whose Terminal page was removed: the page comes back after
        // Home and the wall lands on it. Every way a terminal opens reaches here, as the row
        // changes; the store's change reads the pages again.
        if (!mFacts.homePagesOnly()
            && TerminalPageReturn.needed(mFacts.wallPages(), mFacts.terminalWindowOpen())) {
            mTerminalPending = true;
            if (!mTerminal.restore()) mTerminalPending = false;
        }
        boolean lostWindow = mHadWindow && !hasWindow;
        mHadWindow = hasWindow;
        if (hasWindow) mLandingShellStarted = false;
        int before = mWall.currentPageId();
        mWall.setCells(cells(hasWindow));
        // The empty Terminal page shows its card, not a blank view, until a window arrives.
        mWall.setKindReady(PageKind.TERMINAL, hasWindow);
        int after = mWall.currentPageId();
        // The bindings follow the list: a bound page that went hands its view to another.
        int home = mWall.boundPageId(PageKind.HOME);
        if (after != before && home == after) bindHome(after);
        else bindRunOf(home);
        // A kind-only cell holds every page of its kind: handed the wall, it turns to its last.
        if (after != before && after < 0 && mWall.currentPage() != PageKind.TERMINAL) {
            PageKind kind = mWall.currentPage();
            showInner(kind, Math.max(0, innerCount(kind) - 1));
        }
        int display = mWall.boundPageId(PageKind.DISPLAY);
        bindDisplay(display >= 0 ? display : -1);
        syncHomePages();
        syncDisplayPages();
        syncLinux();
        syncTerminalStops();
        syncAssistantPage();
        recordFront();
        // The last window closed under the wall: the empty Terminal page keeps its cell, and the
        // wall hands to the Home page beside it (ADR 0015). Closing a window is not a landing,
        // so no shell starts.
        if (lostWindow && !mTerminalPending && mWall.currentPage() == PageKind.TERMINAL) {
            goToLastHomePage(false);
        }
        // The Terminal page asked for before its window: the wall goes there once one arrives.
        if (mTerminalPending && hasWindow && has(PageKind.TERMINAL)) {
            mTerminalPending = false;
            mWall.goTo(PageKind.TERMINAL, false);
        }
    }

    /**
     * The cells the wall lays out: one per page of {@link WallFacts#wallPages}, in order, by page id;
     * under Terminal only without the Home pages. The Terminal page is a cell with or without a
     * window: empty, it is a stop whose landing starts the first shell (ADR 0016 section 6). A
     * kind-only list ({@link UseAs#rowPages}) where the pages are not known.
     */
    @NonNull
    private java.util.List<PaneWallLayout.Cell> cells(boolean hasWindow) {
        boolean homeShown = mFacts.useAs() == UseAs.HOME;
        // Under Terl the wall is Home's pages alone: the rest are windows of the stack.
        boolean homeOnly = mFacts.homePagesOnly();
        WallPages pages = mFacts.wallPages();
        java.util.List<PaneWallLayout.Cell> out = new java.util.ArrayList<>();
        boolean hasHome = false;
        boolean hasTerminal = false;
        boolean hasDisplay = false;
        boolean homeAfterTerminal = false;
        if (pages != null) {
            for (WallPages.Page page : pages.pages()) {
                PageKind kind = page.kind;
                if (kind == PageKind.HOME && !homeShown) continue;
                if (!TerlWall.laysOut(kind, homeOnly)) continue;
                if (kind == PageKind.HOME && hasTerminal) homeAfterTerminal = true;
                hasHome |= kind == PageKind.HOME;
                hasTerminal |= kind == PageKind.TERMINAL;
                // The Assistant page stands off the default order as a Display page does: the
                // wallpaper's pan reads the pages' order.
                hasDisplay |= kind == PageKind.DISPLAY || kind == PageKind.ASSISTANT;
                out.add(new PaneWallLayout.Cell(page.id, kind, pages.displayName(page, null)));
            }
        }
        // A row with windows and a list without the Terminal page do not agree: the kinds alone.
        if (pages == null || out.isEmpty() || (homeShown && !hasHome)
            || (!homeOnly && hasWindow && !hasTerminal)) {
            mKnownPages = null;
            mDefaultOrder = true;
            mHasDisplayPage = false;
            out.clear();
            // The Terminal page is on the row whether or not it has a window; under Terl Home
            // alone is (the stack covers the wall where there is no Home).
            java.util.List<PageKind> kinds = homeOnly && homeShown
                ? java.util.Collections.singletonList(PageKind.HOME) : mFacts.useAs().rowPages();
            for (PageKind kind : kinds) {
                out.add(new PaneWallLayout.Cell(PaneWallLayout.kindPageId(kind), kind, null));
            }
            return out;
        }
        mKnownPages = pages;
        mHasDisplayPage = hasDisplay;
        mDefaultOrder = !hasDisplay && !homeAfterTerminal;
        return out;
    }

    /** Whether the wall has a cell of {@code kind}. */
    private boolean has(@NonNull PageKind kind) {
        return mWall.boundPageId(kind) != PaneWallLayout.NO_PAGE;
    }

    /**
     * Builds the Assistant page's view while the wall holds an Assistant page and binds it, and
     * drops it once the page is gone.
     */
    private void syncAssistantPage() {
        WallPages.Page page = mKnownPages == null ? null : mKnownPages.assistant();
        int id = page == null ? -1 : page.id;
        if (id < 0) {
            View view = mAssistantView;
            if (view == null) return;
            mAssistantView = null;
            mAssistantPage = null;
            if (mPages != null && mAssistantPageId >= 0) mPages.remove(mAssistantPageId);
            mAssistantPageId = -1;
            mWall.setPageView(PageKind.ASSISTANT, null);
            mWall.removeView(view);
            return;
        }
        if (mAssistantView == null) {
            com.termux.app.page.Page assistant = mFacts.assistantPageHost().createPage(mWall.getContext(),
                mPages == null ? com.termux.app.page.PageHost.NONE : mPages.host());
            View view = assistant.view();
            mAssistantPage = assistant;
            mWall.addView(view);
            mAssistantView = view;
            mWall.setPageView(PageKind.ASSISTANT, view);
            if (view instanceof com.termux.app.assistant.AssistantPageView) {
                ((com.termux.app.assistant.AssistantPageView) view).applyStyle(mStyle);
            }
            if (view instanceof com.termux.app.terminal.PaneContentFrame)
                ((com.termux.app.terminal.PaneContentFrame) view).setStatusRowPx(mStatusRowPx);
        }
        if (id >= 0) mWall.bind(PageKind.ASSISTANT, id);
        if (mPages != null && mAssistantPage != null && mAssistantPageId != id) {
            if (mAssistantPageId >= 0) mPages.remove(mAssistantPageId);
            mPages.put(id, mAssistantPage);
            mAssistantPageId = id;
        }
    }

    /** The Assistant page's view while the wall holds the page; null otherwise. */
    @Nullable
    public View assistantView() {
        return mAssistantView;
    }

    /** Go to page {@code pageId}'s cell; false when the wall has no such page. */
    public boolean goToPageId(int pageId, boolean animate) {
        return goToPage(pageId, animate);
    }

    /** Go to the Assistant page; false when the wall has none. */
    public boolean goToAssistantPage(boolean animate) {
        WallPages pages = mKnownPages;
        if (pages == null) return false;
        WallPages.Page page = pages.assistant();
        return page != null && goToPage(page.id, animate);
    }

    /** Bind the display view to Display page {@code pageId}, telling the host when it moved. */
    private void bindDisplay(int pageId) {
        if (pageId >= 0) {
            mDisplayPageId = pageId;
            mWall.bind(PageKind.DISPLAY, pageId);
        }
        if (pageId == mBoundDisplayPageId) return;
        mBoundDisplayPageId = pageId;
        mDisplay.bind(pageId);
    }

    /** The Display page the display view is bound to; -1 while the wall has none. */
    public int boundDisplayPage() {
        return mBoundDisplayPageId;
    }

    /**
     * Go to Display page {@code pageId} (-1: the last used one, else the nearest): the wall slides
     * there, a far one from a stand-off (W3), and the landing binds the display view to it. False
     * when the wall has no such Display page.
     */
    public boolean goToDisplayPage(int pageId, boolean animate) {
        WallPages pages = mKnownPages;
        if (pages == null) {
            if (!has(PageKind.DISPLAY)) return false;
            mTerminalPending = false;
            return mWall.goTo(PageKind.DISPLAY, animate);
        }
        int id = pageId;
        if (mWall.kindOf(id) == PageKind.DISPLAY) {
            mLastDisplayPageId = id;
        } else {
            WallPages.Page target = pages.displayTarget(currentPageId(), mLastDisplayPageId);
            id = target == null ? PaneWallLayout.NO_PAGE : target.id;
        }
        if (mWall.kindOf(id) != PageKind.DISPLAY) return false;
        return goToPage(id, animate);
    }

    /** The id of the page the wall is on; -1 while the wall's pages are not known. */
    public int currentPageId() {
        int id = mWall.currentPageId();
        return id < 0 ? -1 : id;
    }

    /**
     * The page kind of the cell {@code steps} cells from the current one (0: the current cell),
     * in cell order; null past either end of the wall. What the chrome dresses for during a
     * slide: the arriving cell's kind, live or still (ADR 0016 section 3).
     */
    @Nullable
    public PageKind kindAt(int steps) {
        int id = steps == 0 ? mWall.currentPageId() : mWall.neighbourId(steps);
        return id == PaneWallLayout.NO_PAGE ? null : mWall.kindOf(id);
    }

    /** The cells {@link #mCellKinds} was read from. */
    @Nullable private java.util.List<PaneWallLayout.Cell> mCellKindsOf;
    @NonNull private java.util.List<PageKind> mCellKinds = java.util.Collections.emptyList();

    /**
     * The page kinds of the wall's cells, one entry per cell in cell order: what the chrome
     * travel, the ground strip and the status lens read the cells around the current one from,
     * with {@link #currentCellIndex}. Read on every frame of a slide; it allocates only when the
     * cells change.
     */
    @NonNull
    public java.util.List<PageKind> cellKinds() {
        java.util.List<PaneWallLayout.Cell> cells = mWall.cells();
        if (cells != mCellKindsOf) {
            java.util.List<PageKind> kinds = new java.util.ArrayList<>(cells.size());
            for (PaneWallLayout.Cell cell : cells) kinds.add(cell.kind);
            mCellKinds = java.util.Collections.unmodifiableList(kinds);
            mCellKindsOf = cells;
        }
        return mCellKinds;
    }

    /** {@link #slideCellKinds}' list during a far jump; reused, so a frame allocates nothing. */
    @NonNull private final java.util.ArrayList<PageKind> mSlideKinds =
        new java.util.ArrayList<>();

    /**
     * The page kinds of the cells as the slide shows them, for the chrome's travel: {@link
     * #cellKinds}, but during a far jump (W3) the cell the wall left stands beside the arriving
     * one, so every cell on that side reads as the left page's kind. Without this a jump from
     * the Terminal to Home 1 over Home 2 read Home beside Home: a frame of Home with itself, so
     * the content's room and the chrome never travelled toward the arriving place.
     */
    @NonNull
    public java.util.List<PageKind> slideCellKinds() {
        java.util.List<PageKind> kinds = cellKinds();
        int left = mWall.jumpLeftCellIndex();
        int side = mWall.jumpSide();
        if (left < 0 || side == 0) return kinds;
        int at = currentCellIndex();
        PageKind leftKind = kinds.get(left);
        mSlideKinds.clear();
        for (int i = 0; i < kinds.size(); i++) {
            boolean onLeftSide = side > 0 ? i > at : i < at;
            mSlideKinds.add(onLeftSide ? leftKind : kinds.get(i));
        }
        return mSlideKinds;
    }

    /** The index of the current page's cell in {@link #cellKinds}. */
    public int currentCellIndex() {
        return Math.max(0, mWall.indexOfCell(mWall.currentPageId()));
    }

    /**
     * Go to page {@code pageId}'s cell. The slide shows the page as it stands, live or as its
     * still (picture, else card); the landing binds its kind's view to it ({@link
     * #onWallCellSettled}), so a slide caught and rested elsewhere leaves every binding as it was
     * (ADR 0016). A Home page of the run the wall is on is shown by the frame where it stands.
     */
    private boolean goToPage(int pageId, boolean animate) {
        PageKind kind = mWall.kindOf(pageId);
        if (kind == null) return false;
        mTerminalPending = false;
        if (kind == PageKind.HOME && mWall.currentPage() == PageKind.HOME
            && !mWall.isMoving() && sameRun(mWall.currentPageId(), pageId)) {
            boolean changed = pageId != mWall.currentPageId();
            bindHome(pageId);
            mWall.follow(pageId);
            // A page change inside the run lands as a slide does: the front is recorded and the
            // host's settle refreshes what follows the current page (the glyph row).
            if (changed) onWallCellSettled(pageId, PageKind.HOME);
            return true;
        }
        return mWall.goToCell(pageId, animate);
    }

    /** Bind the view of page {@code pageId}'s kind to it: the binding a landing makes. */
    private void bindCell(int pageId) {
        PageKind kind = mWall.kindOf(pageId);
        if (kind == null) return;
        if (kind == PageKind.HOME) {
            bindHome(pageId);
        } else if (kind == PageKind.DISPLAY) {
            if (pageId >= 0) bindDisplay(pageId);
            else mWall.bind(kind, pageId);
        } else {
            mWall.bind(kind, pageId);
        }
    }

    /**
     * Bind the Home frame to Home page {@code pageId}: to the run of Home cells that holds it,
     * turned to that page. The page the frame leaves keeps its picture first, taken while the
     * frame still shows it (W5: never one run's page while bound to another).
     */
    private void bindHome(int pageId) {
        if (mWall.kindOf(pageId) != PageKind.HOME) return;
        mWall.takePicture(PageKind.HOME, pageId);
        bindRunOf(pageId);
        int index = homeIndexOf(pageId);
        if (index >= 0 && innerIndex(PageKind.HOME) != index) {
            showInner(PageKind.HOME, index);
        }
        mWall.bind(PageKind.HOME, pageId);
    }

    /** Hand the host the run of Home cells that holds page {@code pageId}. */
    private void bindRunOf(int pageId) {
        if (pageId < 0 || mWall.kindOf(pageId) != PageKind.HOME) {
            bindRun(0, -1);
            return;
        }
        java.util.List<PaneWallLayout.Cell> cells = mWall.cells();
        int at = mWall.indexOfCell(pageId);
        int start = at;
        while (start > 0 && cells.get(start - 1).kind == PageKind.HOME) start--;
        int end = at;
        while (end + 1 < cells.size() && cells.get(end + 1).kind == PageKind.HOME) end++;
        int first = homeIndexOf(cells.get(start).pageId);
        int last = first + end - start;
        // Every Home page in one run: the default order's run.
        if (first == 0 && last == homeCount() - 1) bindRun(0, -1);
        else bindRun(first, last);
    }

    /** Whether two Home pages stand in one run of Home cells. */
    private boolean sameRun(int a, int b) {
        java.util.List<PaneWallLayout.Cell> cells = mWall.cells();
        int from = mWall.indexOfCell(a);
        int to = mWall.indexOfCell(b);
        if (from < 0 || to < 0) return false;
        for (int i = Math.min(from, to); i <= Math.max(from, to); i++) {
            if (cells.get(i).kind != PageKind.HOME) return false;
        }
        return true;
    }

    /** The global Home-page index of Home page {@code pageId}; -1 when it is no Home cell. */
    private int homeIndexOf(int pageId) {
        if (pageId < 0) return -1;
        int index = 0;
        for (PaneWallLayout.Cell cell : mWall.cells()) {
            if (cell.pageId == pageId) return cell.kind == PageKind.HOME ? index : -1;
            if (cell.kind == PageKind.HOME) index++;
        }
        return -1;
    }

    /** The Home page at global Home-page index {@code index}; NO_PAGE when the pages are not known. */
    private int homeIdAt(int index) {
        if (mKnownPages == null) return PaneWallLayout.NO_PAGE;
        int at = 0;
        for (PaneWallLayout.Cell cell : mWall.cells()) {
            if (cell.kind != PageKind.HOME) continue;
            if (at++ == index) return cell.pageId;
        }
        return PaneWallLayout.NO_PAGE;
    }

    private int homeCount() {
        int count = 0;
        for (PaneWallLayout.Cell cell : mWall.cells()) {
            if (cell.kind == PageKind.HOME) count++;
        }
        return count;
    }

    /**
     * The Home frame paged inside its run by the user's own swipe, the one change of Home page
     * the wall does not make itself: the wall stands on the cell of the Home page the frame now
     * shows. Told by the host as the swipe commits, before the turned page is drawn. Everywhere
     * else the wall's page id is the owner and the frame is turned to it ({@link #bindHome});
     * a read of the current page never moves the wall. Only while the frame is bound to the cell
     * the wall rests on: a wall carried to another Home cell (a home press, a far jump under
     * way) has not bound the frame yet, and the frame's old page must not pull the wall back.
     */
    public void onHomePageTurned() {
        if (mKnownPages == null || mWall.currentPage() != PageKind.HOME) return;
        if (mWall.isMoving()) return;
        if (mWall.boundPageId(PageKind.HOME) != mWall.currentPageId()) return;
        int id = homeIdAt(innerIndex(PageKind.HOME));
        if (id != PaneWallLayout.NO_PAGE && sameRun(mWall.currentPageId(), id)) mWall.follow(id);
    }

    private void bindRun(int first, int last) {
        if (first == mBoundFirst && last == mBoundLast) return;
        mBoundFirst = first;
        mBoundLast = last;
        mHome.bindRun(first, last);
    }

    /**
     * Go to global Home page {@code index}: its cell, the frame bound to it on the way; where the
     * pages are not known, the Home page turned to it.
     */
    private boolean goToHomePage(int index, boolean animate) {
        return goToHomePage(index, animate, false);
    }

    /**
     * @param bindFirst bind the Home frame to the page before the slide, so it slides in live
     *                  rather than as a still or an empty frame: the home press's own arrival.
     *                  The pages the frame leaves keep their picture (bindHome takes it).
     */
    private boolean goToHomePage(int index, boolean animate, boolean bindFirst) {
        int id = homeIdAt(index);
        if (id != PaneWallLayout.NO_PAGE) {
            return bindFirst ? goToHomeBound(id, animate) : goToPage(id, animate);
        }
        showInner(PageKind.HOME, index);
        return goTo(PageKind.HOME, animate);
    }

    /**
     * The swipe from Overview back to the first page starts (#356): the wall lands on its first
     * page at once (its first window, for a Workspace), whichever page Overview was opened from,
     * and stands it one width right, under Overview, for the finger to bring in. False when the
     * wall is moving or has no Overview before it.
     */
    public boolean beginOverviewReturn() {
        if (mWall.isMoving() || !mWall.areGesturesEnabled()) return false;
        goToFirstPageNow();
        return mWall.beginOverviewReturn();
    }

    /** Lands on the wall's first page with no slide: Home's first page, or a cell's first window. */
    private void goToFirstPageNow() {
        java.util.List<PaneWallLayout.Cell> cells = mWall.cells();
        if (cells.isEmpty()) return;
        PaneWallLayout.Cell first = cells.get(0);
        if (first.kind == PageKind.HOME) {
            goToHomePage(0, false, true);
            return;
        }
        goToPage(first.pageId, false);
        if (first.kind == PageKind.TERMINAL && innerIndex(PageKind.TERMINAL) != 0) {
            showInner(PageKind.TERMINAL, 0);
        }
    }

    /**
     * Go to global Home page {@code index} (clamped to the pages there are), the frame bound to
     * it before any slide: how the activity shows a Home page of its choosing (a float's page,
     * a position at once), so the wall, not the frame, decides which Home page shows.
     */
    public boolean goToHomePageAt(int index, boolean animate) {
        int count = Math.max(1, innerCount(PageKind.HOME));
        return goToHomePage(Math.max(0, Math.min(count - 1, index)), animate, true);
    }

    /**
     * Go to the first Home page; false when the wall already rests on it or holds no Home page.
     * The Back chain's tail (ADR 0030 section 3).
     */
    public boolean goToHomePageOne(boolean animate) {
        if (!mFacts.useAs().showsHomePage()) return false;
        int index = homeIndexOf(mWall.currentPageId());
        if (index < 0 && mWall.currentPage() == PageKind.HOME) {
            index = innerIndex(PageKind.HOME);
        }
        if (PaneWallPolicy.restsOnHomePageOne(mWall.currentPage(), mWall.isMoving(), index)) return false;
        if (mKnownPages != null && homeCount() == 0) return false;
        return goToHomePage(0, animate, true);
    }

    /**
     * Go to Home page {@code id} with the Home frame bound to it before the slide, so it slides
     * in live rather than as its still: the home press's and the Home key's arrival. The page
     * the frame leaves keeps its picture ({@link #bindHome} takes it).
     */
    private boolean goToHomeBound(int id, boolean animate) {
        if (mWall.currentPageId() != id) {
            mTerminalPending = false;
            bindHome(id);
        }
        return goToPage(id, animate);
    }

    /**
     * The seam out of Home for the widget grid: whether a swipe off the Home run's edge has a
     * cell to go to on that side, and the wall's plain drag to carry it there. The empty Terminal
     * page is such a cell like any other; its first shell starts when the wall lands on it.
     */
    @NonNull
    public com.termux.app.launcher.widget.WidgetPaneView.Seam homeSeam() {
        return new com.termux.app.launcher.widget.WidgetPaneView.Seam() {
            @Override public boolean canLeaveForward() {
                return mWall.currentPage() == PageKind.HOME && !mWall.isMoving()
                    && mWall.areGesturesEnabled()
                    && mWall.neighbourId(1) != PaneWallLayout.NO_PAGE;
            }
            // A Home run after the Terminal page leaves backward from its first page, and the
            // wall's first page leaves backward into Overview, which stands before it (#356).
            @Override public boolean canLeaveBackward() {
                return mWall.currentPage() == PageKind.HOME && !mWall.isMoving()
                    && mWall.areGesturesEnabled()
                    && (mWall.neighbourId(-1) != PaneWallLayout.NO_PAGE
                        || mWall.overviewBefore());
            }
            @Override public void begin() {
                mTerminalPending = false;
                mWall.beginPlainDrag();
            }
            @Override public void drag(float dxPx) { mWall.dragTo(dxPx); }
            @Override public void end(float velocityPxPerSec) { mWall.endDrag(velocityPxPerSec); }
            @Override public void cancel() { mWall.cancelDrag(); }
        };
    }

    /** The Home pages or windows the wall's view of {@code kind} holds; Home's come from its seam. */
    private int innerCount(@NonNull PageKind kind) {
        return kind == PageKind.HOME ? mHome.count() : mTerminal.count();
    }

    private int innerIndex(@NonNull PageKind kind) {
        return kind == PageKind.HOME ? mHome.index() : mTerminal.index();
    }

    private void showInner(@NonNull PageKind kind, int index) {
        if (kind == PageKind.HOME) mHome.show(index);
        else mTerminal.show(index);
    }

    /** The seam out to Home; null for none. */
    public void setHome(@Nullable WallHome home) {
        mHome = home == null ? WallHome.NONE : home;
    }

    /** Build the wall's Widgets page: the app-widget grid, dressed as a pane. */
    @Nullable
    public WidgetPaneFrame attachWidgetsPage(@NonNull LayoutInflater inflater) {
        if (mWidgetsPage != null) return mWidgetsPage;
        WidgetPaneFrame frame = (WidgetPaneFrame) inflater.inflate(
            com.termux.R.layout.view_widget_pane, mWall, false);
        mWall.addView(frame, 0);
        mWidgetsPage = frame;
        mWall.setPageView(PageKind.HOME, frame);
        applyStyle(mStyle);
        return frame;
    }

    @Nullable
    public WidgetPaneFrame widgetsPage() {
        return mWidgetsPage;
    }

    /** True once the widget grid has been moved onto the wall. */
    public boolean hasWidgetsPage() {
        return mWidgetsPage != null;
    }

    /**
     * Build the display's frame on the wall as the Display pages' one view (A3b): it stands on
     * the bound Display page ({@link WallDisplay#bind}). Inflated here rather than in the
     * layout file so an install that never runs a display never builds its surface.
     */
    @Nullable
    public com.termux.app.x11.X11PaneFrame attachDisplayWindow(@NonNull LayoutInflater inflater) {
        if (mDisplayWindow != null) return mDisplayWindow;
        com.termux.app.x11.X11PaneFrame frame = (com.termux.app.x11.X11PaneFrame) inflater.inflate(
            com.termux.R.layout.view_x11_pane, mWall, false);
        frame.setStatusRowPx(mStatusRowPx);
        mWall.addView(frame);
        mDisplayWindow = frame;
        mWall.setPageView(PageKind.DISPLAY, frame);
        applyStyle(mStyle);
        return frame;
    }

    /** The room the Assistant's card and the Display's window hold for the status row. */
    private int mStatusRowPx;

    /**
     * Holds {@code px} at the top of the Assistant's card and the Display's window for the status
     * row (PaneRowPolicy.pageRoomPx, the room the Terminal's top pane holds; 0 when the strip
     * stands), now and for either page built later, and stands the Assistant's card where the
     * Terminal's top pane stands while it does. Each compares first: a layout only on a change.
     */
    public void setStatusRowPx(int px) {
        int room = Math.max(0, px);
        mStatusRowPx = room;
        mWall.setStatusRowPx(room);
        // A frame lent to a tiling window holds no status row.
        if (mDisplayWindow != null && mDisplayWindow.getParent() == mWall) mDisplayWindow.setStatusRowPx(room);
        if (mAssistantView instanceof com.termux.app.terminal.PaneContentFrame)
            ((com.termux.app.terminal.PaneContentFrame) mAssistantView).setStatusRowPx(room);
    }

    /**
     * The frame the status row lies in on the wall's own pages: the Assistant's card, the
     * Display's window; null for any other kind and while the page has no view.
     */
    @Nullable
    public com.termux.app.terminal.PaneContentFrame statusRowFrame(@NonNull PageKind page) {
        if (page == PageKind.DISPLAY) return mDisplayWindow;
        if (page == PageKind.ASSISTANT
            && mAssistantView instanceof com.termux.app.terminal.PaneContentFrame)
            return (com.termux.app.terminal.PaneContentFrame) mAssistantView;
        return null;
    }

    @Nullable
    public com.termux.app.x11.X11PaneFrame displayWindow() {
        return mDisplayWindow;
    }

    /**
     * Take the display window's frame off the wall: the view goes, and the X view's native context
     * goes with it when the view leaves the window. The Display page stays on the wall, with
     * no view until a frame is built again.
     */
    public void detachDisplayWindow() {
        com.termux.app.x11.X11PaneFrame frame = mDisplayWindow;
        if (frame == null) return;
        mDisplayWindow = null;
        mWall.setPageView(PageKind.DISPLAY, null);
        refreshPages();
        // Lent to a tiling window (#419), it stands there rather than on the wall.
        if (frame.getParent() instanceof ViewGroup) ((ViewGroup) frame.getParent()).removeView(frame);
    }

    /**
     * The display's frame for the tiling desktop's Display window to hold (#419): it leaves the
     * wall, whose Display page keeps no view, and stays this controller's display window (dressed
     * and torn down here). Null when no frame is built.
     */
    @Nullable
    public com.termux.app.x11.X11PaneFrame lendDisplayWindow() {
        com.termux.app.x11.X11PaneFrame frame = mDisplayWindow;
        if (frame == null) return null;
        if (frame.getParent() == mWall) {
            mWall.setPageView(PageKind.DISPLAY, null);
            mWall.removeView(frame);
            frame.setStatusRowPx(0);
        }
        return frame;
    }

    /**
     * The wall's ground colour moved and nothing else did. Only the Display page's corner mask
     * reads it, so the other pages keep their dress.
     */
    public void applyWallGround(@Nullable PaneSurfaceStyle style) {
        mStyle = style;
        if (mDisplayWindow != null) mDisplayWindow.applyStyle(style);
    }

    /**
     * Dress every non-terminal page from the surface style. The terminal pages dress themselves
     * through {@code TerminalPaneController}; this is the same pass for the rest of the wall, and
     * it runs on the same triggers — a wallpaper change, a blur change, an editor slider tick.
     */
    public void applyStyle(@Nullable PaneSurfaceStyle style) {
        mStyle = style;
        // The grabber wears the rims' accent; a re-dress is where a scheme change arrives.
        mWall.refreshGrabberColor();
        if (mWidgetsPage != null) mWidgetsPage.applyStyle(style);
        if (mDisplayWindow != null) mDisplayWindow.applyStyle(style);
        if (mAssistantView instanceof com.termux.app.assistant.AssistantPageView) {
            ((com.termux.app.assistant.AssistantPageView) mAssistantView).applyStyle(style);
        }
        mLinux.applyStyle(style);
    }

    /** The kind of the page the wall is on. */
    @NonNull
    public PageKind currentPage() {
        return mWall.currentPage();
    }

    /** Whether a terminal window is the place the user is on, at rest. */
    public boolean isTerminalShowing() {
        return !mWall.isMoving() && terminalHasPixels();
    }

    /**
     * Whether any part of the terminal place can be on screen: it is the page at rest, or the wall
     * is moving and the terminal may be sliding into or out of the frame.
     *
     * <p>Not the negation of {@link #isTerminalShowing()}. That one asks whether the terminal is
     * the place the user is on; this one asks whether its pixels can be seen. A drag towards the
     * terminal shows it long before the wall commits to it, so anything that stops painting while
     * the terminal is away has to start again the moment the wall moves at all.
     */
    public boolean isTerminalOnScreen() {
        return mWall.isMoving() || terminalHasPixels();
    }

    private boolean mDisagreementLogged;

    /**
     * Whether the terminal page has pixels on screen, asked of the page view. The wall's record
     * of its current page is what it means to show; the pixels are what it shows. They part when a
     * slide is cut short, and that is logged once per episode so the path that cut it can be found.
     */
    private boolean terminalHasPixels() {
        boolean pixels = mWall.isTerminalHostOnScreen();
        boolean record = mWall.currentPage() == PageKind.TERMINAL;
        if (mWall.isMoving() || pixels == record) {
            mDisagreementLogged = false;
        } else if (!mDisagreementLogged) {
            mDisagreementLogged = true;
            android.util.Log.w("TermuxWall", "record and pixels disagree: " + describeState());
        }
        return pixels;
    }

    /** The wall's state in one line, for the log. */
    @NonNull
    public String describeState() {
        View terminal = mWall.pageView(PageKind.TERMINAL);
        return "page=" + mWall.currentPage() + " moving=" + mWall.isMoving()
            + " dragging=" + mWall.isDragging() + " offset=" + mWall.offsetPx()
            + " pageId=" + mWall.currentPageId() + " displayPage=" + mBoundDisplayPageId
            + " width=" + mWall.getWidth()
            + " terminalX=" + (terminal == null ? "none" : String.valueOf(terminal.getTranslationX()))
            + " terminalVisibility=" + (terminal == null ? "none" : String.valueOf(terminal.getVisibility()));
    }

    /**
     * The terminal page slides in from {@code fromPx} beside its place; see PaneWallLayout#nudgePage.
     *
     * @param fromWindow the window the arrival left, for the wallpaper's pan over every window
     *                   between ({@link #rowPosition}), or {@link WallParallax#ADJACENT} for the
     *                   one next door
     */
    public void nudgeTerminalPage(float fromPx, int fromWindow, long durationMs,
                                  @Nullable android.view.animation.Interpolator interpolator,
                                  @Nullable Runnable onEnd) {
        nudgeTerminalPage(fromPx, fromWindow, durationMs, interpolator, onEnd, null);
    }

    /** {@link #nudgeTerminalPage}, its clock held behind {@code clock}; see PaneWallLayout#nudgePage. */
    public void nudgeTerminalPage(float fromPx, int fromWindow, long durationMs,
                                  @Nullable android.view.animation.Interpolator interpolator,
                                  @Nullable Runnable onEnd, @Nullable FirstDrawGate clock) {
        mNudgeFromWindow = fromWindow;
        mWall.nudgePage(PageKind.TERMINAL, fromPx, durationMs, interpolator, onEnd, clock);
    }

    /** The window the terminal page's current nudge left; see {@link #nudgeTerminalPage}. */
    private int mNudgeFromWindow = WallParallax.ADJACENT;

    // ---- The row's live position, for the wallpaper's pan ----------------------------------

    /**
     * How many stops the wallpaper's row spans: every Home page and window, and each Display
     * page, in the order of the cells, so the pan reads the wall the user arranged.
     */
    public int rowCount() {
        if ((!mDefaultOrder || mHasDisplayPage) && mKnownPages != null) {
            return WallParallax.rowCount(mKnownPages, rowWindowCount());
        }
        return WallParallax.rowCount(rowHomeCount(), rowWindowCount());
    }

    /**
     * Where the wall stands on the wallpaper's row right now, in stops: the current cell's stop,
     * shifted by Home's own swipe or a window's nudge, and blended toward the neighbouring cell
     * while the wall slides. Read on every frame the wallpaper pans; it must not allocate.
     */
    public float rowPosition() {
        int homes = rowHomeCount();
        int windows = rowWindowCount();
        PageKind current = mWall.currentPage();
        int restAt = rowIndexOfCell(mWall.currentPageId(), homes, windows);
        int width = mWall.getWidth();
        float at = restAt;
        if (width > 0 && current == PageKind.HOME) {
            at = WallParallax.innerPosition(restAt,
                mHome.shiftFraction(), WallParallax.ADJACENT);
        } else if (width > 0 && current == PageKind.TERMINAL) {
            float nudgePx = mWall.nudgePx(PageKind.TERMINAL);
            if (nudgePx != 0f) {
                int from = mNudgeFromWindow == WallParallax.ADJACENT ? WallParallax.ADJACENT
                    : rowIndexOf(PageKind.TERMINAL, mNudgeFromWindow,
                        PaneWallLayout.NO_PAGE, homes, windows);
                at = WallParallax.innerPosition(restAt, nudgePx / width, from);
            }
        }
        float offsetPx = mWall.offsetPx();
        if (offsetPx == 0f || width <= 0) return at;
        // Positive: the pages sit to the right, so what is coming in is on the left.
        int steps = offsetPx > 0f ? -1 : 1;
        float neighbourAt;
        if (showsInnerPage(steps)) {
            // A border drag toward the next window inside the Terminal.
            neighbourAt = restAt + steps;
        } else {
            int neighbour = mWall.neighbourId(steps);
            // Past the row's end nothing comes in, and the picture holds.
            neighbourAt = neighbour == PaneWallLayout.NO_PAGE ? at
                : rowIndexOfCell(neighbour, homes, windows);
        }
        return WallParallax.slidePosition(at, neighbourAt, offsetPx, width);
    }

    /**
     * Whether a step of {@code steps} from the current cell stays inside its view (the next
     * window of the Terminal page) rather than crossing to the neighbouring cell. Never during a
     * slide from one cell to another.
     */
    public boolean showsInnerPage(int steps) {
        return !mWall.isCrossSlide() && hasInnerPage(mWall.currentPage(), steps);
    }

    /** Where page {@code pageId}'s cell stands on the wallpaper's row. */
    private int rowIndexOfCell(int pageId, int homes, int windows) {
        PageKind kind = mWall.kindOf(pageId);
        if (kind == null) return 0;
        int index = kind == PageKind.HOME && pageId >= 0 ? homeIndexOf(pageId)
            : innerIndex(kind);
        return rowIndexOf(kind, index, pageId, homes, windows);
    }

    private int rowIndexOf(@NonNull PageKind kind, int index, int pageId, int homes,
                           int windows) {
        WallPages pages = mKnownPages;
        if ((mDefaultOrder && !mHasDisplayPage) || pages == null) {
            return WallParallax.rowIndex(kind, index, homes, windows);
        }
        WallPages.Stop stop;
        if (kind == PageKind.DISPLAY) {
            int id = pageId >= 0 ? pageId : mDisplayPageId;
            if (id < 0) return 0;
            stop = WallPages.Stop.of(id);
        } else if (kind == PageKind.ASSISTANT) {
            WallPages.Page assistant = pages.assistant();
            int id = pageId >= 0 ? pageId : assistant == null ? -1 : assistant.id;
            if (id < 0) return 0;
            stop = WallPages.Stop.of(id);
        } else if (kind == PageKind.HOME) {
            java.util.List<WallPages.Page> homePages = pages.homePages();
            if (homePages.isEmpty()) return 0;
            stop = WallPages.Stop.of(homePages.get(
                Math.max(0, Math.min(homePages.size() - 1, index))).id);
        } else {
            WallPages.Page terminal = pages.terminal();
            if (terminal == null) return 0;
            stop = WallPages.Stop.window(terminal.id, Math.max(0, index));
        }
        return WallParallax.rowIndex(pages, stop, windows);
    }

    private int rowHomeCount() {
        return Math.max(1, innerCount(PageKind.HOME));
    }

    private int rowWindowCount() {
        if (!has(PageKind.TERMINAL)) return 0;
        return Math.max(1, innerCount(PageKind.TERMINAL));
    }

    /**
     * Go to a page by name ({@code wall.go}, the extra keys): {@code left}/{@code right} the
     * neighbouring cell whatever its kind; {@code home} and {@code widgets} alike the home
     * press's Home page while the home page is a Home page, else the nearest Home page; {@code
     * display} the last used Display page, else the nearest; {@code terminal}/{@code workspace} the Terminal page (ADR 0016
     * section 5). The target arrives as it stands, live or as its still, and its view is bound
     * when the wall lands; a far one arrives one width from a stand-off (W3). False when the wall
     * has no such page.
     */
    public boolean goTo(@Nullable String name) {
        WallPages pages = mKnownPages;
        if (pages == null) {
            PageKind page = PaneWallPolicy.parsePage(mWall.pages(), mWall.currentPage(), name);
            if (page == null) return false;
            mTerminalPending = false;
            if (page == PageKind.HOME && PaneWallPolicy.namesHome(name))
                showInner(PageKind.HOME, Math.max(0, mHome.pressPage()));
            return mWall.goTo(page, true);
        }
        if (name == null) return false;
        int current = currentPageId();
        WallPages.Page target;
        switch (name.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "left": target = pages.leftOf(current); break;
            case "right": target = pages.rightOf(current); break;
            case "home":
            case "widgets": {
                int id = homeKeyPage(pages, current);
                return id != PaneWallLayout.NO_PAGE && goToHomeBound(id, true);
            }
            case "display": target = pages.displayTarget(current, mLastDisplayPageId); break;
            case "workspace":
            case "terminal": target = pages.terminalTarget(); break;
            default: return false;
        }
        return target != null && goToPage(target.id, true);
    }

    /**
     * Where the Home key and {@code wall.widgets} go, one answer for both: while Start in is a
     * Home page, that page (the home press's, W9); while it is the Terminal or a Display page,
     * the first Home page (ADR 0016 section 5, amended 2026-11-02). {@link PaneWallLayout#NO_PAGE}
     * when there is none.
     */
    private int homeKeyPage(@NonNull WallPages pages, int current) {
        WallPages.Page home = pages.home();
        boolean homeIsHome = home != null && home.kind == PageKind.HOME;
        int id = homeIdAt(homeIsHome ? Math.max(0, mHome.pressPage()) : 0);
        if (id != PaneWallLayout.NO_PAGE) return id;
        WallPages.Page target = pages.homeTarget(current);
        return target == null ? PaneWallLayout.NO_PAGE : target.id;
    }

    /**
     * Go to a page of kind {@code page}: the current cell when it is of that kind, else the page
     * its view is bound to.
     */
    public boolean goTo(@NonNull PageKind page, boolean animate) {
        mTerminalPending = false;
        return mWall.goTo(page, animate);
    }

    /**
     * Go to the Terminal page: its cell, with or without a window. Empty, the landing starts its
     * first shell (ADR 0016 section 6); with the wall already resting there, it starts now, as
     * the Terminal entry does into an empty row. False when the wall has no Terminal page.
     */
    public boolean goToTerminal(boolean animate) {
        if (!has(PageKind.TERMINAL)) return false;
        mTerminalPending = false;
        if (mWall.currentPage() == PageKind.TERMINAL && !mWall.isMoving()) {
            startShellOnLanding();
            return true;
        }
        return goTo(PageKind.TERMINAL, animate);
    }

    /**
     * The wall rests on the Terminal page: with no window, its first shell starts, once per
     * landing. A cold start or a restored state waiting for its windows starts none.
     */
    private void startShellOnLanding() {
        if (mLandingShellStarted || mTerminalPending || mFacts.hasWindow()
            || !mTerminal.canStartShell()) {
            return;
        }
        mLandingShellStarted = true;
        if (!mTerminal.startShell()) {
            // No shell could be made: the page keeps its card, and the next landing asks again.
            mLandingShellStarted = false;
            return;
        }
        refreshPages();
    }

    /** Back to the Terminal page's cell, from wherever the wall stands. */
    public void returnToTerminal(boolean animate) {
        goTo(PageKind.TERMINAL, animate);
    }

    /**
     * The Android home press: the home page Edit pages set (a Display page, the Terminal page, or
     * the home press's Home page), wherever the wall is. A far one slides one width from a
     * stand-off and the pages between are never shown or started (W3, W9).
     */
    public void goHome(boolean animate) {
        // Edit pages set a Display page as home: the press lands there (A3b), and the landing
        // starts the display.
        int displayHome = DisplayPagePolicy.homeDisplayPage(mFacts.wallPages());
        if (displayHome >= 0 && goToDisplayPage(displayHome, animate)) return;
        // Edit pages set the Assistant page as home: the press and the cold start land on it.
        if (homeIsAssistantPage() && goToAssistantPage(animate)) return;
        if (!mFacts.useAs().showsHomePage()) {
            // Terminal only: the home press lands on the Terminal.
            goToTerminal(animate);
            return;
        }
        if (homeIsTerminalPage()) {
            // Edit pages set the Terminal page as home: the press lands on its window in front,
            // or starts its first shell, as Home's swipe into the Terminal does.
            goToTerminal(animate);
            return;
        }
        // The home press's page, wherever the wall is: a far one slides one width from a
        // stand-off, the pages between never shown (W3, W9).
        goToHomePage(Math.max(0, mHome.pressPage()), animate, true);
    }

    /**
     * Whether {@link #goHome} now turns the wall to a Home page from a page of another kind: the
     * press whose landing is held behind a frame with no pages
     * ({@link com.termux.app.chrome.HomeLandingGate}). False wherever the press lands on a
     * Display, the Assistant or the Terminal, and on a Home page already.
     */
    public boolean goHomeTurnsToHomePage() {
        if (DisplayPagePolicy.homeDisplayPage(mFacts.wallPages()) >= 0) return false;
        if (homeIsAssistantPage() || homeIsTerminalPage()) return false;
        if (!mFacts.useAs().showsHomePage()) return false;
        return mWall.currentPage() != PageKind.HOME;
    }

    private boolean homeIsAssistantPage() {
        WallPages pages = mFacts.wallPages();
        WallPages.Page home = pages == null ? null : pages.home();
        return home != null && home.kind == PageKind.ASSISTANT;
    }

    private boolean homeIsTerminalPage() {
        WallPages pages = mFacts.wallPages();
        WallPages.Page home = pages == null ? null : pages.home();
        return home != null && home.kind == PageKind.TERMINAL;
    }

    /**
     * Go to the Home page beside the Terminal page, the last before it, else the first after:
     * where the wall goes when the last window closes under it.
     */
    public void goToLastHomePage(boolean animate) {
        if (!mFacts.useAs().showsHomePage()) {
            goTo(PageKind.TERMINAL, animate);
            return;
        }
        if (mKnownPages != null) {
            java.util.List<PaneWallLayout.Cell> cells = mWall.cells();
            int terminal = mWall.indexOfCell(mWall.boundPageId(PageKind.TERMINAL));
            int id = PaneWallLayout.NO_PAGE;
            if (terminal > 0 && cells.get(terminal - 1).kind == PageKind.HOME) {
                id = cells.get(terminal - 1).pageId;
            } else if (terminal >= 0 && terminal + 1 < cells.size()
                && cells.get(terminal + 1).kind == PageKind.HOME) {
                id = cells.get(terminal + 1).pageId;
            } else {
                id = homeIdAt(homeCount() - 1);
            }
            if (id != PaneWallLayout.NO_PAGE) {
                goToPage(id, animate);
                return;
            }
        }
        showInner(PageKind.HOME,
            WallRow.lastHomePage(innerCount(PageKind.HOME)));
        goTo(PageKind.HOME, animate);
    }

    /**
     * Land where Start in says on a cold start, without a slide: the Terminal page, or the home
     * press's page.
     */
    public void landOnColdStart(@Nullable String startIn) {
        if (WallRow.coldStart(mFacts.useAs().startIn(startIn)) == PageKind.TERMINAL) {
            landInTerminal();
        } else {
            goHome(false);
        }
    }

    private void landInTerminal() {
        // A cold start or a restored state is not a landing: with no window yet the wall waits on
        // the Terminal page's card for its windows, and starts no shell of its own.
        if (!mFacts.hasWindow()) mTerminalPending = true;
        if (has(PageKind.TERMINAL)) mWall.goTo(PageKind.TERMINAL, false);
    }

    /** The page in front, as a row position. */
    @NonNull
    public WallRow.Position currentPosition() {
        PageKind page = mWall.currentPage();
        return new WallRow.Position(page, innerIndex(page));
    }

    /** Hold the wall still while another surface owns the gesture. */
    public void setGesturesEnabled(boolean enabled) {
        mWall.setGesturesEnabled(enabled);
    }

    // ---- Saved state -----------------------------------------------------------------------

    /**
     * Activity recreation (rotation, theme) keeps the row position: the half the wall rested in
     * and the page's index within it, never a page name. Process death does not: a cold start
     * follows Start in ({@link #landOnColdStart}).
     */
    public void onSaveInstanceState(@NonNull Bundle state) {
        WallRow.Position position = currentPosition();
        state.putString(ARG_HALF, WallRow.halfOf(position.kind));
        state.putInt(ARG_INDEX, WallRow.savedIndex(position));
    }

    /**
     * Back to the saved row position. A half this version does not know comes back to Home page
     * 1; a state with no half at all changes nothing.
     *
     * @return whether the state named a position, so the caller does not land a cold start too
     */
    public boolean restoreInstanceState(@Nullable Bundle state) {
        if (state == null || !state.containsKey(ARG_HALF)) return false;
        String half = state.getString(ARG_HALF);
        int index = state.getInt(ARG_INDEX, 0);
        // The windows come back with the terminal's own saved state, which may not be there yet:
        // a count of 0 means "not known yet", and the Terminal is waited for.
        int windows = has(PageKind.TERMINAL) ? innerCount(PageKind.TERMINAL) : 0;
        WallRow.Position position = WallRow.restore(half, index,
            innerCount(PageKind.HOME), windows);
        if (WallRow.HALF_TERMINAL.equals(half) && windows <= 0) {
            landInTerminal();
            return true;
        }
        if (position.kind == PageKind.HOME) {
            // wall_index is the global Home-page index: the run that holds it is bound first.
            goToHomePage(position.index, false);
        } else {
            landInTerminal();
        }
        return true;
    }

    // ---- PaneWallLayout.Listener -----------------------------------------------------------

    @Override
    public void onWallPageChanged(@NonNull PageKind page) {
        mLanding.committed(page);
    }

    @Override
    public void onWallCellChanged(int pageId, @NonNull PageKind page) {
        if (mPages != null) mPages.recordCommit(pageId, page);
        mLanding.committed(page);
        if (mPages != null) mPages.committed(pageId, page);
    }

    @Override
    public void onWallPageSettled(@NonNull PageKind page) {
        onWallCellSettled(mWall.currentPageId(), page);
    }

    /**
     * The wall came to rest on page {@code pageId}'s cell: the landing binds that page's kind's
     * view to it, and the still there gives way (ADR 0016). The chrome is not touched here: it
     * took the page kind's dress when the slide committed (ADR 0012). A landing on the empty
     * Terminal page starts its first shell, by whatever route it came (section 6); a far jump
     * that lands elsewhere starts none (W3).
     */
    @Override
    public void onWallCellSettled(int pageId, @NonNull PageKind page) {
        if (page != PageKind.TERMINAL) mLandingShellStarted = false;
        if (page == PageKind.DISPLAY && pageId >= 0) mLastDisplayPageId = pageId;
        bindCell(pageId);
        // Recorded before the host's settle, which applies the typing claim and reads the front.
        if (mPages != null) mPages.landed(pageId, page);
        mLanding.landed(page);
        if (page == PageKind.TERMINAL) startShellOnLanding();
        // Every landing: the bound Linux window keeps its surface only near the front.
        syncLinux();
        if (page == PageKind.DISPLAY && mBoundDisplayPageId >= 0) {
            mDisplay.landed(mBoundDisplayPageId);
        }
    }

    // The windows page inside the Terminal's view by the border drag; Home's pages page inside
    // the widget grid by its own swipe, so the wall never steps inside Home.
    @Override
    public boolean hasInnerPage(@NonNull PageKind page, int steps) {
        return page == PageKind.TERMINAL && WallRow.hasInnerStep(innerIndex(page),
            innerCount(page), steps);
    }

    @Override
    public void onInnerStepBegin(@NonNull PageKind page, int steps) {
        if (page == PageKind.TERMINAL) mTerminal.stepBegin(steps);
    }

    @Override
    public void onInnerStep(@NonNull PageKind page, int steps, float fromPx) {
        if (page == PageKind.TERMINAL) mTerminal.step(steps, fromPx);
        else mTerminal.dropDeparture();
    }

    /**
     * A drag is taking the wall from the current cell: the views that can arrive beside the seam
     * are put on the page they will show while still off screen (the window next to Home, a
     * kind-only Home cell's last page).
     */
    @Override
    public void onWallDragBegin(@NonNull PageKind page) {
        mTerminalPending = false;
        // Off the first window a kind-only Home cell, which holds every Home page, comes in on
        // its last page, the one beside the seam.
        int left = mWall.neighbourId(-1);
        if (page == PageKind.TERMINAL && left < 0 && left != PaneWallLayout.NO_PAGE
            && mWall.kindOf(left) == PageKind.HOME
            && innerIndex(PageKind.TERMINAL) == 0) {
            int last = WallRow.lastHomePage(innerCount(PageKind.HOME));
            if (innerIndex(PageKind.HOME) != last) {
                showInner(PageKind.HOME, last);
            }
        }
        // Leaving Home, the Terminal page's window beside the seam waits in front: the first
        // window when the Terminal page stands on Home's right, the last on its left.
        if (page != PageKind.HOME) return;
        int windows = innerCount(PageKind.TERMINAL);
        if (windows <= 0 || !has(PageKind.TERMINAL)) return;
        boolean right = mWall.indexOfCell(mWall.boundPageId(PageKind.TERMINAL))
            > mWall.indexOfCell(mWall.currentPageId());
        showInner(PageKind.TERMINAL, right ? 0 : windows - 1);
    }

    /**
     * A drag now heads {@code steps} cells away. Nothing is bound here: the cell it heads for
     * shows its page as it stands, live or as its still, and the landing binds (ADR 0016; ADR
     * 0015 asks nothing live under the finger), so a drag that springs back changes no binding.
     */
    @Override
    public void onWallDragHeading(@NonNull PageKind page, int steps) {
    }

    @Override
    public boolean isBorderPagingEnabled(@NonNull PageKind page) {
        // A Home page has no border; it pages by a plain swipe anywhere.
        return page != PageKind.HOME;
    }

    /**
     * A terminal window's text pages the row by a sideways swipe, as its border does, and so does
     * the Assistant page's chat, as a Home page's grid does, and so does a Display page with no
     * display running on it. Not a running display's: what it shows takes every touch, and only
     * its border pages.
     */
    @Override
    public boolean isBodySwipePaging(@NonNull PageKind page, float rawX, float rawY) {
        // Each side asked only for its own page, so a terminal is never asked about a touch
        // on the Assistant page.
        boolean terminal = page == PageKind.TERMINAL && mFacts.bodySwipePagesWall(rawX, rawY);
        boolean assistant = page == PageKind.ASSISTANT
            && mAssistantView instanceof com.termux.app.assistant.AssistantPageView
            && ((com.termux.app.assistant.AssistantPageView) mAssistantView)
                .bodySwipePagesWall(rawX, rawY);
        return PaneWallPolicy.bodySwipePages(page, terminal, assistant, isDisplayRunning());
    }

    @Override
    public boolean isDisplayRunning() {
        return mDisplayWindow != null && mDisplayWindow.isRunning();
    }

    /** The display started or stopped: the Display page's borders follow ({@link #isDisplayRunning}). */
    public void onDisplayRunningChanged() {
        mWall.refreshBorderReach();
    }

    /**
     * A terminal window's last row pages the row by a clearly sideways swipe from the seam band
     * ({@link SeamSwipe}), where the terminal gives the swipe away, whatever the body swipe's
     * setting. Not the display window's picture, which takes every touch.
     */
    @Override
    public boolean isSeamSwipePaging(@NonNull PageKind page, float rawX, float rawY) {
        return page == PageKind.TERMINAL && mFacts.seamSwipePagesWall(rawX, rawY);
    }

    @Override
    public void onWallOffsetChanged(float offsetPx) {
        if (mPages != null) {
            int width = mWall.getWidth();
            mPages.travelled(width > 0 ? Math.abs(offsetPx) / width : 0f);
        }
        reaimPages();
        syncOutlineAlpha(offsetPx);
        mPose.setOffsetPx(offsetPx);
        tellPoseReaders(WallPose.OFFSET);
    }

    /**
     * The pages' outlines fade out over the first stretch of a slide and back in over the last,
     * so one edge shows while two pages share the screen. Written only when the number moves,
     * which is a few frames at either end; a page pressed into a line's outer edge keeps its
     * outline, since it is alone on screen.
     */
    private void syncOutlineAlpha(float offsetPx) {
        float alpha = PaneWallPolicy.blendsPlaces(mWall.pages(), mWall.currentPage(), offsetPx)
            ? PaneWallPolicy.outlineAlpha(offsetPx, mWall.getWidth()) : 1f;
        if (alpha != mOutlineAlpha) {
            mOutlineAlpha = alpha;
            if (mWidgetsPage != null) mWidgetsPage.setOutlineTravelAlpha(alpha);
            if (mDisplayWindow != null) mDisplayWindow.setOutlineTravelAlpha(alpha);
            if (mAssistantView instanceof com.termux.app.assistant.AssistantPageView) {
                ((com.termux.app.assistant.AssistantPageView) mAssistantView).setOutlineTravelAlpha(alpha);
            }
            if (mLinuxView instanceof com.termux.app.wayland.LinuxWindowPageView) {
                ((com.termux.app.wayland.LinuxWindowPageView) mLinuxView).setOutlineTravelAlpha(alpha);
            }
        }
        // The terminal's rims are drawn in its page: while the transition fades that page they
        // go with its fade, not ahead of it (PaneWallPolicy#terminalRimAlpha).
        float rims = PaneWallPolicy.terminalRimAlpha(alpha, mWall.terminalPageAlpha());
        if (rims == mTerminalRimAlpha) return;
        mTerminalRimAlpha = rims;
        mPose.setTerminalRimAlpha(rims);
        tellPoseReaders(WallPose.RIMS);
    }

    @Override
    public void onTerminalOffScreenChanged(boolean offScreen) {
        mPose.setTerminalOffScreen(offScreen);
        tellPoseReaders(WallPose.OFF_SCREEN);
    }

    @Override
    public boolean isBorderKeyboardSwipeEnabled() {
        return mKeyboardSwipe.isEnabled();
    }

    @Override
    public void onBorderKeyboardSwipe(boolean open) {
        mKeyboardSwipe.onSwipe(open);
    }

    @Override
    public int onKeyboardRevealBegin(boolean opening) {
        return mKeyboardSwipe.onRevealBegin(opening);
    }

    @Override
    public void onKeyboardRevealProgress(float reveal) {
        mKeyboardSwipe.onRevealProgress(reveal);
    }

    @Override
    public void onKeyboardRevealEnd(boolean open) {
        mKeyboardSwipe.onRevealEnd(open);
    }

    @Override
    public boolean isBorderStatusSwipeEnabled() {
        return mStatusFold.isEnabled();
    }

    @Override
    public void onBorderStatusSwipe(boolean expand) {
        mStatusFold.onSwipe(expand);
    }

    @Override
    public int onStatusFoldBegin(boolean expanding) {
        return mStatusFold.onFoldBegin(expanding);
    }

    @Override
    public void onStatusFoldProgress(float towardOpenPx) {
        mStatusFold.onFoldProgress(towardOpenPx);
    }

    @Override
    public void onStatusFoldEnd(boolean expanded, float towardOpenVelocityPxPerSec) {
        mStatusFold.onFoldEnd(expanded, towardOpenVelocityPxPerSec);
    }

    @Override
    public void onPageSinkChanged(@NonNull PageKind page, float scale) {
        reaimPages();
        if (page != PageKind.TERMINAL) return;
        mPose.setTerminalSinkScale(scale);
        tellPoseReaders(WallPose.SINK);
    }

    @Override
    public void onPagePoseChanged() {
        reaimPages();
    }

    /**
     * The pages' glass is aimed at the wallpaper in screen space, past the transforms the wall
     * puts on its pages — the slide, the sink, the plank's tilt — and a transform never redraws
     * a child: each page re-aims its slab for this frame. Only on the frames the wall moves.
     */
    private void reaimPages() {
        if (mWidgetsPage != null) mWidgetsPage.onWallMoved();
    }

    @Override
    public boolean isPlankTiltEnabled(@NonNull PageKind page) {
        // Every page, the display window too: its picture is a SurfaceView a rotation leaves flat,
        // so the wall tips it only while a still copy stands in for the surface (SurfacePage), and
        // slides it flat until then.
        return mFacts.isPlankTiltEnabled();
    }
}
