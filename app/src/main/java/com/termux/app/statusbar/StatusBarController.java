package com.termux.app.statusbar;

import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import android.os.Build;
import android.widget.FrameLayout;
import com.termux.app.terminal.GeometryScheduler;
import android.view.Gravity;
import com.termux.app.place.PlaceLayout;
import com.termux.app.place.PlaceLayoutStore;
import com.termux.app.place.PlaceOrientation;
import com.termux.R;
import android.graphics.Rect;
import com.termux.app.surfaces.SurfaceEditorController;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import android.view.ViewGroup;
// @@IMPORTS@@

/**
 * The status bar's events and state (ADR 0018 §1, wave B step one, D17), moved out of the
 * activity line for line. It reaches the activity only through {@link Host}.
 */
public final class StatusBarController {

    /** What only the activity gives the status bar. */
    public interface Host {
        void applyPlaceStripInset(float expansion);
        void applyStatusColumnRowGeometry(@NonNull FrameLayout.LayoutParams params, @NonNull View statusRow, boolean collapsed);
        int bottomStatusBandPx();
        @Nullable com.termux.app.chrome.LiveChromeShape.Clip chromeClipOf( @NonNull com.termux.app.place.ChromeShape.PieceId... ids);
        PlaceOrientation currentPlaceOrientation();
        float dpToPx(float dp);
        @Nullable <T extends View> T findViewById(int id);
        @NonNull android.content.res.Resources getResources();
        void growAccessoryStackBy(int deltaPx);
        boolean isAppDrawerEngaged();
        boolean isCommandPaletteOpen();
        boolean isReducedMotionEnabled();
        boolean isRoundedStyle(@NonNull com.termux.app.place.SeamDress.Surface surface);
        boolean isStatusBarVertical();
        boolean isStatusClockShown();
        @Nullable PlaceLayoutStore placeLayoutStore();
        void refreshTerminalWindowBar();
        float resolveStatusBarCapsuleCornerRadiusPx(int surfaceHeightPx);
        int targetStatusBarHeightPx(boolean capsule, boolean collapsed);
        GeometryScheduler geometry();
        @Nullable com.termux.app.wall.PaneWallController paneWallController();
        TermuxAppSharedPreferences preferences();
        @NonNull PlaceLayout.Edge statusBarEdge();
        com.termux.app.chrome.ChromeShapeOutlineProvider statusBarSurfaceOutline();
        SurfaceEditorController surfaceEditor();
        // @@HOST@@
    }

    @NonNull private final Host mHost;

    public StatusBarController(@NonNull Host host) {
        mHost = host;
    }

    @Nullable private android.animation.ValueAnimator mStatusBarCollapseAnimator;
    /**
     * A finger is folding the bar (dragTopStatusBar): the thickness it started from and the
     * resize lease the drag holds, carried into the landing so the panes hear one size at its end.
     */
    private boolean mStatusBarFoldDragging;
    private int mStatusBarFoldDragStartHeight;
    @Nullable private GeometryScheduler.Transition mStatusBarFoldDragTransition;
    /**
     * Whether the pane's top border has a bar to fold (syncBorderStatusSwipe): the bar shown,
     * along the top, where it can unfold. Kept as a field because the wall reads it on every
     * frame it draws its grabbers.
     */
    private boolean mBorderStatusBarFoldable;

    /**
     * Whether the status bar on screen is resting compact. It is the memory of the orientation it
     * is showing in, not one state for the whole launcher — a bar the user opened on a tall screen
     * is not a bar they asked for on a short one — and every place shares it, so crossing the wall
     * never moves the bar.
     */
    public boolean isStatusBarCompact() {
        if (!com.termux.app.statusbar.StatusBarGesturePolicy.expansionAllowed(mHost.statusBarEdge())) {
            return true;
        }
        // The Look editor holds the bar open without touching the stored choice.
        if (mStatusBarExpandedForEditor) {
            return false;
        }
        PlaceLayoutStore store = mHost.placeLayoutStore();
        return store != null && store.isStatusCompact(mHost.currentPlaceOrientation());
    }

    private void setStatusBarCompact(boolean compact) {
        PlaceLayoutStore store = mHost.placeLayoutStore();
        if (store != null) store.setStatusCompact(mHost.currentPlaceOrientation(), compact);
    }

