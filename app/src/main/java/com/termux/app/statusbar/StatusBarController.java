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
import com.termux.app.dock.DockLayoutPolicy;
import com.termux.shared.termux.settings.properties.TermuxAppSharedProperties;
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants;
import com.termux.app.place.Element;
import com.termux.app.terminal.TermuxActivityRootView;
import androidx.core.view.ViewCompat;
import com.termux.app.chrome.ChromeRenderer;
import android.graphics.drawable.Drawable;
import com.google.android.material.color.MaterialColors;
import com.termux.privileged.PrivilegedBackendManager;
// @@IMPORTS@@

/**
 * The status bar's events and state (ADR 0018 §1, wave B step one, D17), moved out of the
 * activity line for line. It reaches the activity only through {@link Host}.
 */
public final class StatusBarController {

    /** What only the activity gives the status bar. */
    public interface Host {
        void applyPlaceStripInset(float expansion);
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
        @Nullable PlaceLayoutStore placeLayoutStore();
        void refreshTerminalWindowBar();
        GeometryScheduler geometry();
        @Nullable com.termux.app.wall.PaneWallController paneWallController();
        TermuxAppSharedPreferences preferences();
        SurfaceEditorController surfaceEditor();
        int chromeMarginDp();
        int chromeMarginDp(@NonNull com.termux.app.place.SeamDress.Surface surface);
        @NonNull com.termux.app.place.ChromeShape chromeShape();
        @NonNull com.termux.app.place.ChromeShape chromeShape( @NonNull com.termux.app.place.SeamDress.Surface surface);
        @NonNull com.termux.app.place.ChromeShape chromeShape(@NonNull PlaceLayout stored);
        @NonNull com.termux.app.place.ChromeShape chromeShape( @NonNull com.termux.app.place.SeamDress.Surface surface, @NonNull PlaceLayout stored);
        @NonNull com.termux.app.chrome.Dress dressOf(@NonNull com.termux.app.wall.PaneWallPage page);
        @NonNull com.termux.app.chrome.Dress dressOf(@NonNull com.termux.app.wall.PageRef page);
        float layoutCornersPx();
        float layoutCornersPx(@NonNull com.termux.app.place.SeamDress.Surface surface);
        int lookPageId(@NonNull com.termux.app.place.SeamDress.Surface surface);
        int resolveSurfaceHorizontalInsetPx(int configuredDp, boolean capsule);
        int terminalLookPageId();
        boolean wallpaperSampleable();
        com.termux.app.place.Crossing crossing();
        @NonNull float[] displayCornerRadiiPx();
        TermuxAppSharedProperties properties();
        void applyTerminalWindowBarBackdropInsets();
        boolean isSplitPanesEnabled();
        com.termux.app.dock.DockRows dockRows();
        TermuxActivityRootView termuxActivityRootView();
        void ensureAiIndicatorController();
        com.termux.app.statusbar.SystemStatsController ensureStatsController();
        @NonNull com.termux.app.statusbar.WeatherController ensureWeatherController();
        void runOnUiThread(@NonNull Runnable action);
        boolean statsConsumerVisible();
        long statsInterval(long normalMs);
        long statsBarIntervalMs();
        long statsCardIntervalMs();
        com.termux.app.statusbar.StatusBarStatSmoother barCpuSmoother();
        com.termux.app.statusbar.StatusBarStatSmoother barMemorySmoother();
        ChromeRenderer chrome();
        @Nullable com.termux.app.statusbar.SystemStatsCardView statsCardView();
        @Nullable com.termux.app.statusbar.SystemStatsController statsController();
        com.termux.app.statusbar.StatusCardHost statusCardHost();
        @Nullable com.termux.app.statusbar.WeatherController weatherController();
        @NonNull android.content.Context context();
        void toggleStatsCard(@NonNull View anchor);
        void toggleWeatherCard(@NonNull View anchor);
        void applyBarMarginEase(@Nullable View host, float insetPx);
        @Nullable @SuppressWarnings("unchecked") <T extends View> T hotView(int id);
        void resetBarMarginEase(@NonNull View host);
        void syncPlaceBar();
        @NonNull com.termux.app.chrome.ChromeEdgeRule.TopLead statusStripLead();
        void markChromeTravelMoved();
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
        if (!com.termux.app.statusbar.StatusBarGesturePolicy.expansionAllowed(mStatusBarEdge)) {
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
        boolean vertical = isStatusBarVertical();
        ViewGroup.LayoutParams params = host.getLayoutParams();
        if (vertical ? params.width != height : params.height != height) {
            int bandBeforePx = bottomStatusBandPx();
            if (vertical) params.width = height;
            else params.height = height;
            host.setLayoutParams(params);
            // A bar along the bottom is a band of the accessory stack, whose height is arithmetic
            // rather than wrap_content — so the stack has to grow with the bar, or the fold would
            // open into a container that never made room for it. By the band's change alone: the
            // whole geometry pass per frame was a full chrome apply per frame, and the fold's
            // settle runs the one pass that lays everything else out
            // (beginStatusBarTerminalResize).
            if (mStatusBarEdge == PlaceLayout.Edge.BOTTOM)
                mHost.growAccessoryStackBy(bottomStatusBandPx() - bandBeforePx);
        }
        int collapsedHeight = targetStatusBarHeightPx(capsule, true);
        int expandedHeight = targetStatusBarHeightPx(capsule, false);
        float expansion = expandedHeight == collapsedHeight ? 0f
            : Math.max(0f, Math.min(1f,
                (height - collapsedHeight) / (float) (expandedHeight - collapsedHeight)));
        com.termux.app.statusbar.StatusBarResizeGeometry.Row rowGeometry = vertical ? null
            : applyInteractiveStatusRowGeometry(height, capsule, collapsedHeight, expandedHeight);
        View columnRow = vertical ? mHost.findViewById(R.id.terminal_status_row) : null;
        if (columnRow != null && columnRow.getLayoutParams() instanceof FrameLayout.LayoutParams) {
            // A column's row does not ride a moving edge: the bar grows sideways and the row
            // simply stands under whatever the open bar puts above it.
            applyStatusColumnRowGeometry((FrameLayout.LayoutParams) columnRow.getLayoutParams(),
                columnRow, expansion < 0.5f);
        }
        com.termux.app.statusbar.StatusBarLensView lens = mHost.findViewById(R.id.terminal_status_lens);
        if (lens != null) lens.setExpansion(expansion);
        mHost.applyPlaceStripInset(expansion);
        View stackedClock = mHost.findViewById(R.id.terminal_status_column_clock);
        if (stackedClock != null && vertical) {
            // The column's clock arrives with the width that makes room for it, rather than
            // sliding: there is nothing above it in a column for it to slide out from under.
            stackedClock.setVisibility(expansion > 0.02f && isStatusClockShown()
                ? View.VISIBLE : View.GONE);
            stackedClock.setAlpha(expansion);
        }
        if (topWidgets != null && !vertical) {
            // The slot stands above the row on both row edges, so what it shows is always the
            // stretch the row has not reached — its own top down to the row's crown. It slides in
            // the way the bar is growing: down out of a top bar, up out of a bottom one.
            float sign = com.termux.app.statusbar.StatusBarGesturePolicy.expandSign(mStatusBarEdge);
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
                mStatusBarSurfaceOutline.setClip(capsule
                    ? com.termux.app.chrome.LiveChromeShape.cardClip(
                        resolveStatusBarCapsuleCornerRadiusPx(height))
                    : mHost.chromeClipOf(com.termux.app.place.ChromeShape.PieceId.STATUS));
                if (host.getOutlineProvider() != mStatusBarSurfaceOutline)
                    host.setOutlineProvider(mStatusBarSurfaceOutline);
                mStatusBarSurfaceOutline.follow(host);
                host.setClipToOutline(mStatusBarSurfaceOutline.clipsCorners());
            }
            host.invalidateOutline();
        }
    }

