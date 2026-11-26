package com.termux.app.statusbar;

import android.content.Context;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;
import com.termux.app.page.PageKind;
import com.termux.app.place.EdgeStackPolicy;
import com.termux.app.place.Element;
import com.termux.app.place.PlaceLayout;
import com.termux.app.terminal.PaneContentFrame;
import com.termux.app.wall.PageRef;
import com.termux.app.wall.PaneWallLayout;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

/**
 * Presents the one status host as the thin row or as the strip, and places and sizes the row:
 * Status bar &rsaquo; Place read once per arrangement pass, the presentation per page and wall
 * offset (compared, so it changes once per change), the row's rect over the top pane of the page
 * it belongs to, its marks and readings sizes and its ground. Moved
 * out of the activity; the activity implements {@link Host} and calls in.
 */
public final class StatusRowController {

    /** What the row's controller reads of the activity and writes back to it. */
    public interface Host {
        @Nullable TermuxAppSharedPreferences preferences();
        @NonNull Context context();
        @Nullable View findView(int id);
        /** The page the wall last committed to. */
        @NonNull PageKind wallPage();
        @Nullable PaneWallLayout wall();
        /** The far end of the crossing in flight, null when none. */
        @Nullable PageRef crossingOtherEnd();
        /**
         * The frame the row lies in on a page of {@code page}: the Terminal window's top-left
         * pane, the Assistant's card, the Display's window, the Linux window's page; null on
         * Home and wherever the page has no frame laid out.
         */
        @Nullable PaneContentFrame pageFrame(@NonNull PageKind page);
        /**
         * The frame at the row's other end on a page of {@code page}: the Terminal window's
         * top-right pane when columns stand along its top, the {@link #pageFrame} itself
         * everywhere else.
         */
        @Nullable default PaneContentFrame pageFrameEnd(@NonNull PageKind page) {
            return pageFrame(page);
        }
        /**
         * The room every page's frame holds for the row at its top (PaneRowPolicy.pageRoomPx; 0
         * when the strip stands): the Terminal's top pane, the Assistant's card, the Display's
         * window and the Linux window's page.
         */
        void setPaneStatusRowPx(int px);
        void refreshTerminalWindowBar();
        /** Whether the bar marks the pages with the Marks line (Status bar card › Page bar). */
        boolean marksLookShown();
        /**
         * Whether the Home page {@code home} shows the status bar (StatusPresentation.homeShowsBar
         * on its layout); {@link PageRef#HOME} names the Home page in front.
         */
        boolean homeShowsBar(@NonNull PageRef home);
    }

    @NonNull private final Host mHost;

    public StatusRowController(@NonNull Host host) {
        mHost = host;
    }

    @SuppressWarnings("unchecked")
    private <T extends View> T find(int id) {
        return (T) mHost.findView(id);
    }

    /** Whether the status bar stands as the thin row in the Terminal's top pane (PaneRowPolicy). */
    private boolean mPaneRowStands;
    /** Status bar › Place as the last arrangement pass read it; null before the first. */
    @Nullable private StatusPlace mPaneRowPlace;
    /** How the status host presents right now (syncStatusPresentation). */
    @NonNull private StatusPresentation mStatusPresentation =
        StatusPresentation.STRIP;

    /** Status bar › Place, as stored; the strip before there is a store. */
    @NonNull
    private StatusPlace statusPlace() {
        TermuxAppSharedPreferences preferences = mHost.preferences();
        return preferences == null ? StatusPlace.DEFAULT
            : StatusPlace.fromValue(preferences.getStatusPlace());
    }

    /** The page whose pane the row lies over (StatusPresentation.rowPage). */
    @NonNull private PageKind mRowPage = PageKind.TERMINAL;

    /**
     * Whether a Home page in view shows the bar, as last read for {@link #mHomeBarCommitted},
     * {@link #mHomeBarOther} and {@link #mHomeBarWallPage}: read again only when one of those
     * changes or an arrangement pass ran, never per frame of a slide.
     */
    private boolean mHomeBarShows;
    private boolean mHomeBarKnown;
    @Nullable private PageKind mHomeBarCommitted;
    @Nullable private PageRef mHomeBarOther;
    @Nullable private PageKind mHomeBarWallPage;