    private void applyTopStatusBarInteractiveHeight(View host, @Nullable View topWidgets,
                                                     int height, boolean capsule) {
        boolean vertical = mHost.isStatusBarVertical();
        ViewGroup.LayoutParams params = host.getLayoutParams();
        if (vertical ? params.width != height : params.height != height) {
            int bandBeforePx = mHost.bottomStatusBandPx();
            if (vertical) params.width = height;
            else params.height = height;
            host.setLayoutParams(params);
            // A bar along the bottom is a band of the accessory stack, whose height is arithmetic
            // rather than wrap_content — so the stack has to grow with the bar, or the fold would
            // open into a container that never made room for it. By the band's change alone: the
            // whole geometry pass per frame was a full chrome apply per frame, and the fold's
            // settle runs the one pass that lays everything else out
            // (beginStatusBarTerminalResize).
            if (mHost.statusBarEdge() == PlaceLayout.Edge.BOTTOM)
                mHost.growAccessoryStackBy(mHost.bottomStatusBandPx() - bandBeforePx);
        }
        int collapsedHeight = mHost.targetStatusBarHeightPx(capsule, true);
        int expandedHeight = mHost.targetStatusBarHeightPx(capsule, false);
        float expansion = expandedHeight == collapsedHeight ? 0f
            : Math.max(0f, Math.min(1f,
                (height - collapsedHeight) / (float) (expandedHeight - collapsedHeight)));
        com.termux.app.statusbar.StatusBarResizeGeometry.Row rowGeometry = vertical ? null
            : applyInteractiveStatusRowGeometry(height, capsule, collapsedHeight, expandedHeight);
        View columnRow = vertical ? mHost.findViewById(R.id.terminal_status_row) : null;
        if (columnRow != null && columnRow.getLayoutParams() instanceof FrameLayout.LayoutParams) {
            // A column's row does not ride a moving edge: the bar grows sideways and the row
            // simply stands under whatever the open bar puts above it.
            mHost.applyStatusColumnRowGeometry((FrameLayout.LayoutParams) columnRow.getLayoutParams(),
                columnRow, expansion < 0.5f);
        }
        com.termux.app.statusbar.StatusBarLensView lens = mHost.findViewById(R.id.terminal_status_lens);
        if (lens != null) lens.setExpansion(expansion);
        mHost.applyPlaceStripInset(expansion);
        View stackedClock = mHost.findViewById(R.id.terminal_status_column_clock);
        if (stackedClock != null && vertical) {
            // The column's clock arrives with the width that makes room for it, rather than
            // sliding: there is nothing above it in a column for it to slide out from under.
            stackedClock.setVisibility(expansion > 0.02f && mHost.isStatusClockShown()
                ? View.VISIBLE : View.GONE);
            stackedClock.setAlpha(expansion);
        }
        if (topWidgets != null && !vertical) {
            // The slot stands above the row on both row edges, so what it shows is always the
            // stretch the row has not reached — its own top down to the row's crown. It slides in
            // the way the bar is growing: down out of a top bar, up out of a bottom one.
            float sign = com.termux.app.statusbar.StatusBarGesturePolicy.expandSign(mHost.statusBarEdge());
            // The clock and the cards show only once the bar has the height to hold them, rising
            // the last of the way in (StatusBarFoldMotion.CONTENT_REVEAL_START): a function of the
            // height, so a fold reads the same curve down and half-drawn content never shows. The
            // clock's own flip to the current time after it is revealed is its own behaviour.
            float content = com.termux.app.statusbar.StatusBarFoldMotion.contentAlpha(expansion);
            topWidgets.setVisibility(View.VISIBLE);
            topWidgets.setAlpha(content);
            topWidgets.setTranslationY(-sign * mHost.dpToPx(8) * (1f - content));
            int clipRight = Math.max(1, Math.max(host.getWidth(), topWidgets.getWidth()));
            int widgetHeight = topWidgets.getHeight() > 0
                ? topWidgets.getHeight() : rowGeometry.clockClipBottom;
            int clear = Math.max(0, Math.min(widgetHeight, rowGeometry.clockClipBottom));
            topWidgets.setClipBounds(new Rect(0, 0, clipRight, clear));
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            // The clip radius must follow the CURRENT interactive height, not the endpoint it is
            // heading to: min(configured, height/2) keeps the compact pill a pill and rounds the
            // growing bar continuously, instead of dragging a stale endpoint radius through every
            // intermediate height (visible as mismatched corners mid-gesture).
            {
                // Floating rounds to the radius this height resolves to. Docked is the model's
                // slice of the frame, so it holds through the gesture rather than dropping to 0
                // mid-drag and snapping back on release.
                mHost.statusBarSurfaceOutline().setClip(capsule
                    ? com.termux.app.chrome.LiveChromeShape.cardClip(
                        mHost.resolveStatusBarCapsuleCornerRadiusPx(height))
                    : mHost.chromeClipOf(com.termux.app.place.ChromeShape.PieceId.STATUS));
                if (host.getOutlineProvider() != mHost.statusBarSurfaceOutline())
                    host.setOutlineProvider(mHost.statusBarSurfaceOutline());
                mHost.statusBarSurfaceOutline().follow(host);
                host.setClipToOutline(mHost.statusBarSurfaceOutline().clipsCorners());
            }
            host.invalidateOutline();
        }
    }

