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
 * offset, the row's rect over the top pane, its marks and readings sizes and its ground. Moved
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
        @Nullable View displayWindow();
        /** The top-left pane that holds the row, null when there is none. */
        @Nullable PaneContentFrame statusRowFrame();
        /** The inset the top pane holds for the row (0 when the strip stands). */
        void setPaneStatusRowPx(int px);
        boolean isReducedMotionEnabled();
        int surfaceBaseColor();
        void refreshTerminalWindowBar();
        /** Whether the bar marks the pages with the Marks line (Status bar card › Page bar). */
        boolean marksLookShown();
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

    /**
     * Presents the one status host as the thin row or as the strip (StatusPresentation): the row
     * at rest on a page with a pane, the strip on Home and through any slide that can show Home.
     * Called on every arrangement pass and every wall offset, it compares first: under the strip
     * it is two field reads, and a swap costs one host relayout. No page moves on a swap, since
     * each page's top is its own (PageTopInsets).
     */
    public void syncStatusPresentation() {
        if (!mPaneRowStands && !mStatusPresentation.isRow()) return;
        com.termux.app.wall.PaneWallLayout wall =
            mHost.wall();
        boolean atRest = wall == null || com.termux.app.place.PageTopInsets.wallAtRestOn(
            mHost.wallPage(), wall.isMoving(), wall.offsetPx(), wall.currentPage());
        // A wall that leaves its rest takes the enlarged row with it: it settles before the page
        // starts to move.
        if (!atRest && mStatusPresentation.isRow()) endStatusRowEnlargement();
        com.termux.app.wall.PageRef other = mHost.crossingOtherEnd();
        boolean homeInView = mHost.wallPage() == com.termux.app.page.PageKind.HOME
            || (wall != null && wall.currentPage() == com.termux.app.page.PageKind.HOME)
            || (other != null && other.isHome());
        StatusPresentation presentation =
            StatusPresentation.of(mPaneRowStands, mHost.wallPage(), atRest,
                homeInView);
        if (presentation == mStatusPresentation) return;
        mStatusPresentation = presentation;
        boolean row = presentation.isRow();
        View hostView = find(R.id.terminal_window_bar_host);
        if (hostView instanceof StatusBarSwipeLayout) {
            StatusBarSwipeLayout host =
                (StatusBarSwipeLayout) hostView;
            // The row has no expansion; the strip's own rule comes back with the strip.
            host.setExpansionAllowed(!row && StatusBarGesturePolicy
                .expansionAllowed(host.edge()));
            host.setRowPresented(row);
            if (row) wireRowEnlargement(host);
        }
        applyRowSizes(row, false);
        // The sessions indicator stands in the strip only, as syncPlaceBar decides it too.
        View sessions = find(R.id.terminal_sessions_indicator);
        if (sessions != null) {
            int shown = sessionsIndicatorVisibility(true, mHost.marksLookShown());
            if (sessions.getVisibility() != shown) sessions.setVisibility(shown);
        }
        syncStatusRowPlace();
        // The chips stand in the strip only (chipsKeptAway).
        mHost.refreshTerminalWindowBar();
    }

    /**
     * The row's marks and readings at the size the row has: thin at rest (12dp marks, readings
     * laid out at 9sp), and while enlarged the full 18dp marks with the readings at their own
     * size. The strip takes neither cap nor size.
     */
    private void applyRowSizes(boolean row, boolean enlarged) {
        float density = mHost.context().getResources().getDisplayMetrics().density;
        MarksLineView marks = find(R.id.terminal_status_marks_row);
        if (marks != null) {
            marks.setMarkCapPx(!row ? 0f : (enlarged
                ? PaneRowPolicy.ENLARGED_MARK_DP
                : PaneRowPolicy.MARK_DP) * density);
        }
        View readings = find(R.id.terminal_status_widgets);
        if (readings != null) {
            setReadingSizes(readings, row && !enlarged
                ? PaneRowPolicy.READING_SP : 0f);
        }
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

    /** Hooks the host's enlarged row to the sizes above, the ground, and reduced motion. */
    private void wireRowEnlargement(@NonNull StatusBarSwipeLayout host) {
        host.setRowReducedMotion(mHost::isReducedMotionEnabled);
        host.setRowEnlargeListener(enlarged -> applyRowSizes(true, enlarged));
        syncRowGround(host);
    }

    /**
     * The enlarged row's solid ground: the surface colour on a terminal page, and the Display
     * page's dark band made opaque, whose ink the row already reads on.
     */
    private void syncRowGround(@NonNull StatusBarSwipeLayout host) {
        if (mHost.wallPage() == com.termux.app.page.PageKind.DISPLAY) {
            host.setRowGround(PaneRowPolicy.DISPLAY_BAND_ARGB | 0xFF000000);
        } else {
            host.setRowGround(mHost.surfaceBaseColor() | 0xFF000000);
        }
    }

    /** Settles the enlarged thin row at once (page change, wall moving, pause). */
    public void endStatusRowEnlargement() {
        View hostView = find(R.id.terminal_window_bar_host);
        if (hostView instanceof StatusBarSwipeLayout)
            ((StatusBarSwipeLayout) hostView).endEnlargement(false);
    }

    private final int[] mStatusRowOrigin = new int[2];
    private final int[] mStatusRowHostOrigin = new int[2];
    private final int[] mStatusRowScratch = new int[2];

    /**
     * Lays the row over the top-left pane of the page in front, read off where the pane and the
     * host are laid out (not where a slide draws them): over the top pane's top margin on a
     * Terminal window, which holds the row (PaneShape.topInsetPx), and over the display's top
     * edge on a dark band on the Display page. Runs after every window layout and on a swap;
     * the host relays out only when the rect changed.
     *
     * <p>#334 grows the row from here: the pane's inset stays {@link
     * PaneRowPolicy#rowHeightPx}, and the enlarged row is drawn over
     * the pane (a taller rect in the host, on a solid band) without touching the pane's layout.
     */
    public void syncStatusRowPlace() {
        if (!mStatusPresentation.isRow()) return;
        View hostView = find(R.id.terminal_window_bar_host);
        if (!(hostView instanceof StatusBarSwipeLayout)
            || hostView.getWidth() <= 0) return;
        StatusBarSwipeLayout host =
            (StatusBarSwipeLayout) hostView;
        boolean display = mHost.wallPage() == com.termux.app.page.PageKind.DISPLAY;
        View pane;
        float radius;
        if (display) {
            pane = mHost.displayWindow();
            radius = 0f;
        } else if (mHost.wallPage() != com.termux.app.page.PageKind.TERMINAL) {
            // A page with no terminal pane (the Assistant): the row lies along the canvas's top.
            pane = mHost.wall();
            radius = 0f;
        } else {
            com.termux.app.terminal.PaneContentFrame frame =
                mHost.statusRowFrame();
            pane = frame;
            radius = frame == null ? 0f : com.termux.app.terminal.PaneShape.radiusForBounds(
                frame.requestedRadiusPx(), frame.getWidth(), frame.getHeight());
        }
        if (pane == null || pane.getWidth() <= 0) {
            host.setRowRect(0, 0, 0, 0, 0);
            return;
        }
        float density = mHost.context().getResources().getDisplayMetrics().density;
        com.termux.app.chrome.GlassAnchor.layoutOriginOnScreen(pane, mStatusRowOrigin,
            mStatusRowScratch);
        com.termux.app.chrome.GlassAnchor.layoutOriginOnScreen(host, mStatusRowHostOrigin,
            mStatusRowScratch);
        int rowPx = PaneRowPolicy.rowHeightPx(density);
        boolean rtl = host.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
        int start = PaneRowPolicy.startInsetPx(radius, rowPx, density);
        int end = PaneRowPolicy.endInsetPx(radius, rowPx, density);
        int left = mStatusRowOrigin[0] - mStatusRowHostOrigin[0];
        int top = mStatusRowOrigin[1] - mStatusRowHostOrigin[1];
        int right = left + pane.getWidth();
        int band = display ? PaneRowPolicy.DISPLAY_BAND_ARGB : 0;
        // The display's band spans its whole top edge; the row's content keeps its insets.
        if (display) {
            host.setRowRect(left, top, right, top + rowPx, band);
        } else {
            host.setRowRect(left + (rtl ? end : start), top, right - (rtl ? start : end),
                top + rowPx, band);
        }
        syncRowGround(host);
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
        mPaneRowPlace = statusPlace();
        mPaneRowStands = PaneRowPolicy.rowStands(mPaneRowPlace,
            com.termux.app.page.PageKind.TERMINAL, barShown && mPaneRowPlace.inPane()
                && EdgeStackPolicy.stack(
                terminalLayout == null ? layout : terminalLayout, PlaceLayout.Edge.TOP)
                .contains(Element.STATUS));
        mHost.setPaneStatusRowPx(PaneRowPolicy.paneTopInsetPx(mPaneRowStands, true,
            PaneRowPolicy.rowHeightPx(mHost.context().getResources().getDisplayMetrics().density)));
    }
}