    private com.termux.app.statusbar.StatusBarResizeGeometry.Row
            applyInteractiveStatusRowGeometry(int surfaceHeight, boolean capsule,
                                              int collapsedHeight, int expandedHeight) {
        float density = mHost.getResources().getDisplayMetrics().density;
        com.termux.app.statusbar.StatusBarEdgeGeometry.RowGeometry folded =
            com.termux.app.statusbar.StatusBarEdgeGeometry.rowGeometry(mStatusBarEdge, capsule,
                true, true, density);
        com.termux.app.statusbar.StatusBarEdgeGeometry.RowGeometry open =
            com.termux.app.statusbar.StatusBarEdgeGeometry.rowGeometry(mStatusBarEdge, capsule,
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
                mStatusBarEdge, surfaceHeight, geometry.height, geometry.top);
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
        if (isStatusBarVertical()) {
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
            && com.termux.app.statusbar.StatusBarGesturePolicy.expansionAllowed(mStatusBarEdge);
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
            || !com.termux.app.statusbar.StatusBarGesturePolicy.expansionAllowed(mStatusBarEdge);
        View host = mHost.findViewById(R.id.terminal_window_bar_host);
        View topWidgets = mHost.findViewById(R.id.terminal_top_widget_area);
        com.termux.app.statusbar.StatusBarSwipeLayout swipeHost = host instanceof
            com.termux.app.statusbar.StatusBarSwipeLayout
            ? (com.termux.app.statusbar.StatusBarSwipeLayout) host : null;
        boolean capsule = mHost.isRoundedStyle(com.termux.app.place.SeamDress.Surface.STATUS);
        int targetHeight = targetStatusBarHeightPx(capsule, collapsed);
        boolean preferenceChanged = isStatusBarCompact() != collapsed;
        boolean geometryChanged = host != null && currentTopStatusBarHeight(host) != targetHeight;
        if (swipeHost != null) swipeHost.setCollapsed(collapsed);
        if (!preferenceChanged && !geometryChanged) {
            if (topWidgets != null) {
                topWidgets.setClipBounds(null);
                topWidgets.setAlpha(1f);
                topWidgets.setTranslationY(0f);
                topWidgets.setVisibility(collapsed || isStatusBarVertical()
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
        if (startHeight <= 0) startHeight = targetStatusBarHeightPx(capsule, !collapsed);
        applyTopStatusBarInteractiveHeight(host, topWidgets, startHeight, capsule);
        int fullDistance = Math.max(1, targetStatusBarHeightPx(capsule, false)
            - targetStatusBarHeightPx(capsule, true));
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
                    topWidgets.setVisibility(collapsed || isStatusBarVertical()
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
            || !com.termux.app.statusbar.StatusBarGesturePolicy.expansionAllowed(mStatusBarEdge)) {
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
            mStatusBarFoldDragStartHeight, towardOpenPx, targetStatusBarHeightPx(capsule, true),
            targetStatusBarHeightPx(capsule, false));
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
        boolean foldable = barShown && mStatusBarEdge == PlaceLayout.Edge.TOP
            && com.termux.app.statusbar.StatusBarGesturePolicy.expansionAllowed(mStatusBarEdge);
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
        int travel = targetStatusBarHeightPx(capsule, false)
            - targetStatusBarHeightPx(capsule, true);
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

    private final com.termux.app.chrome.ChromeShapeOutlineProvider mStatusBarSurfaceOutline =
        new com.termux.app.chrome.ChromeShapeOutlineProvider();

    /** The half whose look the status bar wears now. */
    public int statusLookPage() {
        return mHost.lookPageId(com.termux.app.place.SeamDress.Surface.STATUS);
    }

    /**
     * Whether the status bar wears anything different on Home than on the Workspace: the shape
     * (Style, Corners, Margin) or the look values (ADR 0013).
     */
    public boolean statusDressDiffersAcrossHalves() {
        if (mHost.preferences() == null) return false;
        return !mHost.dressOf(com.termux.app.wall.PaneWallPage.WIDGETS).shape().equals(
                mHost.dressOf(com.termux.app.wall.PaneWallPage.TERMINAL).shape())
            || !com.termux.app.place.HalfLook.read(mHost.preferences(), TermuxAppSharedPreferences.PAGE_WALL)
                .statusEquals(com.termux.app.place.HalfLook.read(mHost.preferences(),
                    mHost.terminalLookPageId()));
    }

    /** Full screen and the screen's corner radii as {@link #applyStatusBarStyle} last read them. */
    private boolean mStatusStyleFullScreen;
    @Nullable private float[] mStatusStyleCornerRadii;

    /**
     * Apply the selected status-bar style to the top window-bar host. Default is the edge-to-edge
     * glass pane (no margins, square corners, glass extended behind the system status bar). Rounded
     * capsule floats the pane: horizontal margins, a gap below the status bar, and a rounded-outline
     * clip so the blur + glass read as the same capsule geometry as the Rounded dock.
     */
    public void applyStatusBarStyle(@NonNull View host) {
        boolean capsule = mHost.isRoundedStyle(com.termux.app.place.SeamDress.Surface.STATUS);
        boolean collapsed = isStatusBarCompact();
        ViewGroup.LayoutParams lp = host.getLayoutParams();
        if (lp instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams mlp = (ViewGroup.MarginLayoutParams) lp;
            int sideMargin = resolveStatusBarHorizontalInsetPx();
            // Extend Rounded away from its own edge without moving the edge it faces the terminal
            // with, or the terminal content beside it.
            int outerMargin = statusBarColumnOuterMarginPx();
            int targetThickness = targetStatusBarHeightPx(capsule, collapsed);
            com.termux.app.statusbar.StatusBarEdgeGeometry.RowGeometry rows =
                com.termux.app.statusbar.StatusBarEdgeGeometry.rowGeometry(mStatusBarEdge,
                    capsule, collapsed, collapsed, mHost.getResources().getDisplayMetrics().density);
            boolean vertical = isStatusBarVertical();
            // A column keeps only its own air from the band beside it. The camera hole it used to
            // start past is the padded content root's now, so the bar is a plain band and the two
            // can never be reckoned from different origins again.
            int columnLeadIn = vertical ? outerMargin : 0;
            int leftMargin = vertical
                ? (mStatusBarEdge == PlaceLayout.Edge.LEFT ? columnLeadIn : 0) : sideMargin;
            int rightMargin = vertical
                ? (mStatusBarEdge == PlaceLayout.Edge.RIGHT ? columnLeadIn : 0) : sideMargin;
            // And it runs the canvas band, not the display: a side stack flanks the terminal
            // between the top and bottom stacks, so a column reaches exactly as far as the canvas
            // beside it and cancels nothing. It used to be a sibling of the padded content root and
            // pulled itself out to both ends of the screen with negative margins for that.
            // In Full screen a Floating bar's edge is the screen's own: it floats as far from it
            // as from the sides, and its corners clear the screen's rounded ones.
            mStatusStyleFullScreen = mHost.properties() != null && mHost.properties().isUsingFullScreen();
            mStatusStyleCornerRadii = mHost.displayCornerRadiiPx();
            boolean floatsOnScreenEdge = capsule && mStatusStyleFullScreen;
            float barRadius = resolveStatusBarCapsuleCornerRadiusPx(targetThickness);
            int topMargin = vertical ? 0
                : (mStatusBarEdge == PlaceLayout.Edge.TOP
                    ? com.termux.app.chrome.FullScreenCornerPolicy.floatingEdgeGapPx(
                        floatsOnScreenEdge, outerMargin, sideMargin, barRadius,
                        Math.max(mHost.displayCornerRadiiPx()[0], mHost.displayCornerRadiiPx()[1]))
                    : 0);
            int bottomMargin = vertical ? 0
                : (mStatusBarEdge == PlaceLayout.Edge.BOTTOM ? outerMargin : 0);
            boolean sizeStale = !isStatusBarFoldInteractive()
                && (vertical ? mlp.width != targetThickness : mlp.height != targetThickness);
            if (mlp.leftMargin != leftMargin || mlp.rightMargin != rightMargin
                || mlp.topMargin != topMargin || mlp.bottomMargin != bottomMargin || sizeStale) {
                mlp.leftMargin = leftMargin;
                mlp.rightMargin = rightMargin;
                mlp.topMargin = topMargin;
                mlp.bottomMargin = bottomMargin;
                if (!isStatusBarFoldInteractive()) {
                    if (vertical) mlp.width = targetThickness;
                    else mlp.height = targetThickness;
                }
                host.setLayoutParams(mlp);
                // Laid out at the Margin the crossing eased it to: it draws unscaled from the
                // same pass, so the bar keeps the width it landed at.
                if (!mHost.crossing().seam().sliding()) resetStatusMarginEase(host);
            }
            View topWidgets = mHost.findViewById(R.id.terminal_top_widget_area);
            if (topWidgets != null && topWidgets.getLayoutParams() != null) {
                int targetWidgetHeight = rows.slot;
                ViewGroup.LayoutParams widgetParams = topWidgets.getLayoutParams();
                if (widgetParams.height != targetWidgetHeight) {
                    widgetParams.height = targetWidgetHeight;
                    topWidgets.setLayoutParams(widgetParams);
                }
                if (!isStatusBarFoldInteractive()) {
                    topWidgets.setAlpha(1f);
                    topWidgets.setTranslationY(0f);
                    // A column carries the stacked clock instead of the row's widget slot.
                    topWidgets.setVisibility(collapsed || vertical ? View.GONE : View.VISIBLE);
                }
            }
            View stackedClock = mHost.findViewById(R.id.terminal_status_column_clock);
            if (stackedClock != null && !isStatusBarFoldInteractive()) {
                stackedClock.setAlpha(1f);
                stackedClock.setVisibility(vertical && !collapsed && isStatusClockShown()
                    ? View.VISIBLE : View.GONE);
                // The column's glass starts where the canvas does, so its clock starts at the top
                // of the column itself — the system status bar is already above all of it.
                if (stackedClock.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
                    ViewGroup.MarginLayoutParams clockParams =
                        (ViewGroup.MarginLayoutParams) stackedClock.getLayoutParams();
                    int clockTop = 0;
                    if (clockParams.topMargin != clockTop) {
                        clockParams.topMargin = clockTop;
                        stackedClock.setLayoutParams(clockParams);
                    }
                }
            }

            // While a spring or animator drives the pane, applyFrame's
            // applyInteractiveStatusRowGeometry() is the sole writer of status-row geometry —
            // the same ownership rule the host-height and top-slot writes above already follow.
            // A live collapse/expand animator owns the row's position, as does the finger folding
            // the bar; a refresh mid-animation must not re-anchor it to its rest rule and park it
            // where the gesture is not.
            boolean interactiveGeometryOwnsRow = isStatusBarFoldInteractive();

            // Keep the bottom chip corners inside the capsule's 26dp outline. At the former 4dp
            // inset, the rounded host clip intersected the session and weather chip backgrounds.
            View statusRow = mHost.findViewById(R.id.terminal_status_row);
            if (statusRow != null && !interactiveGeometryOwnsRow && vertical
                && statusRow.getLayoutParams() instanceof FrameLayout.LayoutParams) {
                applyStatusColumnRowGeometry((FrameLayout.LayoutParams) statusRow.getLayoutParams(),
                    statusRow, collapsed);
            } else if (statusRow != null && !interactiveGeometryOwnsRow
                && statusRow.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
                ViewGroup.MarginLayoutParams rowParams =
                    (ViewGroup.MarginLayoutParams) statusRow.getLayoutParams();
                // A column's foot clearance goes back to nothing here: 10dp of it inside the
                // row's 24dp would squash the chips the way the strip's own padding once did.
                if (statusRow.getPaddingBottom() != 0 || statusRow.getPaddingTop() != 0) {
                    statusRow.setPaddingRelative(0, 0, 0, 0);
                }
                // Keep only enough inset for the capsule clip and move the side content inward
                // below where the curve becomes tight.
                int targetEdgeMargin = rows.foot;
                int targetRowHeight = rows.rowHeight;
                // The row keeps the screen edge its bar stands on: a bottom bar's row stays at
                // the foot of the panel and the clock's band grows upward above it, which is the
                // reading order a top bar has always had. Mirroring it is what used to lift a
                // bottom bar's row clear off the screen's edge the moment the bar opened.
                int targetGravity = collapsed ? Gravity.CENTER_VERTICAL : Gravity.BOTTOM;
                int targetTopMargin = 0;
                int targetBottomMargin = targetEdgeMargin;
                boolean rowChanged = rowParams.bottomMargin != targetBottomMargin
                    || rowParams.topMargin != targetTopMargin
                    || rowParams.height != targetRowHeight
                    || rowParams.width != ViewGroup.LayoutParams.MATCH_PARENT;
                if (rowParams instanceof FrameLayout.LayoutParams) {
                    FrameLayout.LayoutParams frameParams = (FrameLayout.LayoutParams) rowParams;
                    rowChanged |= frameParams.gravity != targetGravity;
                    frameParams.gravity = targetGravity;
                }
                if (rowChanged) {
                    rowParams.width = ViewGroup.LayoutParams.MATCH_PARENT;
                    rowParams.topMargin = targetTopMargin;
                    rowParams.bottomMargin = targetBottomMargin;
                    rowParams.height = targetRowHeight;
                    statusRow.setLayoutParams(rowParams);
                }
            }

            View sessions = mHost.findViewById(R.id.terminal_sessions_indicator);
            if (sessions != null
                && sessions.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
                ViewGroup.MarginLayoutParams sessionParams =
                    (ViewGroup.MarginLayoutParams) sessions.getLayoutParams();
                int targetStartMargin = statusBarContentEdgeInsetPx(capsule);
                int targetSessionHeight = Math.round(mHost.dpToPx(collapsed
                    ? capsule ? 18 : 20 : 20));
                int targetSessionWidth = sessions instanceof
                    com.termux.app.statusbar.SessionsIndicatorView
                    && ((com.termux.app.statusbar.SessionsIndicatorView) sessions).isShowingSessionNumber()
                    ? targetSessionHeight : ViewGroup.LayoutParams.WRAP_CONTENT;
                boolean sessionLayoutChanged =
                    sessionParams.getMarginStart() != (vertical ? 0 : targetStartMargin)
                    || sessionParams.topMargin != (vertical ? targetStartMargin : 0)
                    || sessionParams.height != targetSessionHeight
                    || sessionParams.width != targetSessionWidth;
                // The chip's height is row geometry too; the interactive writer owns it as well.
                if (sessionLayoutChanged && !interactiveGeometryOwnsRow) {
                    // The gap before the badge is the bar's leading end: the row's start, the
                    // column's top. Kept on the start in a column it pushes the badge sideways.
                    sessionParams.setMarginStart(vertical ? 0 : targetStartMargin);
                    sessionParams.topMargin = vertical ? targetStartMargin : 0;
                    sessionParams.height = targetSessionHeight;
                    sessionParams.width = targetSessionWidth;
                    if (sessionParams instanceof android.widget.LinearLayout.LayoutParams) {
                        ((android.widget.LinearLayout.LayoutParams) sessionParams).gravity =
                            vertical ? Gravity.CENTER_HORIZONTAL : Gravity.CENTER_VERTICAL;
                    }
                    sessions.setLayoutParams(sessionParams);
                }
                if (sessions instanceof com.termux.app.statusbar.SessionsIndicatorView) {
                    ((com.termux.app.statusbar.SessionsIndicatorView) sessions).setSurfaceStyle(
                        capsule, resolveStatusIndicatorCornerRadiusPx(targetThickness, capsule));
                }
            }

            com.termux.app.terminal.TerminalWindowBar windows =
                mHost.findViewById(R.id.terminal_window_bar);
            if (windows != null) {
                windows.setSurfaceStyle(capsule,
                    resolveStatusIndicatorCornerRadiusPx(targetThickness, capsule));
            }
            com.termux.app.statusbar.StatusBarWindowColumn windowColumn =
                mHost.findViewById(R.id.terminal_status_window_column);
            if (windowColumn != null) {
                windowColumn.setChipRadiusPx(
                    resolveStatusIndicatorCornerRadiusPx(targetThickness, capsule));
            }

            View statusWidgets = mHost.findViewById(R.id.terminal_status_widgets);
            if (statusWidgets != null) {
                int targetEndPadding = statusBarContentEdgeInsetPx(capsule);
                int end = vertical ? 0 : targetEndPadding;
                int bottom = vertical ? targetEndPadding : 0;
                if (statusWidgets.getPaddingEnd() != end
                    || statusWidgets.getPaddingBottom() != bottom) {
                    statusWidgets.setPaddingRelative(statusWidgets.getPaddingStart(),
                        statusWidgets.getPaddingTop(), end, bottom);
                }
            }
            if (host instanceof com.termux.app.statusbar.StatusBarSwipeLayout) {
                ((com.termux.app.statusbar.StatusBarSwipeLayout) host).setCollapsed(collapsed);
            }
        }
        applyStatusBarOutline(host);
    }

    /**
     * The row inside a bar standing in a column: under the stacked clock while the bar is open, and
     * as long as the column leaves it. The system status bar is not in this sum any more — the
     * column stands in the canvas band, which already starts below it.
     */
    public void applyStatusColumnRowGeometry(@NonNull FrameLayout.LayoutParams params,
                                              @NonNull View statusRow, boolean collapsed) {
        int top = Math.round(mHost.dpToPx(8)) + statusColumnClockHeightPx(collapsed);
        int length = Math.max(Math.round(mHost.dpToPx(48)), statusColumnContentLengthPx() - top);
        // The bar's foot is the lens's: the place below peeks half past it. The stats stop short
        // of that icon, the way the row's content stops short of the one past its end.
        int foot = Math.round(mHost.dpToPx(com.termux.app.statusbar.PlaceContentStrip.LENS_WIDTH_DP));
        if (statusRow.getPaddingBottom() != foot || statusRow.getPaddingTop() != 0) {
            statusRow.setPaddingRelative(0, 0, 0, foot);
        }
        boolean changed = params.gravity != (Gravity.TOP | Gravity.START)
            || params.topMargin != top || params.bottomMargin != 0
            || params.height != length
            || params.width != ViewGroup.LayoutParams.MATCH_PARENT;
        if (!changed) return;
        params.gravity = Gravity.TOP | Gravity.START;
        params.topMargin = top;
        params.bottomMargin = 0;
        params.width = ViewGroup.LayoutParams.MATCH_PARENT;
        params.height = length;
        statusRow.setLayoutParams(params);
    }

    /**
     * How tall the stacked clock stands, measured rather than assumed: it is the one piece of the
     * column whose height is type, and type is what a raised font scale grows.
     */
    private int statusColumnClockHeightPx(boolean collapsed) {
        if (collapsed || !isStatusClockShown()) return 0;
        View clock = mHost.findViewById(R.id.terminal_status_column_clock);
        if (clock == null) return 0;
        int width = targetStatusBarHeightPx(mHost.isRoundedStyle(com.termux.app.place.SeamDress.Surface.STATUS), false);
        clock.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        return clock.getMeasuredHeight() + Math.round(mHost.dpToPx(6));
    }

    /** The margin a floating column keeps from the edge it stands on; docked hugs it. */
    public int statusBarColumnOuterMarginPx() {
        return mHost.isRoundedStyle(com.termux.app.place.SeamDress.Surface.STATUS) ? Math.round(mHost.dpToPx(2)) : 0;
    }

    /**
     * How far down its column the bar's content reaches: the whole of it. The bar used to give the
     * lower half of a column back to a rail or an extra-keys column holding the same side; they
     * stand beside it on the edge stack now, so nothing shares its length with it.
     *
     * <p>Measured down the canvas band the column stands in, which is the whole of the bar: a side
     * stack flanks the terminal alone, so no part of the column runs under a system bar or behind
     * the dock any more.
     */
    private int statusColumnContentLengthPx() {
        View band = mHost.findViewById(R.id.terminal_canvas_band);
        if (band != null && band.getHeight() > 0) return band.getHeight();
        // Before the band's first layout, the container it sits in is the nearest honest answer.
        View container = mHost.findViewById(R.id.terminal_root_container);
        return container == null ? 0 : Math.max(0, container.getHeight());
    }

    /**
     * One outline clips every status-pane layer, including live blur and wallpaper frost. The
     * model says what it is: under Docked the status bar's slice of the joined frame (square) or
     * of the top edge card (square at the screen, its bottom corners rounded at Corners); under
     * Floating its own card at Corners.
     */
    private void applyStatusBarOutline(@NonNull View host) {
        boolean changed = mStatusBarSurfaceOutline.setClip(
            mHost.chromeClipOf(com.termux.app.place.ChromeShape.PieceId.STATUS));
        if (host.getOutlineProvider() != mStatusBarSurfaceOutline)
            host.setOutlineProvider(mStatusBarSurfaceOutline);
        // The host draws no background, so nothing else rebuilds its outline when the layout pass
        // after a Style change resizes it: follow the size, or the card's width outlives the Style.
        mStatusBarSurfaceOutline.follow(host);
        host.setClipToOutline(mStatusBarSurfaceOutline.clipsCorners());
        if (changed || host.getOutlineProvider() == mStatusBarSurfaceOutline)
            host.invalidateOutline();
    }

    public int resolveStatusBarHorizontalInsetPx() {
        return mHost.resolveSurfaceHorizontalInsetPx(mHost.preferences() == null
            ? TermuxPreferenceConstants.TERMUX_APP.DEFAULT_SURFACE_HORIZONTAL_INSET
            : TermuxAppSharedPreferences.clampSurfaceHorizontalInset(mHost.chromeMarginDp(com.termux.app.place.SeamDress.Surface.STATUS)),
            mHost.isRoundedStyle(com.termux.app.place.SeamDress.Surface.STATUS));
    }

    /**
     * Internal row inset only; the floating capsule's outer screen margin remains unchanged.
     *
     * <p>The bottom row — sessions chip on the left, status widgets on the right — is bottom-gravity
     * and therefore sits in the surface's bottom corners. Whatever radius rounds those corners eats
     * into the row, so the inset that keeps the content clear of the clip has to follow the radius
     * rather than be a fixed number. Half the radius is the arc's worst-case encroachment over the
     * row's height, which is what both styles grow by.</p>
     *
     * <p>They start from different places because their radii do. Docked is square by default, so
     * its 3dp baseline is the whole story at rest and every bit of radius the user dials in is new
     * encroachment. Floating is a card whose corners are already rounded at rest — 26dp, the auto
     * radius — and its 8dp baseline was measured against exactly that, so only radius beyond the
     * default is encroachment the baseline does not already answer. That keeps a stock Floating
     * surface looking precisely as it did while a raised radius stops clipping the chips.</p>
     */
    private int statusBarContentEdgeInsetPx(boolean capsule) {
        boolean collapsed = isStatusBarCompact();
        float radiusPx = capsule
            ? resolveStatusBarCapsuleCornerRadiusPx(targetStatusBarHeightPx(true, collapsed))
            : statusPieceCornerRadiusPx();
        float baselineRadiusPx = capsule
            ? mHost.dpToPx(TermuxPreferenceConstants.TERMUX_APP.STATUS_AUTO_CORNER_RADIUS_MAX_DP)
            : 0f;
        return DockLayoutPolicy.statusBarContentEdgeInsetPx(capsule, radiusPx, baselineRadiusPx,
            mHost.getResources().getDisplayMetrics().density);
    }

    /** The largest corner the model gives the status bar under Docked: Corners at its bottom, or none. */
    private float statusPieceCornerRadiusPx() {
        com.termux.app.place.ChromeShape.Piece piece =
            mHost.chromeShape(com.termux.app.place.SeamDress.Surface.STATUS).piece(com.termux.app.place.ChromeShape.PieceId.STATUS);
        if (piece == null) return 0f;
        return Math.max(Math.max(piece.corners.topLeft, piece.corners.topRight),
            Math.max(piece.corners.bottomRight, piece.corners.bottomLeft));
    }

    /**
     * The shape the status row's two chips wear: the sessions indicator and the window pills.
     *
     * <p>They are content inside the status surface rather than surfaces of their own, so they are
     * not part of the Base cascade and have their own knob. Until it is touched they follow the bar
     * — square while Docked, the capsule's radius while Floating — which is what they always did.
     * Once it is set, that shape holds in both styles, clamped so a chip can never round past half
     * of its own height.
     */
    public float resolveStatusIndicatorCornerRadiusPx(int surfaceHeightPx, boolean capsule) {
        int configured = mHost.preferences() == null
            ? TermuxPreferenceConstants.TERMUX_APP.DEFAULT_STATUS_INDICATOR_CORNER_RADIUS
            : mHost.preferences().getStatusIndicatorCornerRadius();
        if (configured < 0)
            return capsule ? resolveStatusBarCapsuleCornerRadiusPx(surfaceHeightPx) : 0f;
        // No clamp of our own: both chips draw rounded rects, which already stop at half of the
        // shorter side, and the two chips are not the same height — clamping here would cap the
        // taller one at the shorter one's pill.
        return mHost.dpToPx(configured);
    }

    /** A Floating card's radius for a surface this tall: Corners, never past half the surface. */
    public float resolveStatusBarCapsuleCornerRadiusPx(int surfaceHeightPx) {
        return Math.min(mHost.layoutCornersPx(com.termux.app.place.SeamDress.Surface.STATUS), Math.max(0, surfaceHeightPx) / 2f);
    }

    /**
     * How thick the bar is across its own axis: the height of a row along the top or the bottom,
     * the width of a column down a side.
     */
    public int targetStatusBarHeightPx(boolean capsule, boolean collapsed) {
        // Minimal mode needs no case of its own: the minimal layout puts the bar away, or keeps it,
        // like any layout does (applyStatusBarEdge), and a bar it keeps is a bar with a band.
        return com.termux.app.statusbar.StatusBarEdgeGeometry.thicknessPx(mStatusBarEdge, capsule,
            collapsed, mHost.getResources().getDisplayMetrics().density);
    }

    /** The bar stands in a column rather than a row, so its content runs down the screen. */
    public boolean isStatusBarVertical() {
        return com.termux.app.statusbar.StatusBarEdgeGeometry.isVertical(mStatusBarEdge);
    }

    /** @see #getEffectiveExtraKeysBlurRadius() */
    public int getEffectiveStatusBarBlurRadius() {
        if (mHost.preferences() == null || !mHost.wallpaperSampleable()) return 0;
        return Math.max(0, mHost.preferences().getStatusBarBlurRadius(statusLookPage()));
    }

    /** Whether the status bar shows its clock (the Status bar card's Clock toggle). */
    public boolean isStatusClockShown() {
        return mHost.preferences() == null || mHost.preferences().isStatusClockShown();
    }

    /** The edge the bar stands on, as {@link #applyStatusBarEdge} last placed it. */
    @NonNull
    public PlaceLayout.Edge statusBarEdge() {
        return mStatusBarEdge;
    }

    /** The bar's surface outline, which the crossing's margin ease grows sideways. */
    @NonNull
    public com.termux.app.chrome.ChromeShapeOutlineProvider statusBarSurfaceOutline() {
        return mStatusBarSurfaceOutline;
    }

    /** Whether the last style pass dressed the bar for full screen ({@link #applyStatusBarStyle}). */
    public boolean statusStyleFullScreen() {
        return mStatusStyleFullScreen;
    }

    /** The display's corner radii the last style pass dressed the bar with; null before one. */
    @Nullable
    public float[] statusStyleCornerRadii() {
        return mStatusStyleCornerRadii;
    }

    /** The edge the bar is standing on right now; every piece of its geometry reads this. */
    @NonNull private PlaceLayout.Edge mStatusBarEdge = PlaceLayout.Edge.TOP;
    /** Whether the bar has been stood on that edge at least once since the views were built. */
    private boolean mStatusBarEdgeApplied;

    /**
     * Where the status bar stands. Every arrangement keeps a bar — it moves rather than hides, so
     * the wall's paging gesture always has one.
     *
     * <p>A row along the top lives in the content column and takes its slice of the terminal's
     * height. A row along the bottom is a band of the dock's own stack, standing where its order
     * puts it among the dock's rows — on the dock's sheet of glass between them, on a sheet of its
     * own as the band touching the canvas. A column
     * down a side lives beside the padded content root, in the band the apps rail and the extra
     * keys column already share, and the content is inset from it by the same seam. One host, one
     * set of views: the bar is moved rather than rebuilt, so everything bound to it by id follows.
     */
    public void applyStatusBarEdge(@NonNull PlaceLayout layout) {
        View host = mHost.findViewById(R.id.terminal_window_bar_host);
        if (!(host instanceof ViewGroup)) return;
        // Put away in the Layout editor: the bar and its fold go, and the content takes the band.
        if (layout.slot(Element.STATUS).hidden) {
            if (mHost.dockRows().statusBarShown()) {
                cancelTopStatusBarAnimator();
                finishStatusBarTerminalResize(endTopStatusBarDrag(), true);
                // Through Dock rows, which counts a bar along the bottom as a band of the stack.
                mHost.dockRows().setStatusBarShown(false);
                mHost.applyTerminalWindowBarBackdropInsets();
                if (mHost.termuxActivityRootView() != null) ViewCompat.requestApplyInsets(mHost.termuxActivityRootView());
            }
            return;
        }
        if (!mHost.dockRows().statusBarShown() && mHost.isSplitPanesEnabled()) {
            mHost.dockRows().setStatusBarShown(true);
            mHost.applyTerminalWindowBarBackdropInsets();
            setTopStatusBarCollapsed(isStatusBarCompact(), false);
            mHost.refreshTerminalWindowBar();
            if (mHost.termuxActivityRootView() != null) ViewCompat.requestApplyInsets(mHost.termuxActivityRootView());
        }
        PlaceLayout.Edge edge = layout.slot(Element.STATUS).edge;
        boolean edgeChanged = edge != mStatusBarEdge;
        if (!edgeChanged && mStatusBarEdgeApplied) {
            // The edge has not moved, but a column is measured against the screen's height, which
            // a turn changes; the style pass is what re-derives that.
            if (isStatusBarVertical()) applyStatusBarStyle(host);
            return;
        }
        if (edgeChanged) {
            // A fold under way on the old edge — animated or under a finger — is dropped; the new
            // edge is laid out at rest below. The drag's lease is returned with it.
            cancelTopStatusBarAnimator();
            finishStatusBarTerminalResize(endTopStatusBarDrag(), true);
        }
        mStatusBarEdge = edge;
        boolean first = !mStatusBarEdgeApplied;
        mStatusBarEdgeApplied = true;
        // The XML already stands the bar along the top; a place that wants it there costs nothing.
        if (first && edge == PlaceLayout.Edge.TOP) return;
        // Which stack the host now stands in is the arrangement walk's answer; this only gives it
        // the band that edge asks for and turns its contents to face the right way.
        com.termux.app.statusbar.StatusBarEdgeArrangement.band(host, edge,
            targetStatusBarHeightPx(mHost.isRoundedStyle(com.termux.app.place.SeamDress.Surface.STATUS), isStatusBarCompact()));
        com.termux.app.statusbar.StatusBarEdgeArrangement.apply((ViewGroup) host, edge);
        if (host instanceof com.termux.app.statusbar.StatusBarSwipeLayout) {
            com.termux.app.statusbar.StatusBarSwipeLayout swipeHost =
                (com.termux.app.statusbar.StatusBarSwipeLayout) host;
            swipeHost.setEdge(edge);
            swipeHost.setExpansionAllowed(
                com.termux.app.statusbar.StatusBarGesturePolicy.expansionAllowed(edge));
        }
        // The bar is not the system status bar's glass anywhere but along the top; everywhere else
        // the terminal simply starts under the system bar, as it does with the bar folded today.
        mHost.applyTerminalWindowBarBackdropInsets();
        setTopStatusBarCollapsed(isStatusBarCompact(), false);
        mHost.refreshTerminalWindowBar();
        // The bar's column is a band on its edge like the rail's; the insets pass re-derives what
        // the content gives up to the whole stack.
        if (mHost.termuxActivityRootView() != null) ViewCompat.requestApplyInsets(mHost.termuxActivityRootView());
    }

    /**
     * The band a status bar standing along the bottom claims inside the dock's stack: the bar
     * itself plus the air the style keeps under it. Zero on every other edge, and zero while the
     * window bar is switched off.
     *
     * <p>Read off the host's own layout params rather than off the style's target, because the
     * expand/collapse animator is the writer of that height while it runs and the stack has to
     * follow it frame by frame instead of jumping to where it will end up.
     */
    public int bottomStatusBandPx() {
        if (mStatusBarEdge != PlaceLayout.Edge.BOTTOM || !mHost.dockRows().statusBarShown()) return 0;
        View host = mHost.findViewById(R.id.terminal_window_bar_host);
        if (host == null) return 0;
        ViewGroup.LayoutParams params = host.getLayoutParams();
        int thicknessPx = params != null && params.height > 0
            ? params.height : targetStatusBarHeightPx(mHost.isRoundedStyle(com.termux.app.place.SeamDress.Surface.STATUS), isStatusBarCompact());
        return thicknessPx + statusBarColumnOuterMarginPx();
    }

    /**
     * The band a status bar along the bottom claims at rest in a Style, as {@link
     * #bottomStatusBandPx} measures the one on screen: the bar at its Style's thickness and the
     * air a Floating bar keeps under it. Zero while the window bar is switched off. A half's rest
     * reads it in its own Style (Stack layout's StatusBand).
     */
    public int bottomStatusBandAtRestPx(boolean floating) {
        if (!mHost.isSplitPanesEnabled()) return 0;
        return com.termux.app.statusbar.StatusBarEdgeGeometry.thicknessPx(PlaceLayout.Edge.BOTTOM,
                floating, isStatusBarCompact(), mHost.getResources().getDisplayMetrics().density)
            + (floating ? Math.round(mHost.dpToPx(2)) : 0);
    }

    /**
     * The status bar as the layout just applied has it. Whether it is there at all is the
     * layout's ({@link #applyStatusBarEdge} puts it away or keeps it), so this only settles what a
     * bar that stays looks like: folded or open as the orientation remembers, with its swipe, the
     * same in the minimal layout as in the normal one, and drawn in full.
     */
    public void applyMinimalStatusChrome() {
        View host = mHost.findViewById(R.id.terminal_window_bar_host);
        if (host instanceof com.termux.app.statusbar.StatusBarSwipeLayout) {
            com.termux.app.statusbar.StatusBarSwipeLayout swipeHost =
                (com.termux.app.statusbar.StatusBarSwipeLayout) host;
            swipeHost.setExpansionAllowed(
                com.termux.app.statusbar.StatusBarGesturePolicy.expansionAllowed(mStatusBarEdge));
        }
        if (host != null && host.getAlpha() != 1f) host.setAlpha(1f);
        View row = mHost.findViewById(R.id.terminal_status_row);
        if (row != null && row.getVisibility() != View.VISIBLE) row.setVisibility(View.VISIBLE);
        View lens = mHost.findViewById(R.id.terminal_status_lens);
        if (lens != null && lens.getVisibility() != View.VISIBLE) lens.setVisibility(View.VISIBLE);
        // The fold's own path sizes the bar, under the resize lease that gives the panes one
        // row-and-column update for the change instead of one per pass.
        setTopStatusBarCollapsed(isStatusBarCompact(), false);
    }

    /**
     * Hears the notifications the dock badges its icons with, while the status bar's
     * notifications indicator is on; null while it is off.
     */
    @Nullable private com.termux.app.launcher.notifications.LauncherNotificationBadgeStore.Listener
        mStatusNotificationsListener;

    /**
     * The status bar's notifications mark ({@code status_widget_notifications}): a bell and the
     * count while notifications wait, the same ones the dock badges ({@link
     * com.termux.app.statusbar.StatusNotificationsMark}); gone with none, or with the indicator
     * off. Listens to the badge store only while the indicator is on.
     */
    private void syncStatusNotificationsMark() {
        boolean enabled = mHost.preferences() != null && mHost.preferences().isStatusWidgetNotificationsEnabled();
        if (enabled && mStatusNotificationsListener == null) {
            mStatusNotificationsListener = packages -> mHost.runOnUiThread(this::showStatusNotificationsMark);
            // Adding it reports the packages at once, which draws the mark.
            com.termux.app.launcher.notifications.LauncherNotificationBadgeStore.addListener(
                mStatusNotificationsListener);
            return;
        }
        if (!enabled && mStatusNotificationsListener != null) {
            com.termux.app.launcher.notifications.LauncherNotificationBadgeStore.removeListener(
                mStatusNotificationsListener);
            mStatusNotificationsListener = null;
        }
        showStatusNotificationsMark();
    }

    private void showStatusNotificationsMark() {
        com.termux.app.statusbar.StatusBarWidgetView mark =
            mHost.findViewById(R.id.terminal_status_widget_notifications);
        com.termux.app.statusbar.MaterialDotSeparatorView dot =
            mHost.findViewById(R.id.terminal_status_dot_notifications);
        if (mark == null || dot == null) return;
        boolean enabled = mHost.preferences() != null && mHost.preferences().isStatusWidgetNotificationsEnabled();
        int count = enabled ? com.termux.app.launcher.notifications.LauncherNotificationBadgeStore
            .countFor(com.termux.app.launcher.notifications.LauncherNotificationBadgeStore
                .getActivePackages()) : 0;
        boolean shown = com.termux.app.statusbar.StatusNotificationsMark.shown(enabled, count);
        if (shown && mark.getTag() == null) {
            mark.setTag("wired");
            mark.setColorRole(com.termux.app.statusbar.StatusBarWidgetView.ColorRole.SECONDARY);
            mark.setIconGlyph(com.termux.app.statusbar.StatusNotificationsMark.GLYPH);
        }
        if (shown)
            mark.setValue(com.termux.app.statusbar.StatusNotificationsMark.label(count));
        boolean anyStatBefore = mHost.preferences() != null && (mHost.preferences().isStatusWidgetCpuEnabled()
            || mHost.preferences().isStatusWidgetRamEnabled() || mHost.preferences().isStatusWidgetWeatherEnabled());
        int visibility = shown ? View.VISIBLE : View.GONE;
        boolean changed = mark.getVisibility() != visibility;
        mark.setVisibility(visibility);
        dot.setVisibility(shown && anyStatBefore ? View.VISIBLE : View.GONE);
        dot.setColorRole(com.termux.app.statusbar.StatusBarWidgetView.ColorRole.SECONDARY);
        if (changed) syncStatusBarInk();
    }

    // ---- What the status bar's content is drawn in, measured against what it is standing on ----

    /** The status widgets and their dots, in the order the row lays them out. */
    private static final int[] STATUS_INK_WIDGET_IDS = {
        R.id.terminal_status_widget_cpu, R.id.terminal_status_widget_ram,
        R.id.terminal_status_widget_weather, R.id.terminal_status_widget_notifications,
        R.id.terminal_status_widget_ai, R.id.terminal_status_widget_mouse,
    };

    private static final int[] STATUS_INK_DOT_IDS = {
        R.id.terminal_status_dot_cpu_ram, R.id.terminal_status_dot_ram_weather,
        R.id.terminal_status_dot_notifications, R.id.terminal_status_dot_ai,
        R.id.terminal_status_dot_mouse,
    };

    /** The bar's own rect on screen: one measurement, and every ink in the bar comes out of it. */
    private final android.graphics.Rect mStatusInkRect = new android.graphics.Rect();

    /**
     * What every piece of the status bar's own content is drawn in, resolved from one measurement
     * of what the bar is actually standing on.
     *
     * <p>The bar carries four contrast tiers at once — the stats' labels are body text, their icons
     * and the place marks are graphics, the separator dots are decoration — and a band can only
     * carry one veil. So the band is resolved <em>once</em> here, at its strictest tier and in the
     * role colour of its most saturated content, and every other ink on it comes from
     * {@link com.termux.app.chrome.ChromeInk#inkOn} at its own looser tier, which asks for no more
     * veil. Anything else in this activity that stands on the status bar should read the resolution
     * this method takes rather than call {@code onGlass} for the band a second time: the last
     * caller in a frame would otherwise decide the veil for all of them.</p>
     *
     * <p>The band is measured on the pane this content is laid out in — the top pane's glass,
     * shared with the window chips — and not on the strip that continues that glass under the
     * system status bar, which nothing stands on. {@code ChromeInk} owns that mapping; this method
     * names the band and asks its question.</p>
     *
     * <p>Silently does nothing until the bar has been laid out and a wallpaper sample exists; the
     * accessory render pass runs this again, and the chrome asks for a pass of its own whenever a
     * band's veil moves.</p>
     */
    public void syncStatusBarInk() {
        com.termux.app.chrome.ChromeInk ink = mHost.chrome().ink();
        if (!ink.bandRect(com.termux.app.chrome.GlassBackdropCache.Band.STATUS_BAR, mStatusInkRect))
            return;
        int primary = statusInkSeed(com.termux.app.statusbar.StatusBarWidgetView.ColorRole.PRIMARY);
        // A real ink pair: the primary role toned dark (about 30) for pale glass and pale (about
        // 85) for dark glass, so the band can flip with the chrome's polarity rather than buying
        // a veil to keep one ink legible.
        double primaryTone = com.termux.app.theme.SchemeTone.tone(primary);
        int darkPrimary = com.termux.app.theme.SchemeTone.toneShift(primary, 30d - primaryTone);
        int palePrimary = com.termux.app.theme.SchemeTone.toneShift(primary, 85d - primaryTone);
        com.termux.app.chrome.OnGlass.Resolution band = ink.onGlass(
            com.termux.app.chrome.GlassBackdropCache.Band.STATUS_BAR, mStatusInkRect,
            darkPrimary, palePrimary, com.termux.app.chrome.OnGlass.TARGET_BODY_TEXT);

        // A muted widget is the AI glyph's few seconds of afterlife: the state is said by the
        // neutral role, and the fade is allowed only as far as the backdrop can carry it.
        int muted = com.termux.app.statusbar.StatusBarInk.inkAtAlpha(band.surface,
            MaterialColors.getColor(mHost.context(), com.termux.shared.R.attr.termuxColorOnSurfaceVariant,
                primary),
            com.termux.app.statusbar.StatusBarInk.MUTED_ALPHA,
            com.termux.app.chrome.OnGlass.TARGET_LARGE_TEXT);

        for (int widgetId : STATUS_INK_WIDGET_IDS) {
            com.termux.app.statusbar.StatusBarWidgetView widget = mHost.findViewById(widgetId);
            if (widget == null) continue;
            int seed = statusInkSeed(widget.colorRole());
            widget.setInk(
                ink.inkOn(band, seed, seed, com.termux.app.chrome.OnGlass.TARGET_BODY_TEXT),
                ink.inkOn(band, seed, seed, com.termux.app.chrome.OnGlass.TARGET_LARGE_TEXT),
                muted);
        }
        for (int dotId : STATUS_INK_DOT_IDS) {
            com.termux.app.statusbar.MaterialDotSeparatorView dot = mHost.findViewById(dotId);
            if (dot == null) continue;
            int seed = statusInkSeed(dot.colorRole());
            dot.setInk(ink.inkOn(band, seed, seed,
                com.termux.app.chrome.OnGlass.TARGET_DECORATION));
        }
        com.termux.app.statusbar.StatusBarLensView lens = mHost.findViewById(R.id.terminal_status_lens);
        if (lens != null) lens.setBandSurface(band.surface);
        com.termux.app.statusbar.SessionsIndicatorView sessions =
            mHost.findViewById(R.id.terminal_sessions_indicator);
        if (sessions != null) sessions.setBandInk(ink, band);
        // What stands on the same band takes its ink from the same resolution.
        com.termux.app.statusbar.PinnedNotificationsView pinned =
            mHost.findViewById(R.id.terminal_pinned_notifications);
        if (pinned != null) pinned.setBandSurface(band.surface);
        com.termux.app.statusbar.MediaWidgetView media = mHost.findViewById(R.id.terminal_media_widget);
        if (media != null) media.onThemeChanged();
        com.termux.app.terminal.TerminalClockWidget clock = mHost.findViewById(R.id.terminal_clock_widget);
        if (clock != null) {
            clock.setBandInk(band.surface, band.ink);
            clock.onThemeChanged();
        }
    }

    /** The role colour a status tier is written in; its hue is the tier, and the toning keeps it. */
    private int statusInkSeed(@NonNull com.termux.app.statusbar.StatusBarWidgetView.ColorRole role) {
        return com.termux.app.statusbar.StatusBarWidgetView.seedFor(mHost.context(), role);
    }

    // ---- Trailing status widgets (CPU / RAM / weather) + their anchored detail cards ----

    /** Apply widget visibility from preferences, wire taps once, and drive the data controllers. */
    public void updateStatusWidgets() {
        com.termux.app.statusbar.StatusBarWidgetView cpu = mHost.findViewById(R.id.terminal_status_widget_cpu);
        com.termux.app.statusbar.StatusBarWidgetView ram = mHost.findViewById(R.id.terminal_status_widget_ram);
        com.termux.app.statusbar.StatusBarWidgetView weather = mHost.findViewById(R.id.terminal_status_widget_weather);
        com.termux.app.statusbar.MaterialDotSeparatorView cpuRamDot =
            mHost.findViewById(R.id.terminal_status_dot_cpu_ram);
        com.termux.app.statusbar.MaterialDotSeparatorView ramWeatherDot =
            mHost.findViewById(R.id.terminal_status_dot_ram_weather);
        // The CPU reading needs privileged access; without it the widget hides rather than show a
        // dash, and comes back through mPrivilegedAvailabilityListener. The backend itself is
        // brought up (and retried) by refreshPrivilegedBackendIfNeeded() on every resume.
        boolean cpuOn = com.termux.app.statusbar.StatusStatsClusterPolicy.cpuShown(
            mHost.preferences() != null && mHost.preferences().isStatusWidgetCpuEnabled(),
            PrivilegedBackendManager.getInstance().isPrivilegedAvailableForUi());
        boolean ramOn = mHost.preferences() != null && mHost.preferences().isStatusWidgetRamEnabled();
        boolean weatherOn = mHost.preferences() != null && mHost.preferences().isStatusWidgetWeatherEnabled();
        View widgetSlot = mHost.findViewById(R.id.terminal_top_widget_area);
        if (widgetSlot instanceof com.termux.app.statusbar.TopPaneWidgetSlot) {
            ((com.termux.app.statusbar.TopPaneWidgetSlot) widgetSlot).setMediaEnabled(
                mHost.preferences() == null || mHost.preferences().isStatusWidgetMediaEnabled());
        }

        if (cpu != null) {
            cpu.setVisibility(cpuOn ? View.VISIBLE : View.GONE);
            cpu.dressAsCpu();
            // Seeded from the smoother every pass: onStatsUpdated skips hidden widgets, so a value
            // published while this one was off or covered is otherwise only repainted when the
            // reading next changes \u2014 and before the first sample this is what puts "--" on screen
            // instead of an empty slot.
            if (cpuOn) cpu.setValue(mHost.barCpuSmoother().text());
            if (cpu.getTag() == null) {
                cpu.setTag("wired");
                cpu.setOnClickListener(v -> mHost.toggleStatsCard(v));
            }
        }
        if (ram != null) {
            ram.setVisibility(ramOn ? View.VISIBLE : View.GONE);
            ram.dressAsRam();
            if (ramOn) ram.setValue(mHost.barMemorySmoother().text());
            if (ram.getTag() == null) {
                ram.setTag("wired");
                ram.setOnClickListener(v -> mHost.toggleStatsCard(v));
            }
        }
        if (weather != null) {
            weather.setVisibility(weatherOn ? View.VISIBLE : View.GONE);
            weather.setColorRole(com.termux.app.statusbar.StatusBarWidgetView.ColorRole.SECONDARY);
            if (weather.getTag() == null) {
                weather.setTag("wired");
                weather.setIconAnimation(
                    com.termux.app.statusbar.WeatherController.animationAssetFor(0, true));
                weather.setValue("--");
                weather.setOnClickListener(v -> mHost.toggleWeatherCard(v));
            }
        }

        if (cpuRamDot != null) {
            boolean show = com.termux.app.statusbar.StatusStatsClusterPolicy
                .cpuRamDotVisible(cpuOn, ramOn, weatherOn);
            cpuRamDot.setVisibility(show ? View.VISIBLE : View.GONE);
            // The dot takes the tier it leads into: RAM, or the weather when RAM is off. Both are
            // secondary now, so AI alone carries the tertiary role.
            cpuRamDot.setColorRole(
                com.termux.app.statusbar.StatusBarWidgetView.ColorRole.SECONDARY);
        }
        if (ramWeatherDot != null) {
            boolean show = com.termux.app.statusbar.StatusStatsClusterPolicy
                .ramWeatherDotVisible(ramOn, weatherOn);
            ramWeatherDot.setVisibility(show ? View.VISIBLE : View.GONE);
            ramWeatherDot.setColorRole(
                com.termux.app.statusbar.StatusBarWidgetView.ColorRole.SECONDARY);
        }

        if (cpuOn || ramOn) {
            boolean cardShowing = mHost.statusCardHost().isShowing() && mHost.statsCardView() != null;
            mHost.ensureStatsController().start(
                mHost.statsInterval(cardShowing ? mHost.statsCardIntervalMs() : mHost.statsBarIntervalMs()),
                cardShowing);
            // The sampler pauses while nothing shows its numbers (StatsPollCadence).
            mHost.statsController().setConsumerVisible(mHost.statsConsumerVisible());
        } else if (mHost.statsController() != null) {
            mHost.statsController().stop();
        }

        if (weatherOn) {
            mHost.ensureWeatherController().refreshIfStale();
        } else if (mHost.weatherController() != null) {
            mHost.weatherController().stop();
        }

        mHost.ensureAiIndicatorController();
        syncStatusNotificationsMark();
        // The tiers just moved, and a tier is which hue this widget's ink is toned from.
        syncStatusBarInk();
    }

    @NonNull
    public com.termux.app.statusbar.StatusCardHost.StyleProvider statusCardStyleProvider() {
        return new com.termux.app.statusbar.StatusCardHost.StyleProvider() {
            @Override
            public Drawable cardBackground() {
                com.google.android.material.shape.MaterialShapeDrawable card =
                    com.termux.app.material.M3.surface(mHost.context(),
                        com.google.android.material.R.attr.shapeAppearanceCornerLarge,
                        com.termux.app.material.M3.surfaceContainer(mHost.context()));
                card.setStroke(mHost.getResources().getDimension(
                        com.google.android.material.R.dimen.m3_comp_outlined_card_outline_width),
                    com.termux.app.material.M3.outlineVariant(mHost.context()));
                return card;
            }

            @Override
            public float cornerRadiusPx() {
                return com.termux.app.material.M3.shape(mHost.context(),
                    com.google.android.material.R.attr.shapeAppearanceCornerLarge)
                    .getTopLeftCornerSize().getCornerSize(new android.graphics.RectF(0, 0, 1, 1));
            }

            @Override
            public float contentInsetPx() {
                return mHost.dpToPx(12);
            }
        };
    }

    /** The window is going away: the process-wide badge store lets go of the bar's mark. */
    public void removeStatusNotificationsListener() {
        com.termux.app.launcher.notifications.LauncherNotificationBadgeStore.removeListener(
            mStatusNotificationsListener);
        mStatusNotificationsListener = null;
    }

    /** Where the status bar was last drawn from its rest as it travels with the Workspace's page. */
    private float mStatusTravelXPx;

    /**
     * The status bar drawn where the Workspace's page stands (SeamTravel.workspaceScreenXPx) on
     * any frame the crossing says it rides (SeamCrossing.rideBar), with the strip behind the system
     * bar when it continues the bar's glass: a translation, and the bar dressed for that page
     * rather than the one the wall has committed to, so its chips do not change while it is on
     * screen. Whether it rides.
     */
    public boolean syncStatusTravel(@NonNull com.termux.app.place.PlaceChromeTravel.Frame frame,
                                     @NonNull com.termux.app.place.SeamCrossing.Answers seam) {
        View host = mHost.hotView(R.id.terminal_window_bar_host);
        // The crossing rides the bar (Crossing#rideBar), through its Bar adapter.
        boolean rides = mHost.crossing().rideBar(frame, host != null
            && host.getVisibility() == View.VISIBLE
            && statusBarEdge() == PlaceLayout.Edge.TOP && mHost.paneWallController() != null,
            mHost.paneWallController() == null ? 0 : mHost.paneWallController().wall().getWidth());
        if (rides) {
            mHost.markChromeTravelMoved();
            // The bar carries its place content with it: the strip does not also pan inside it.
            View strip = mHost.hotView(R.id.terminal_status_place_content);
            if (strip != null) {
                if (strip.getTranslationX() != 0f) strip.setTranslationX(0f);
                if (strip.getTranslationY() != 0f) strip.setTranslationY(0f);
                if (strip.getAlpha() != 1f) strip.setAlpha(1f);
                if (strip.getClipBounds() != null) strip.setClipBounds(null);
            }
        }
        if (seam.barRedressed()) mHost.syncPlaceBar();
        // The strip behind the system bar stands with the bar on every riding frame, whatever
        // pass of this frame dressed or showed it.
        if (rides) syncStatusStripTravel();
        return rides;
    }

    /**
     * The status bar, and the strip behind the system bar while it continues the bar's inset,
     * drawn at the Margin the crossing eases them to (SeamCrossing.Answers#barMarginShare): both
     * stay laid out at the committed page's Margin and are drawn at the inset between the two
     * pages' resolved insets, so no frame lays anything out. The bar stretches only its plain
     * glass; its chips, clock and readings keep their size and move with their edge
     * ({@link #applyBarMarginEase}). The strip is plain glass and is stretched whole. The frame the wall lands on draws them at the arriving page's inset, which the
     * landing's re-dress then lays out as it resets the scale ({@link #resetStatusMarginEase}).
     * Nothing while the Margin holds.
     */
    public void syncStatusMarginEase(@NonNull com.termux.app.place.SeamCrossing.Answers seam) {
        float share = seam.barMarginShare();
        if (Float.isNaN(share) || isStatusBarVertical()) return;
        com.termux.app.place.SeamCrossing crossing = mHost.crossing().seam();
        boolean capsule = mHost.isRoundedStyle(com.termux.app.place.SeamDress.Surface.STATUS);
        float here = mHost.resolveSurfaceHorizontalInsetPx(
            TermuxAppSharedPreferences.clampSurfaceHorizontalInset(crossing.committedBarMarginDp()),
            capsule);
        float there = mHost.resolveSurfaceHorizontalInsetPx(
            TermuxAppSharedPreferences.clampSurfaceHorizontalInset(crossing.otherBarMarginDp()),
            capsule);
        float insetPx = here + (there - here) * share;
        View host = mHost.hotView(R.id.terminal_window_bar_host);
        mHost.applyBarMarginEase(host, insetPx);
        // The strip eases with the bar only where it wears the bar's inset (not a dock sheet's).
        View strip = mHost.hotView(R.id.terminal_status_bar_background);
        if (host != null && strip != null && strip.getWidth() == host.getWidth())
            applyStatusMarginEase(strip, insetPx);
    }

    /** One of the bar's views scaled about its centre so it stands {@code insetPx} from each side. */
    private static void applyStatusMarginEase(@Nullable View view, float insetPx) {
        if (view == null || view.getWidth() <= 0 || !(view.getParent() instanceof View)) return;
        View parent = (View) view.getParent();
        float room = parent.getWidth() - parent.getPaddingLeft() - parent.getPaddingRight();
        // The inset it is laid out at, read from where it stands: its margins are even.
        float laidInsetPx = (room - view.getWidth()) / 2f;
        float scale = (view.getWidth() + 2f * (laidInsetPx - insetPx)) / view.getWidth();
        if (!(scale > 0f)) return;
        float pivot = view.getWidth() / 2f;
        if (view.getPivotX() != pivot) view.setPivotX(pivot);
        if (view.getScaleX() != scale) view.setScaleX(scale);
    }

    /** Both of the bar's views drawn at their laid-out width again. */
    public void resetStatusMarginEase() {
        resetStatusMarginEase(mHost.findViewById(R.id.terminal_window_bar_host));
        resetStatusMarginEase(mHost.findViewById(R.id.terminal_status_bar_background));
    }

    /** {@code view} drawn at its laid-out width again; the bar's host with everything it eased. */
    public void resetStatusMarginEase(@Nullable View view) {
        if (view == null) return;
        if (view.getId() == R.id.terminal_window_bar_host) mHost.resetBarMarginEase(view);
        else if (view.getScaleX() != 1f) view.setScaleX(1f);
    }

    /**
     * The status bar, and the strip behind the system bar while it continues the bar's glass, at
     * {@code x} from their rest. Their glass samples the wallpaper where it is drawn, and a
     * transform alone redraws nothing, so the bar's backdrop is re-aimed with the move.
     */
    public void applyStatusTravelX(float x) {
        boolean moved = x != mStatusTravelXPx;
        mStatusTravelXPx = x;
        if (moved) {
            View host = mHost.hotView(R.id.terminal_window_bar_host);
            if (host != null) host.setTranslationX(x);
        }
        // The strip follows on every call, moved or not: one shown since the last move (dressed
        // for the Workspace as the ride began) must not stand at its rest while the bar arrives.
        syncStatusStripTravel();
        if (!moved) return;
        View backdrop = mHost.hotView(R.id.terminal_window_bar_wallpaper_backdrop);
        if (backdrop != null && backdrop.getVisibility() == View.VISIBLE) backdrop.invalidate();
        View sideBackdrop = mHost.hotView(R.id.terminal_window_bar_wallpaper_backdrop_side);
        if (sideBackdrop != null && sideBackdrop.getVisibility() == View.VISIBLE)
            sideBackdrop.invalidate();
    }

    /**
     * The strip behind the system bar where the bar stands now: with it while the strip is the
     * bar's own glass running up under the system bar (the lead it was last dressed for, not one
     * read live mid-crossing, which can still answer for the half the bar left), at rest
     * otherwise (StatusStripRide). Its backdrop samples where it is drawn, so it is re-aimed.
     */
    public void syncStatusStripTravel() {
        View strip = mHost.hotView(R.id.terminal_status_bar_background);
        if (strip == null) return;
        float stripX = com.termux.app.place.StatusStripRide.stripX(mStatusTravelXPx,
            strip.getVisibility() == View.VISIBLE,
            mHost.statusStripLead() == com.termux.app.chrome.ChromeEdgeRule.TopLead.WINDOW_BAR);
        if (strip.getTranslationX() == stripX) return;
        strip.setTranslationX(stripX);
        View backdrop = mHost.hotView(R.id.terminal_status_bar_wallpaper_backdrop);
        if (backdrop != null && backdrop.getVisibility() == View.VISIBLE) backdrop.invalidate();
    }

    // @@BODY@@
}