    private com.termux.app.statusbar.StatusBarResizeGeometry.Row
            applyInteractiveStatusRowGeometry(int surfaceHeight, boolean capsule,
                                              int collapsedHeight, int expandedHeight) {
        float density = mHost.getResources().getDisplayMetrics().density;
        com.termux.app.statusbar.StatusBarEdgeGeometry.RowGeometry folded =
            com.termux.app.statusbar.StatusBarEdgeGeometry.rowGeometry(mHost.statusBarEdge(), capsule,
                true, true, density);
        com.termux.app.statusbar.StatusBarEdgeGeometry.RowGeometry open =
            com.termux.app.statusbar.StatusBarEdgeGeometry.rowGeometry(mHost.statusBarEdge(), capsule,
                false, false, density);
        int collapsedRowHeight = folded.rowHeight;
        int expandedRowHeight = open.rowHeight;
        int expandedBottomMargin = open.foot;
        com.termux.app.statusbar.StatusBarResizeGeometry.Row geometry =
            com.termux.app.statusbar.StatusBarResizeGeometry.calculate(surfaceHeight,
                collapsedHeight, expandedHeight, collapsedRowHeight, expandedRowHeight,
                expandedBottomMargin);

        View statusRow = mHost.findViewById(R.id.terminal_status_row);
        if (statusRow != null && statusRow.getLayoutParams() instanceof FrameLayout.LayoutParams) {
            FrameLayout.LayoutParams params =
                (FrameLayout.LayoutParams) statusRow.getLayoutParams();
            params.gravity = Gravity.TOP;
            // The row keeps the bar's own screen edge, so the offset the resize geometry measured
            // from the panel's foot is the answer on a top bar and a bottom one alike.
            params.topMargin = com.termux.app.statusbar.StatusBarLensPolicy.rowOffsetPx(
                mHost.statusBarEdge(), surfaceHeight, geometry.height, geometry.top);
            params.bottomMargin = 0;
            params.width = ViewGroup.LayoutParams.MATCH_PARENT;
            params.height = geometry.height;
            statusRow.setLayoutParams(params);
        }

        View sessions = mHost.findViewById(R.id.terminal_sessions_indicator);
        if (sessions != null && sessions.getLayoutParams() != null) {
            int collapsedSessionHeight = Math.round(mHost.dpToPx(capsule ? 18 : 20));
            int expandedSessionHeight = Math.round(mHost.dpToPx(20));
            int sessionHeight = Math.round(collapsedSessionHeight
                + (expandedSessionHeight - collapsedSessionHeight) * geometry.expansion);
            ViewGroup.LayoutParams params = sessions.getLayoutParams();
            params.height = sessionHeight;
            if (sessions instanceof com.termux.app.statusbar.SessionsIndicatorView
                && ((com.termux.app.statusbar.SessionsIndicatorView) sessions).isShowingSessionNumber()) {
                params.width = sessionHeight;
            }
            sessions.setLayoutParams(params);
        }
        return geometry;
    }

    /** The bar's thickness across its own axis right now, whichever axis that is. */
    private int currentTopStatusBarHeight(View host) {
        ViewGroup.LayoutParams params = host.getLayoutParams();
        if (mHost.isStatusBarVertical()) {
            return params != null && params.width > 0 ? params.width : host.getWidth();
        }
        return params != null && params.height > 0 ? params.height : host.getHeight();
    }

