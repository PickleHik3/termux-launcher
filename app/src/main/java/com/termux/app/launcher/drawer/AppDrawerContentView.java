package com.termux.app.launcher.drawer;

import android.content.Context;
import android.os.SystemClock;
import android.view.Choreographer;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewParent;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.NestedScrollingParent3;
import androidx.core.view.NestedScrollingParentHelper;
import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.termux.app.Spring;
import com.termux.app.SuggestionBarView;
import com.termux.app.launcher.data.LauncherAppDataProvider;
import com.termux.app.launcher.drawer.AppDrawerTransitionGeometry.Frame;
import com.termux.app.launcher.model.LauncherAppEntry;

import java.util.Collections;
import java.util.List;

/**
 * The open drawer's contents: the search pill and the vertical app grid, plus the arbitration that
 * decides whether a downward drag scrolls that grid or puts the drawer away.
 *
 * <p><b>Why the grid, and not the plane, owns the stream.</b> The plane claims a close drag at
 * 1.15x touch slop; {@code RecyclerView} starts scrolling at 1.0x and immediately calls
 * {@code requestDisallowInterceptTouchEvent(true)}, which kills the plane's interceptor for the rest
 * of that stream. Whoever wins is therefore a function of how fast the finger moved — a slow drag
 * reaches the grid and a flick reaches the plane. So the plane does not compete: when the down point
 * belongs to the grid ({@link #ownsPoint}) the plane steps aside and every delta is resolved here,
 * through the nested-scroll channel, where the parent gets first refusal without stealing anything.
 * Nothing is ever intercepted, so the grid never receives an {@code ACTION_CANCEL} and no scroll is
 * cut in half.
 *
 * <p><b>Every threshold lives in {@link AppDrawerCloseArmingPolicy}.</b> This class samples the
 * three facts the policy needs at {@code ACTION_DOWN} — over the grid, at the top, scrollable — and
 * then does what it is told with each delta: consume it and drive the close, let the child scroll,
 * or damp it into overpull. There is no second reading of the scroll position mid-gesture, because
 * re-reading "am I at the top" is precisely the bug that closes a drawer at the end of an ordinary
 * flick.
 *
 * <p><b>There is a third touch category.</b> The A-Z column down the right edge is neither the grid
 * nor chrome, and a scrub <em>is</em> a sustained downward drag in the same place at the same speed as
 * a close, so the two can only be separated by where the finger went down. That decision is made once
 * per stream, at {@code ACTION_DOWN}, by {@link AppDrawerTouchRegions} — never revisited, never
 * inferred from motion. A {@code COLUMN} down leaves the arming policy disarmed and
 * {@link #mGestureActive} false, because the recycler never sees that stream at all.
 *
 * <p><b>Overpull</b> is exponentially damped toward {@link #OVERPULL_MAX_DP} and released on a
 * short-lived {@link Choreographer} loop, with the platform's own overscroll turned off so the two
 * cannot stack. That is also the gesture's currency: a release that reached far enough, or was
 * thrown hard enough, arms the next pull, and only an armed pull closes.
 *
 * <p>Nothing on any path here may touch the accessory stack — no {@code setTerminalToolbarHeight},
 * no {@code applyAccessoryGeometryIfNeeded}, no {@code requestAccessoryGeometrySync}. That geometry
 * is deliberately frozen for the life of the transition, and the visible cost of thawing it is a
 * dock that jumps on close.
 */
