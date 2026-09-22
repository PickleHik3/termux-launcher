package com.termux.app.notice;

import android.annotation.SuppressLint;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

/**
 * Puts the notice pill where the screen it is on wants it.
 *
 * <p>Two placements, one rule for keeping either of them current. With a terminal on screen the
 * pill belongs to the terminal: it lands inside the terminal area, centred over the whole of it
 * however many panes it has been split into, and drops out of the window's own top rim. Everywhere
 * else — Settings, the keyboard colour scheme, the file picker — it hangs in the row just under
 * whatever chrome that screen has.
 *
 * <p>The offset used to be measured once, when the host was first attached, off whatever the
 * toolbar happened to be at that moment. That is wrong more often than it is right: a notice raised
 * from a screen's {@code onCreate} measures a toolbar that has never been laid out and lands on the
 * title, and an offset that was correct at attach time survives a rotation, a multi-window resize,
 * a cutout change and a bar that shows or hides — all of which move the edge the chip is supposed
 * to be hanging from. The terminal moves more than any of them: the keyboard comes up and down, a
 * split changes the area's height, a window bar shows and hides.
 *
 * <p>So the placement is derived, not remembered. It is recomputed from the real chrome, the real
 * terminal bounds and the real insets whenever any of them moves, and once more immediately before
 * a pill is shown, which is the only moment it has to be right. Layout params are touched only when
 * a number actually changes, so a screen that never moves its chrome never lays out on this account.
 *
 * <p>The pill is parented to the window's content root either way, never to the terminal. It used
 * to hang off a structural anchor — the surface host, whose top edge is the window bar's bottom
 * edge — but a child of that host is a sibling of whatever the terminal opens inside it, and a
 * later sibling draws on top: the preset-applied notice was laid out at the right place and
 * completely hidden behind the surface editor that raised it. The band this class positions gives
 * the pill the terminal's geometry from the window's topmost layer.
 */
final class AppNoticePlacement implements View.OnLayoutChangeListener {

    /**
     * The chrome a host that names none hangs from, in order of preference. The container comes
     * before the toolbar inside it: a screen that grows a second row under its title bar means the
     * bottom edge moved, and the chip belongs under the whole thing.
     */
    static final int[] DEFAULT_CHROME_IDS = {
        com.termux.shared.R.id.toolbar_container,
        com.termux.shared.R.id.toolbar,
        com.termux.R.id.terminal_window_bar_host,
    };

    /**
     * The terminal area, spanning every pane of a split. The pill is centred over the whole of it
     * rather than over the focused pane: a notice belongs to the terminal, not to one of its
     * columns, and a pill that jumped between panes as focus moved would be read as pointing at one.
     */
    static final int TERMINAL_AREA_ID = com.termux.R.id.terminal_surface_host;

    /** Air between the chrome's bottom edge — or the terminal's own rim — and the pill. */
    private static final float TOP_GAP_DP = 8f;

    /**
     * What the entrance has to cover before the pill has cleared the rim, when it has not been
     * measured yet. The pill's own minimum height plus its padding: over-travelling is invisible
     * behind the clip, a short travel shows a sliver of it above the terminal.
     */
    private static final float UNMEASURED_PILL_DP = 44f;

    @NonNull private final ViewGroup mAnchor;
    @NonNull private final FrameLayout mFrame;
    @NonNull private final AppNoticeHostView mHost;
    @NonNull private final int[] mChromeIds;

    /** The chrome we are currently listening to, so a screen that swaps it is followed. */
    @Nullable private View mChrome;
    /** The terminal area, watched in its own right: the keyboard and a split resize it alone. */
    @Nullable private View mTerminalArea;

    private int mAppliedTopPx = Integer.MIN_VALUE;
    private int mAppliedLeftPx = Integer.MIN_VALUE;
    private int mAppliedWidthPx = Integer.MIN_VALUE;
    private int mAppliedGapPx = Integer.MIN_VALUE;

    /**
     * Starts keeping {@code host}, inside {@code frame}, where the screen {@code anchor} belongs to
     * wants it.
     */
    static void attach(@NonNull ViewGroup anchor, @NonNull FrameLayout frame,
                       @NonNull AppNoticeHostView host) {
        attach(anchor, frame, host, DEFAULT_CHROME_IDS);
    }

    /**
     * As {@link #attach(ViewGroup, FrameLayout, AppNoticeHostView)}, hanging the pill from the first
     * visible view among {@code chromeIds} — looked up from the anchor's root in the order given —
     * on any screen with no terminal area on it.
     */
    static void attach(@NonNull ViewGroup anchor, @NonNull FrameLayout frame,
                       @NonNull AppNoticeHostView host, @NonNull int[] chromeIds) {
        new AppNoticePlacement(anchor, frame, host, chromeIds).install();
    }