    /**
     * Opens the fold's transition: the panes' grid and the display's screen are held, and the
     * layout the fold moves every frame books no geometry pass, until
     * {@link #finishStatusBarTerminalResize}. Each pane then sends one final row/column update
     * instead of a SIGWINCH for every frame.
     */
    @NonNull
    private GeometryScheduler.Transition beginStatusBarTerminalResize() {
        return mHost.geometry().begin(GeometryScheduler.Reason.STATUS_FOLD, true, true);
    }

    /**
     * Ends a fold's transition. A landing ({@code settle}) books the one geometry pass for the
     * bar's new height; a fold taken over by another hands what it moved to the one taking over,
     * which opened its own transition first. Either way the grid resumes behind that pass's layout.
     */
    public void finishStatusBarTerminalResize(@Nullable GeometryScheduler.Transition fold,
                                               boolean settle) {
        mHost.geometry().settle(fold, settle);
    }

    public void setTopStatusBarCollapsed(boolean requestedCollapsed, boolean animate) {
        setTopStatusBarCollapsed(requestedCollapsed, animate, 0f);
    }

    /**
     * Whether the Appearance editor's Look page holds the top status bar open. A transient state
     * ({@link #isStatusBarCompact} reads it as expanded) that is never stored: the stored
     * compact/expanded choice, per orientation, is exactly what it was.
     */
    private boolean mStatusBarExpandedForEditor;

    /**
     * Opens the top status bar for the editor, or lets it fold back to its stored shape, with
     * the same animated height change a fold makes. Nothing is written: with the flag set the
     * bar already reads as expanded, so the fold's own preference write sees no change. A bar down
     * a side cannot expand, so there this does nothing.
     */
    public void setTopStatusBarExpandedForEditor(boolean expanded, boolean animate) {
        boolean held = expanded
            && com.termux.app.statusbar.StatusBarGesturePolicy.expansionAllowed(mHost.statusBarEdge());
        if (mStatusBarExpandedForEditor == held) return;
        mStatusBarExpandedForEditor = held;
        setTopStatusBarCollapsed(isStatusBarCompact(), animate);
    }