    /**
     * Whether a Home page that can be on screen shows the status bar: the committed Home page in
     * front, the crossing's other end when it is a Home page, and the Home page in front when the
     * wall moves onto one with no crossing to name it. Cached per those inputs.
     */
    private boolean homeShowsBar(@NonNull PageKind committed, @Nullable PageRef other,
                                 @Nullable PageKind wallPage) {
        if (mHomeBarKnown && committed == mHomeBarCommitted && wallPage == mHomeBarWallPage
            && java.util.Objects.equals(other, mHomeBarOther)) return mHomeBarShows;
        boolean otherHome = other != null && other.isHome();
        boolean shows = false;
        if (committed == PageKind.HOME || (wallPage == PageKind.HOME && !otherHome))
            shows = mHost.homeShowsBar(PageRef.HOME);
        if (!shows && otherHome) shows = mHost.homeShowsBar(other);
        mHomeBarShows = shows;
        mHomeBarKnown = true;
        mHomeBarCommitted = committed;
        mHomeBarOther = other;
        mHomeBarWallPage = wallPage;
        return shows;
    }

    /**
     * Presents the one status host as the thin row or as the strip (StatusPresentation): the row
     * at rest on a page with a pane, the strip on a Home that shows the bar and through any slide
     * that can show it; the row on a Home that shows none and through its slides, over the pane
     * it rides in with. Called on every arrangement pass and every wall offset, it compares
     * first: under the strip it is two field reads, and a swap costs one host relayout. No page
     * moves on a swap, since each page's top is its own (PageTopInsets).
     */
    public void syncStatusPresentation() {
        if (!mPaneRowStands && !mStatusPresentation.isRow()) return;
        com.termux.app.wall.PaneWallLayout wall =
            mHost.wall();
        PageKind committed = mHost.wallPage();
        PageKind wallPage = wall == null ? null : wall.currentPage();
        boolean atRest = wall == null || com.termux.app.place.PageTopInsets.wallAtRestOn(
            committed, wall.isMoving(), wall.offsetPx(), wallPage);
        // A wall that leaves its rest ends a touch on the marks: it is gone before the page moves.
        if (!atRest) endMarksTouch();
        PageRef other = mHost.crossingOtherEnd();
        boolean crossingBegun = other != null && !mDiagCrossing;
        mDiagCrossing = other != null;
        boolean homeInView = committed == PageKind.HOME
            || wallPage == PageKind.HOME
            || (other != null && other.isHome());
        boolean homeShowsBar = homeInView && homeShowsBar(committed, other, wallPage);
        StatusPresentation presentation =
            StatusPresentation.of(mPaneRowStands, committed, atRest, homeInView, homeShowsBar);
        PageKind rowPage = StatusPresentation.rowPage(committed, other == null ? null : other.kind);
        boolean rowPageMoved = rowPage != mRowPage;
        mRowPage = rowPage;
        if (presentation == mStatusPresentation) {
            // A slide from Home toward another page than the one the row stood over: placed once.
            if (rowPageMoved && presentation.isRow()) syncStatusRowPlace();
            if (crossingBegun) logRow("crossing-begin");
            return;
        }
        if (com.termux.app.chrome.MotionLog.enabled) {
            com.termux.app.chrome.MotionLog.log("status-presentation", "value=" + presentation
                + " rowStands=" + mPaneRowStands + " committed=" + committed
                + " atRest=" + atRest + " homeInView=" + homeInView
                + " homeShowsBar=" + homeShowsBar + " rowPage=" + rowPage);
        }
        mStatusPresentation = presentation;
        endMarksTouch();
        boolean row = presentation.isRow();
        View hostView = find(R.id.terminal_window_bar_host);
        if (hostView instanceof StatusBarSwipeLayout) {
            StatusBarSwipeLayout host =
                (StatusBarSwipeLayout) hostView;
            // The row has no expansion; the strip's own rule comes back with the strip.
            host.setExpansionAllowed(!row && StatusBarGesturePolicy
                .expansionAllowed(host.edge()));
            host.setRowPresented(row);
        }
        applyRowSizes(row);
        // The sessions indicator stands in the strip only, as syncPlaceBar decides it too.
        View sessions = find(R.id.terminal_sessions_indicator);
        if (sessions != null) {
            int shown = sessionsIndicatorVisibility(true, mHost.marksLookShown());
            if (sessions.getVisibility() != shown) sessions.setVisibility(shown);
        }
        syncStatusRowPlace();
        // The chips stand in the strip only (chipsKeptAway).
        mHost.refreshTerminalWindowBar();
        if (crossingBegun) logRow("crossing-begin");
    }