    private AppNoticePlacement(@NonNull ViewGroup anchor, @NonNull FrameLayout frame,
                               @NonNull AppNoticeHostView host, @NonNull int[] chromeIds) {
        mAnchor = anchor;
        mFrame = frame;
        mHost = host;
        mChromeIds = chromeIds;
    }

    private void install() {
        // Insets are read at apply() time rather than remembered from this callback: with a legacy
        // target a sibling that fits system windows consumes them before they reach the chip, so
        // what arrives here is not what the screen is actually inset by. The callback is only a
        // signal that something moved.
        ViewCompat.setOnApplyWindowInsetsListener(mFrame, (view, insets) -> {
            schedule();
            return insets;
        });
        mAnchor.addOnLayoutChangeListener(this);
        mFrame.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(@NonNull View view) { schedule(); }
            @Override public void onViewDetachedFromWindow(@NonNull View view) { uninstall(); }
        });
        // The one moment the placement has to be right: the frame a pill becomes visible.
        mHost.setPlacementRefresh(this::apply);
        apply();
    }

    private void uninstall() {
        mAnchor.removeOnLayoutChangeListener(this);
        if (mChrome != null) {
            mChrome.removeOnLayoutChangeListener(this);
            mChrome = null;
        }
        if (mTerminalArea != null) {
            mTerminalArea.removeOnLayoutChangeListener(this);
            mTerminalArea = null;
        }
        mHost.setPlacementRefresh(null);
    }

    @Override
    public void onLayoutChange(View view, int left, int top, int right, int bottom,
                               int oldLeft, int oldTop, int oldRight, int oldBottom) {
        schedule();
    }

    /**
     * Applies, but never from inside the layout pass that told us to: writing layout params there
     * is the "requestLayout() improperly called during layout" warning, and one frame late is
     * invisible for a pill that is not on screen yet.
     */
    private void schedule() {
        if (mFrame.isInLayout() || mAnchor.isInLayout()) mFrame.post(this::apply);
        else apply();
    }

    private void apply() {
        View terminal = resolveTerminalArea();
        if (terminal != null) applyTerminalBand(terminal);
        else applyChromeRow(resolveChrome());
    }

    /**
     * Inside the terminal: the frame takes the terminal area's own left edge and width, so the pill
     * centres over every pane of a split at once, and its top edge is the terminal's rim, which is
     * what the frame clips at.
     */
    private void applyTerminalBand(@NonNull View terminal) {
        int[] location = new int[2];
        terminal.getLocationInWindow(location);
        int[] anchorLocation = new int[2];
        mAnchor.getLocationInWindow(anchorLocation);
        int gap = Math.round(TOP_GAP_DP * density());
        applyBand(bandEdge(location[0], anchorLocation[0]), bandEdge(location[1], anchorLocation[1]),
            terminal.getWidth(), gap, true);
    }

    /** Off the terminal: the row just under whatever chrome the screen has, full width. */
    private void applyChromeRow(@Nullable View chrome) {
        applyBand(0, placementTop(insetFloor(), chromeBottom(chrome), density()),
            ViewGroup.LayoutParams.MATCH_PARENT, 0, false);
    }

    @SuppressLint("RtlHardcoded")
    private void applyBand(int leftPx, int topPx, int widthPx, int gapPx, boolean clipAtRim) {
        if (leftPx == mAppliedLeftPx && topPx == mAppliedTopPx && widthPx == mAppliedWidthPx
            && gapPx == mAppliedGapPx) {
            refreshEntranceRise(gapPx, clipAtRim);
            return;
        }
        ViewGroup.LayoutParams frameParams = mFrame.getLayoutParams();
        if (!(frameParams instanceof FrameLayout.LayoutParams)) return;
        mAppliedLeftPx = leftPx;
        mAppliedTopPx = topPx;
        mAppliedWidthPx = widthPx;
        mAppliedGapPx = gapPx;

        FrameLayout.LayoutParams band = (FrameLayout.LayoutParams) frameParams;
        band.gravity = Gravity.TOP | Gravity.LEFT;
        band.leftMargin = leftPx;
        band.topMargin = topPx;
        band.width = widthPx;
        mFrame.setLayoutParams(band);
        mFrame.setClipChildren(clipAtRim);

        ViewGroup.LayoutParams hostParams = mHost.getLayoutParams();
        if (hostParams instanceof ViewGroup.MarginLayoutParams) {
            ((ViewGroup.MarginLayoutParams) hostParams).topMargin = gapPx;
            mHost.setLayoutParams(hostParams);
        }
        refreshEntranceRise(gapPx, clipAtRim);
    }

    /**
     * How far the pill travels on its way in. Inside the terminal that is the whole way back
     * through the rim; elsewhere the host keeps its own small drop.
     */
    private void refreshEntranceRise(int gapPx, boolean clipAtRim) {
        mHost.setEntranceRisePx(clipAtRim
            ? entranceRisePx(gapPx, mHost.getHeight(), density()) : 0f);
    }

    /**
     * How far the status bar and any cutout reach into the anchor. Subtracting the anchor's own
     * position is what makes one rule work on both kinds of window: a screen whose content already
     * starts below the status bar needs no offset for it, and one drawn edge to edge needs the lot.
     */
    private int insetFloor() {
        WindowInsetsCompat insets = ViewCompat.getRootWindowInsets(mFrame);
        if (insets == null) return 0;
        Insets bars = insets.getInsets(WindowInsetsCompat.Type.statusBars()
            | WindowInsetsCompat.Type.displayCutout()
            | WindowInsetsCompat.Type.captionBar());
        return insetFloor(bars.top, anchorTopInWindow());
    }

    /** The chrome's bottom edge, in the anchor's own coordinates. */
    private int chromeBottom(@Nullable View chrome) {
        if (chrome == null || chrome.getVisibility() == View.GONE) return 0;
        int[] chromeLocation = new int[2];
        chrome.getLocationInWindow(chromeLocation);
        return chromeBottom(chromeLocation[1], chrome.getHeight(), chrome.getMinimumHeight(),
            insetFloor(), anchorTopInWindow());
    }

    private int anchorTopInWindow() {
        int[] anchorLocation = new int[2];
        mAnchor.getLocationInWindow(anchorLocation);
        return anchorLocation[1];
    }

    /** The bars' reach into the anchor: nothing for a screen whose content already starts below them. */
    static int insetFloor(int barsTopPx, int anchorTopInWindowPx) {
        return Math.max(0, barsTopPx - anchorTopInWindowPx);
    }

    /**
     * The chrome's bottom edge in the anchor's coordinates. Raised before the first layout pass — a
     * settings page can raise a notice from onCreate — the declared minimum height below the inset
     * floor is a good enough guess to keep the chip off the title, and the anchor's first layout
     * corrects it.
     */
    static int chromeBottom(int chromeTopInWindowPx, int chromeHeightPx, int chromeMinHeightPx,
                            int insetFloorPx, int anchorTopInWindowPx) {
        if (chromeHeightPx <= 0) return insetFloorPx + chromeMinHeightPx;
        return Math.max(0, chromeTopInWindowPx + chromeHeightPx - anchorTopInWindowPx);
    }

    /** The row the pill lands in: clear of the bars and of the chrome, by a hair. */
    static int placementTop(int insetFloorPx, int chromeBottomPx, float density) {
        return Math.max(insetFloorPx, chromeBottomPx) + Math.round(TOP_GAP_DP * density);
    }

    /** One edge of the terminal band in the anchor's coordinates; never outside it. */
    static int bandEdge(int areaEdgeInWindowPx, int anchorEdgeInWindowPx) {
        return Math.max(0, areaEdgeInWindowPx - anchorEdgeInWindowPx);
    }

    /**
     * The distance from the pill's resting place back through the rim it came from. Its own height
     * plus the gap it rests below the edge, so at the start of the entrance it is entirely on the
     * far side of the clip.
     *
     * @param pillHeightPx the pill's measured height, or 0 before it has ever been laid out.
     */
    static float entranceRisePx(int gapPx, int pillHeightPx, float density) {
        return gapPx + Math.max(pillHeightPx, UNMEASURED_PILL_DP * density);
    }

    /** The terminal area of this screen, or null on a screen that has none showing. */
    @Nullable
    private View resolveTerminalArea() {
        View found = mAnchor.getRootView().findViewById(TERMINAL_AREA_ID);
        if (found != null && (!found.isShown() || found.getWidth() <= 0 || found.getHeight() <= 0))
            found = null;
        if (found != mTerminalArea) {
            if (mTerminalArea != null) mTerminalArea.removeOnLayoutChangeListener(this);
            mTerminalArea = found;
            // The keyboard, a split and a window bar all resize this without the anchor moving.
            if (mTerminalArea != null) mTerminalArea.addOnLayoutChangeListener(this);
        }
        return found;
    }

    /** The chrome this screen has, looked up afresh: screens replace their bars. */
    @Nullable
    private View resolveChrome() {
        View root = mAnchor.getRootView();
        View found = null;
        for (int id : mChromeIds) {
            View candidate = root.findViewById(id);
            if (candidate != null && candidate.getVisibility() != View.GONE) {
                found = candidate;
                break;
            }
        }
        if (found != mChrome) {
            if (mChrome != null) mChrome.removeOnLayoutChangeListener(this);
            mChrome = found;
            // A collapsing bar changes height without the anchor moving, so the chrome is watched
            // in its own right rather than only through the container it sits in.
            if (mChrome != null) mChrome.addOnLayoutChangeListener(this);
        }
        return found;
    }

    private float density() {
        return mFrame.getResources().getDisplayMetrics().density;
    }
}