    /**
     * Lands the bar in one of its two forms. The fold's arithmetic — the landing's time, when the
     * open bar's content shows — is {@link com.termux.app.statusbar.StatusBarFoldMotion}'s; the
     * curve is the shared settle curve. A fold the finger was driving ({@link #dragTopStatusBar})
     * ends here whichever way the call lands the bar, from wherever the finger left it.
     *
     * @param towardOpenVelocityPxPerSec how fast the finger that asked was moving across the bar,
     *                                   positive the way the bar opens; 0 with no finger behind it
     */
    public void setTopStatusBarCollapsed(boolean requestedCollapsed, boolean animate,
                                          float towardOpenVelocityPxPerSec) {
        if (mHost.preferences() == null) return;
        GeometryScheduler.Transition dragLease = endTopStatusBarDrag();
        // A bar down a side never rests expanded; isStatusBarCompact() already reflects that, so
        // this coercion never lands on a preference write below — it only refuses the request.
        boolean collapsed = requestedCollapsed
            || !com.termux.app.statusbar.StatusBarGesturePolicy.expansionAllowed(mHost.statusBarEdge());
        View host = mHost.findViewById(R.id.terminal_window_bar_host);
        View topWidgets = mHost.findViewById(R.id.terminal_top_widget_area);
        com.termux.app.statusbar.StatusBarSwipeLayout swipeHost = host instanceof
            com.termux.app.statusbar.StatusBarSwipeLayout
            ? (com.termux.app.statusbar.StatusBarSwipeLayout) host : null;
        boolean capsule = mHost.isRoundedStyle(com.termux.app.place.SeamDress.Surface.STATUS);
        int targetHeight = mHost.targetStatusBarHeightPx(capsule, collapsed);
        boolean preferenceChanged = isStatusBarCompact() != collapsed;
        boolean geometryChanged = host != null && currentTopStatusBarHeight(host) != targetHeight;
        if (swipeHost != null) swipeHost.setCollapsed(collapsed);
        if (!preferenceChanged && !geometryChanged) {
            if (topWidgets != null) {
                topWidgets.setClipBounds(null);
                topWidgets.setAlpha(1f);
                topWidgets.setTranslationY(0f);
                topWidgets.setVisibility(collapsed || mHost.isStatusBarVertical()
                    ? View.GONE : View.VISIBLE);
            }
            // Nothing began a resize here, so nothing is finished either — except the lease a
            // finger's fold took, which ends with the fold: closing a lease this call never
            // opened would end the keyboard's or an animation's early and let panes settle on a
            // size they never reported.
            mHost.refreshTerminalWindowBar();
            finishStatusBarTerminalResize(dragLease, true);
            return;
        }
        if (preferenceChanged) setStatusBarCompact(collapsed);
        if (host == null) {
            mHost.refreshTerminalWindowBar();
            finishStatusBarTerminalResize(dragLease, true);
            return;
        }
        if (!animate || mHost.isReducedMotionEnabled()) {
            GeometryScheduler.Transition snap = dragLease != null ? dragLease
                : beginStatusBarTerminalResize();
            mHost.refreshTerminalWindowBar();
            finishStatusBarTerminalResize(snap, true);
            return;
        }

        // Opened before the landing still running is cancelled, so what that one moved is this
        // one's to settle rather than a pass of its own.
        final GeometryScheduler.Transition foldTransition = dragLease != null ? dragLease
            : beginStatusBarTerminalResize();
        cancelTopStatusBarAnimator();
        int startHeight = currentTopStatusBarHeight(host);
        if (startHeight <= 0) startHeight = mHost.targetStatusBarHeightPx(capsule, !collapsed);
        applyTopStatusBarInteractiveHeight(host, topWidgets, startHeight, capsule);
        int fullDistance = Math.max(1, mHost.targetStatusBarHeightPx(capsule, false)
            - mHost.targetStatusBarHeightPx(capsule, true));
        // The finger's speed toward the form it asked for, in whole ways a second; a release
        // moving the other way lends the landing nothing.
        float towardTarget = (collapsed ? -towardOpenVelocityPxPerSec : towardOpenVelocityPxPerSec)
            / fullDistance;
        long settleDuration = com.termux.app.statusbar.StatusBarFoldMotion.durationMs(
            Math.abs(targetHeight - startHeight) / (float) fullDistance, towardTarget);
        android.animation.ValueAnimator fold =
            android.animation.ValueAnimator.ofInt(startHeight, targetHeight);
        fold.setDuration(settleDuration);
        fold.setInterpolator(com.termux.app.terminal.Motion.settle());
        fold.addUpdateListener(animation -> {
            int height = (Integer) animation.getAnimatedValue();
            applyTopStatusBarInteractiveHeight(host, topWidgets, height, capsule);
        });
        fold.addListener(new android.animation.AnimatorListenerAdapter() {
            private boolean mCancelled;
            @Override public void onAnimationCancel(android.animation.Animator animation) {
                mCancelled = true;
            }
            @Override public void onAnimationEnd(android.animation.Animator animation) {
                if (mStatusBarCollapseAnimator == animation) mStatusBarCollapseAnimator = null;
                if (mCancelled) {
                    // Taken over — by a finger, another landing, or a new edge — from wherever the
                    // bar stands: whoever took over owns its geometry now, and only the lease
                    // this landing held is returned.
                    finishStatusBarTerminalResize(foldTransition, false);
                    return;
                }
                if (topWidgets != null) {
                    topWidgets.setClipBounds(null);
                    topWidgets.setAlpha(1f);
                    topWidgets.setTranslationY(0f);
                    topWidgets.setVisibility(collapsed || mHost.isStatusBarVertical()
                        ? View.GONE : View.VISIBLE);
                }
                mHost.refreshTerminalWindowBar();
                finishStatusBarTerminalResize(foldTransition, true);
            }
        });
        mStatusBarCollapseAnimator = fold;
        fold.start();
    }

    /** Whether a finger or an animator is driving the bar's fold, and so owns its geometry. */
    public boolean isStatusBarFoldInteractive() {
        return mStatusBarCollapseAnimator != null || mStatusBarFoldDragging;
    }

    /** Stops a landing under way where it stands; its end listener returns its lease. */
    public void cancelTopStatusBarAnimator() {
        android.animation.ValueAnimator fold = mStatusBarCollapseAnimator;
        if (fold == null) return;
        mStatusBarCollapseAnimator = null;
        fold.cancel();
    }

    /** Ends a finger's fold, returning the transition it held, or null when none was under way. */
    @Nullable
    public GeometryScheduler.Transition endTopStatusBarDrag() {
        if (!mStatusBarFoldDragging) return null;
        mStatusBarFoldDragging = false;
        GeometryScheduler.Transition drag = mStatusBarFoldDragTransition;
        mStatusBarFoldDragTransition = null;
        return drag;
    }