    /** Whether the last presentation sync saw a crossing in flight (diag: its begin is logged). */
    private boolean mDiagCrossing;

    /**
     * Diag: one motion-log line naming the status row at a crossing's begin or landing: the
     * presentation, the host's frame and translation, and the row's frame in the host. Not per frame.
     */
    public void logRow(@NonNull String when) {
        if (!com.termux.app.chrome.MotionLog.enabled) return;
        View host = find(R.id.terminal_window_bar_host);
        String hostKv = host == null ? "none" : host.getLeft() + "," + host.getTop() + ","
            + host.getRight() + "," + host.getBottom() + " tx=" + host.getTranslationX()
            + " vis=" + host.getVisibility();
        String rowKv = host instanceof StatusBarSwipeLayout
            ? ((StatusBarSwipeLayout) host).rowRect().toShortString()
                + " presented=" + ((StatusBarSwipeLayout) host).isRowPresented() : "none";
        PaneContentFrame frame = mHost.pageFrame(mRowPage);
        String frameKv = frame == null ? "none" : frame.getClass().getSimpleName() + " "
            + frame.getWidth() + "x" + frame.getHeight();
        com.termux.app.chrome.MotionLog.log("status-row", "when=" + when
            + " presentation=" + mStatusPresentation + " rowPage=" + mRowPage
            + " frame=" + frameKv
            + " committed=" + mHost.wallPage() + " host=" + hostKv + " row=" + rowKv);
    }

    /**
     * The row's marks and readings at the size the row has: 12dp marks and readings laid out at
     * 9sp in the thin row, which never changes size; the strip takes neither cap nor size.
     */
    private void applyRowSizes(boolean row) {
        float density = mHost.context().getResources().getDisplayMetrics().density;
        MarksLineView marks = find(R.id.terminal_status_marks_row);
        if (marks != null) {
            marks.setMarkCapPx(row ? PaneRowPolicy.MARK_DP * density : 0f);
        }
        View readings = find(R.id.terminal_status_widgets);
        if (readings != null) setReadingSizes(readings, row ? PaneRowPolicy.READING_SP : 0f);
    }