public final class AppDrawerContentView extends FrameLayout
    implements NestedScrollingParent3, Choreographer.FrameCallback,
    AppDrawerPlaneView.CloseDragGate, AppDrawerSearchController.ResultsListener,
    AppDrawerSearchPillView.Callbacks, AppDrawerRopeColumnView.Callbacks {

    /** Raw close-drag reports, in the plane's own vocabulary. The controller interprets them. */
    public interface Callbacks {

        /** The grid has claimed the stream as a close; {@code downRawY} is the ACTION_DOWN point. */
        void onContentCloseDragBegin(float downRawY);

        void onContentCloseDragUpdate(float rawY);

        /** @param velocityPxPerSec release velocity, positive downwards */
        void onContentCloseDragEnd(float velocityPxPerSec);

        void onContentCloseDragCancel();
    }

    /** Ceiling on the damped overpull, in dp. The raw pull is unbounded; the travel is not. */
    public static final float OVERPULL_MAX_DP = 96f;

    private static final float PILL_MARGIN_H_DP = 16f;
    private static final float PILL_MARGIN_TOP_DP = 12f;
    private static final float PILL_TO_GRID_DP = 10f;
    /**
     * Strip kept clear at the bottom for B-5's settings affordance. It is chrome, not grid: a drag
     * that starts there closes the drawer, which is what keeps a close gesture reachable even when
     * the grid is armed against one.
     */
    private static final float BOTTOM_BAND_DP = 64f;

    private final AppDrawerSearchPillView mPill;
    private final RecyclerView mGrid;
    private final AppDrawerRopeColumnView mColumn;
    private final GridLayoutManager mLayoutManager;
    private final AppDrawerAppsAdapter mAdapter;
    private final AppDrawerCloseArmingPolicy mPolicy = new AppDrawerCloseArmingPolicy();
    private final NestedScrollingParentHelper mParentHelper = new NestedScrollingParentHelper(this);
    /**
     * Stiff and lightly damped: the release has to look like the grid snapping back rather than
     * settling, and it is the only motion in the drawer that runs while a finger is off the glass.
     */
    private final Spring mOverpullSpring = new Spring(0f, 900f, 60f);
    /**
     * The scrub highlight's strength: 1 while a finger is on a letter, sprung back to 0 on release
     * with the house arrival ({@code 2·√420 ≈ 41}, ~260ms). It goes <em>on</em> instantly rather
     * than springing up, because the letter under the finger at {@code ACTION_DOWN} is a decision
     * and not a gesture — the dim is the answer to a tap as much as to a drag.
     */
    private final Spring mScrubSpring = new Spring(0f, 420f, 41f);
    private final float mDensity;
    private final float mColumnWidthPx;
    private final float[] mPlaneOffset = new float[2];

    @Nullable private SuggestionBarView mDock;
    @Nullable private Callbacks mCallbacks;
    @Nullable private LauncherAppDataProvider mProvider;
    @Nullable private AppDrawerSearchController mSearch;
    @Nullable private Runnable mRevealListener;
    @Nullable private Runnable mKeyboardRequestListener;
    @Nullable private Runnable mFrameRequestListener;
    /** Rebuilt once per submitted list; the column's letters and the scrub's scroll targets. */
    @NonNull private AppDrawerSectionIndex mSectionIndex = AppDrawerSectionIndex.build(null);

    /** Re-registered on every catalogue change: {@code invalidate()} drops pending callbacks. */
    private final Runnable mCatalogueCallback = this::pushCatalogue;

    private boolean mInteractive;
    /** True when {@code ACTION_DOWN} landed on the grid, i.e. when this view owns the stream. */
    private boolean mDownOverGrid;
    /** True between an {@code ACTION_DOWN} here and the gesture's one settling stop. */
    private boolean mGestureActive;
    /** True between a claimed close and its end, so the duplicate stop report is idempotent. */
    private boolean mNestedCloseActive;
    /** True while an {@code ACTION_CANCEL} is being dispatched, i.e. the stream was taken away. */
    private boolean mStreamCancelled;
    private boolean mSearchRevealRequested;
    private float mDownRawY;
    private float mLastRawY;
    private float mOverpullRawPx;
    private float mOverpullTranslationPx;
    /** Last nested pre-fling velocity, in scroll units; negative is a downward finger. */
    private float mFlingVelocityY;
    private boolean mFrameScheduled;
    private long mLastFrameTimeNanos;
    /** The letter the highlight is keyed to. Outlives the finger, for the length of the fade. */
    private char mScrubLetter = '\0';
    /**
     * True while at least one attached cell has been written away from 1/1. The per-frame walk runs
     * for every frame of every transition, so without this the no-scrub case would touch every
     * attached child on every frame to tell it what it already is.
     */
    private boolean mHighlightWritten;

    public AppDrawerContentView(@NonNull Context context) {
        this(context, null);
    }

    /**
     * @param dock the launcher row the cells borrow their icons, tint, launch ladder and context
     *     menu from; null builds a grid that renders labels only, which is what the unit tests use
     */
    public AppDrawerContentView(@NonNull Context context, @Nullable SuggestionBarView dock) {
        super(context);
        mDock = dock;
        mDensity = context.getResources().getDisplayMetrics().density;
        setClipChildren(false);
        setClipToPadding(false);

        mPill = new AppDrawerSearchPillView(context);
        mPill.setCallbacks(this);
        LayoutParams pillParams = new LayoutParams(LayoutParams.MATCH_PARENT,
            Math.round(AppDrawerSearchPillView.HEIGHT_DP * mDensity));
        pillParams.leftMargin = dp(PILL_MARGIN_H_DP);
        pillParams.rightMargin = dp(PILL_MARGIN_H_DP);
        pillParams.topMargin = dp(PILL_MARGIN_TOP_DP);
        addView(mPill, pillParams);

        mAdapter = new AppDrawerAppsAdapter(dock);
        mLayoutManager = new GridLayoutManager(context, AppDrawerGridMetrics.MIN_COLUMNS);
        mGrid = new RecyclerView(context);
        mGrid.setLayoutManager(mLayoutManager);
        mGrid.setAdapter(mAdapter);
        mGrid.setHasFixedSize(true);
        mGrid.setItemViewCacheSize(AppDrawerGridMetrics.MIN_COLUMNS * 2);
        // The spring below is the only overscroll this surface has; the platform's glow (and, on
        // RecyclerView 1.2+, its stretch) would otherwise sit on top of it.
        mGrid.setOverScrollMode(OVER_SCROLL_NEVER);
        // Positions change wholesale on every query, so item animations would cross-fade one app
        // into another. There is nothing to animate between two unrelated lists.
        mGrid.setItemAnimator(null);
        mGrid.setClipToPadding(false);
        mGrid.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                // A context menu is anchored to a cell view. Scroll far enough and that view is
                // recycled under a menu still floating where it used to be.
                if (dx != 0 || dy != 0) dismissContextPopups();
            }
        });
        mColumnWidthPx = AppDrawerRopeMetrics.resolveColumnWidthPx(mDensity);
        LayoutParams gridParams = new LayoutParams(LayoutParams.MATCH_PARENT,
            LayoutParams.MATCH_PARENT);
        gridParams.topMargin = pillParams.topMargin + pillParams.height + dp(PILL_TO_GRID_DP);
        gridParams.bottomMargin = dp(BOTTOM_BAND_DP);
        // The letters are not an overlay: the grid gives up exactly the strip's width so no cell
        // ever sits under one, and the column count the controller resolves is computed from the
        // same subtraction.
        gridParams.rightMargin = Math.round(mColumnWidthPx);
        addView(mGrid, gridParams);

        mColumn = new AppDrawerRopeColumnView(context);
        mColumn.setDock(dock);
        mColumn.setCallbacks(this);
        // Added after the grid so the letters paint over it — which they do transiently, because the
        // rope's lean carries them out of their own strip while the column is fading in.
        LayoutParams columnParams = new LayoutParams(Math.round(mColumnWidthPx),
            LayoutParams.MATCH_PARENT, Gravity.END);
        columnParams.topMargin = gridParams.topMargin;
        columnParams.bottomMargin = gridParams.bottomMargin;
        addView(mColumn, columnParams);
    }

    // ------------------------------------------------------------------ wiring

    public void setCallbacks(@Nullable Callbacks callbacks) {
        mCallbacks = callbacks;
    }

    /**
     * The dock the cells borrow from, for callers that build the content before the row exists.
     * Additive to the drag contract; {@link #bind} does not carry it because the row is not part of
     * the drawer's data path.
     */
    public void setDock(@Nullable SuggestionBarView dock) {
        mDock = dock;
        // The column borrows the dock's text colour and its row-haptics preference, so it has to be
        // told about a dock that arrived after the content was built.
        mColumn.setDock(dock);
    }

    /**
     * Notified when {@link #getRevealFraction()} would answer differently, so the controller can
     * retarget the search-keyboard reveal on its own spring.
     */
    public void setRevealListener(@Nullable Runnable onRevealTargetChanged) {
        mRevealListener = onRevealTargetChanged;
    }

    /**
     * Run when the pill is tapped and there is nowhere to type: the host summons the system IME
     * (without a focus change) for the drawer's fallback intake channel.
     */
    public void setSearchKeyboardRequestListener(@Nullable Runnable onKeyboardRequested) {
        mKeyboardRequestListener = onKeyboardRequested;
    }

    /**
     * Run when the drawer's effects need frames the controller's loop is not currently running.
     *
     * <p>Mirrors {@link #setRevealListener}: the controller owns the only {@link Choreographer}
     * callback the transition has, and a scrub on a drawer that has finished settling has to be able
     * to restart it — otherwise the first letter dims and nothing after it does.
     */
    public void setFrameRequestListener(@Nullable Runnable onFrameRequested) {
        mFrameRequestListener = onFrameRequested;
    }

    private void requestFrames() {
        Runnable listener = mFrameRequestListener;
        if (listener != null) listener.run();
    }

    /**
     * Interactivity follows the drawer's open state and nothing else. Driven by {@code p} instead,
     * a close drag that started on the grid would have the stream yanked out from under it halfway
     * through; and a closed drawer that stayed interactive is a full-screen invisible grid eating
     * the terminal's touches.
     */
    public void setInteractive(boolean interactive) {
        if (mInteractive == interactive) return;
        mInteractive = interactive;
        if (!interactive) {
            cancelCellLongPresses();
            stopOverpullSpring();
            mPolicy.disarm();
            // A scrub left in flight is a finger's worth of state on a surface that has stopped
            // answering fingers: the cells it dimmed would stay dimmed, and the holders among them
            // would go back to the pool at 0.28 alpha.
            clearScrub();
        }
    }

    public boolean isInteractive() {
        return mInteractive;
    }

    /** Columns, cell and icon size. Re-resolved per open, never cached across configurations. */
    public void setMetrics(@NonNull AppDrawerGridMetrics metrics) {
        mLayoutManager.setSpanCount(Math.max(1, metrics.columns));
        // Two rows of holders past the viewport: enough that a fling never rebinds visible cells,
        // small enough that the shared icon cache is not asked to hold a second screenful.
        mGrid.setItemViewCacheSize(Math.max(1, metrics.columns) * 2);
        mAdapter.setMetrics(metrics);
    }

    /** The drawer surface's corner radius, passed through to the pill, which clamps it. */
    public void setSurfaceRadiusPx(float radiusPx) {
        mPill.setSurfaceRadiusPx(radiusPx);
    }

    /**
     * Binds the data path. Deliberately preserves the query: a rebind happens on every open and on
     * every configuration change, and a drawer that forgot what was typed on a rotation would be
     * the rotation's fault and look like the search's.
     */
    public void bind(@Nullable LauncherAppDataProvider provider,
                     @NonNull AppDrawerSearchController search) {
        mProvider = provider;
        mSearch = search;
        search.setResultsListener(this);
        onAppCatalogChanged();
        // Rebinding leaves the query alone but never the scroll: the grid is shown from its top
        // every time it opens, whatever the last session left behind.
        applyResults(search.results(), true);
    }

    /**
     * Re-reads the catalogue and re-drives the warm-up.
     *
     * <p>Idempotent, and idempotent on purpose: {@code LauncherAppDataProvider.invalidate()} clears
     * {@code pendingRefreshCallbacks}, so a one-shot callback registered just before a package
     * change is silently dropped — and a drawer that trusted it would sit on an empty grid until it
     * was closed and reopened. Every call re-registers, and the callback only ever pushes the
     * provider's current answer.
     */
    public void onAppCatalogChanged() {
        LauncherAppDataProvider provider = mProvider;
        if (provider == null) return;
        pushCatalogue();
        provider.warmAsync(mCatalogueCallback);
    }

    private void pushCatalogue() {
        LauncherAppDataProvider provider = mProvider;
        AppDrawerSearchController search = mSearch;
        if (provider == null || search == null) return;
        // Sorted before it reaches the search, not after: getAllApps() appends work and clone
        // entries after the primary user's sorted block, and an A-Z index over that tail points at
        // the wrong position for every letter that also has a profile app. Sorting here is what
        // makes each letter one contiguous run, which is the whole premise of both the section
        // index and the highlight.
        search.setCatalogue(AppDrawerSectionIndex.sortByLabel(provider.getAllApps()));
    }

    /** Empties the query and puts the grid back to the top. For a drawer that has closed. */
    public void resetSearch() {
        AppDrawerSearchController search = mSearch;
        mSearchRevealRequested = false;
        if (search != null) {
            search.reset();
        } else {
            applyResults(Collections.emptyList(), true);
        }
        notifyRevealTarget();
    }

    public void disarm() {
        mPolicy.disarm();
    }

    public boolean hasQuery() {
        AppDrawerSearchController search = mSearch;
        return search != null && search.hasQuery();
    }

    /** @return true when a non-empty query was cleared, i.e. when Back has already been spent */
    public boolean clearQueryIfPresent() {
        AppDrawerSearchController search = mSearch;
        if (search == null || !search.hasQuery()) return false;
        return search.clearQuery();
    }

    /**
     * The search-keyboard reveal target: 1 while there is something to type into the pill for, 0
     * otherwise. The controller owns the spring that chases it; this is only the target.
     */
    public float getRevealFraction() {
        return mSearchRevealRequested || hasQuery() ? 1f : 0f;
    }

    /** Launches the first ranked result, as Enter on any of the three intake channels does. */
    public boolean launchFirstResult() {
        AppDrawerSearchController search = mSearch;
        SuggestionBarView dock = mDock;
        LauncherAppEntry first = search == null ? null : search.firstResult();
        if (dock == null || first == null) return false;
        return dock.launchEntryFromDrawer(firstCellView(), first);
    }

    @Nullable
    private View firstCellView() {
        RecyclerView.ViewHolder holder = mGrid.findViewHolderForAdapterPosition(0);
        if (holder instanceof AppDrawerAppsAdapter.Cell) {
            return ((AppDrawerAppsAdapter.Cell) holder).icon;
        }
        return null;
    }

    // ------------------------------------------------------------------ results

    @Override
    public void onSearchResultsChanged(@NonNull List<LauncherAppEntry> results,
                                       boolean queryChanged) {
        applyResults(results, queryChanged);
    }

    private void applyResults(@NonNull List<LauncherAppEntry> results, boolean queryChanged) {
        AppDrawerSearchController search = mSearch;
        mPill.setQuery(search == null ? "" : search.query(), search == null ? 0 : search.caret());
        // The list identity changed under whatever menu was open, and every cell it was anchored to
        // is about to be rebound.
        dismissContextPopups();
        cancelCellLongPresses();
        // One pass over the list, once per submitted list rather than once per scrub frame. Built
        // even while a query is up, where its scroll targets would be a lie — a ranked list is
        // ordered by match quality, not by letter — because the column is inactive there and nothing
        // can ask it for one.
        mSectionIndex = AppDrawerSectionIndex.build(results);
        mAdapter.submit(results, mSectionIndex);
        applyColumnLetters();
        // The list identity changed under the finger, and the scroll target the scrub was driving
        // no longer means the same app.
        clearScrub();
        if (queryChanged) {
            mGrid.scrollToPosition(0);
            // A different list is a different scroll: an arming earned against the previous one is
            // no longer a promise about anything.
            mPolicy.disarm();
            stopOverpullSpring();
        }
        if (!hasQuery()) mSearchRevealRequested = false;
        notifyRevealTarget();
    }

    private void notifyRevealTarget() {
        Runnable listener = mRevealListener;
        if (listener != null) listener.run();
    }

    private void dismissContextPopups() {
        SuggestionBarView dock = mDock;
        if (dock != null) dock.dismissContextPopups();
    }

    /**
     * Cancels a pending long press on every live cell.
     *
     * <p>The dock's own {@code cancelPendingContextLongPresses()} walks {@code SuggestionBarView}'s
     * children, and drawer cells are not among them — they live in the plane. Without this, a close
     * drag that begins while a finger is resting on a cell leaves the long press armed, and the menu
     * opens over a drawer that is already on its way out.
     */
    private void cancelCellLongPresses() {
        mGrid.cancelLongPress();
        for (int i = 0; i < mGrid.getChildCount(); i++) {
            View child = mGrid.getChildAt(i);
            if (child != null) child.cancelLongPress();
        }
    }

    // ------------------------------------------------------------------ A-Z column and scrub

    /**
     * Pushes the letter set at the column and decides whether it is a scrubber at all.
     *
     * <p>A non-empty query deactivates it: the ranked list is ordered by match quality, so its
     * letters are not contiguous and an index over it would scroll to the wrong place. Fewer than two
     * letters deactivates it too — a one-letter alphabet is a decoration that eats a close drag.
     */
    private void applyColumnLetters() {
        int letterCount = mSectionIndex.letterCount();
        char[] letters = new char[letterCount];
        for (int i = 0; i < letterCount; i++) {
            letters[i] = mSectionIndex.letterAt(i);
        }
        mColumn.setLetters(letters);
        mColumn.setActive(!hasQuery() && letterCount >= 2);
    }

    /** True while a finger is on the column. Not true during the release fade. */
    public boolean isScrubbing() {
        return mColumn.isScrubbing();
    }

    /** True when the strip is a scrubber rather than chrome. */
    public boolean isColumnActive() {
        return mColumn.isActive();
    }

    /** The strip's width, which is also the grid's right margin. */
    public float getColumnWidthPx() {
        return mColumnWidthPx;
    }

    @Override
    public void onScrubLetterChanged(char letter) {
        mScrubLetter = letter;
        int position = mSectionIndex.firstPositionForLetter(letter);
        if (position >= 0 && position < mAdapter.getItemCount()) {
            // Not smoothScrollToPosition: a smooth scroll would still be animating toward the last
            // letter when the finger reached the next one, and the two would fight for the length of
            // the scrub. The jump also fires onScrolled, which dismisses any context popup anchored
            // to a cell that is about to be recycled.
            mLayoutManager.scrollToPositionWithOffset(position, 0);
        }
        // Full strength immediately. The letter under the finger at ACTION_DOWN is an answer, and a
        // dim that ramped up over 260ms would make a tap on a letter look like a missed tap.
        mScrubSpring.value = 1f;
        mScrubSpring.target = 1f;
        mScrubSpring.vel = 0f;
        applyScrubHighlight();
        requestFrames();
    }

    @Override
    public void onScrubEnded() {
        // The letter is kept until the fade reaches zero, so the cells that were dim fade back
        // rather than snapping. The scroll position is kept too: where the letter put the grid is
        // where the user asked for it to be.
        mScrubSpring.target = 0f;
        requestFrames();
    }

    /**
     * One frame of everything the drawer animates that is not the plane: the rope and the scrub
     * highlight's release.
     *
     * <p>Called from the controller's loop rather than from one of this view's own, for the same
     * reason the reveal is: the plane's growing rectangle and the letters inside it are one surface,
     * and two {@link Choreographer} callbacks render them a frame apart.
     *
     * @return true while either still needs another frame
     */
    public boolean advanceDrawerFx(float p, float dt, boolean reduced) {
        float delta = Spring.clampDelta(dt);
        boolean ropeMoving = mColumn.advance(p, delta, reduced);
        boolean scrubMoving = mScrubSpring.tick(reduced, delta);
        if (!scrubMoving && mScrubSpring.target == 0f) {
            // Exactly zero, not nearly: the strength-0 case has to be byte-identical to B-2.
            mScrubSpring.reset(0f);
            mScrubLetter = '\0';
        }
        applyScrubHighlight();
        return ropeMoving || scrubMoving;
    }

    /** Drops the rope and the highlight. For a drawer that has closed. */
    public void resetDrawerFx() {
        clearScrub();
        mColumn.resetRope();
    }

    /** Ends any scrub, on the column and in the highlight, and restores every attached cell. */
    private void clearScrub() {
        mColumn.cancelScrub();
        mScrubLetter = '\0';
        mScrubSpring.reset(0f);
        applyScrubHighlight();
    }

    /**
     * The per-frame half of the highlight: {@code setAlpha} and {@code setScaleX/Y} over the grid's
     * <em>attached</em> children, which is 24-36 views rather than the 400 in the adapter.
     *
     * <p>A {@code notifyDataSetChanged()} per frame would rebind every one of them sixty times a
     * second, re-render icons included. The other half of the rule lives in
     * {@link AppDrawerAppsAdapter#onBindViewHolder}, for the cells the auto-scroll binds while this
     * walk is running, and the two read the same {@code (letter, strength)} pair by construction.
     *
     * <p>Nothing here may touch anything but alpha and scale. An icon size change would put a second
     * rendered bitmap per cell into the dock's shared byte-budgeted cache, and an A-to-# scrub
     * touches the whole catalogue in about a second.
     */
    private void applyScrubHighlight() {
        float strength = AppDrawerTransitionGeometry.clamp01(mScrubSpring.value);
        char active = strength > 0f ? mScrubLetter : '\0';
        mAdapter.setScrubState(active == '\0' ? null : active, strength);
        if (active == '\0') {
            if (!mHighlightWritten) return;
            restoreCellAppearance();
            mHighlightWritten = false;
            return;
        }
        int children = mGrid.getChildCount();
        for (int i = 0; i < children; i++) {
            View child = mGrid.getChildAt(i);
            if (child == null) continue;
            char letter = mAdapter.letterForPosition(mGrid.getChildAdapterPosition(child));
            child.setAlpha(AppDrawerScrubHighlight.alphaFor(letter, active, strength));
            float scale = AppDrawerScrubHighlight.scaleFor(letter, active, strength);
            child.setScaleX(scale);
            child.setScaleY(scale);
        }
        mHighlightWritten = true;
    }

    /**
     * Every attached cell back to exactly 1 and 1. The adapter's own recycle reset covers holders
     * that leave the screen mid-scrub; this covers the ones still on it.
     */
    private void restoreCellAppearance() {
        for (int i = 0; i < mGrid.getChildCount(); i++) {
            View child = mGrid.getChildAt(i);
            if (child == null) continue;
            child.setAlpha(1f);
            child.setScaleX(1f);
            child.setScaleY(1f);
        }
    }

    // ------------------------------------------------------------------ pill

    @Override
    public void onSearchPillClear() {
        clearQueryIfPresent();
    }

    @Override
    public void onSearchPillTapped() {
        // The pill is the only thing on screen that says "type here", and with the plane covering
        // the keyboard band there is otherwise nowhere for the first keystroke to come from.
        mSearchRevealRequested = true;
        notifyRevealTarget();
        Runnable request = mKeyboardRequestListener;
        if (request != null) request.run();
    }

    // ------------------------------------------------------------------ ownership

    /**
     * Who owns a point, from the plane's point of view.
     *
     * <p>B-1/B-2 had one boolean here and it meant "the grid owns it". With the A-Z column on the
     * plane the contract widens to "the <em>content</em> owns it and the plane must defer", because
     * the column is neither the grid nor chrome and a scrub cannot be told apart from a close drag by
     * motion. The plane still only needs defer-or-claim, which is why it is not modified at all; the
     * three-way split is resolved one level down, in {@link #dispatchTouchEvent}.
     *
     * @param x the plane's local X
     * @param y the plane's local Y
     * @return true when the grid or the column owns it; false for chrome — the pill, the margins, the
     *     strip below the grid, the reserved bottom band, and the column's strip while it is
     *     inactive — where the plane's own close drag runs exactly as it did in B-1
     */
    @Override
    public boolean ownsPoint(float x, float y) {
        resolvePlaneOffset(mPlaneOffset);
        return ownsLocalPoint(x - mPlaneOffset[0], y - mPlaneOffset[1]);
    }

    /** The same question, asked in this view's own coordinates. */
    private boolean ownsLocalPoint(float localX, float localY) {
        return regionAt(localX, localY) != AppDrawerTouchRegions.Region.CHROME;
    }

    /**
     * The three-way split, from geometry at the down point alone. The column is tested before the
     * grid, and the two rectangles are laid out not to overlap.
     */
    @NonNull
    private AppDrawerTouchRegions.Region regionAt(float localX, float localY) {
        return AppDrawerTouchRegions.resolve(localX, localY, boundsOf(mGrid), boundsOf(mColumn),
            mInteractive, isColumnActive());
    }

    @NonNull
    private static Frame boundsOf(@NonNull View view) {
        return new Frame(view.getLeft(), view.getTop(), view.getRight(), view.getBottom());
    }

    /**
     * This view's offset inside the plane's coordinate space.
     *
     * <p>The plane reports touch coordinates local to itself, and the content sits inside the
     * plane's content host, which carries the open-rect padding — so the two spaces differ by
     * however much padding the controller applied. Walking the ancestors is what keeps this correct
     * when that padding changes, instead of assuming the two origins coincide.
     */
    private void resolvePlaneOffset(@NonNull float[] out) {
        out[0] = 0f;
        out[1] = 0f;
        View view = this;
        while (true) {
            out[0] += view.getLeft() + view.getTranslationX();
            out[1] += view.getTop() + view.getTranslationY();
            ViewParent parent = view.getParent();
            if (!(parent instanceof View) || parent instanceof AppDrawerPlaneView) return;
            view = (View) parent;
            out[0] -= view.getScrollX();
            out[1] -= view.getScrollY();
        }
    }

    // ------------------------------------------------------------------ touch

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        // An observer, never an interceptor. The close drag is measured against raw screen Y, which
        // only the raw stream carries; claiming here instead is what would take the scroll away
        // from the grid and reintroduce the slop race this whole design exists to avoid.
        int action = ev.getActionMasked();
        // A MOVE is recorded before the child sees it: the pre-scroll it provokes runs inside this
        // dispatch and reads the Y from here, so recording afterwards would drive the close one
        // whole event behind the finger.
        if (action == MotionEvent.ACTION_MOVE) mLastRawY = ev.getRawY();
        // Likewise a cancel, and for the same timing: the child answers it by ending its nested
        // scroll from inside this dispatch, and that end has to know it is a cancellation rather
        // than a release, or a stream taken away by another window would report as a decision.
        if (action == MotionEvent.ACTION_CANCEL) mStreamCancelled = true;
        boolean handled = super.dispatchTouchEvent(ev);
        if (action == MotionEvent.ACTION_CANCEL || action == MotionEvent.ACTION_UP) {
            mStreamCancelled = false;
        }
        // A DOWN is recorded after, for the opposite reason: dispatching it is what flushes the
        // previous stream. The platform cancels the old touch target, the RecyclerView answers that
        // cancel by ending its nested scroll, and that end is this view's end-of-gesture
        // bookkeeping — sampled before the dispatch, the new gesture's snapshot would be exactly
        // what the old gesture's ending erased.
        if (action == MotionEvent.ACTION_DOWN) {
            mDownRawY = ev.getRawY();
            mLastRawY = mDownRawY;
            AppDrawerTouchRegions.Region region = regionAt(ev.getX(), ev.getY());
            if (region == AppDrawerTouchRegions.Region.COLUMN) {
                // The column owns this stream from here to its UP. onNestedPreScroll is already
                // gated on mDownOverGrid, so a scrub can never report a close with no change to it.
                mDownOverGrid = false;
                // mGestureActive is deliberately left false. The recycler never sees this stream, so
                // no onStopNestedScroll ever arrives to settle it; a stale stop arriving later would
                // otherwise end a gesture that never began and spend the arming a real pull earned.
                mPolicy.disarm();
            } else {
                mDownOverGrid = region == AppDrawerTouchRegions.Region.GRID;
                mGestureActive = true;
                mPolicy.begin(new AppDrawerCloseArmingPolicy.Down(mDownOverGrid,
                    !mGrid.canScrollVertically(-1), isGridScrollable()), SystemClock.uptimeMillis());
            }
        }
        return handled;
    }

    private boolean isGridScrollable() {
        return mGrid.canScrollVertically(-1) || mGrid.canScrollVertically(1);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        // A drawer that is not open is not a surface: swallow rather than let the grid answer.
        return !mInteractive;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!mInteractive) return true;
        return super.onTouchEvent(event);
    }

    // ------------------------------------------------------------------ nested scrolling

    @Override
    public boolean onStartNestedScroll(@NonNull View child, @NonNull View target, int axes,
                                       int type) {
        return mInteractive && (axes & ViewCompat.SCROLL_AXIS_VERTICAL) != 0;
    }

    @Override
    public void onNestedScrollAccepted(@NonNull View child, @NonNull View target, int axes,
                                       int type) {
        mParentHelper.onNestedScrollAccepted(child, target, axes, type);
    }

    @Override
    public void onNestedPreScroll(@NonNull View target, int dx, int dy, @NonNull int[] consumed,
                                  int type) {
        // Only a finger closes a drawer. A fling's continuation arrives as TYPE_NON_TOUCH long
        // after the hand has left the glass, and closing on it would look like the drawer fell out.
        if (type != ViewCompat.TYPE_TOUCH) return;
        // Chrome points belong to the plane's arbiter, which is already driving this stream; a
        // second close drag from here would report begin twice for one gesture.
        if (!mDownOverGrid) return;
        AppDrawerCloseArmingPolicy.Decision decision = mPolicy.claimOnPreScroll(dy);
        if (decision != AppDrawerCloseArmingPolicy.Decision.CLOSE_DRAG) return;
        consumed[1] = dy;
        if (!mNestedCloseActive) {
            mNestedCloseActive = true;
            // The finger is leaving with the drawer; anything it was about to open must not.
            cancelCellLongPresses();
            dismissContextPopups();
            stopOverpullSpring();
            if (mCallbacks != null) mCallbacks.onContentCloseDragBegin(mDownRawY);
        }
        if (mCallbacks != null) mCallbacks.onContentCloseDragUpdate(mLastRawY);
    }

    @Override
    public void onNestedScroll(@NonNull View target, int dxConsumed, int dyConsumed,
                               int dxUnconsumed, int dyUnconsumed, int type,
                               @NonNull int[] consumed) {
        int taken = takeOverpull(dyUnconsumed, type);
        consumed[1] += taken;
    }

    @Override
    public void onNestedScroll(@NonNull View target, int dxConsumed, int dyConsumed,
                               int dxUnconsumed, int dyUnconsumed, int type) {
        takeOverpull(dyUnconsumed, type);
    }

    /**
     * Whatever the grid could not use at its top becomes damped travel.
     *
     * @param dyUnconsumed scroll units the child left over; negative is a pull past the top
     * @return the units taken, in the child's sign convention
     */
    private int takeOverpull(int dyUnconsumed, int type) {
        if (type != ViewCompat.TYPE_TOUCH || dyUnconsumed >= 0) return 0;
        if (mNestedCloseActive || mPolicy.isClosing()) return 0;
        mOverpullRawPx = Math.max(0f, mOverpullRawPx - dyUnconsumed);
        applyOverpull(dampedOverpullPx(mOverpullRawPx, overpullMaxPx()));
        return dyUnconsumed;
    }

    @Override
    public boolean onNestedPreFling(@NonNull View target, float velocityX, float velocityY) {
        mFlingVelocityY = velocityY;
        if (!mNestedCloseActive) return false;
        // A throw released mid-close is the release: the drawer's own commit policy decides from
        // here, and letting the grid fling underneath it would scroll a list on its way out.
        endNestedClose(AppDrawerCloseArmingPolicy.closeVelocityForNestedFling(velocityY));
        return true;
    }

    @Override
    public void onStopNestedScroll(@NonNull View target, int type) {
        mParentHelper.onStopNestedScroll(target, type);
        if (type != ViewCompat.TYPE_TOUCH) return;
        // One gesture settles once. A stop can arrive twice — a cancelled close is followed by the
        // child's own stop, and a stale stream is stopped by the dispatch of the next DOWN — and a
        // second pass would end a gesture on an overpull of zero and spend the arming the first
        // pass just earned.
        if (!mGestureActive) return;
        mGestureActive = false;
        if (mNestedCloseActive) {
            // A slow release of a claimed close carries no fling, so there is no velocity to hand
            // on; a cancelled one carries no decision either, and the controller puts the drawer
            // back where the drag picked it up.
            if (mStreamCancelled) {
                cancelNestedClose();
            } else {
                endNestedClose(0f);
            }
        }
        float velocityPxPerSec =
            AppDrawerCloseArmingPolicy.closeVelocityForNestedFling(mFlingVelocityY);
        mPolicy.end(mOverpullTranslationPx, armOverpullPx(), velocityPxPerSec,
            !mGrid.canScrollVertically(-1), SystemClock.uptimeMillis());
        mFlingVelocityY = 0f;
        releaseOverpull();
    }

    private void endNestedClose(float velocityPxPerSec) {
        mNestedCloseActive = false;
        if (mCallbacks != null) mCallbacks.onContentCloseDragEnd(velocityPxPerSec);
    }

    /** The host revoking a close the grid was driving — a lifecycle stop, a palette summon. */
    public void cancelCloseDrag() {
        cancelNestedClose();
    }

    private void cancelNestedClose() {
        if (!mNestedCloseActive) return;
        mNestedCloseActive = false;
        if (mCallbacks != null) mCallbacks.onContentCloseDragCancel();
    }

    // ------------------------------------------------------------------ overpull

    /**
     * The damping curve: linear at the first pixel, asymptotic at {@code maxPx}. Exponential rather
     * than a clamp so there is no travel at which the grid visibly stops answering the finger.
     */
    public static float dampedOverpullPx(float rawPx, float maxPx) {
        if (maxPx <= 0f) return 0f;
        float raw = Math.max(0f, rawPx);
        return (float) (maxPx * (1d - Math.exp(-raw / maxPx)));
    }

    private float overpullMaxPx() {
        return OVERPULL_MAX_DP * mDensity;
    }

    private float armOverpullPx() {
        return AppDrawerCloseArmingPolicy.ARM_OVERPULL_DP * mDensity;
    }

    /** The grid's current overpull travel, in pixels. */
    public float getOverpullTranslationPx() {
        return mOverpullTranslationPx;
    }

    private void applyOverpull(float translationPx) {
        mOverpullTranslationPx = translationPx;
        mGrid.setTranslationY(translationPx);
    }

    private void releaseOverpull() {
        mOverpullRawPx = 0f;
        if (mOverpullTranslationPx == 0f) return;
        mOverpullSpring.value = mOverpullTranslationPx;
        mOverpullSpring.target = 0f;
        mOverpullSpring.vel = 0f;
        kick();
    }

    /** Drops the overpull and its spring on the floor; for a drawer that is closing or closed. */
    public void stopOverpullSpring() {
        if (mFrameScheduled) {
            Choreographer.getInstance().removeFrameCallback(this);
            mFrameScheduled = false;
        }
        mOverpullSpring.reset(0f);
        mOverpullRawPx = 0f;
        applyOverpull(0f);
    }

    private void kick() {
        if (mFrameScheduled) return;
        mFrameScheduled = true;
        mLastFrameTimeNanos = 0L;
        Choreographer.getInstance().postFrameCallback(this);
    }

    @Override
    public void doFrame(long frameTimeNanos) {
        mFrameScheduled = false;
        float dt = mLastFrameTimeNanos == 0L
            ? Spring.MIN_DT
            : (frameTimeNanos - mLastFrameTimeNanos) / 1_000_000_000f;
        mLastFrameTimeNanos = frameTimeNanos;
        boolean moving = mOverpullSpring.tick(false, Spring.clampDelta(dt));
        applyOverpull(mOverpullSpring.value);
        if (moving) {
            // Re-arming here rather than in kick() keeps the loop short-lived by construction: it
            // stops the frame the spring settles, and nothing outside a release ever starts it.
            mFrameScheduled = true;
            Choreographer.getInstance().postFrameCallback(this);
            return;
        }
        mOverpullSpring.reset(0f);
        applyOverpull(0f);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        stopOverpullSpring();
    }

    private int dp(float value) {
        return Math.round(value * mDensity);
    }

    /** @return the grid, for the horizontal view in B-3 to share this one's recycled view pool */
    @NonNull
    public RecyclerView getGrid() {
        return mGrid;
    }

    /** @return the A-Z column, whose touch stream and geometry the drawer's tests drive directly */
    @NonNull
    public AppDrawerRopeColumnView getRopeColumn() {
        return mColumn;
    }
}