    /**
     * A finger is folding the bar: it stands at the height the finger has taken it to, between
     * its two forms ({@link com.termux.app.statusbar.StatusBarFoldMotion#heightForDrag}), and the
     * release — {@link #setTopStatusBarCollapsed(boolean, boolean, float)} — lands it from there.
     * The first frame takes the resize lease a landing would, so the panes hear one size at the
     * landing rather than one a frame, and takes over from any landing still running.
     */
    public void dragTopStatusBar(float towardOpenPx) {
        if (mHost.preferences() == null
            || !com.termux.app.statusbar.StatusBarGesturePolicy.expansionAllowed(mHost.statusBarEdge())) {
            return;
        }
        View host = mHost.findViewById(R.id.terminal_window_bar_host);
        if (host == null) return;
        View topWidgets = mHost.findViewById(R.id.terminal_top_widget_area);
        boolean capsule = mHost.isRoundedStyle(com.termux.app.place.SeamDress.Surface.STATUS);
        if (!mStatusBarFoldDragging) {
            // The drag's transition first: a landing it takes over hands it what that one moved.
            GeometryScheduler.Transition drag = beginStatusBarTerminalResize();
            cancelTopStatusBarAnimator();
            mStatusBarFoldDragging = true;
            mStatusBarFoldDragStartHeight = currentTopStatusBarHeight(host);
            mStatusBarFoldDragTransition = drag;
        }
        int height = com.termux.app.statusbar.StatusBarFoldMotion.heightForDrag(
            mStatusBarFoldDragStartHeight, towardOpenPx, mHost.targetStatusBarHeightPx(capsule, true),
            mHost.targetStatusBarHeightPx(capsule, false));
        applyTopStatusBarInteractiveHeight(host, topWidgets, height, capsule);
    }

    /**
     * Whether a swipe off the pane's top border may fold the bar now: there is one to fold
     * ({@link #mBorderStatusBarFoldable}) and no other surface has the bar's gesture (the same vetoes as the bar's own drag). Cheap: the wall asks it per frame.
     */
    public boolean isBorderStatusSwipeAvailable() {
        return mBorderStatusBarFoldable && mHost.preferences() != null
            && !mHost.isCommandPaletteOpen() && !mHost.isAppDrawerEngaged() && !mHost.surfaceEditor().isActive();
    }

    /**
     * The bar was shown or put away, or moved to another edge: the top border's swipe, and the
     * grabber that marks it, go with a bar along the top that can unfold.
     */
    public void syncBorderStatusSwipe(boolean barShown) {
        boolean foldable = barShown && mHost.statusBarEdge() == PlaceLayout.Edge.TOP
            && com.termux.app.statusbar.StatusBarGesturePolicy.expansionAllowed(mHost.statusBarEdge());
        if (foldable == mBorderStatusBarFoldable) return;
        mBorderStatusBarFoldable = foldable;
        if (mHost.paneWallController() != null) mHost.paneWallController().wall().invalidate();
    }

    /**
     * A swipe off the top border was claimed, going down ({@code expanding}) or up: the bar's
     * fold follows it from the form it rests in, through {@link #dragTopStatusBar} as the bar's
     * own drag does, taking the same resize lease on this first frame. Answers the fold's whole
     * way in px, or 0 where it cannot follow — nothing to fold, the bar already where the swipe
     * points, or a landing still running — and the release then asks on its own.
     */
    public int beginBorderStatusFold(boolean expanding) {
        if (!isBorderStatusSwipeAvailable() || isStatusBarFoldInteractive()) return 0;
        if (isStatusBarCompact() != expanding) return 0;
        boolean capsule = mHost.isRoundedStyle(com.termux.app.place.SeamDress.Surface.STATUS);
        int travel = mHost.targetStatusBarHeightPx(capsule, false)
            - mHost.targetStatusBarHeightPx(capsule, true);
        if (travel <= 0) return 0;
        dragTopStatusBar(0f);
        return mStatusBarFoldDragging ? travel : 0;
    }

    /** {@link #mBorderStatusBarFoldable}, for the long-press menu's Fold item. */
    public boolean isBorderStatusBarFoldable() {
        return mBorderStatusBarFoldable;
    }

    /** {@link #mStatusBarFoldDragging}: whether a finger is folding the bar now. */
    public boolean isStatusBarFoldDragging() {
        return mStatusBarFoldDragging;
    }

    // @@BODY@@
}