    /** Every reading under {@code view} at {@code sp}, or at its tier's with 0. */
    private static void setReadingSizes(@NonNull View view, float sp) {
        if (view instanceof StatusBarWidgetView) {
            ((StatusBarWidgetView) view).setValueSizeOverrideSp(sp);
        } else if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++)
                setReadingSizes(group.getChildAt(i), sp);
        }
    }

    /**
     * Ends a touch under way on the Marks line, row or column, with its zoom and title: the page
     * changed, the wall moved, the row's presentation changed, the activity paused. Safe at any time.
     */
    public void endMarksTouch() {
        View row = find(R.id.terminal_status_marks_row);
        View column = find(R.id.terminal_status_marks_column);
        if (row instanceof MarksLineView) ((MarksLineView) row).cancelTouch("presentation-change");
        if (column instanceof MarksLineView) ((MarksLineView) column).cancelTouch("presentation-change");
    }

    private final int[] mStatusRowOrigin = new int[2];
    private final int[] mStatusRowHostOrigin = new int[2];
    private final int[] mStatusRowScratch = new int[2];

    private final android.graphics.Rect mStatusRowRect = new android.graphics.Rect();
    private final android.graphics.Rect mStatusBandRect = new android.graphics.Rect();
    /** The style the panes' glass last wore, which the band wears too ({@link #dressBand}). */
    @Nullable private com.termux.app.terminal.PaneSurfaceStyle mBandStyle;

    /**
     * Feeds the band the panes' glass: called wherever the panes are re-dressed, with the same
     * style, so the band follows them and holds no pixels of its own.
     */
    public void dressBand(@Nullable com.termux.app.terminal.PaneSurfaceStyle style) {
        mBandStyle = style;
        View hostView = find(R.id.terminal_window_bar_host);
        if (hostView instanceof StatusBarSwipeLayout)
            ((StatusBarSwipeLayout) hostView).dressBand(style);
    }

    /**
     * Lays the row in the top of the frame of the page it belongs to ({@link Host#pageFrame}: a
     * Terminal window's top-left pane, the Assistant's card, the Display's window, a Linux
     * window), read off where the frame and the host are laid out (not where a slide draws them):
     * the frame's top {@link PaneRowPolicy#rowHeightPx}, which it holds for the row
     * ({@link PaneRowPolicy#pageRoomPx}), in by the insets its corner arc asks for
     * ({@link PaneRowPolicy#rowRect}). The same rule on every page kind; only the frame differs.
     * Where columns stand along a Terminal window's top the row runs on to the top-right pane
     * ({@link Host#pageFrameEnd}, {@link PaneRowPolicy#spanRowRect}); over three or more it stands
     * above their cards, which start below it. Runs after every window layout and on a swap; the host relays out only when the rect changed.
     */
    public void syncStatusRowPlace() {
        View hostView = find(R.id.terminal_window_bar_host);
        if (!mStatusPresentation.isRow()) {
            // The strip is back: no band glass stays behind.
            if (hostView instanceof StatusBarSwipeLayout
                && !((StatusBarSwipeLayout) hostView).bandRect().isEmpty()) {
                ((StatusBarSwipeLayout) hostView).setBandRect(0, 0, 0, 0);
                ((StatusBarSwipeLayout) hostView).dressBand(mBandStyle);
            }
            return;
        }
        if (!(hostView instanceof StatusBarSwipeLayout)
            || hostView.getWidth() <= 0) return;
        StatusBarSwipeLayout host =
            (StatusBarSwipeLayout) hostView;
        // The page the row belongs to: on Home, the page it rides in with (or waits beside).
        PaneContentFrame frame = mHost.pageFrame(mRowPage);
        if (frame == null || frame.getWidth() <= 0) {
            boolean wasPlaced = !host.rowRect().isEmpty();
            mStatusRowRect.setEmpty();
            host.setRowRect(0, 0, 0, 0);
            host.setBandRect(0, 0, 0, 0);
            host.dressBand(mBandStyle);
            if (wasPlaced) logPlace(null, 0f);
            return;
        }
        // Two or more columns along the top: the row runs on to the top-right pane's right edge.
        PaneContentFrame end = mHost.pageFrameEnd(mRowPage);
        if (end == null || end.getWidth() <= 0) end = frame;
        float radius = rowEndRadius(frame);
        float density = mHost.context().getResources().getDisplayMetrics().density;
        com.termux.app.chrome.GlassAnchor.layoutOriginOnScreen(host, mStatusRowHostOrigin,
            mStatusRowScratch);
        int right;
        if (end == frame) {
            right = frame.getWidth();
        } else {
            com.termux.app.chrome.GlassAnchor.layoutOriginOnScreen(end, mStatusRowOrigin,
                mStatusRowScratch);
            right = mStatusRowOrigin[0] + end.getWidth();
        }
        com.termux.app.chrome.GlassAnchor.layoutOriginOnScreen(frame, mStatusRowOrigin,
            mStatusRowScratch);
        if (end == frame) right += mStatusRowOrigin[0];
        boolean rtl = host.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
        int left = mStatusRowOrigin[0] - mStatusRowHostOrigin[0];
        PaneRowPolicy.spanRowRect(left, mStatusRowOrigin[1] - mStatusRowHostOrigin[1],
            right - mStatusRowHostOrigin[0], radius, end == frame ? radius : rowEndRadius(end),
            rtl, density, mStatusRowRect);
        android.graphics.Rect was = host.rowRect();
        boolean moved = !was.equals(mStatusRowRect);
        host.setRowRect(mStatusRowRect.left, mStatusRowRect.top, mStatusRowRect.right,
            mStatusRowRect.bottom);
        // Two or more columns: the panes' cards start below the row, which stands as a band of
        // the panes' glass across their full width.
        if (frame.cardTopPx() > 0) {
            PaneRowPolicy.bandRect(left, mStatusRowOrigin[1] - mStatusRowHostOrigin[1],
                right - mStatusRowHostOrigin[0], density, mStatusBandRect);
        } else {
            mStatusBandRect.setEmpty();
        }
        host.setBandRect(mStatusBandRect.left, mStatusBandRect.top, mStatusBandRect.right,
            mStatusBandRect.bottom);
        host.dressBand(mBandStyle);
        if (moved) logPlace(frame, radius);
    }

    /**
     * The corner radius the row's end over {@code frame} clears: the pane's own as it is laid out,
     * none where its card starts below the row (PaneRowPolicy.endRadiusPx).
     */
    private static float rowEndRadius(@NonNull PaneContentFrame frame) {
        return PaneRowPolicy.endRadiusPx(com.termux.app.terminal.PaneShape.radiusForBounds(
            frame.requestedRadiusPx(), frame.getWidth(), frame.getHeight()), frame.cardTopPx());
    }

    /**
     * Diag: one motion-log line when the row's rect changes, naming the page kind it belongs to
     * and the frame it chose (none, or its class, origin, size and radius). Never per frame: only
     * on a change of rect.
     */
    private void logPlace(@Nullable PaneContentFrame frame, float radius) {
        if (!com.termux.app.chrome.MotionLog.enabled) return;
        String frameKv = frame == null ? "none" : frame.getClass().getSimpleName() + "@"
            + mStatusRowOrigin[0] + "," + mStatusRowOrigin[1] + " " + frame.getWidth() + "x"
            + frame.getHeight() + " r=" + radius + " room=" + frame.statusRowPx();
        com.termux.app.chrome.MotionLog.log("status-row", "when=place rowPage=" + mRowPage
            + " frame=" + frameKv + " row=" + mStatusRowRect.toShortString());
    }

    /**
     * The sessions indicator's visibility: shown on a bar dressed for the Terminal, and never where
     * the bar holds only the Marks line (under the Marks look, and in the thin row, which holds
     * only the Marks line or the readings and the text size button).
     */
    public int sessionsIndicatorVisibility(boolean terminal, boolean marksShown) {
        return sessionsIndicatorShown(terminal, mStatusPresentation.isRow(), marksShown)
            ? View.VISIBLE : View.GONE;
    }

    /** The one rule for the sessions indicator: a Terminal bar that is neither the row nor Marks. */
    public static boolean sessionsIndicatorShown(boolean terminal, boolean row, boolean marksShown) {
        return terminal && !row && !marksShown;
    }

    /**
     * Whether the window chips stay away from the bar: under the Marks look ({@code marksShown}),
     * and in the thin row, which has no room for them (under Glyphs the row shows its readings
     * only).
     */
    public boolean chipsKeptAway(boolean marksShown) {
        return marksShown || mStatusPresentation.isRow();
    }

    /** Whether the status bar stands as the thin row in the Terminal's top pane (PaneRowPolicy). */
    public boolean paneRowStands() {
        return mPaneRowStands;
    }

    /** Whether the status host presents as the thin row right now. */
    public boolean isRow() {
        return mStatusPresentation.isRow();
    }

    /** Whether Status bar › Place is no longer what the last arrangement pass read. */
    public boolean placeChanged() {
        return mPaneRowPlace != null && statusPlace() != mPaneRowPlace;
    }

    /**
     * Reads Status bar › Place and where the Terminal's layout stands the bar, once per
     * arrangement pass: the row stands where the layout has the bar along the top, and the top
     * pane then holds it (PaneRowPolicy), never per frame.
     *
     * @param barShown whether the bar is shown at all
     * @param layout the layout on screen
     * @param terminalLayout the Terminal's own layout, null when it has none
     */
    public void readArrangement(boolean barShown, @NonNull PlaceLayout layout,
                                @Nullable PlaceLayout terminalLayout) {
        // Home's layout may have changed with the arrangement: whether it shows the bar is read again.
        mHomeBarKnown = false;
        mPaneRowPlace = statusPlace();
        mPaneRowStands = PaneRowPolicy.rowStands(mPaneRowPlace,
            com.termux.app.page.PageKind.TERMINAL, barShown && mPaneRowPlace.inPane()
                && EdgeStackPolicy.stack(
                terminalLayout == null ? layout : terminalLayout, PlaceLayout.Edge.TOP)
                .contains(Element.STATUS));
        // One room for every page's frame (Terminal, Assistant, Display, Linux window): a slide
        // between them finds the row at the same height of each frame's top.
        mHost.setPaneStatusRowPx(PaneRowPolicy.pageRoomPx(PageKind.TERMINAL, mPaneRowStands,
            PaneRowPolicy.rowHeightPx(mHost.context().getResources().getDisplayMetrics().density)));
    }
}
