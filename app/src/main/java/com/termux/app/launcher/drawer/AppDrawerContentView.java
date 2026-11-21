package com.termux.app.launcher.drawer;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.os.Trace;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.util.Log;
import android.util.TypedValue;
import android.view.Choreographer;
import android.view.DragEvent;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.ViewConfiguration;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.ViewParent;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;
import androidx.core.view.NestedScrollingParent3;
import androidx.core.view.NestedScrollingParentHelper;
import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.termux.BuildConfig;
import com.termux.app.AnimationSpeed;
import com.termux.app.Spring;
import com.termux.app.SuggestionBarView;
import com.termux.app.launcher.data.LauncherAppDataProvider;
import com.termux.app.launcher.data.LauncherCategoryAssignmentSource;
import com.termux.app.launcher.data.LauncherCategoryOrderStore;
import com.termux.app.launcher.data.LauncherCategoryOverrideStore;
import com.termux.app.launcher.data.LauncherUsageStatsStore;
import com.termux.app.launcher.drawer.AppDrawerTransitionGeometry.Frame;
import com.termux.app.launcher.model.LauncherAppEntry;
import com.termux.app.launcher.model.AppRef;
import com.termux.app.launcher.model.PinnedFolderItem;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.io.IOException;

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
    AppDrawerSearchPillView.Callbacks, AppDrawerRopeColumnView.Callbacks,
    AppDrawerDragController.Host {

    /** Raw close-drag reports, in the plane's own vocabulary. The controller interprets them. */
    public interface Callbacks {

        /** The grid has claimed the stream as a close; {@code downRawY} is the ACTION_DOWN point. */
        void onContentCloseDragBegin(float downRawY);

        void onContentCloseDragUpdate(float rawY);

        /** @param velocityPxPerSec release velocity, positive downwards */
        void onContentCloseDragEnd(float velocityPxPerSec);

        void onContentCloseDragCancel();

        /** The section changed through {@link #setSection}; not called for a restore. */
        default void onSectionChanged(@NonNull AppDrawerSection section) {
        }

        /**
         * Default's two pages gave way to one section's own presentation (a search began), or
         * came back; the grid's metrics differ between the two.
         */
        default void onPagesPresentationChanged() {
        }

        /** The drawer's corner tab asked for the drawer's settings page. */
        default void onCornerSettingsRequested() {
        }

        /** The stop the stored column count stands on, in {@link AppDrawerGridChoices#COLUMN_VALUES}. */
        default int gridColumnIndex() {
            return 0;
        }

        /** The stop the stored row count stands on, in {@link AppDrawerGridChoices#ROW_VALUES}. */
        default int gridRowIndex() {
            return 0;
        }

        /**
         * The grid-size wheels moved to these stops; {@code rowIndex} is -1 where the type has no
         * rows to choose. The same preferences the Drawer layout page writes.
         */
        default void onGridSizeChosen(int columnIndex, int rowIndex) {
        }

    }

    /** The search bar's alpha while the drawer is edited: still there, plainly not for typing. */
    private static final float SEARCH_DIMMED_ALPHA = 0.38f;

    /** Ceiling on the damped overpull, in dp. The raw pull is unbounded; the travel is not. */
    public static final float OVERPULL_MAX_DP = 96f;

    /** The drawer's side padding, and the content inset every view type ends above the card's bottom. */
    static final float PILL_MARGIN_H_DP = AppDrawerChromeLayout.SIDE_MARGIN_DP;
    /** Band the categories search reserves above its rows for the "N RESULTS" label. */
    private static final float RESULTS_LABEL_BAND_DP = 24f;
    /** The no-results state's drop below the content's top. */
    private static final float NO_RESULTS_BELOW_CONTENT_DP = 64f;

    /** Distance from the content's bottom edge to its last element; see {@link #setBottomInsetPx}. */
    private int mBottomInsetPx;
    /** Settings › Search bar at the bottom; read once per open. */
    private boolean mSearchAtBottom;
    private final AppDrawerSearchPillView mPill;
    private final AppDrawerSearchInputView mInput;
    /**
     * The search bar's fold, owned here: 0 the full bar, 1 the round button in the corner. The
     * resting target is {@link #mSearchCollapsed}; one reused animator walks the fraction there
     * (from wherever it stands, so a reversal mid-flight starts from the current value), and each
     * step only moves and redraws the pill.
     */
    private float mSearchFold;
    private boolean mSearchCollapsed;
    @Nullable private ValueAnimator mSearchFoldAnimator;
    private final AppDrawerChromeLayout.Morph mSearchMorph = new AppDrawerChromeLayout.Morph();
    @Nullable private AppDrawerChromeLayout.Result mChromeResult;
    private final android.graphics.RectF mPillRect = new android.graphics.RectF();
    private final TextView mResultsLabel;
    private final TextView mNoResults;
    private final RecyclerView mGrid;
    private final AppDrawerHorizontalPagerView mHorizontalPager;
    private final AppDrawerPageIndicatorView mPageIndicator;
    private final AppDrawerCategoryView mCategoryView;
    private final AppDrawerRopeColumnView mColumn;
    private final AppDrawerDragOverlayView mDragOverlay;
    private final GridLayoutManager mLayoutManager;
    private final AppDrawerAppsAdapter mAdapter;
    private final AppDrawerHorizontalPageAdapter mHorizontalAdapter;
    private final LauncherUsageStatsStore mUsageStats;
    private final LauncherCategoryOverrideStore mCategoryOverrides;
    private final LauncherCategoryAssignmentSource mCategoryAssignments;
    private final AppDrawerCategoryClassifier mCategoryClassifier;
    /** What the last category pass classified, so an identical rebind skips the pipeline. */
    @Nullable private List<String> mClassifiedIds;
    private long mClassifiedUsageVersion = -1L;
    private long mClassifiedAtMs;
    private static final long CLASSIFY_REUSE_WINDOW_MS = 60_000L;
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
    @Nullable private AppDrawerDragController mDragController;
    @Nullable private Callbacks mCallbacks;
    /** The corner tab a still hold on a corner of the pane drops; see {@link AppDrawerCornerTab}. */
    private final AppDrawerCornerTab mCornerTab = new AppDrawerCornerTab(
        new AppDrawerCornerTab.Host() {
            @NonNull @Override public ViewGroup view() {
                return AppDrawerContentView.this;
            }

            @Override public int chromeRects(@NonNull float[] out) {
                return fillChromeRects(out);
            }

            @Override public void cancelContent(float x, float y) {
                cancelContentTouch(x, y);
            }

            @Override public void openSettings() {
                if (mCallbacks != null) mCallbacks.onCornerSettingsRequested();
            }

            @NonNull @Override public AppDrawerEditModel editModel() {
                return mEditModel;
            }

            @Override public void enterEdit() {
                AppDrawerContentView.this.enterEdit(AppDrawerEditModel.Entry.CORNER_BUTTON);
            }

            @Override public void leaveEdit() {
                AppDrawerContentView.this.leaveEdit(AppDrawerEditModel.Leave.TICK);
            }

            @Override public void openGridSize(@NonNull android.graphics.RectF tab) {
                showGridSizePopup(tab);
            }

            @NonNull @Override public String gridSizeText() {
                return gridSizeTabText();
            }

            @NonNull @Override public AppDrawerViewType viewType() {
                return mViewType;
            }

            @NonNull @Override public AppDrawerSection section() {
                return mSection;
            }

            @Override public float cardCornerRadiusPx() {
                return mCardCornerRadiusPx;
            }

            @Nullable @Override
            public com.termux.app.terminal.PaneSurfaceStyle tabStyle() {
                TabStyleSource source = mTabStyleSource;
                return source == null ? null : source.style();
            }

            @Override public float tabBorderPx() {
                TabStyleSource source = mTabStyleSource;
                return source == null ? 0f : source.borderPx();
            }
        });
    @Nullable private com.termux.app.launcher.widget.WidgetGridSizePopup mGridSizePopup;
    /** Edit mode, owned here: one value for the whole drawer, never stored. */
    @NonNull private AppDrawerEditModel mEditModel = AppDrawerEditModel.OFF;
    private float mCardCornerRadiusPx;
    /** True only inside {@link #cancelContentTouch}: that cancel is the content's, not the corner tab's. */
    private boolean mCancellingContent;
    @Nullable private LauncherAppDataProvider mProvider;
    @Nullable private AppDrawerSearchController mSearch;
    @Nullable private Runnable mRevealListener;
    /** Settings › App search › Open the only match; read once per open. */
    private boolean mOpenOnlyMatch;
    private boolean mRememberPosition;
    private boolean mLowerStart;
    @NonNull private AppDrawerAzSide mAzSide = AppDrawerAzSide.RIGHT;
    private int mLowerStartPx;
    private int mSavedPosition;
    private int mSavedOffsetPx;
    private final Runnable mAutoOpenOnlyMatch = this::autoOpenOnlyMatch;
    @Nullable private Runnable mKeyboardRequestListener;
    @Nullable private Runnable mFrameRequestListener;
    /** Rebuilt once per submitted list; the column's letters and the scrub's scroll targets. */
    @NonNull private AppDrawerSectionIndex mSectionIndex = AppDrawerSectionIndex.build(null);
    @NonNull private List<LauncherAppEntry> mVisibleResults = Collections.emptyList();
    @NonNull private List<AppDrawerCategoryBucket> mCategoryBuckets = Collections.emptyList();
    /** stableId → category label res, rebuilt with the buckets; the search rows' second line. */
    @NonNull private final HashMap<String, Integer> mCategoryLabelIds =
        new HashMap<>();
    /** Vertical grid columns, retained so the search-row presentation can restore the span. */
    private int mVerticalColumns = AppDrawerGridMetrics.MIN_COLUMNS;
    private boolean mCategorySearchPresentation;
    /** Where the content starts under the top chrome; moves with the section switch ({@link #applySectionSwitchLayout}). */
    private int mGridBaseTopMarginPx;
    private final AppDrawerSectionSwitchView mSectionSwitch;
    /** The configured type. Everything that draws or routes touches reads {@link #presentedType()}. */
    @NonNull private AppDrawerViewType mViewType = AppDrawerViewType.VERTICAL;
    /** The remembered section; only the Default type shows it. */
    @NonNull private AppDrawerSection mSection = AppDrawerSection.APPS;
    /**
     * The one owner of Default's page position; the pages read it, nothing else stores one.
     * Its settled section and {@link #mSection} agree whenever the pages are shown.
     */
    private final AppDrawerSectionPager mSectionPager =
        new AppDrawerSectionPager(new SectionSettleClock());
    private final AppDrawerSectionGesturePolicy mSectionGesture =
        new AppDrawerSectionGesturePolicy();
    /** True while Default shows its two pages side by side (not a search, not another type). */
    private boolean mPagesActive;
    /** True from a section claim until its UP or CANCEL: this view drives the pager. */
    private boolean mSectionStream;
    /** The slop the claim spent, which the pages do not move by (0 for a caught settle). */
    private float mSectionSlopPx;
    private float mSectionDownX;
    private float mSectionDownY;
    private int mTouchSlopPx;
    @Nullable private VelocityTracker mSectionVelocity;
    @NonNull private AppDrawerViewType mGestureViewType = AppDrawerViewType.VERTICAL;

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
    private boolean mGestureCategorySearch;
    @NonNull private AppDrawerCategoryTouchRegions.Part mGestureCategoryPart =
        AppDrawerCategoryTouchRegions.Part.OUTSIDE;
    @Nullable private RecyclerView mGestureRecycler;
    @Nullable private View mOverpullSurface;
    private boolean mSearchRevealRequested;
    /**
     * The user scrolled the search keyboard away. It stays away, query or not, until the search is
     * asked for again (a pill tap) or the search is reset.
     */
    private boolean mKeyboardDismissedByScroll;
    /** True from the finger starting to drag the list until the list comes to rest. */
    private boolean mGridUserScrolling;
    /** Whether that drag began with somewhere to scroll down, and so may send the keyboard away. */
    private boolean mGridDragHidesKeyboard;
    @Nullable private KeyboardScrollListener mKeyboardScrollListener;
    /** True while the Android-keyboard search's text field, not the key stream, owns the query. */
    private boolean mTextFieldSearch;
    private float mDownRawY;
    private float mLastRawY;
    private float mOverpullRawPx;
    private float mOverpullTranslationPx;
    /** Last nested pre-fling velocity, in scroll units; negative is a downward finger. */
    private float mFlingVelocityY;
    private boolean mFrameScheduled;
    private long mLastFrameTimeNanos;
    /** True while {@link #doFrame} runs: a kick from inside it continues the loop and keeps its clock. */
    private boolean mInFrame;
    /** The letter the highlight is keyed to. Outlives the finger, for the length of the fade. */
    private char mScrubLetter = '\0';
    /**
     * True while at least one attached cell has been written away from 1/1. The per-frame walk runs
     * for every frame of every transition, so without this the no-scrub case would touch every
     * attached child on every frame to tell it what it already is.
     */
    private boolean mHighlightWritten;
    private boolean mSuppressCellClickDuringTerminalDispatch;
    private boolean mDragActive;
    /** A config mutation that landed mid-drag; the recompose replays once the drag ends. */
    private boolean mPendingDockConfigRefresh;
    private boolean mGestureDragEligible;
    @Nullable private String mGestureDragStableId;
    @Nullable private AppDrawerDragPolicy mDragPolicy;
    /** What a finger does while the drawer is edited; decided at the down, read by the pager. */
    private final AppDrawerEditGesturePolicy mEditGesture = new AppDrawerEditGesturePolicy();
    /** The folder cell a still-undecided edit gesture started on: a tap opens it, a move lifts it. */
    @Nullable private View mEditFolderCell;
    @Nullable private PinnedFolderItem mEditFolder;
    private float mEditDownX;
    private float mEditDownY;
    private long mEditDownTime;
    private final Runnable mEditFolderHold = this::onEditFolderHold;
    /** A Categories tile is lifted and this view owns the rest of the stream. */
    private boolean mTileLiftActive;
    private float mLastTouchX;
    private float mLastTouchY;
    @NonNull private final LauncherCategoryOrderStore mCategoryOrderStore;
    private int mDragEdgeDirection;
    private boolean mDragEdgeConsumed;
    private float mDragAutoscrollVelocity;
    private final int[] mDragLocation = new int[2];
    private final Runnable mDragEdgeDwell = this::onDragEdgeDwell;
    private final AppDrawerDragPolicy.HorizontalDropGate mHorizontalDropGate =
        new AppDrawerDragPolicy.HorizontalDropGate();

    private void onDragEdgeDwell() {
        if (!mDragActive || mDragEdgeDirection == 0 || mDragEdgeConsumed) return;
        int next = mHorizontalPager.getSelectedPage() + mDragEdgeDirection;
        if (next >= 0 && next < mHorizontalAdapter.getItemCount()) {
            mHorizontalDropGate.onNavigationStarted();
            mHorizontalPager.setSelectedPage(next, true);
        }
        mDragEdgeConsumed = true;
    }
    private final Runnable mDragAutoscroll = new Runnable() {
        @Override public void run() {
            if (!mDragActive || mGestureViewType != AppDrawerViewType.VERTICAL
                || mDragAutoscrollVelocity == 0f) return;
            mGrid.scrollBy(0, Math.round(mDragAutoscrollVelocity / 60f));
            postOnAnimation(this);
        }
    };

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
        if (dock != null) {
            dock.setDrawerConfigChangedListener(this::onDockConfigChanged);
            dock.setCategoryChoiceHandler(this::showCategoryOverrideDialog);
        }
        mDensity = context.getResources().getDisplayMetrics().density;
        mTouchSlopPx = ViewConfiguration.get(context).getScaledTouchSlop();
        mSectionPager.addListener(new AppDrawerSectionPager.Listener() {
            @Override public void onPositionChanged(float position) {
                // Per move: draw-time transforms only, no layout, no visibility, no binding.
                Trace.beginSection("Drawer.sectionDrag");
                try {
                    applyPageTransforms();
                } finally {
                    Trace.endSection();
                }
            }

            @Override public void onSettled(@NonNull AppDrawerSection section, boolean changed) {
                onPagerSettled(section, changed);
            }
        });
        mUsageStats = LauncherUsageStatsStore.getInstance(context);
        mCategoryOverrides = new LauncherCategoryOverrideStore(context);
        mCategoryOrderStore = new LauncherCategoryOrderStore(context);
        mCategoryAssignments = new LauncherCategoryAssignmentSource(mCategoryOverrides);
        mCategoryClassifier = new AppDrawerCategoryClassifier(loadCuratedMap(context.getResources()),
            mCategoryAssignments);
        setClipChildren(false);
        setClipToPadding(false);

        AppDrawerChromeLayout.Result chrome = AppDrawerChromeLayout.resolve(
            new AppDrawerChromeLayout.Input(mDensity, 0, false, 0, false, 0));
        LayoutParams pillParams = new LayoutParams(LayoutParams.MATCH_PARENT,
            chrome.searchHeightPx, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        pillParams.leftMargin = chrome.sideMarginPx;
        pillParams.rightMargin = chrome.sideMarginPx;
        pillParams.topMargin = chrome.searchTopPx;
        // Under the pill, in the pill's own rectangle: the pill stays what is tapped and what is
        // seen, the field is only what the Android keyboard types into.
        mInput = new AppDrawerSearchInputView(context);
        mInput.setListener(new AppDrawerSearchInputView.Listener() {
            @Override public void onQueryEdited(@NonNull String text, int caret) {
                AppDrawerSearchController search = mSearch;
                if (search != null && mTextFieldSearch) search.replaceQuery(text, caret);
            }

            @Override public void onSearchAction() {
                launchHighlightedResult();
            }

            @Override public boolean onArrowKey(int keyCode) {
                AppDrawerSearchController search = mSearch;
                return search != null && mTextFieldSearch && search.handleHighlightKey(keyCode);
            }

            @Override public void onDismissAction() {
                AppDrawerSearchController search = mSearch;
                if (search != null) search.requestDismiss();
            }
        });
        addView(mInput, new LayoutParams(pillParams));

        mPill = new AppDrawerSearchPillView(context);
        mPill.setCallbacks(this);
        addView(mPill, pillParams);

        // Default only: laid out and shown by applySectionSwitchLayout. Above the search bar, so
        // a drag that starts on it is chrome to the section gesture and moves nothing.
        mSectionSwitch = new AppDrawerSectionSwitchView(context);
        mSectionSwitch.attachTo(mSectionPager);
        mSectionSwitch.setCallbacks(this::onSectionSwitchChosen);
        mSectionSwitch.setInkSource(this::sectionSwitchInk);
        mSectionSwitch.setVisibility(GONE);
        LayoutParams switchParams = new LayoutParams(LayoutParams.WRAP_CONTENT,
            Math.round(AppDrawerSectionSwitchGeometry.HEIGHT_DP * mDensity),
            Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        switchParams.leftMargin = chrome.sideMarginPx;
        switchParams.rightMargin = chrome.sideMarginPx;
        switchParams.topMargin = chrome.searchTopPx;
        addView(mSectionSwitch, switchParams);

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
        // This recycler is a margin-inset sibling of the fixed search pill. Its parent must keep
        // clipChildren=false for the rope lean and the full-plane drag overlay, so the recycler has
        // to establish its own hard draw boundary. With clipToPadding=false a partially visible
        // leading row can paint above this view during a fling and into the pill rectangle.
        // Bottom padding (the card's content inset) is the one thing it lets rows paint into, and the
        // outline below still holds the hard boundary at the top.
        mGrid.setClipToPadding(false);
        mGrid.setOutlineProvider(ViewOutlineProvider.BOUNDS);
        mGrid.setClipToOutline(true);
        mGrid.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                // A context menu is anchored to a cell view. Scroll far enough and that view is
                // recycled under a menu still floating where it used to be.
                if (dx != 0 || dy != 0) dismissContextPopups();
                // Only the finger's scroll (and the fling it throws) sends the keyboard away; a
                // scrub or a query's jump to the top is the launcher moving the list, not the user.
                KeyboardScrollListener keyboard = mKeyboardScrollListener;
                if (keyboard != null && mGridUserScrolling && mGridDragHidesKeyboard && dy > 0)
                    keyboard.onListScrolledDown(dy);
                // Back at the top (a scroll up, or the A-Z strip's jump), the bar is back too.
                // (A layout's own dy of 0 is not a scroll.)
                if (dy < 0 && mSearchCollapsed) unfoldSearchAtTop(recyclerView);
            }

            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                    if (!mGridUserScrolling) {
                        notifyListDragStarted(recyclerView.computeVerticalScrollRange()
                            > recyclerView.computeVerticalScrollExtent());
                        // Decided once, as the drag starts: a list that fits has no scroll to give.
                        mGridDragHidesKeyboard = AppDrawerKeyboardScrollHide.armsOnDrag(
                            recyclerView.computeVerticalScrollRange(),
                            recyclerView.computeVerticalScrollExtent(),
                            recyclerView.computeVerticalScrollOffset());
                    }
                    mGridUserScrolling = true;
                } else if (newState == RecyclerView.SCROLL_STATE_IDLE && mGridUserScrolling) {
                    mGridUserScrolling = false;
                    if (mSearchCollapsed) unfoldSearchAtTop(recyclerView);
                    KeyboardScrollListener keyboard = mKeyboardScrollListener;
                    if (keyboard != null) keyboard.onListScrollIdle();
                }
            }
        });
        mColumnWidthPx = AppDrawerRopeMetrics.resolveColumnWidthPx(mDensity);
        LayoutParams gridParams = new LayoutParams(LayoutParams.MATCH_PARENT,
            LayoutParams.MATCH_PARENT);
        gridParams.topMargin = chrome.contentTopPx;
        mGridBaseTopMarginPx = gridParams.topMargin;
        gridParams.bottomMargin = 0;
        // The letters are not an overlay: the grid gives up exactly the strip's width so no cell
        // ever sits under one, and the column count the controller resolves is computed from the
        // same subtraction.
        gridParams.rightMargin = Math.round(mColumnWidthPx);
        addView(mGrid, gridParams);

        mHorizontalAdapter = new AppDrawerHorizontalPageAdapter(dock);
        mHorizontalPager = new AppDrawerHorizontalPagerView(context);
        mPageIndicator = new AppDrawerPageIndicatorView(context);
        mHorizontalPager.setAdapter(mHorizontalAdapter);
        mHorizontalPager.setClaimGate(this::claimDrawerGesture);
        mHorizontalAdapter.setClickGate(mHorizontalPager);
        mHorizontalPager.setPageSelectionListener(page -> mPageIndicator.setSelectedPage(page));
        mHorizontalPager.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                if (dx != 0 || dy != 0) dismissContextPopups();
            }

            @Override public void onScrollStateChanged(@NonNull RecyclerView recyclerView,
                                                       int newState) {
                mHorizontalDropGate.onScrollStateChanged(newState);
                // A sideways page swipe is browsing too, like dragging a list.
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                    notifyListDragStarted(recyclerView.canScrollHorizontally(1)
                        || recyclerView.canScrollHorizontally(-1));
                } else if (newState == RecyclerView.SCROLL_STATE_IDLE && mSearchCollapsed
                    && mHorizontalPager.getSelectedPage() == 0) {
                    // The first page is the pages' top.
                    unfoldSearch(AppDrawerSearchCollapsePolicy.Event.SCROLLED_TO_TOP, true);
                }
            }
        });
        LayoutParams pagerParams = new LayoutParams(LayoutParams.MATCH_PARENT,
            LayoutParams.MATCH_PARENT);
        pagerParams.topMargin = gridParams.topMargin;
        pagerParams.bottomMargin = Math.round(pagerBottomBandPx(0));
        mHorizontalPager.setVisibility(GONE);
        addView(mHorizontalPager, pagerParams);

        mCategoryView = new AppDrawerCategoryView(context, dock);
        mCategoryView.setFrameRequestListener(this::requestFrames);
        mCategoryView.setPopupDismissCallback(this::dismissContextPopups);
        hideKeyboardWhenDragged(mCategoryView.getOverview());
        hideKeyboardWhenDragged(mCategoryView.getDetailList());
        mCategoryView.getDetailAdapter().setCategoryChoiceListener(
            this::showCategoryOverrideDialog);
        mCategoryView.getTileAdapter().setCategoryChoiceListener(
            this::showCategoryOverrideDialog);
        LayoutParams categoryParams = new LayoutParams(LayoutParams.MATCH_PARENT,
            LayoutParams.MATCH_PARENT);
        categoryParams.topMargin = gridParams.topMargin;
        categoryParams.bottomMargin = 0;
        mCategoryView.setVisibility(GONE);
        addView(mCategoryView, categoryParams);

        // The categories search chrome: a quiet mono "N RESULTS" band between the pill and the
        // rows, and a centred no-results state. Both exist only while that presentation is up.
        mResultsLabel = new TextView(context);
        mResultsLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f);
        mResultsLabel.setTypeface(Typeface.MONOSPACE);
        mResultsLabel.setLetterSpacing(0.16f);
        mResultsLabel.setSingleLine(true);
        mResultsLabel.setIncludeFontPadding(false);
        mResultsLabel.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        mResultsLabel.setTextColor(resultsChromeColor(0x73));
        mResultsLabel.setVisibility(GONE);
        LayoutParams resultsLabelParams = new LayoutParams(LayoutParams.MATCH_PARENT,
            dp(RESULTS_LABEL_BAND_DP));
        resultsLabelParams.leftMargin = dp(PILL_MARGIN_H_DP + 4f);
        resultsLabelParams.rightMargin = resultsLabelParams.leftMargin;
        resultsLabelParams.topMargin = mGridBaseTopMarginPx;
        addView(mResultsLabel, resultsLabelParams);

        mNoResults = new TextView(context);
        mNoResults.setGravity(Gravity.CENTER);
        mNoResults.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f);
        mNoResults.setLineSpacing(0f, 1.3f);
        mNoResults.setVisibility(GONE);
        bindNoResultsText();
        LayoutParams noResultsParams = new LayoutParams(LayoutParams.MATCH_PARENT,
            LayoutParams.WRAP_CONTENT);
        noResultsParams.topMargin = mGridBaseTopMarginPx + dp(NO_RESULTS_BELOW_CONTENT_DP);
        noResultsParams.leftMargin = dp(PILL_MARGIN_H_DP);
        noResultsParams.rightMargin = dp(PILL_MARGIN_H_DP);
        addView(mNoResults, noResultsParams);

        LayoutParams indicatorParams = new LayoutParams(LayoutParams.MATCH_PARENT,
            AppDrawerPageIndicatorView.dotsHeightPx(mDensity), Gravity.BOTTOM);
        indicatorParams.bottomMargin = mBottomInsetPx;
        addView(mPageIndicator, indicatorParams);

        mColumn = new AppDrawerRopeColumnView(context);
        mColumn.setDock(dock);
        mColumn.setCallbacks(this);
        // Added after the grid so the letters paint over it — which they do transiently, because the
        // rope's lean carries them out of their own strip while the column is fading in.
        LayoutParams columnParams = new LayoutParams(Math.round(mColumnWidthPx),
            LayoutParams.MATCH_PARENT, Gravity.END);
        columnParams.topMargin = gridParams.topMargin;
        columnParams.bottomMargin = 0;
        addView(mColumn, columnParams);

        setOnDragListener((view, event) -> {
            AppDrawerDragController controller = mDragController;
            if (controller != null && controller.handedToHome(event.getLocalState())) {
                // Home's now; the drawer is on its way out and only passes the finger on.
                getLocationOnScreen(mDragLocation);
                return controller.forwardToHome(event, mDragLocation[0] + event.getX(),
                    mDragLocation[1] + event.getY());
            }
            if (controller == null || !controller.owns(event.getLocalState()))
                return handleFolderEntryDragEvent(event);
            switch (event.getAction()) {
                case DragEvent.ACTION_DRAG_STARTED:
                    return true;
                case DragEvent.ACTION_DRAG_LOCATION:
                    getLocationOnScreen(mDragLocation);
                    controller.onDragMoved(mDragLocation[0] + event.getX(),
                        mDragLocation[1] + event.getY());
                    if (!controller.isDragging()) return true;
                    onContentDragLocation(event.getX(), event.getY());
                    return true;
                case DragEvent.ACTION_DROP:
                    // Blank drawer space: only a folder has somewhere to land here, and the only
                    // position past the last row is the end of the list.
                    // Editing: a drop on blank space cancels, the folder goes back to its place.
                    return mEditModel.isEditing() ? false
                        : controller.isFolderSourceDrag() && controller.dropFolderAtDrawerEnd();
                case DragEvent.ACTION_DRAG_EXITED:
                    onDragTargetExited();
                    return true;
                case DragEvent.ACTION_DRAG_ENDED:
                    onDragTargetExited();
                    controller.onHostDragEnded(event);
                    return true;
                default:
                    return true;
            }
        });
        mDragOverlay = new AppDrawerDragOverlayView(context);
        mDragOverlay.setVisibility(INVISIBLE);
        addView(mDragOverlay, new LayoutParams(LayoutParams.MATCH_PARENT,
            LayoutParams.MATCH_PARENT));
        // Chrome stays last for its ordinary elevation/shadow language. Content safety does not
        // depend on that ordering: each scrolling surface clips at the top margin below the pill.
        com.termux.app.window.WindowLayers.raiseWithin(mPill);
        applyViewType();
    }

    @NonNull
    private static AppDrawerCuratedCategoryMap loadCuratedMap(@NonNull Resources resources) {
        try (InputStream input = resources.openRawResource(
            com.termux.R.raw.app_drawer_category_overrides)) {
            return AppDrawerCuratedCategoryMap.parse(input);
        } catch (IOException | Resources.NotFoundException ignored) {
            return AppDrawerCuratedCategoryMap.empty();
        }
    }

    /** Long-press on a category app icon: pick a category for it, or go back to auto. */
    private void showCategoryOverrideDialog(@NonNull LauncherAppEntry entry, @NonNull View anchor) {
        SuggestionBarView dock = mDock;
        if (dock == null) return;
        List<AppDrawerCategory> choices = new ArrayList<>();
        for (AppDrawerCategory category : AppDrawerCategory.values())
            if (!category.synthetic) choices.add(category);
        AppDrawerCategory current = AppDrawerCategory.fromSlug(
            mCategoryOverrides.get(entry.appRef.packageName));
        dock.showCategoryPickerPopup(entry, anchor, choices, current, picked -> {
            if (picked == null) mCategoryOverrides.clear(entry.appRef.packageName);
            else mCategoryOverrides.set(entry.appRef.packageName, picked.slug);
            reclassifyCategories();
        });
    }

    /** Reruns the pipeline over the live catalogue after an override mutation; no data reload. */
    private void reclassifyCategories() {
        if (hasQuery()) return;
        // An external edit or a finished sort run rewrites app-categories.conf behind our back.
        mCategoryAssignments.invalidate();
        classifyCatalogue(mVisibleResults, true);
        if (categoryBucketsLive()) mCategoryView.submitBuckets(mCategoryBuckets);
    }

    /**
     * Runs the category pipeline over {@code catalogue}, unless the last run saw the same apps, the
     * same usage history and happened within {@link #CLASSIFY_REUSE_WINDOW_MS}.
     *
     * <p>Every open rebinds the grid, and a rebind pushes the catalogue and then re-applies the
     * results, so the pipeline ran two or three times per pull on identical input — each run a
     * PackageManager round trip per default role plus a usage ranking, on the frame the gesture
     * began. A slow device lost the drag to that stall. Overrides bypass the memo through
     * {@code force}; time only enters the pipeline through the recently-added window and the usage
     * decay, and neither moves inside the reuse window.
     */
    private void classifyCatalogue(@NonNull List<LauncherAppEntry> catalogue, boolean force) {
        long now = System.currentTimeMillis();
        long usageVersion = mUsageStats.version();
        if (!force && mClassifiedIds != null && usageVersion == mClassifiedUsageVersion
            && now - mClassifiedAtMs >= 0 && now - mClassifiedAtMs < CLASSIFY_REUSE_WINDOW_MS
            && sameCatalogue(catalogue, mClassifiedIds))
            return;
        setCategoryBuckets(mCategoryClassifier.classify(catalogue, mUsageStats,
            AppDrawerSystemRoleResolver.resolve(getContext()), now));
        logCategoryDebugReport();
        List<String> ids = new ArrayList<>(catalogue.size());
        for (LauncherAppEntry entry : catalogue) ids.add(entry.appRef.stableId());
        mClassifiedIds = ids;
        mClassifiedUsageVersion = usageVersion;
        mClassifiedAtMs = now;
    }

    private static boolean sameCatalogue(@NonNull List<LauncherAppEntry> catalogue,
                                         @NonNull List<String> ids) {
        if (catalogue.size() != ids.size()) return false;
        for (int i = 0; i < ids.size(); i++)
            if (!ids.get(i).equals(catalogue.get(i).appRef.stableId())) return false;
        return true;
    }

    private void logCategoryDebugReport() {
        if (!BuildConfig.DEBUG) return;
        String report = mCategoryClassifier.debugReport();
        int from = 0;
        while (from < report.length()) {
            int to = Math.min(report.length(), from + 3500);
            int newline = report.lastIndexOf('\n', to - 1);
            if (newline > from && to < report.length()) to = newline + 1;
            Log.d("AppDrawerCategories", report.substring(from, to));
            from = to;
        }
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
    /** The launcher text colour at an alpha, for the search chrome; white before a dock exists. */
    private int resultsChromeColor(int alpha) {
        SuggestionBarView dock = mDock;
        int base = dock == null ? Color.WHITE : dock.getLauncherTextColor();
        return ColorUtils.setAlphaComponent(base, alpha);
    }

    /** "No apps matched" over the quieter suggestion line, per the mock's no-results state. */
    private void bindNoResultsText() {
        String title = getResources().getString(
            com.termux.R.string.app_drawer_search_no_results_title);
        String body = getResources().getString(
            com.termux.R.string.app_drawer_search_no_results_body);
        SpannableStringBuilder text =
            new SpannableStringBuilder(title + "\n" + body);
        int flags = Spanned.SPAN_EXCLUSIVE_EXCLUSIVE;
        text.setSpan(new StyleSpan(Typeface.BOLD),
            0, title.length(), flags);
        text.setSpan(new ForegroundColorSpan(resultsChromeColor(0xCC)),
            0, title.length(), flags);
        text.setSpan(new RelativeSizeSpan(0.8f),
            title.length() + 1, text.length(), flags);
        text.setSpan(new ForegroundColorSpan(resultsChromeColor(0x73)),
            title.length() + 1, text.length(), flags);
        mNoResults.setText(text);
    }

    public void setDock(@Nullable SuggestionBarView dock) {
        if (mDock != null && mDock != dock) {
            mDock.setDrawerConfigChangedListener(null);
            mDock.setCategoryChoiceHandler(null);
        }
        mDock = dock;
        if (dock != null) {
            dock.setDrawerConfigChangedListener(this::onDockConfigChanged);
            dock.setCategoryChoiceHandler(this::showCategoryOverrideDialog);
        }
        mResultsLabel.setTextColor(resultsChromeColor(0x73));
        bindNoResultsText();
        mAdapter.setDock(dock);
        mHorizontalAdapter.setDock(dock);
        mCategoryView.setDock(dock);
        // The column borrows the dock's text colour and its row-haptics preference, so it has to be
        // told about a dock that arrived after the content was built.
        mColumn.setDock(dock);
        if (dock != null && mDragController == null) {
            mDragController = new AppDrawerDragController(dock, mDragOverlay, this);
            mAdapter.setDragController(mDragController, this::suppressCellClick);
            mCategoryView.getDetailAdapter().setPickupDelegate(mDragController);
            mCategoryView.getTileAdapter().setPickupDelegate(mDragController);
            mHorizontalAdapter.setClickGate(this::suppressCellClick);
            mHorizontalAdapter.setDragController(mDragController);
        }
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

    /** The list's scroll, as far as the search keyboard is concerned. */
    public interface KeyboardScrollListener {
        /** The finger scrolled the list {@code dy} pixels further down (its fling included). */
        void onListScrolledDown(int dy);
        /** That scroll has come to rest. */
        void onListScrollIdle();

        /**
         * The finger began dragging a list (past touch slop) or touched the A–Z strip: the user is
         * browsing, not typing, so the search keyboard goes. Never sent for a tap on a cell or the
         * search field.
         */
        default void onListDragStarted() {}
    }

    /** A list the finger started dragging: the keyboard's cue. Shared by every list. */
    private void notifyListDragStarted(boolean scrollable) {
        KeyboardScrollListener keyboard = mKeyboardScrollListener;
        if (keyboard != null) keyboard.onListDragStarted();
        // The same cue folds the search bar into its round button: the user is browsing now.
        foldSearchForBrowse(scrollable);
    }

    /**
     * Sends {@link KeyboardScrollListener#onListDragStarted} when {@code list} starts a drag, and
     * unfolds the search bar when the list is scrolled back to its top.
     */
    private void hideKeyboardWhenDragged(@NonNull RecyclerView list) {
        list.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                if (dy < 0 && mSearchCollapsed) unfoldSearchAtTop(recyclerView);
            }

            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                    notifyListDragStarted(recyclerView.canScrollVertically(1)
                        || recyclerView.canScrollVertically(-1));
                } else if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    unfoldSearchAtTop(recyclerView);
                }
            }
        });
    }

    public void setKeyboardScrollListener(@Nullable KeyboardScrollListener listener) {
        mKeyboardScrollListener = listener;
    }

    /**
     * The list's scroll took the search keyboard away: the reveal target drops to 0 and stays
     * there, whatever is typed, until the search is asked for again.
     */
    public void dismissSearchKeyboardByScroll() {
        if (mKeyboardDismissedByScroll) return;
        mKeyboardDismissedByScroll = true;
        notifyRevealTarget();
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
        // Every open starts with the full bar.
        if (interactive) unfoldSearch(AppDrawerSearchCollapsePolicy.Event.DRAWER_OPENED, false);
        if (!interactive) removeCallbacks(mAutoOpenOnlyMatch);
        if (!interactive) {
            if (mDragController != null) mDragController.cancel();
            cancelCellLongPresses();
            leaveEdit(AppDrawerEditModel.Leave.DRAWER_CLOSED);
            mCornerTab.reset();
            // The drawer is closing, not torn down: what it shows stays until it is gone
            // (resetDrawerFx), or Back blanks the card to "Nothing here yet" for the whole close.
            stopPresentationMotion(true);
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

    /**
     * Switches the query's owner. With the Android-keyboard search on, the hidden text field takes
     * the keyboard's edits and the search follows it; off, the field is emptied and left unfocused,
     * and the key stream drives the search as before. Set per open, before the field is focused.
     */
    public void setTextFieldSearch(boolean enabled) {
        if (mTextFieldSearch == enabled) return;
        mTextFieldSearch = enabled;
        AppDrawerSearchController search = mSearch;
        if (enabled) {
            mInput.mirror(search == null ? "" : search.query(), search == null ? 0 : search.caret());
        } else {
            if (mInput.hasFocus()) mInput.clearFocus();
            mInput.mirror("", 0);
        }
    }

    public boolean isTextFieldSearch() {
        return mTextFieldSearch;
    }

    /** The field the host focuses and shows the Android keyboard for. */
    @NonNull
    public AppDrawerSearchInputView searchInput() {
        return mInput;
    }

    /** Columns, cell and icon size. Re-resolved per open, never cached across configurations. */
    @Deprecated
    void setMetrics(@NonNull AppDrawerGridMetrics metrics) {
        setVerticalMetrics(metrics);
    }

    public void setVerticalMetrics(@NonNull AppDrawerGridMetrics metrics) {
        mVerticalColumns = Math.max(1, metrics.columns);
        // The categories search presents this grid as a single-column row list; the grid span is
        // restored the moment that presentation is put away.
        mLayoutManager.setSpanCount(mCategorySearchPresentation ? 1 : mVerticalColumns);
        // Two rows of holders past the viewport: enough that a fling never rebinds visible cells,
        // small enough that the shared icon cache is not asked to hold a second screenful.
        mGrid.setItemViewCacheSize(mVerticalColumns * 2);
        mAdapter.setMetrics(metrics);
    }

    public void setHorizontalMetrics(@NonNull AppDrawerHorizontalGridMetrics metrics) {
        mHorizontalAdapter.setMetrics(metrics);
        mHorizontalPager.clampSelectedPage();
        updatePageIndicator();
    }

    public void setCategoryMetrics(@NonNull AppDrawerCategoryGridMetrics metrics) {
        mCategoryView.setMetrics(metrics);
    }

    /** Vertical space already reserved by the pill/top gap and the existing bottom band. */
    public float horizontalPagerUsableHeight(float contentHeightPx) {
        LayoutParams params = (LayoutParams) mHorizontalPager.getLayoutParams();
        return AppDrawerContentInsetPolicy.pagerUsableHeightPx(contentHeightPx, params.topMargin,
            params.bottomMargin);
    }

    /** The drawer's side padding in pixels: the one inset every view type keeps above the card. */
    public int contentInsetPx() {
        return dp(PILL_MARGIN_H_DP);
    }

    /**
     * Ends every view type's content {@code bottomInsetPx} above the card's usable bottom (see
     * {@link AppDrawerContentInsetPolicy}). Set once per keyboard decision or open, never per
     * frame; each write compares first so an unchanged inset requests no layout.
     */
    public void setBottomInsetPx(int bottomInsetPx) {
        int inset = Math.max(0, bottomInsetPx);
        if (inset == mBottomInsetPx) return;
        mBottomInsetPx = inset;
        applyBottomChrome(resolveChrome());
    }

    /**
     * The bottom half of the chrome: the search bar's place (when it is down there), the margin the
     * content ends above it, and the inset the content keeps inside that margin. Every write
     * compares first; runs on an inset, placement or type change, never per frame.
     */
    private void applyBottomChrome(@NonNull AppDrawerChromeLayout.Result r) {
        int inset = r.innerBottomInsetPx;
        float rowTrailing = AppDrawerGridMetrics.rowBottomPx(mDensity);
        int listPadding = AppDrawerContentInsetPolicy.listBottomPaddingPx(inset, rowTrailing);
        if (mGrid.getPaddingBottom() != listPadding) mGrid.setPadding(0, 0, 0, listPadding);
        setBottomMargin(mGrid, r.contentBottomPx);
        setBottomMargin(mColumn, r.contentBottomPx);
        setBottomMargin(mCategoryView, r.contentBottomPx);
        // The pages never follow the fold: their band is fixed, with the floor that keeps the
        // button off the last row, so a swipe never re-lays a page.
        setBottomMargin(mHorizontalPager, r.contentBottomPx
            + Math.max(Math.round(pagerBottomBandPx(r.restInsetPx)), r.pagerFloorPx));
        setBottomMargin(mPageIndicator, r.contentBottomPx + r.restInsetPx);
        mCategoryView.setBottomInsetPx(inset);
        int gravity = (mSearchAtBottom ? Gravity.BOTTOM : Gravity.TOP) | Gravity.CENTER_HORIZONTAL;
        applySearchBarParams(mPill, r, gravity);
        applySearchBarParams(mInput, r, gravity);
        mChromeResult = r;
        applySearchMorph();
    }

    // ------------------------------------------------------------------ search bar fold

    /** Moves and redraws the pill for the current fold; no layout, no allocation. */
    private void applySearchMorph() {
        AppDrawerChromeLayout.Result r = mChromeResult;
        if (r == null) return;
        AppDrawerChromeLayout.morph(r, mSearchFold, mSearchMorph);
        mPill.applyMorph(mSearchFold, mSearchMorph);
    }

    /** The round button parks away from the A-Z strip: on the strip's far side, else the end side. */
    private boolean searchButtonAtLeft() {
        switch (mAzSide) {
            case RIGHT: return true;
            case LEFT: return false;
            default: return getLayoutDirection() == LAYOUT_DIRECTION_RTL;
        }
    }

    /** Folds the bar as the user browses; a list that fits never does (nothing would unfold it). */
    private void foldSearchForBrowse(boolean scrollable) {
        setSearchCollapsed(AppDrawerSearchCollapsePolicy.collapsedAfter(mSearchCollapsed,
            AppDrawerSearchCollapsePolicy.Event.SCROLL_STARTED, scrollable, hasQuery(),
            mEditModel.isEditing()), true);
    }

    /** Unfolds the bar for {@code event}; the policy keeps it full while a query shows or editing. */
    private void unfoldSearch(@NonNull AppDrawerSearchCollapsePolicy.Event event,
                              boolean animate) {
        if (!mSearchCollapsed && mSearchFold <= 0f) return;
        setSearchCollapsed(AppDrawerSearchCollapsePolicy.collapsedAfter(mSearchCollapsed, event,
            false, hasQuery(), mEditModel.isEditing()), animate);
    }

    /** The scrolled list reached its top: the bar comes back. */
    private void unfoldSearchAtTop(@NonNull RecyclerView list) {
        if (mSearchCollapsed && !list.canScrollVertically(-1)) {
            unfoldSearch(AppDrawerSearchCollapsePolicy.Event.SCROLLED_TO_TOP, true);
        }
    }

    /**
     * Sets the bar's resting shape and walks the fold there. The resting layout (the list's room
     * for the button, the hidden field out of the way) is set once at the start and compares first;
     * the top band keeps its size so the list never jumps under the finger.
     */
    private void setSearchCollapsed(boolean collapsed, boolean animate) {
        ValueAnimator animator = mSearchFoldAnimator;
        float target = collapsed ? 1f : 0f;
        boolean changed = mSearchCollapsed != collapsed;
        boolean heading = animate && animator != null && animator.isRunning();
        if (!changed && (mSearchFold == target || heading)) return;
        if (changed) {
            mSearchCollapsed = collapsed;
            // The field sits under the bar's old place; hidden, it neither takes a tap there nor
            // keeps the system keyboard's focus once the bar has folded.
            int visibility = collapsed ? INVISIBLE : VISIBLE;
            if (mInput.getVisibility() != visibility) mInput.setVisibility(visibility);
            applyBottomChrome(resolveChrome());
        }
        if (!animate || !isAttachedToWindow() || AnimationSpeed.isOff(getContext())) {
            if (animator != null) animator.cancel();
            mSearchFold = target;
            applySearchMorph();
            return;
        }
        if (animator == null) {
            animator = ValueAnimator.ofFloat(0f, 1f);
            animator.setInterpolator(new android.view.animation.DecelerateInterpolator());
            animator.addUpdateListener(a -> {
                mSearchFold = (float) a.getAnimatedValue();
                applySearchMorph();
            });
            mSearchFoldAnimator = animator;
        }
        animator.cancel();
        animator.setFloatValues(mSearchFold, target);
        animator.setDuration(AnimationSpeed.scale(getContext(),
            AppDrawerCategoryView.REORDER_SETTLE_MS));
        animator.start();
    }

    private void applySearchBarParams(@NonNull View bar, @NonNull AppDrawerChromeLayout.Result r,
                                      int gravity) {
        LayoutParams params = (LayoutParams) bar.getLayoutParams();
        int width = getWidth() > 0 ? r.searchWidthPx : LayoutParams.MATCH_PARENT;
        if (params.width == width && params.gravity == gravity
            && params.topMargin == r.searchTopPx && params.bottomMargin == r.searchBottomMarginPx) {
            return;
        }
        params.width = width;
        params.gravity = gravity;
        params.topMargin = r.searchTopPx;
        params.bottomMargin = r.searchBottomMarginPx;
        bar.setLayoutParams(params);
    }

    private static void setBottomMargin(@NonNull View view, int bottomMargin) {
        LayoutParams params = (LayoutParams) view.getLayoutParams();
        if (params.bottomMargin == bottomMargin) return;
        params.bottomMargin = bottomMargin;
        view.setLayoutParams(params);
    }

    @NonNull
    private AppDrawerChromeLayout.Result resolveChrome() {
        boolean shown = mViewType == AppDrawerViewType.DEFAULT;
        int switchHeight = Math.round(AppDrawerSectionSwitchGeometry.HEIGHT_DP * mDensity);
        return AppDrawerChromeLayout.resolve(new AppDrawerChromeLayout.Input(mDensity, getWidth(),
            shown, switchHeight, mSearchAtBottom, mBottomInsetPx, getHeight(),
            searchButtonAtLeft(), mSearchCollapsed));
    }

    /** Settings › Search bar at the bottom; read once per open. */
    public void setSearchAtBottom(boolean enabled) {
        if (mSearchAtBottom == enabled) return;
        mSearchAtBottom = enabled;
        applyViewType();
    }

    /** Dots, the row gap above them and the inset below: what the pager gives up at the bottom. */
    private float pagerBottomBandPx(int insetPx) {
        return AppDrawerContentInsetPolicy.pagerBottomBandPx(insetPx,
            AppDrawerPageIndicatorView.dotsHeightPx(mDensity),
            AppDrawerGridMetrics.rowBottomPx(mDensity));
    }

    /**
     * The type that is drawn and routed: Default is Vertical on its Apps section and Categories on
     * its Categories section, every other type is itself. No allocation; read freely.
     */
    @NonNull
    AppDrawerViewType presentedType() {
        if (mViewType != AppDrawerViewType.DEFAULT) return mViewType;
        return mSection == AppDrawerSection.CATEGORIES
            ? AppDrawerViewType.CATEGORIES : AppDrawerViewType.VERTICAL;
    }

    @NonNull
    public AppDrawerSection getSection() {
        return mSection;
    }

    /** The user moved to {@code section}: swaps the presentation and tells the callbacks. */
    public void setSection(@NonNull AppDrawerSection section) {
        if (mPagesActive) {
            // The pages slide there; the landing persists and reports it.
            mSectionPager.settleTo(section);
            return;
        }
        applySection(section, true);
    }

    /**
     * The stored section put back as the drawer opens: the same swap, but silent, so a restore never
     * writes the preference it was just read from.
     */
    public void restoreSection(@NonNull AppDrawerSection section) {
        if (mPagesActive) {
            mSectionPager.jumpTo(section);
            return;
        }
        applySection(section, false);
    }

    /**
     * True while Default shows Apps and Categories side by side: the Default type with no query
     * typed. A query shows the settled section's own presentation. A keyboard asked for with
     * nothing typed keeps the pages: that request outlives the keyboard (a scroll can put the
     * keyboard away and leave it set), and the pages must not stay frozen behind it.
     */
    public boolean isDefaultPagesActive() {
        return defaultPagesActive();
    }

    private boolean defaultPagesActive() {
        return mViewType == AppDrawerViewType.DEFAULT && !hasQuery();
    }

    /** Re-lays the type out when the pages gave way to a search, or came back, since last applied. */
    private void syncDefaultPages() {
        if (defaultPagesActive() == mPagesActive) return;
        applyViewType();
        if (mCallbacks != null) mCallbacks.onPagesPresentationChanged();
    }

    /** The pager landed (or was jumped): settle what the pages show and, for a turn, report it. */
    private void onPagerSettled(@NonNull AppDrawerSection section, boolean changed) {
        if (!mPagesActive) return;
        boolean differs = section != mSection;
        mSection = section;
        if (differs) {
            // The presented type changed: lower start, the strip and the grid's pickup follow it.
            applyViewType();
            applyPickupEnabled();
        } else {
            applyPageVisibility();
        }
        applyPageTransforms();
        if (changed && mCallbacks != null) mCallbacks.onSectionChanged(section);
    }

    /**
     * Draw-time position of the pages, from the pager's one number: Apps (grid and strip) slide
     * out as Categories slides in, mirrored in RTL. Only translationX is written.
     */
    private void applyPageTransforms() {
        float appsX = 0f;
        float categoriesX = 0f;
        if (mPagesActive) {
            float position = mSectionPager.position();
            float width = getWidth();
            float dir = getLayoutDirection() == LAYOUT_DIRECTION_RTL ? -1f : 1f;
            appsX = -position * width * dir;
            categoriesX = (1f - position) * width * dir;
        }
        mGrid.setTranslationX(appsX);
        mColumn.setTranslationX(appsX);
        mCategoryView.setTranslationX(categoriesX);
    }

    /**
     * Both pages are drawn while the pager moves, and only the settled one at rest: the other goes
     * INVISIBLE, never GONE, so it stays laid out and bound and the next drag needs no layout.
     */
    private void applyPageVisibility() {
        if (!mPagesActive) return;
        boolean rest = mSectionPager.isAtRest();
        boolean apps = !rest || mSectionPager.settledSection() == AppDrawerSection.APPS;
        boolean categories = !rest || mSectionPager.settledSection() == AppDrawerSection.CATEGORIES;
        setVisibilityIfChanged(mGrid, apps ? VISIBLE : INVISIBLE);
        if (mAzSide.shown()) setVisibilityIfChanged(mColumn, apps ? VISIBLE : INVISIBLE);
        setVisibilityIfChanged(mCategoryView, categories ? VISIBLE : INVISIBLE);
        // The content view does not clip, so the page sliding in would draw into the card's side
        // padding; it clips only while a page is in motion.
        if (getClipChildren() != !rest) setClipChildren(!rest);
    }

    private static void setVisibilityIfChanged(@NonNull View view, int visibility) {
        if (view.getVisibility() != visibility) view.setVisibility(visibility);
    }

    /** The grid's pickup follows the settled page: only the Apps page may pick an icon up. */
    private void applyPickupEnabled() {
        boolean enabled = !hasQuery()
            && (presentedType() == AppDrawerViewType.VERTICAL
                || presentedType() == AppDrawerViewType.HORIZONTAL);
        mAdapter.setPickupEnabled(enabled);
        mHorizontalAdapter.setPickupEnabled(enabled);
    }

    private void applySection(@NonNull AppDrawerSection section, boolean notify) {
        if (mSection == section) return;
        AppDrawerViewType from = presentedType();
        AppDrawerSection previous = mSection;
        mSection = section;
        AppDrawerViewType to = presentedType();
        mSection = previous;
        if (from == to) {
            mSection = section;
        } else {
            swapPresentation(mViewType, section);
        }
        if (notify && mCallbacks != null) mCallbacks.onSectionChanged(section);
    }

    public void setViewType(@NonNull AppDrawerViewType viewType) {
        if (mViewType == viewType) {
            applyViewType();
            return;
        }
        swapPresentation(viewType, mSection);
    }

    /**
     * Tears the current presented type down and brings the new one up. The fields still hold the
     * old values while the old presentation is stopped and cleared; they change after.
     */
    private void swapPresentation(@NonNull AppDrawerViewType viewType,
                                  @NonNull AppDrawerSection section) {
        dismissContextPopups();
        cancelCellLongPresses();
        setEditModel(mEditModel.onViewTypeChanged(mViewType, viewType));
        // Default's sections share one edit session: the pinned tab stays across the swap.
        if (!mEditModel.isEditing()) mCornerTab.reset();
        stopPresentationMotion();
        // Default's Apps page keeps the Categories tiles bound beside it; leaving them bound under
        // another type would hold their previews for nothing.
        if (mPagesActive) mCategoryView.reset();
        switch (presentedType()) {
            case VERTICAL:
                clearScrub();
                restoreCellAppearance();
                mPolicy.disarm();
                stopOverpullSpring();
                break;
            case HORIZONTAL:
                unbindAttachedHorizontalPages();
                mHorizontalAdapter.submit(Collections.emptyList());
                break;
            case CATEGORIES:
                mPolicy.disarm();
                stopOverpullSpring();
                break;
        }
        mViewType = viewType;
        mSection = section;
        applyViewType();
        submitVisibleResults(true);
        mCornerTab.refreshButtons();
    }

    /**
     * Halts whatever the current presentation has in flight — the pager's settle or the category
     * transition — before the drawer is torn down or the presentation swapped. The category
     * transition is cancelled outright: it must not keep animating beneath the closing plane, and
     * reset() suppresses the retained UP before releasing any holder, preserving the reentrant click
     * gate. The vertical grid has no motion of its own here.
     */
    private void stopPresentationMotion() {
        stopPresentationMotion(false);
    }

    /** @param keepContent a closing drawer: the categories keep their tiles until it is gone */
    private void stopPresentationMotion(boolean keepContent) {
        // A drag or settle is dropped back to the settled section with no animation, on close as on
        // a swap: nothing keeps moving under a closed drawer, and a close writes no section.
        endSectionStream();
        mSectionPager.cancel();
        switch (presentedType()) {
            case HORIZONTAL:
                cancelNestedClose();
                mHorizontalPager.stopForModeChange();
                break;
            case CATEGORIES:
                cancelNestedClose();
                if (keepContent) mCategoryView.stopForClose();
                else mCategoryView.reset();
                break;
            case VERTICAL:
                break;
        }
    }

    private void unbindAttachedHorizontalPages() {
        for (int i = 0; i < mHorizontalPager.getChildCount(); i++) {
            RecyclerView.ViewHolder holder =
                mHorizontalPager.getChildViewHolder(mHorizontalPager.getChildAt(i));
            if (holder instanceof AppDrawerHorizontalPageAdapter.PageHolder)
                ((AppDrawerHorizontalPageAdapter.PageHolder) holder).unbindAll();
        }
    }

    @NonNull
    public AppDrawerViewType getViewType() {
        return mViewType;
    }

    private void applyViewType() {
        applySectionSwitchLayout();
        applyViewTypeLayout();
        applyLowerStart();
    }

    /**
     * Stacks the top chrome as {@link AppDrawerChromeLayout} answers: the switch (Default only),
     * the search bar under it, and the content's top. Compared before it is set; runs where the
     * type or the search state changes, never from measure, layout or draw.
     */
    private void applySectionSwitchLayout() {
        boolean shown = mViewType == AppDrawerViewType.DEFAULT;
        AppDrawerChromeLayout.Result r = resolveChrome();
        setVisibilityIfChanged(mSectionSwitch, shown ? VISIBLE : GONE);
        mSectionSwitch.setPagesActive(shown && defaultPagesActive());
        mSectionSwitch.setMaxWidthPx(r.switchMaxWidthPx);
        mSectionSwitch.invalidate();
        if (shown) setTopMargin(mSectionSwitch, r.switchTopPx);
        mGridBaseTopMarginPx = r.contentTopPx;
        setTopMargin(mHorizontalPager, r.contentTopPx);
        setTopMargin(mCategoryView, r.contentTopPx);
        setTopMargin(mResultsLabel, r.contentTopPx);
        setTopMargin(mNoResults, r.contentTopPx + dp(NO_RESULTS_BELOW_CONTENT_DP));
        applyBottomChrome(r);
        applySwitchGlass();
    }

    private void applyChromeForWidth() {
        applySectionSwitchLayout();
        applyLowerStart();
    }

    private static void setTopMargin(@NonNull View view, int topMargin) {
        LayoutParams params = (LayoutParams) view.getLayoutParams();
        if (params.topMargin == topMargin) return;
        params.topMargin = topMargin;
        view.setLayoutParams(params);
    }

    /** A tap on a switch label: the pages slide there; a tap on the page already shown does nothing. */
    private void onSectionSwitchChosen(@NonNull AppDrawerSection section) {
        if (!mPagesActive || !mInteractive) return;
        setSection(section);
    }

    /** The ink the A-Z rope reads: the settled glass ink, else the launcher's text colour. */
    private int sectionSwitchInk() {
        // The search bar's own ink, so the two stacked controls read alike in every theme. The
        // dock's glass ink (the A–Z strip's) is an accent tint that all but vanished on the
        // card's grey in dark.
        return com.google.android.material.color.MaterialColors.getColor(mPill,
            com.google.android.material.R.attr.colorOnSurface,
            androidx.core.content.ContextCompat.getColor(getContext(),
                com.termux.R.color.termux_on_surface));
    }

    /** Settings › A–Z strip; read once per open. */
    public void setAzSide(@NonNull AppDrawerAzSide side) {
        if (mAzSide == side) return;
        mAzSide = side;
        applyViewType();
        applyColumnLetters();
    }

    /** Puts the strip on its edge, mirrored on the left, or takes it away. */
    private void applyAzColumn() {
        if (!mAzSide.shown()) {
            hideColumn();
            return;
        }
        mColumn.setMirrored(mAzSide.mirrored());
        LayoutParams columnParams = (LayoutParams) mColumn.getLayoutParams();
        int gravity = mAzSide.mirrored() ? Gravity.START : Gravity.END;
        if (columnParams.gravity != gravity) {
            columnParams.gravity = gravity;
            mColumn.setLayoutParams(columnParams);
        }
        mColumn.setVisibility(VISIBLE);
    }

    /** Settings › Start in the lower half; read once per open. */
    public void setLowerStart(boolean enabled) {
        if (mLowerStart == enabled) return;
        mLowerStart = enabled;
        applyLowerStart();
    }

    private int gridTopMargin() {
        return mGridBaseTopMarginPx + (mCategorySearchPresentation ? dp(RESULTS_LABEL_BAND_DP) : 0)
            + mLowerStartPx;
    }

    /**
     * Lowers the vertical list (and its letter strip) to the lower half, and puts it back while a
     * search is typed or being asked for. Compared before it is set; called from the paths that
     * change those inputs, never from measure, layout or draw.
     */
    private void applyLowerStart() {
        boolean searching = hasQuery() || mSearchRevealRequested;
        boolean portrait = getResources().getConfiguration().orientation
            != android.content.res.Configuration.ORIENTATION_LANDSCAPE;
        mLowerStartPx = presentedType() != AppDrawerViewType.VERTICAL ? 0
            : AppDrawerLowerStartPolicy.offsetPx(mLowerStart, portrait, searching, getHeight(),
                mGridBaseTopMarginPx);
        int top = gridTopMargin();
        LayoutParams gridParams = (LayoutParams) mGrid.getLayoutParams();
        if (gridParams.topMargin != top) {
            gridParams.topMargin = top;
            mGrid.setLayoutParams(gridParams);
        }
        LayoutParams columnParams = (LayoutParams) mColumn.getLayoutParams();
        if (columnParams.topMargin != top) {
            columnParams.topMargin = top;
            mColumn.setLayoutParams(columnParams);
        }
    }

    private void applyViewTypeLayout() {
        LayoutParams gridParams = (LayoutParams) mGrid.getLayoutParams();
        mPagesActive = defaultPagesActive();
        if (mPagesActive) {
            applyDefaultPagesLayout(gridParams);
            return;
        }
        // A search (or another type) freezes the pager at its settled section.
        mSectionPager.cancel();
        applyPageTransforms();
        if (getClipChildren()) setClipChildren(false);
        switch (presentedType()) {
            case VERTICAL:
                applyCategorySearchPresentation(false);
                gridParams.leftMargin = mAzSide.leftInsetPx(Math.round(mColumnWidthPx));
                gridParams.rightMargin = mAzSide.rightInsetPx(Math.round(mColumnWidthPx));
                mGrid.setLayoutParams(gridParams);
                mGrid.setVisibility(VISIBLE);
                mHorizontalPager.setVisibility(GONE);
                mCategoryView.setVisibility(GONE);
                applyAzColumn();
                applyColumnLetters();
                mPageIndicator.setVisibility(GONE);
                break;
            case HORIZONTAL:
                applyCategorySearchPresentation(false);
                mGrid.setVisibility(GONE);
                mHorizontalPager.setVisibility(VISIBLE);
                mCategoryView.setVisibility(GONE);
                hideColumn();
                updatePageIndicator();
                break;
            case CATEGORIES:
                gridParams.leftMargin = 0;
                gridParams.rightMargin = 0;
                mGrid.setLayoutParams(gridParams);
                boolean search = hasQuery();
                applyCategorySearchPresentation(search);
                mGrid.setVisibility(search ? VISIBLE : GONE);
                mHorizontalPager.setVisibility(GONE);
                mCategoryView.setVisibility(search ? GONE : VISIBLE);
                hideColumn();
                mPageIndicator.setVisibility(GONE);
                break;
        }
    }

    /**
     * Default with no search: the Apps grid with the strip and the Categories view side by side,
     * the grid keeping the vertical layout's strip margin. Nothing here runs per frame; the pager
     * moves the pages with transforms.
     */
    private void applyDefaultPagesLayout(@NonNull LayoutParams gridParams) {
        applyCategorySearchPresentation(false);
        int left = mAzSide.leftInsetPx(Math.round(mColumnWidthPx));
        int right = mAzSide.rightInsetPx(Math.round(mColumnWidthPx));
        if (gridParams.leftMargin != left || gridParams.rightMargin != right) {
            gridParams.leftMargin = left;
            gridParams.rightMargin = right;
            mGrid.setLayoutParams(gridParams);
        }
        mHorizontalPager.setVisibility(GONE);
        mPageIndicator.setVisibility(GONE);
        applyAzColumn();
        applyColumnLetters();
        // The pager follows the section the type was built for, unless it is mid-motion.
        if (mSectionPager.isAtRest() && mSectionPager.settledSection() != mSection)
            mSectionPager.jumpTo(mSection);
        applyPageVisibility();
        applyPageTransforms();
    }

    /**
     * The categories view type shows ranked results as rows — one column, a "N RESULTS" band above
     * them and a centred no-results state — while every other presentation keeps the plain grid.
     */
    private void applyCategorySearchPresentation(boolean active) {
        if (mCategorySearchPresentation != active) {
            mCategorySearchPresentation = active;
            mAdapter.setSearchRowPresentation(active, active ? this::categoryLabelFor : null);
            LayoutParams gridParams = (LayoutParams) mGrid.getLayoutParams();
            gridParams.topMargin = gridTopMargin();
            mGrid.setLayoutParams(gridParams);
        }
        mLayoutManager.setSpanCount(active ? 1 : mVerticalColumns);
        boolean empty = mVisibleResults.isEmpty();
        mResultsLabel.setVisibility(active ? VISIBLE : GONE);
        mNoResults.setVisibility(active && empty ? VISIBLE : GONE);
        if (active) {
            mResultsLabel.setText(empty
                ? getResources().getString(com.termux.R.string.app_drawer_search_no_results_label)
                : getResources().getQuantityString(
                    com.termux.R.plurals.app_drawer_search_result_count,
                    mVisibleResults.size(), mVisibleResults.size()));
        }
    }

    /** The classified bucket's label for a search row's category line, or null when unknown. */
    @Nullable
    private CharSequence categoryLabelFor(@NonNull LauncherAppEntry entry) {
        Integer labelRes = mCategoryLabelIds.get(entry.appRef.stableId());
        return labelRes == null ? null : getResources().getString(labelRes);
    }

    /** The buckets in the user's tile order; nothing stored leaves them as they came. */
    @NonNull
    private List<AppDrawerCategoryBucket> orderedByUser(@NonNull List<AppDrawerCategoryBucket> in) {
        List<String> stored = mCategoryOrderStore.read();
        if (stored.isEmpty() || in.size() < 2) return in;
        List<AppDrawerCategory> present = new ArrayList<>(in.size());
        for (AppDrawerCategoryBucket bucket : in) present.add(bucket.category);
        List<AppDrawerCategory> ordered = new AppDrawerCategoryOrder(stored).apply(present);
        List<AppDrawerCategoryBucket> out = new ArrayList<>(in.size());
        for (AppDrawerCategory category : ordered) {
            for (AppDrawerCategoryBucket bucket : in) {
                if (bucket.category == category) {
                    out.add(bucket);
                    break;
                }
            }
        }
        return Collections.unmodifiableList(out);
    }

    /** One write path for the buckets, so the search rows' category index can never go stale. */
    private void setCategoryBuckets(@NonNull List<AppDrawerCategoryBucket> buckets) {
        mCategoryBuckets = orderedByUser(buckets);
        mCategoryLabelIds.clear();
        for (AppDrawerCategoryBucket bucket : buckets) {
            // Synthetic buckets (Suggestions, Recently Added) duplicate apps that also live in a
            // real category; the row's category line names the real one.
            if (bucket.category.synthetic) continue;
            for (LauncherAppEntry entry : bucket.entries()) {
                String stableId = entry.appRef.stableId();
                if (!mCategoryLabelIds.containsKey(stableId))
                    mCategoryLabelIds.put(stableId, bucket.category.labelRes);
            }
        }
    }

    private void hideColumn() {
        mColumn.cancelScrub();
        mColumn.setActive(false);
        mColumn.resetRope();
        mColumn.setVisibility(GONE);
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

    /**
     * A pin or folder mutation landed in the repository. The catalogue itself is untouched, but the
     * mixed list is composed against the config snapshot, so an open drawer must recompose in place
     * — this is what makes a drag-created folder appear under the finger rather than on reopen.
     * Deferred while a drag is live: the recompose rebinds every cell, and yanking the source cell
     * out from under the platform drag would end it mid-flight.
     */
    private void onDockConfigChanged() {
        if (!isShown()) return; // a closed drawer recomposes when it opens
        if (mDragActive) {
            mPendingDockConfigRefresh = true;
            return;
        }
        mPendingDockConfigRefresh = false;
        // A folder/pin mutation does not change the catalogue. Re-submit the already-live search
        // result so this works even while the provider is between warm-up generations and updates
        // both pickup-capable adapters through their common composition path.
        submitVisibleResults(false);
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
        // While the provider reloads (a finished category sort, an icon pack, a hidden app) it
        // holds nothing; the drawer keeps what it shows until the warm-up's callback brings the
        // fresh catalogue, rather than show an empty drawer for that second.
        boolean loaded = provider.hasLoadedApps();
        mCategoryView.setCatalogueLoaded(loaded);
        if (!loaded) return;
        List<LauncherAppEntry> catalogue = AppDrawerSectionIndex.sortByLabel(provider.getAllApps());
        search.setCatalogue(catalogue);
        if (categoryBucketsLive()) mCategoryView.submitBuckets(mCategoryBuckets);
    }

    /** Settings › Remember position; read once per open. */
    public void setRememberPosition(boolean enabled) {
        mRememberPosition = enabled;
    }

    /**
     * The first visible position (list) or page (pager) of the full list, with the list's pixel
     * offset; zeros for a search, a category view or a drawer never scrolled.
     */
    private void captureReopenPosition() {
        mSavedPosition = 0;
        mSavedOffsetPx = 0;
        if (!mRememberPosition || hasQuery()) return;
        if (presentedType() == AppDrawerViewType.VERTICAL) {
            int first = mLayoutManager.findFirstVisibleItemPosition();
            View view = first < 0 ? null : mLayoutManager.findViewByPosition(first);
            mSavedPosition = Math.max(0, first);
            mSavedOffsetPx = view == null ? 0 : view.getTop() - mGrid.getPaddingTop();
        } else if (presentedType() == AppDrawerViewType.HORIZONTAL) {
            mSavedPosition = mHorizontalPager.getSelectedPage();
        }
    }

    private void restoreReopenPosition() {
        if (!mRememberPosition) return;
        if (presentedType() == AppDrawerViewType.VERTICAL) {
            int position = AppDrawerReopenPolicy.startPosition(true, mSavedPosition,
                mAdapter.getItemCount());
            if (position > 0) mLayoutManager.scrollToPositionWithOffset(position, mSavedOffsetPx);
        } else if (presentedType() == AppDrawerViewType.HORIZONTAL) {
            int page = AppDrawerReopenPolicy.startPosition(true, mSavedPosition,
                mHorizontalAdapter.getItemCount());
            if (page > 0) mHorizontalPager.setSelectedPage(page, false);
        }
    }

    /** Empties the query and puts the grid back to the top. For a drawer that has closed. */
    public void resetSearch() {
        captureReopenPosition();
        clearSearchState();
        restoreReopenPosition();
    }

    /**
     * A home press closed the drawer: no query in the model or the field, and the list back at the
     * top whatever "remember position" says.
     */
    public void resetSearchToTop() {
        clearSearchState();
        mInput.mirror("", 0);
        if (presentedType() == AppDrawerViewType.VERTICAL) {
            mGrid.stopScroll();
            mGrid.scrollToPosition(0);
        } else if (presentedType() == AppDrawerViewType.HORIZONTAL) {
            mHorizontalPager.setSelectedPage(0, false);
        }
    }

    private void clearSearchState() {
        AppDrawerSearchController search = mSearch;
        mSearchRevealRequested = false;
        mKeyboardDismissedByScroll = false;
        syncDefaultPages();
        removeCallbacks(mAutoOpenOnlyMatch);
        if (search != null) {
            search.reset();
        } else {
            applyResults(Collections.emptyList(), true);
        }
        notifyRevealTarget();
    }

    public void disarm() {
        mPolicy.disarm();
        mCategoryView.cancelGesture();
    }

    public boolean hasQuery() {
        AppDrawerSearchController search = mSearch;
        return search != null && search.hasQuery();
    }

    /** The Categories view is fed: it is the presentation, or Default's page beside the grid. */
    private boolean categoryBucketsLive() {
        return !hasQuery()
            && (presentedType() == AppDrawerViewType.CATEGORIES || defaultPagesActive());
    }

    /** True while the category tiles are the presentation: categories view type, no query typed. */
    private boolean categoryTilesShown() {
        return presentedType() == AppDrawerViewType.CATEGORIES && !hasQuery();
    }

    /** @return true when a non-empty query was cleared, i.e. when Back has already been spent */
    public boolean clearQueryIfPresent() {
        AppDrawerSearchController search = mSearch;
        if (search == null || !search.hasQuery()) return false;
        return search.clearQuery();
    }

    /** Back's second rung after search: collapse or reverse any category detail transition. */
    public boolean collapseCategoryIfNeeded() {
        return categoryTilesShown() && mCategoryView.collapseIfNeeded();
    }

    /** One internal hierarchy; one press performs at most one action. */
    public boolean handleBackInDrawer() {
        boolean editing = mEditModel.isEditing();
        // The corner tab is the top-most thing on the card, so the press goes to it first; while
        // editing it is pinned open and the first rung is leaving edit mode instead.
        if (!editing && mCornerTab.dismiss()) return true;
        boolean expanded = categoryTilesShown()
            && mCategoryView.expansionState() != AppDrawerCategoryExpansionModel.State.OVERVIEW;
        if (AppDrawerEditModel.backStep(true, editing, hasQuery(), expanded)
            == AppDrawerEditModel.BackStep.LEAVE_EDIT) {
            leaveEdit(AppDrawerEditModel.Leave.BACK);
            return true;
        }
        if (clearQueryIfPresent()) return true;
        return collapseCategoryIfNeeded();
    }

    // ------------------------------------------------------------------ edit mode

    public boolean isEditing() {
        return mEditModel.isEditing();
    }

    /** Enters edit mode; the corner tab swaps to the editing buttons and stays open. */
    public void enterEdit(@NonNull AppDrawerEditModel.Entry entry) {
        setEditModel(AppDrawerEditModel.enter(entry));
    }

    public void leaveEdit(@NonNull AppDrawerEditModel.Leave reason) {
        setEditModel(mEditModel.leave(reason));
    }

    /** Applies what the model says to the views, once per change. */
    private void setEditModel(@NonNull AppDrawerEditModel next) {
        AppDrawerEditModel previous = mEditModel;
        if (previous == next) return;
        mEditModel = next;
        if (!next.isEditing()) {
            dismissGridSizePopup();
            mTileLiftActive = false;
            mCategoryView.cancelReorder();
            removeCallbacks(mEditFolderHold);
            mEditFolderCell = null;
            mEditFolder = null;
        } else {
            // Tiles reorder only on the overview: an open category closes as editing starts.
            mCategoryView.collapseIfNeeded();
        }
        AppDrawerSearchController search = mSearch;
        if (search != null) search.setInputEnabled(next.searchEnabled());
        mPill.setAlpha(next.searchDimmed() ? SEARCH_DIMMED_ALPHA : 1f);
        // Edit mode dims and parks the bar where it stands: it never wears the round button.
        if (next.isEditing()) unfoldSearch(AppDrawerSearchCollapsePolicy.Event.EDIT_STARTED, false);
        applyEditOutlines(this, next.outlinesVisible());
        mCornerTab.onEditModelChanged();
    }

    @NonNull
    private String[] gridLabels(@NonNull String[] values) {
        String[] labels = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            labels[i] = AppDrawerGridChoices.AUTO.equals(values[i])
                ? getResources().getString(com.termux.R.string.settings_drawer_grid_auto) : values[i];
        }
        return labels;
    }

    /** The grid-size button's text: columns, and rows where the type has them. */
    @NonNull
    private String gridSizeTabText() {
        Callbacks callbacks = mCallbacks;
        String[] columnLabels = gridLabels(AppDrawerGridChoices.COLUMN_VALUES);
        int column = callbacks == null ? 0 : callbacks.gridColumnIndex();
        String columns = columnLabels[Math.max(0, Math.min(columnLabels.length - 1, column))];
        if (!AppDrawerGridChoices.showsRows(mViewType)) return columns;
        String[] rowLabels = gridLabels(AppDrawerGridChoices.ROW_VALUES);
        int row = callbacks == null ? 0 : callbacks.gridRowIndex();
        String rows = rowLabels[Math.max(0, Math.min(rowLabels.length - 1, row))];
        return getResources().getString(com.termux.R.string.app_drawer_corner_grid_size_tab,
            columns, rows);
    }

    /** The wheels, hanging off the tab; each stop writes the preferences and the grid re-lays. */
    private void showGridSizePopup(@NonNull android.graphics.RectF tab) {
        dismissGridSizePopup();
        Callbacks callbacks = mCallbacks;
        if (callbacks == null) return;
        boolean rows = AppDrawerGridChoices.showsRows(mViewType);
        mGridSizePopup = com.termux.app.launcher.widget.WidgetGridSizePopup.show(this, tab,
            com.termux.app.launcher.widget.WidgetGridSizePopup.WheelSpec.choices(
                gridLabels(AppDrawerGridChoices.COLUMN_VALUES), callbacks.gridColumnIndex()),
            rows ? com.termux.app.launcher.widget.WidgetGridSizePopup.WheelSpec.choices(
                gridLabels(AppDrawerGridChoices.ROW_VALUES), callbacks.gridRowIndex()) : null,
            (columnIndex, rowIndex) -> {
                Callbacks current = mCallbacks;
                if (current == null) return;
                current.onGridSizeChosen(columnIndex, rows ? rowIndex : -1);
                mCornerTab.refreshButtons();
            });
    }

    private void dismissGridSizePopup() {
        if (mGridSizePopup != null) {
            mGridSizePopup.dismiss();
            mGridSizePopup = null;
        }
    }

    /** One pass over the attached cells and tiles; recycled ones read the state when they attach. */
    private static void applyEditOutlines(@NonNull ViewGroup group, boolean on) {
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child instanceof AppDrawerAppCellView) {
                ((AppDrawerAppCellView) child).setEditOutline(on);
            } else if (child instanceof AppDrawerCategoryTileView) {
                ((AppDrawerCategoryTileView) child).setEditOutline(on);
            } else if (child instanceof ViewGroup) {
                applyEditOutlines((ViewGroup) child, on);
            }
        }
    }

    /** True while the drawer {@code view} sits in is being edited; read at a tap, never per frame. */
    static boolean isEditingAround(@NonNull View view) {
        for (android.view.ViewParent parent = view.getParent(); parent != null;
             parent = parent.getParent()) {
            if (parent instanceof AppDrawerContentView) {
                return ((AppDrawerContentView) parent).mEditModel.isEditing();
            }
        }
        return false;
    }

    /**
     * The search-keyboard reveal target: 1 while there is something to type into the pill for, 0
     * otherwise. The controller owns the spring that chases it; this is only the target.
     */
    public float getRevealFraction() {
        if (mKeyboardDismissedByScroll) return 0f;
        return mSearchRevealRequested || hasQuery() ? 1f : 0f;
    }

    /**
     * Launches the highlighted result, or the first when nothing is highlighted: what Enter does on
     * any of the three intake channels.
     */
    public boolean launchHighlightedResult() {
        AppDrawerSearchController search = mSearch;
        SuggestionBarView dock = mDock;
        LauncherAppEntry target = search == null ? null : search.commitTarget();
        if (dock == null || target == null) return false;
        int index = search.highlightedIndex();
        return dock.launchEntryFromDrawer(cellViewAt(Math.max(0, index)), target);
    }

    @Nullable
    private View cellViewAt(int position) {
        switch (presentedType()) {
            case HORIZONTAL:
                return mHorizontalAdapter.iconAt(mHorizontalPager, position);
            case CATEGORIES:
                if (!hasQuery()) return null;
                break;
            case VERTICAL:
                break;
        }
        RecyclerView.ViewHolder holder = mGrid.findViewHolderForAdapterPosition(position);
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

    @Override
    public void onSearchHighlightChanged(int oldIndex, int newIndex) {
        if (presentedType() == AppDrawerViewType.HORIZONTAL) {
            mHorizontalAdapter.moveHighlight(newIndex);
            int page = mHorizontalAdapter.pageOf(newIndex);
            if (page >= 0 && page != mHorizontalPager.getSelectedPage()) {
                mHorizontalPager.setSelectedPage(page, true);
                updatePageIndicator();
            }
        } else {
            mAdapter.moveHighlight(newIndex);
            if (newIndex >= 0) mGrid.scrollToPosition(newIndex);
        }
    }

    @Override
    public int searchColumns() {
        switch (presentedType()) {
            case HORIZONTAL:
                AppDrawerHorizontalGridMetrics metrics = mHorizontalAdapter.getMetrics();
                return metrics == null ? 1 : Math.max(1, metrics.columns);
            case VERTICAL:
                return mCategorySearchPresentation ? 1 : mVerticalColumns;
            case CATEGORIES:
            default:
                return 1;
        }
    }

    private void applyResults(@NonNull List<LauncherAppEntry> results, boolean queryChanged) {
        AppDrawerSearchController search = mSearch;
        int highlight = search == null ? AppDrawerSearchSelection.NONE : search.highlightedIndex();
        mAdapter.presetHighlight(highlight);
        mHorizontalAdapter.presetHighlight(highlight);
        mPill.setQuery(search == null ? "" : search.query(), search == null ? 0 : search.caret());
        // Results showing: the full bar, whatever folded it.
        if (hasQuery()) unfoldSearch(AppDrawerSearchCollapsePolicy.Event.QUERY_CHANGED, true);
        if (mTextFieldSearch) {
            mInput.mirror(search == null ? "" : search.query(), search == null ? 0 : search.caret());
        }
        // An empty-query result is the one canonical sorted catalogue. Classifying here keeps
        // package refreshes, direct search-controller fixtures and query-clear all on the same
        // atomic model path; ranked non-empty results never replace the category model.
        if (!hasQuery()) {
            classifyCatalogue(results, false);
        }
        // The list identity changed under whatever menu was open, and every cell it was anchored to
        // is about to be rebound.
        dismissContextPopups();
        cancelCellLongPresses();
        // One pass over the list, once per submitted list rather than once per scrub frame. Built
        // even while a query is up, where its scroll targets would be a lie — a ranked list is
        // ordered by match quality, not by letter — because the column is inactive there and nothing
        // can ask it for one.
        mSectionIndex = AppDrawerSectionIndex.build(results);
        mVisibleResults = new ArrayList<>(results);
        boolean wasSearchRows = mCategorySearchPresentation;
        // A search taking Default's pages away, or giving them back, re-lays the type out first.
        syncDefaultPages();
        if (presentedType() == AppDrawerViewType.CATEGORIES) {
            if (hasQuery()) mCategoryView.cancelForSearch();
            else if (mGrid.getVisibility() == VISIBLE && (wasSearchRows || !mPagesActive))
                recycleSearchGridHolders();
            applyViewType();
        }
        submitVisibleResults(queryChanged);
        // The list identity changed under the finger, and the scroll target the scrub was driving
        // no longer means the same app.
        clearScrub();
        if (queryChanged && (presentedType() == AppDrawerViewType.VERTICAL
            || (presentedType() == AppDrawerViewType.CATEGORIES && hasQuery()))) {
            mGrid.scrollToPosition(0);
            // A different list is a different scroll: an arming earned against the previous one is
            // no longer a promise about anything.
            mPolicy.disarm();
            stopOverpullSpring();
        }
        // Only a query that was just emptied lets the keyboard go. A catalogue push with nothing
        // typed arrives here too — the provider's warm-up callback lands a beat after every open —
        // and a keyboard asked for on the open, or by a pill tap, must survive it.
        if (queryChanged && !hasQuery()) {
            mSearchRevealRequested = false;
            syncDefaultPages();
        }
        scheduleOnlyMatchOpen(results.size());
        applyLowerStart();
        notifyRevealTarget();
    }

    /** Sets whether a search that narrows to one app opens it after a pause. */
    public void setOpenOnlyMatch(boolean enabled) {
        if (mOpenOnlyMatch == enabled) return;
        mOpenOnlyMatch = enabled;
        if (!enabled) removeCallbacks(mAutoOpenOnlyMatch);
    }

    /** Every result list restarts the pause, so typing on keeps the lone match from opening. */
    private void scheduleOnlyMatchOpen(int resultCount) {
        removeCallbacks(mAutoOpenOnlyMatch);
        AppDrawerSearchController search = mSearch;
        if (search == null || !mInteractive) return;
        if (AppDrawerOnlyMatchPolicy.shouldAutoOpen(mOpenOnlyMatch, search.query(), resultCount)) {
            postDelayed(mAutoOpenOnlyMatch, AppDrawerOnlyMatchPolicy.AUTO_OPEN_DELAY_MS);
        }
    }

    private void autoOpenOnlyMatch() {
        AppDrawerSearchController search = mSearch;
        if (!mInteractive || search == null) return;
        if (AppDrawerOnlyMatchPolicy.shouldAutoOpen(mOpenOnlyMatch, search.query(),
            search.results().size())) {
            launchHighlightedResult();
        }
    }

    private void submitVisibleResults(boolean resetPosition) {
        applyPickupEnabled();
        // Default's pages keep both renderers bound, whichever section is settled.
        boolean pages = defaultPagesActive();
        List<AppDrawerItem> mixed = mixedItems();
        switch (pages ? AppDrawerViewType.VERTICAL : presentedType()) {
            case VERTICAL:
                AppDrawerSectionIndex mixedIndex = AppDrawerSectionIndex.build(indexEntries(mixed));
                mSectionIndex = mixedIndex;
                mAdapter.submitItems(mixed, mixedIndex);
                applyColumnLetters();
                if (resetPosition) mGrid.scrollToPosition(0);
                if (pages) mCategoryView.submitBuckets(mCategoryBuckets);
                return;
            case HORIZONTAL:
                int page = resetPosition ? 0 : mHorizontalPager.getSelectedPage();
                mHorizontalPager.stopScroll();
                mHorizontalAdapter.submitItems(mixed);
                mHorizontalPager.setSelectedPage(page, false);
                updatePageIndicator();
                return;
            case CATEGORIES:
                if (hasQuery()) {
                    mAdapter.submit(mVisibleResults, null);
                    if (resetPosition) mGrid.scrollToPosition(0);
                } else {
                    mAdapter.submit(Collections.emptyList(), null);
                    mCategoryView.submitBuckets(mCategoryBuckets);
                }
                applyViewType();
                return;
        }
    }

    @NonNull
    private List<AppDrawerItem> mixedItems() {
        SuggestionBarView dock = mDock;
        boolean pages = defaultPagesActive();
        if (dock == null || hasQuery()
            || (!pages && presentedType() == AppDrawerViewType.CATEGORIES))
            return AppDrawerItemComposer.appsOnly(mVisibleResults);
        return AppDrawerItemComposer.compose(mVisibleResults, dock.getLauncherConfigSnapshot(),
            true, pages ? AppDrawerViewType.VERTICAL : presentedType());
    }

    @NonNull
    private static List<LauncherAppEntry> indexEntries(@NonNull List<AppDrawerItem> items) {
        List<LauncherAppEntry> entries = new ArrayList<>(items.size());
        for (AppDrawerItem item : items) {
            if (item.app != null) entries.add(item.app);
            else entries.add(new LauncherAppEntry(new AppRef("folder." + item.stableId, ""),
                item.folder == null ? "Folder" : item.folder.title, null));
        }
        return entries;
    }

    /** Drops every attached, cached and pooled search holder before category previews bind. */
    private void recycleSearchGridHolders() {
        for (int i = 0; i < mGrid.getChildCount(); i++) {
            RecyclerView.ViewHolder holder = mGrid.getChildViewHolder(mGrid.getChildAt(i));
            if (holder instanceof AppDrawerAppsAdapter.Cell)
                ((AppDrawerAppsAdapter.Cell) holder).cell.unbind();
        }
        // Detaching the adapter makes RecyclerView recycle its attached and cached holders even
        // though this surface is about to become GONE and therefore will not run another layout.
        mGrid.setAdapter(null);
        mGrid.setItemViewCacheSize(0);
        mGrid.getRecycledViewPool().clear();
        mGrid.setItemViewCacheSize(AppDrawerGridMetrics.MIN_COLUMNS * 2);
        mGrid.setAdapter(mAdapter);
    }

    private void updatePageIndicator() {
        int pages = mHorizontalAdapter.getItemCount();
        mPageIndicator.setPageCount(pages);
        mPageIndicator.setSelectedPage(mHorizontalPager.getSelectedPage());
        if (presentedType() != AppDrawerViewType.HORIZONTAL) mPageIndicator.setVisibility(GONE);
    }

    private void notifyRevealTarget() {
        Runnable listener = mRevealListener;
        if (listener != null) listener.run();
    }

    private void dismissContextPopups() {
        SuggestionBarView dock = mDock;
        if (dock != null) dock.dismissContextPopups();
    }

    /** Live-layout apply cancels all B-6 state before changing managers or adapters. */
    public void cancelTransientFolderState() {
        cancelCellLongPresses();
        dismissContextPopups();
        cancelNestedClose();
        mHorizontalPager.stopForModeChange();
        if (mDragController != null) mDragController.cancel();
    }

    /** One post-config list submission against the existing adapters/content tree. */
    public void rebindCurrentResults() {
        submitVisibleResults(false);
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
        RecyclerView surface;
        switch (presentedType()) {
            case VERTICAL:
                surface = mGrid;
                break;
            case HORIZONTAL:
                surface = mHorizontalPager;
                break;
            case CATEGORIES:
                surface = hasQuery() ? mGrid : mCategoryView.activeRecyclerView();
                if (surface == null) {
                    mCategoryView.suppressClicks();
                    return;
                }
                break;
            default:
                return;
        }
        surface.cancelLongPress();
        for (int i = 0; i < surface.getChildCount(); i++) {
            View child = surface.getChildAt(i);
            if (child == null) continue;
            child.cancelLongPress();
            if (child instanceof ViewGroup) {
                ViewGroup group = (ViewGroup) child;
                for (int j = 0; j < group.getChildCount(); j++) {
                    View cell = group.getChildAt(j);
                    if (cell != null) cell.cancelLongPress();
                }
            }
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
        // On Default's pages the strip belongs to the Apps page whichever page is settled: it is
        // active (and drawn) while it slides in.
        boolean stripPage = presentedType() == AppDrawerViewType.VERTICAL || defaultPagesActive();
        mColumn.setActive(stripPage && mAzSide.shown() && !hasQuery() && letterCount >= 2);
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

    /** This touch on the strip has already sent the keyboard its cue; cleared when it ends. */
    private boolean mScrubSentKeyboard;

    @Override
    public void onScrubLetterChanged(char letter) {
        // The first letter of a touch on the strip is browsing: the keyboard goes the way a list
        // drag sends it (the controller keeps one that was up before the drawer opened).
        if (!mScrubSentKeyboard) {
            mScrubSentKeyboard = true;
            notifyListDragStarted(true);
        }
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
        // The walk above runs against the pre-scroll children; the jump above relaid them out. One
        // more walk after that layout is what makes a cell that merely moved — rather than being
        // rebound — carry the new letter's appearance.
        mGrid.post(this::applyScrubHighlight);
        requestFrames();
    }

    @Override
    public void onScrubEnded() {
        mScrubSentKeyboard = false;
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
        switch (presentedType()) {
            case HORIZONTAL:
                return false;
            case CATEGORIES:
                return mCategoryView.advance(dt, reduced);
            case VERTICAL:
                float delta = Spring.clampDelta(dt);
                boolean ropeMoving = mColumn.advance(p, delta, reduced);
                boolean scrubMoving = mScrubSpring.tick(reduced, delta);
                if (!scrubMoving && mScrubSpring.target == 0f) {
                    mScrubSpring.reset(0f);
                    mScrubLetter = '\0';
                }
                applyScrubHighlight();
                return ropeMoving || scrubMoving;
            default:
                return false;
        }
    }

    /** Drops the rope and the highlight. For a drawer that has closed. */
    public void resetDrawerFx() {
        switch (presentedType()) {
            case VERTICAL:
                clearScrub();
                mColumn.resetRope();
                break;
            case HORIZONTAL:
                break;
            case CATEGORIES:
                mCategoryView.reset();
                break;
        }
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
        if (!mEditModel.searchEnabled()) return;
        clearQueryIfPresent();
    }

    @Override
    public void onSearchPillTapped() {
        if (!mEditModel.searchEnabled()) return;
        // The round button opens back into the bar before the keyboard is asked for.
        unfoldSearch(AppDrawerSearchCollapsePolicy.Event.BUTTON_TAPPED, true);
        requestSearchKeyboard();
    }

    /**
     * Brings the keyboard up for the search, as a pill tap does. The pill is the only thing on
     * screen that says "type here", and with the plane covering the keyboard band there is
     * otherwise nowhere for the first keystroke to come from.
     *
     * <p>Also the drawer's keyboard-on-open preference, which asks for this on the frame the open
     * is committed rather than waiting for the tap.
     */
    public void requestSearchKeyboard() {
        mSearchRevealRequested = true;
        mKeyboardDismissedByScroll = false;
        syncDefaultPages();
        applyLowerStart();
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
     * @return true when the grid or the column owns it; false for chrome — the pill, the margins,
     *     the horizontal pager's bottom band, and the column's strip while it is inactive — where
     *     the plane's own close drag runs exactly as it did in B-1
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
        switch (presentedType()) {
            case VERTICAL:
                return AppDrawerTouchRegions.resolve(localX, localY, boundsOf(mGrid),
                    boundsOf(mColumn), mInteractive, isColumnActive());
            case HORIZONTAL:
                return AppDrawerTouchRegions.resolve(localX, localY, boundsOf(mHorizontalPager),
                    null, mInteractive, false);
            case CATEGORIES:
                if (hasQuery())
                    return AppDrawerTouchRegions.resolve(localX, localY, boundsOf(mGrid), null,
                        mInteractive, false);
                float x = localX - mCategoryView.getLeft();
                float y = localY - mCategoryView.getTop();
                return AppDrawerCategoryTouchRegions.isContentOwned(mCategoryView.touchPart(x, y))
                    ? AppDrawerTouchRegions.Region.GRID : AppDrawerTouchRegions.Region.CHROME;
            default:
                return AppDrawerTouchRegions.Region.CHROME;
        }
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
        int terminalAction = ev.getActionMasked();
        boolean terminal = terminalAction == MotionEvent.ACTION_UP
            || terminalAction == MotionEvent.ACTION_CANCEL;
        try {
            if (mTileLiftActive && !mCancellingContent) return handleTileLiftTouch(ev);
            if (!mCancellingContent && mCornerTab.filter(ev) == AppDrawerCornerTab.CONSUME) {
                return true;
            }
            boolean handled = dispatchTouchEventObserved(ev);
            // A corner armed on a down nothing under it took (the card's edge below the grid, a
            // gap between tiles): keep the stream, or its lift goes to the plane and the tab
            // never opens.
            if (!handled && terminalAction == MotionEvent.ACTION_DOWN && mCornerTab.isArmed()) {
                return true;
            }
            return handled;
        } finally {
            if (terminal) mSuppressCellClickDuringTerminalDispatch = false;
        }
    }

    /** The chrome that answers its own taps, in this view's coordinates; the count of numbers written. */
    private int fillChromeRects(@NonNull float[] out) {
        int n = 0;
        View[] chrome = {mPill, mInput, mSectionSwitch, mColumn};
        for (View view : chrome) {
            if (view == null || view.getVisibility() != VISIBLE || n + 4 > out.length) continue;
            if (view == mPill) {
                // Folded, the hold is blocked by the round button, not by the bar's old place.
                mPill.getDrawnRect(mPillRect);
                out[n++] = mPillRect.left;
                out[n++] = mPillRect.top;
                out[n++] = mPillRect.right;
                out[n++] = mPillRect.bottom;
                continue;
            }
            out[n++] = view.getX();
            out[n++] = view.getY();
            out[n++] = view.getX() + view.getWidth();
            out[n++] = view.getY() + view.getHeight();
        }
        return n;
    }

    /** The corner's hold fired: the cells and the grid are told the touch is over. */
    private void cancelContentTouch(float x, float y) {
        long now = SystemClock.uptimeMillis();
        MotionEvent cancel = MotionEvent.obtain(now, now, MotionEvent.ACTION_CANCEL, x, y, 0);
        mCancellingContent = true;
        try {
            dispatchTouchEvent(cancel);
            cancelCellLongPresses();
        } finally {
            mCancellingContent = false;
            cancel.recycle();
        }
    }

    private boolean dispatchTouchEventObserved(@NonNull MotionEvent ev) {
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
        mLastTouchX = ev.getX();
        mLastTouchY = ev.getY();
        if (mEditFolderCell != null) trackEditFolder(ev, action);
        // The highlight is a keyboard affordance: a touch on the results puts it away (the pill is
        // the one place a finger lands while typing).
        if (action == MotionEvent.ACTION_DOWN && mSearch != null
            && mSearch.highlightedIndex() >= 0 && (mSearchAtBottom ? ev.getY() < mPill.getTop() : ev.getY() > mPill.getBottom())) {
            mSearch.clearHighlight();
        }
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
            mGestureViewType = presentedType();
            mGestureCategorySearch = mGestureViewType == AppDrawerViewType.CATEGORIES && hasQuery();
            mGestureCategoryPart = AppDrawerCategoryTouchRegions.Part.OUTSIDE;
            mGestureRecycler = null;
            mOverpullSurface = null;
            AppDrawerTouchRegions.Region region = regionAt(ev.getX(), ev.getY());
            AppDrawerItem downItem = drawerItemAt(ev.getX(), ev.getY());
            mGestureDragStableId = downItem == null ? null : downItem.stableId;
            // Folders join apps here: a folder tile is picked up the same way, it just travels to a
            // free position instead of merging into what it lands on.
            mGestureDragEligible = mInteractive && !hasQuery() && downItem != null;
            mDragPolicy = new AppDrawerDragPolicy(new AppDrawerDragPolicy.FrozenDown(
                mGestureViewType, mInteractive, !hasQuery(), mGestureDragEligible,
                mGestureDragStableId));
            if (region == AppDrawerTouchRegions.Region.COLUMN || mSectionStream) {
                // A stream the section pager caught is this view's own, like the column's: no child
                // saw the down, so no nested scroll will ever stop it. The column owns this stream from here to its UP. onNestedPreScroll is already
                // gated on mDownOverGrid, so a scrub can never report a close with no change to it.
                mDownOverGrid = false;
                // mGestureActive is deliberately left false. The recycler never sees this stream, so
                // no onStopNestedScroll ever arrives to settle it; a stale stop arriving later would
                // otherwise end a gesture that never began and spend the arming a real pull earned.
                mPolicy.disarm();
            } else {
                mDownOverGrid = region == AppDrawerTouchRegions.Region.GRID;
                mGestureActive = true;
                switch (mGestureViewType) {
                    case VERTICAL:
                        mGestureRecycler = mGrid;
                        mOverpullSurface = mGrid;
                        beginClosePolicy(mGrid);
                        break;
                    case HORIZONTAL:
                        mGestureRecycler = mHorizontalPager;
                        break;
                    case CATEGORIES:
                        if (mGestureCategorySearch) {
                            mGestureRecycler = mGrid;
                            mOverpullSurface = mGrid;
                            beginClosePolicy(mGrid);
                        } else {
                            float categoryX = ev.getX() - mCategoryView.getLeft();
                            float categoryY = ev.getY() - mCategoryView.getTop();
                            mGestureCategoryPart = mCategoryView.touchPart(categoryX, categoryY);
                            mGestureRecycler = mCategoryView.activeRecyclerView();
                            boolean atTop = mGestureRecycler == null
                                || !mGestureRecycler.canScrollVertically(-1);
                            mCategoryView.beginTouchStream(mGestureCategoryPart, mDownRawY, atTop);
                            if (mGestureCategoryPart
                                == AppDrawerCategoryTouchRegions.Part.OVERVIEW_LIST
                                || mGestureCategoryPart
                                == AppDrawerCategoryTouchRegions.Part.EXPAND_ACTION) {
                                mOverpullSurface = mCategoryView.getOverview();
                                beginClosePolicy(mCategoryView.getOverview());
                            }
                        }
                        break;
                }
            }
            beginEditGesture(ev, region, downItem);
        }
        return handled;
    }

    // ------------------------------------------------------------------ edit-mode lift

    private static boolean hits(@Nullable View view, float x, float y) {
        return view != null && view.getVisibility() == VISIBLE
            && x >= view.getX() && x < view.getX() + view.getWidth()
            && y >= view.getY() && y < view.getY() + view.getHeight();
    }

    /**
     * Hands the down to the edit gesture policy and acts on its first answer: an app lifts at once
     * through the same pickup a long press starts; a folder waits for a tap, a move or a hold; a
     * tile lifts for reordering. Runs after the down's snapshot, which the pickup is gated on.
     */
    private void beginEditGesture(@NonNull MotionEvent ev,
                                  @NonNull AppDrawerTouchRegions.Region region,
                                  @Nullable AppDrawerItem downItem) {
        removeCallbacks(mEditFolderHold);
        mEditFolderCell = null;
        mEditFolder = null;
        boolean editing = mEditModel.isEditing();
        float x = ev.getX();
        float y = ev.getY();
        AppDrawerEditGesturePolicy.Touched touched = AppDrawerEditGesturePolicy.Touched.GAP;
        View cell = null;
        View tile = null;
        if (editing) {
            if (region == AppDrawerTouchRegions.Region.COLUMN) {
                touched = AppDrawerEditGesturePolicy.Touched.AZ_INDEX;
            } else if (hits(mPill, x, y) || hits(mInput, x, y)) {
                touched = AppDrawerEditGesturePolicy.Touched.SEARCH_BAR;
            } else if (hits(mSectionSwitch, x, y)) {
                touched = AppDrawerEditGesturePolicy.Touched.SECTION_SWITCH;
            } else if (presentedType() == AppDrawerViewType.CATEGORIES) {
                if (!mGestureCategorySearch) {
                    tile = mCategoryView.tileViewAt(x - mCategoryView.getLeft(),
                        y - mCategoryView.getTop());
                }
                if (tile != null) touched = AppDrawerEditGesturePolicy.Touched.TILE_BODY;
            } else if (downItem != null) {
                cell = cellViewAt(x, y);
                if (cell != null) {
                    touched = downItem.folder != null
                        ? AppDrawerEditGesturePolicy.Touched.FOLDER_CELL
                        : AppDrawerEditGesturePolicy.Touched.APP_CELL;
                }
            }
        }
        AppDrawerEditGesturePolicy.Action action = mEditGesture.begin(editing, mViewType, mSection,
            touched, getLayoutDirection() == LAYOUT_DIRECTION_RTL, mCategoryView.isOverview());
        switch (action) {
            case LIFT_APP:
                if (cell != null && downItem != null && downItem.app != null)
                    liftApp(cell, downItem.app);
                break;
            case LIFT_TILE:
                if (tile instanceof AppDrawerCategoryTileView)
                    liftTile((AppDrawerCategoryTileView) tile, x, y);
                break;
            case PENDING:
                if (cell != null && downItem != null && downItem.folder != null) {
                    mEditFolderCell = cell;
                    mEditFolder = downItem.folder;
                    mEditDownX = x;
                    mEditDownY = y;
                    mEditDownTime = ev.getEventTime();
                    postDelayed(mEditFolderHold, AppDrawerEditGesturePolicy.HOLD_LIFT_MS);
                }
                break;
            default:
                break;
        }
    }

    /** The cell view under a point of the vertical grid or the pages grid, or null. */
    @Nullable
    private View cellViewAt(float x, float y) {
        AppDrawerViewType type = presentedType();
        if (type == AppDrawerViewType.VERTICAL) {
            return mGrid.findChildViewUnder(x - mGrid.getLeft(), y - mGrid.getTop());
        }
        if (type != AppDrawerViewType.HORIZONTAL) return null;
        View pageView = mHorizontalPager.findChildViewUnder(x - mHorizontalPager.getLeft(),
            y - mHorizontalPager.getTop());
        if (pageView == null) return null;
        RecyclerView.ViewHolder raw = mHorizontalPager.getChildViewHolder(pageView);
        if (!(raw instanceof AppDrawerHorizontalPageAdapter.PageHolder)) return null;
        AppDrawerHorizontalPageAdapter.PageHolder page =
            (AppDrawerHorizontalPageAdapter.PageHolder) raw;
        float localX = x - mHorizontalPager.getLeft() - pageView.getLeft();
        float localY = y - mHorizontalPager.getTop() - pageView.getTop();
        for (int i = 0; i < page.cells.size(); i++) {
            View cell = page.cells.get(i);
            if (cell.getVisibility() == VISIBLE
                && localX >= cell.getLeft() && localX < cell.getRight()
                && localY >= cell.getTop() && localY < cell.getBottom()) return cell;
        }
        return null;
    }

    private void liftApp(@NonNull View cell, @NonNull LauncherAppEntry entry) {
        AppDrawerDragController controller = mDragController;
        if (controller == null) return;
        // The long press's own steps: the press is spent, then the same pickup runs.
        cancelCellLongPresses();
        controller.startPickup(cell, entry);
    }

    private void liftFolder() {
        View cell = mEditFolderCell;
        PinnedFolderItem folder = mEditFolder;
        removeCallbacks(mEditFolderHold);
        mEditFolderCell = null;
        mEditFolder = null;
        AppDrawerDragController controller = mDragController;
        if (cell == null || folder == null || controller == null) return;
        cancelCellLongPresses();
        controller.startFolderPickup(cell, folder);
    }

    private void onEditFolderHold() {
        if (mEditFolderCell == null) return;
        if (mEditGesture.evaluate(0f, 0f, AppDrawerEditGesturePolicy.HOLD_LIFT_MS, mTouchSlopPx,
            false) == AppDrawerEditGesturePolicy.Action.LIFT_FOLDER) liftFolder();
    }

    /** A move past the slop lifts the folder; a release inside it is a tap the cell handles. */
    private void trackEditFolder(@NonNull MotionEvent ev, int action) {
        if (action == MotionEvent.ACTION_CANCEL) {
            removeCallbacks(mEditFolderHold);
            mEditFolderCell = null;
            mEditFolder = null;
            return;
        }
        if (action != MotionEvent.ACTION_MOVE && action != MotionEvent.ACTION_UP) return;
        boolean released = action == MotionEvent.ACTION_UP;
        AppDrawerEditGesturePolicy.Action answer = mEditGesture.evaluate(ev.getX() - mEditDownX,
            ev.getY() - mEditDownY, ev.getEventTime() - mEditDownTime, mTouchSlopPx, released);
        if (answer == AppDrawerEditGesturePolicy.Action.LIFT_FOLDER && !released) {
            liftFolder();
        } else if (answer != AppDrawerEditGesturePolicy.Action.PENDING || released) {
            removeCallbacks(mEditFolderHold);
            mEditFolderCell = null;
            mEditFolder = null;
        }
    }

    // ------------------------------------------------------------------ tile reorder

    /** The tile takes the finger: the children are cancelled and this view owns the stream. */
    private void liftTile(@NonNull AppDrawerCategoryTileView tile, float x, float y) {
        if (mTileLiftActive) return;
        cancelContentTouch(x, y);
        if (!mCategoryView.beginReorder(tile, x - mCategoryView.getLeft(),
            y - mCategoryView.getTop(), this::onCategoryReordered)) return;
        mTileLiftActive = true;
        mGestureActive = false;
        mDownOverGrid = false;
        disarm();
        ViewParent parent = getParent();
        if (parent != null) parent.requestDisallowInterceptTouchEvent(true);
    }

    private boolean handleTileLiftTouch(@NonNull MotionEvent ev) {
        float x = ev.getX() - mCategoryView.getLeft();
        float y = ev.getY() - mCategoryView.getTop();
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_MOVE:
                mCategoryView.moveReorder(x, y);
                break;
            case MotionEvent.ACTION_UP:
                mTileLiftActive = false;
                mCategoryView.dropReorder(x, y);
                break;
            case MotionEvent.ACTION_CANCEL:
                mTileLiftActive = false;
                mCategoryView.cancelReorder();
                break;
            default:
                break;
        }
        return true;
    }

    /** The drop's answer: the new order is stored, and the tiles are bound once in it. */
    private void onCategoryReordered(@NonNull AppDrawerCategory what,
                                     @Nullable AppDrawerCategory before) {
        mCategoryOrderStore.write(new AppDrawerCategoryOrder(mCategoryOrderStore.read())
            .moved(what, before));
        setCategoryBuckets(mCategoryBuckets);
        if (categoryBucketsLive()) mCategoryView.submitBuckets(mCategoryBuckets);
    }

    /**
     * A long press on a Categories tile's heading outside edit mode: edit mode starts and the tile
     * lifts in the same gesture. Called by the heading; false when it is not the drawer's to do.
     */
    static boolean enterEditFromTile(@NonNull AppDrawerCategoryTileView tile) {
        for (ViewParent parent = tile.getParent(); parent != null; parent = parent.getParent()) {
            if (parent instanceof AppDrawerContentView) {
                return ((AppDrawerContentView) parent).enterEditLiftingTile(tile);
            }
        }
        return false;
    }

    private boolean enterEditLiftingTile(@NonNull AppDrawerCategoryTileView tile) {
        if (mEditModel.isEditing() || !mInteractive || hasQuery()
            || presentedType() != AppDrawerViewType.CATEGORIES || !mCategoryView.isOverview())
            return false;
        enterEdit(AppDrawerEditModel.Entry.TILE_LONG_PRESS);
        if (mEditModel.liftsTileOnEntry()) liftTile(tile, mLastTouchX, mLastTouchY);
        return true;
    }

    @Nullable
    private AppDrawerItem drawerItemAt(float x, float y) {
        if (presentedType() == AppDrawerViewType.VERTICAL) {
            View child = mGrid.findChildViewUnder(x - mGrid.getLeft(), y - mGrid.getTop());
            if (child == null) return null;
            return mAdapter.itemAt(mGrid.getChildAdapterPosition(child));
        }
        if (presentedType() == AppDrawerViewType.CATEGORIES) {
            LauncherAppEntry entry = mCategoryView.appEntryAt(x - mCategoryView.getLeft(),
                y - mCategoryView.getTop());
            return entry == null ? null : AppDrawerItem.app(entry);
        }
        if (presentedType() != AppDrawerViewType.HORIZONTAL) return null;
        View pageView = mHorizontalPager.findChildViewUnder(x - mHorizontalPager.getLeft(),
            y - mHorizontalPager.getTop());
        if (pageView == null) return null;
        RecyclerView.ViewHolder raw = mHorizontalPager.getChildViewHolder(pageView);
        if (!(raw instanceof AppDrawerHorizontalPageAdapter.PageHolder)) return null;
        AppDrawerHorizontalPageAdapter.PageHolder page =
            (AppDrawerHorizontalPageAdapter.PageHolder) raw;
        float localX = x - mHorizontalPager.getLeft() - pageView.getLeft();
        float localY = y - mHorizontalPager.getTop() - pageView.getTop();
        for (int i = 0; i < page.cells.size(); i++) {
            View cell = page.cells.get(i);
            if (localX >= cell.getLeft() && localX < cell.getRight()
                && localY >= cell.getTop() && localY < cell.getBottom()) {
                return mHorizontalAdapter.itemAt(raw.getAdapterPosition()
                    * mHorizontalAdapter.itemsPerPage() + i);
            }
        }
        return null;
    }

    private boolean suppressCellClick() {
        return mSuppressCellClickDuringTerminalDispatch || mDragActive;
    }

    @Override public boolean isFrozenPickupEligible(@NonNull String stableId) {
        return mGestureDragEligible && stableId.equals(mGestureDragStableId);
    }

    private boolean claimDrawerGesture(@NonNull AppDrawerDragPolicy.Claim claim) {
        AppDrawerDragPolicy policy = mDragPolicy;
        return policy != null && policy.claim(claim);
    }

    @Override public boolean claimPickupContext(@NonNull String stableId) {
        return isFrozenPickupEligible(stableId)
            && claimDrawerGesture(AppDrawerDragPolicy.Claim.CONTEXT);
    }

    @Override public boolean claimPickupDrag(@NonNull String stableId) {
        return isFrozenPickupEligible(stableId)
            && claimDrawerGesture(AppDrawerDragPolicy.Claim.DRAG);
    }

    @Override public void armTerminalDispatchDragLatch() {
        mSuppressCellClickDuringTerminalDispatch = true;
    }

    @Override public void onDragStateChanged(boolean dragging) {
        mDragActive = dragging;
        if (dragging) {
            mDragOverlay.setVisibility(VISIBLE);
            mHorizontalDropGate.reset();
        }
        mHorizontalPager.setDragLocked(dragging
            && mGestureViewType == AppDrawerViewType.HORIZONTAL);
        if (!dragging) {
            cancelDragNavigation();
            mHorizontalDropGate.reset();
            // Posted, not inline: the drag's own cleanup (overlay settle, ENDED dispatch) must
            // finish before the recompose rebinds the cells it is animating over.
            if (mPendingDockConfigRefresh) post(() -> {
                if (mPendingDockConfigRefresh) onDockConfigChanged();
            });
        }
    }

    /** Home, for an app picked up here to be carried to; null while there is none. */
    @Nullable private com.termux.app.launcher.widget.HomeAppDrop.Target mHomeAppDropTarget;

    public void setHomeAppDropTarget(
            @Nullable com.termux.app.launcher.widget.HomeAppDrop.Target target) {
        mHomeAppDropTarget = target;
    }

    @Nullable @Override
    public com.termux.app.launcher.widget.HomeAppDrop.Target homeAppDropTarget() {
        return mHomeAppDropTarget;
    }

    /**
     * The drawer's top edge for a carried app: the top of its app area, just under the search
     * pill, where the list starts when it is not lowered. Not the glass sheet's own top, which in
     * the default style is the screen's edge and cannot be crossed.
     */
    @Override public float handOverTopRawY() {
        if (!isAttachedToWindow()) return Float.NaN;
        getLocationOnScreen(mDragLocation);
        return mDragLocation[1] + mGridBaseTopMarginPx;
    }

    @Override public boolean dragMayLeaveDrawer() {
        return mEditModel.dragMayLeaveDrawer();
    }

    @NonNull @Override public AppDrawerViewType frozenSourceViewType() {
        return mGestureViewType;
    }

    /**
     * Folders are anchored to the app they sit in front of, so a drop resolves to the first app at
     * or after the row it landed on. Dropping on the trailing folders — or on nothing — parks the
     * folder at the end, which is the only position past the last app.
     */
    @NonNull @Override public String drawerAnchorFor(@Nullable String targetStableId) {
        if (targetStableId == null) return PinnedFolderItem.DRAWER_ANCHOR_END;
        List<AppDrawerItem> items = presentedType() == AppDrawerViewType.HORIZONTAL
            ? mHorizontalAdapter.items() : mAdapter.items();
        int index = -1;
        for (int i = 0; i < items.size(); i++) {
            if (targetStableId.equals(items.get(i).stableId)) {
                index = i;
                break;
            }
        }
        if (index < 0) return PinnedFolderItem.DRAWER_ANCHOR_END;
        for (int i = index; i < items.size(); i++) {
            AppDrawerItem item = items.get(i);
            if (item.kind == AppDrawerItem.Kind.APP) return item.stableId;
        }
        return PinnedFolderItem.DRAWER_ANCHOR_END;
    }

    @Override public void onDragLocation(@NonNull View target, float localX, float localY) {
        target.getLocationOnScreen(mDragLocation);
        int rawX = Math.round(mDragLocation[0] + localX);
        int rawY = Math.round(mDragLocation[1] + localY);
        getLocationOnScreen(mDragLocation);
        float x = rawX - mDragLocation[0];
        float y = rawY - mDragLocation[1];
        onContentDragLocation(x, y);
    }

    private void onContentDragLocation(float x, float y) {
        // Bounds, not cells: the edge a drag has to be held against to scroll or flip pages is
        // usually past the last row, where there is no cell under the finger at all.
        if (!withinDragSurface(x, y)) {
            cancelDragNavigation();
            return;
        }
        if (mGestureViewType == AppDrawerViewType.HORIZONTAL) {
            int direction = AppDrawerDragPolicy.edgeDirection(x, getWidth(), mDensity);
            if (direction == 0) {
                removeCallbacks(mDragEdgeDwell);
                mDragEdgeDirection = 0;
                mDragEdgeConsumed = false;
            } else if (direction != mDragEdgeDirection) {
                removeCallbacks(mDragEdgeDwell);
                mDragEdgeDirection = direction;
                mDragEdgeConsumed = false;
                postDelayed(mDragEdgeDwell, AppDrawerDragPolicy.HORIZONTAL_DWELL_MS);
            }
            return;
        }
        if (mGestureViewType == AppDrawerViewType.VERTICAL) {
            float velocity = AppDrawerDragPolicy.verticalAutoscrollVelocity(y, getHeight(), mDensity);
            if (velocity != mDragAutoscrollVelocity) {
                mDragAutoscrollVelocity = velocity;
                removeCallbacks(mDragAutoscroll);
                if (velocity != 0f) postOnAnimation(mDragAutoscroll);
            }
        }
    }

    /**
     * The other drag that can land here: an app carried out of the folder popup, which is a
     * different window and therefore a drag this view does not own. Dropping it anywhere on the
     * drawer means "leave the folder" — the app is suppressed from the list precisely because the
     * folder holds it, so removal is the whole move.
     */
    private boolean handleFolderEntryDragEvent(@NonNull DragEvent event) {
        SuggestionBarView dock = mDock;
        if (dock == null || !dock.isFolderEntryDrag(event)) return false;
        if (event.getAction() != DragEvent.ACTION_DROP) return true;
        if (!dock.dropFolderEntryOnDrawer(event)) return false;
        post(this::onDockConfigChanged);
        return true;
    }

    /** @return true while the drag point is over the scrolling surface of the current view type. */
    private boolean withinDragSurface(float x, float y) {
        View surface = mGestureViewType == AppDrawerViewType.HORIZONTAL ? mHorizontalPager : mGrid;
        if (surface == null || surface.getVisibility() != VISIBLE) return false;
        return x >= surface.getLeft() && x <= surface.getRight()
            && y >= surface.getTop() && y <= surface.getBottom();
    }

    @Override public void onDragTargetExited() {
        cancelDragNavigation();
    }

    @Override public void onAcceptedDrop() {
        // Keep the source holder stable through ACTION_DRAG_ENDED, then recompose both pickup
        // adapters from the repository snapshot even if its asynchronous listener is delayed.
        mPendingDockConfigRefresh = true;
    }

    private void cancelDragNavigation() {
        removeCallbacks(mDragEdgeDwell);
        removeCallbacks(mDragAutoscroll);
        mDragEdgeDirection = 0;
        mDragEdgeConsumed = false;
        mDragAutoscrollVelocity = 0f;
    }

    @Override public boolean canDropOnCurrentTarget() {
        return mGestureViewType != AppDrawerViewType.HORIZONTAL || mHorizontalDropGate.canDrop();
    }

    @Nullable @Override
    public AppDrawerItem resolveCurrentDropTarget(@NonNull String stableId) {
        if (mGestureViewType == AppDrawerViewType.HORIZONTAL) {
            return mHorizontalAdapter.itemOnPageByStableId(
                mHorizontalPager.getSelectedPage(), stableId);
        }
        for (int i = 0; i < mAdapter.getItemCount(); i++) {
            AppDrawerItem item = mAdapter.itemAt(i);
            if (item != null && stableId.equals(item.stableId)) return item;
        }
        return null;
    }

    private void beginClosePolicy(@NonNull RecyclerView recycler) {
        mPolicy.begin(new AppDrawerCloseArmingPolicy.Down(mDownOverGrid,
            !recycler.canScrollVertically(-1), isScrollable(recycler)),
            SystemClock.uptimeMillis());
    }

    private static boolean isScrollable(@NonNull RecyclerView recycler) {
        return recycler.canScrollVertically(-1) || recycler.canScrollVertically(1);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        // A drawer that is not open is not a surface: swallow rather than let the grid answer.
        if (!mInteractive) return true;
        if (!mPagesActive) return false;
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                beginSectionTracking(ev);
                // A down on a settling pager catches it, before any child sees it.
                return mSectionGesture.claim() == AppDrawerSectionGesturePolicy.Claim.SECTION;
            case MotionEvent.ACTION_MOVE:
                return trackSectionMove(ev);
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (!mSectionStream) endSectionTracking();
                return false;
            default:
                return false;
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!mInteractive) return true;
        if (mSectionStream
            || mSectionGesture.claim() == AppDrawerSectionGesturePolicy.Claim.SECTION)
            return handleSectionTouch(event);
        return super.onTouchEvent(event);
    }

    // ------------------------------------------------------------------ section drag

    /** Freezes what the down landed on and hands it to the policy. */
    private void beginSectionTracking(@NonNull MotionEvent ev) {
        endSectionTracking();
        float x = ev.getX();
        float y = ev.getY();
        boolean settling = !mSectionPager.isAtRest();
        AppDrawerSectionGesturePolicy.Target target;
        AppDrawerCategoryTouchRegions.Part part = AppDrawerCategoryTouchRegions.Part.OUTSIDE;
        if (settling) {
            // Mid-motion the pages are off their layout: any point over them is the pages'.
            target = y >= mGrid.getTop() ? AppDrawerSectionGesturePolicy.Target.APPS_GRID
                : AppDrawerSectionGesturePolicy.Target.CHROME;
        } else if (mSection == AppDrawerSection.APPS) {
            AppDrawerTouchRegions.Region region = regionAt(x, y);
            target = region == AppDrawerTouchRegions.Region.COLUMN
                ? AppDrawerSectionGesturePolicy.Target.AZ_COLUMN
                : region == AppDrawerTouchRegions.Region.GRID
                    ? AppDrawerSectionGesturePolicy.Target.APPS_GRID
                    : AppDrawerSectionGesturePolicy.Target.CHROME;
        } else {
            part = mCategoryView.touchPart(x - mCategoryView.getLeft(),
                y - mCategoryView.getTop());
            target = part == AppDrawerCategoryTouchRegions.Part.OUTSIDE
                ? AppDrawerSectionGesturePolicy.Target.CHROME
                : AppDrawerSectionGesturePolicy.Target.CATEGORIES;
        }
        AppDrawerSectionGesturePolicy.Claim claim = mSectionGesture.begin(
            new AppDrawerSectionGesturePolicy.Down(mSection, target, part,
                mCategoryView.isOverview(), hasQuery(), mInteractive,
                settling, getLayoutDirection() == LAYOUT_DIRECTION_RTL));
        if (claim == AppDrawerSectionGesturePolicy.Claim.CHILD) return;
        mSectionDownX = x;
        mSectionDownY = y;
        mSectionVelocity = VelocityTracker.obtain();
        mSectionVelocity.addMovement(ev);
    }

    private boolean trackSectionMove(@NonNull MotionEvent ev) {
        if (mSectionStream) return true;
        if (mSectionGesture.claim() != AppDrawerSectionGesturePolicy.Claim.PENDING) return false;
        VelocityTracker tracker = mSectionVelocity;
        if (tracker != null) tracker.addMovement(ev);
        AppDrawerSectionGesturePolicy.Claim claim = mSectionGesture.evaluate(
            ev.getX() - mSectionDownX, ev.getY() - mSectionDownY, mTouchSlopPx,
            isPickupArmed());
        if (claim != AppDrawerSectionGesturePolicy.Claim.SECTION) return false;
        startSectionStream(ev);
        return true;
    }

    /** A long press or pickup has armed on the cell under the finger. */
    private boolean isPickupArmed() {
        AppDrawerDragPolicy policy = mDragPolicy;
        return mDragActive || mEditGesture.blocksPageSwipe() || (policy != null
            && (policy.claim() == AppDrawerDragPolicy.Claim.CONTEXT
                || policy.claim() == AppDrawerDragPolicy.Claim.DRAG));
    }

    /**
     * This view takes the stream: the children are cancelled by the platform, and the close drag's
     * and nested scroll's bookkeeping is put away so nothing of it fires for this stream.
     */
    private void startSectionStream(@NonNull MotionEvent ev) {
        mSectionStream = true;
        // A drag claimed past the slop spends it; a settle caught on the down has none to spend.
        mSectionSlopPx = ev.getActionMasked() == MotionEvent.ACTION_DOWN ? 0f : mTouchSlopPx;
        dismissContextPopups();
        cancelCellLongPresses();
        // Disarms the close policy and the categories' own touch stream.
        disarm();
        mGestureActive = false;
        mDownOverGrid = false;
        mFlingVelocityY = 0f;
        mGrid.stopScroll();
        ViewParent parent = getParent();
        if (parent != null) parent.requestDisallowInterceptTouchEvent(true);
        mSectionPager.beginDrag();
        applyPageVisibility();
    }

    private boolean handleSectionTouch(@NonNull MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN && !mSectionStream) startSectionStream(event);
        if (!mSectionStream) return false;
        if (!mPagesActive) {
            // A search arrived under the finger: the pages are frozen, the stream ends.
            endSectionStream();
            return true;
        }
        VelocityTracker tracker = mSectionVelocity;
        if (tracker != null) tracker.addMovement(event);
        boolean rtl = getLayoutDirection() == LAYOUT_DIRECTION_RTL;
        switch (action) {
            case MotionEvent.ACTION_MOVE:
                applySectionTravel(event.getX(), rtl);
                break;
            case MotionEvent.ACTION_UP: {
                // The lift's own position counts: a fast swipe's last stretch can arrive on it.
                applySectionTravel(event.getX(), rtl);
                float velocity = 0f;
                if (tracker != null) {
                    tracker.computeCurrentVelocity(1000);
                    velocity = tracker.getXVelocity();
                }
                mSectionPager.release(rtl ? velocity : -velocity, getWidth(), mDensity);
                endSectionStream();
                break;
            }
            case MotionEvent.ACTION_CANCEL:
                mSectionPager.release(0f, getWidth(), mDensity);
                endSectionStream();
                break;
            default:
                break;
        }
        return true;
    }

    /** Puts the pages where the finger at {@code x} has taken them since the down. */
    private void applySectionTravel(float x, boolean rtl) {
        // Towards Categories is the finger moving to the start side.
        float finger = rtl ? x - mSectionDownX : mSectionDownX - x;
        mSectionPager.dragTo(AppDrawerSectionPager.travelPastSlop(finger, mSectionSlopPx),
            getWidth());
    }

    /** The stream is over (or the drawer is going away): the velocity tracker and latch go. */
    private void endSectionStream() {
        mSectionStream = false;
        endSectionTracking();
    }

    private void endSectionTracking() {
        VelocityTracker tracker = mSectionVelocity;
        mSectionVelocity = null;
        if (tracker != null) tracker.recycle();
        mSectionGesture.reset();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        mSectionPager.setPageWidthPx(w);
        // The search bar's width follows the drawer's; a layout pass cannot ask for another, so the
        // new width is applied right after this one.
        // A folded bar's button stands a fixed distance above the card's bottom, so a new height
        // moves it too.
        if (w != oldw || (h != oldh && (mSearchCollapsed || mSearchFold > 0f))) {
            post(this::applyChromeForWidth);
        }
        // The pages stand at position times the width: a new width moves them with the same draw.
        applyPageTransforms();
    }

    /**
     * The pager's settle clock: a ValueAnimator used only as the clock, as the Home pages' content
     * settle does; the spring says where the number stands.
     */
    private final class SectionSettleClock implements AppDrawerSectionPager.Clock {
        @Nullable private ValueAnimator mAnimator;

        @Override
        public void start(long durationMs, @NonNull AppDrawerSectionPager.Tick tick) {
            cancel();
            ValueAnimator settle = ValueAnimator.ofFloat(0f, 1f);
            settle.setDuration(durationMs);
            settle.setInterpolator(null);
            settle.addUpdateListener(animation -> tick.onFraction(animation.getAnimatedFraction()));
            mAnimator = settle;
            settle.start();
        }

        @Override
        public void cancel() {
            ValueAnimator settle = mAnimator;
            mAnimator = null;
            if (settle != null) settle.cancel();
        }

        @Override
        public float springTime() {
            return AnimationSpeed.springTime(getContext());
        }
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
        if (mGestureViewType == AppDrawerViewType.HORIZONTAL) {
            if (target != mHorizontalPager) return;
            // Only the pager's one-way DRAWER_DRAG latch starts this relay. Once it has latched,
            // later residue follows the finger and cannot hand the stream back to paging.
            if (!mNestedCloseActive && (dy >= 0 || !mHorizontalPager.isCloseClaimed())) return;
            consumed[1] = dy;
            if (!mNestedCloseActive) {
                mNestedCloseActive = true;
                cancelCellLongPresses();
                dismissContextPopups();
                if (mCallbacks != null) mCallbacks.onContentCloseDragBegin(mDownRawY);
            }
            if (mCallbacks != null) mCallbacks.onContentCloseDragUpdate(mLastRawY);
            return;
        }
        if (mGestureViewType == AppDrawerViewType.CATEGORIES && !mGestureCategorySearch) {
            if (mGestureCategoryPart == AppDrawerCategoryTouchRegions.Part.DETAIL_LIST) {
                if (mCategoryView.claimDetailPreScroll(target, dy, mLastRawY)) consumed[1] = dy;
                return;
            }
            if (mGestureCategoryPart != AppDrawerCategoryTouchRegions.Part.OVERVIEW_LIST
                && mGestureCategoryPart != AppDrawerCategoryTouchRegions.Part.EXPAND_ACTION) return;
            if (target != mCategoryView.getOverview()) return;
        }
        AppDrawerCloseArmingPolicy.Decision decision = mPolicy.claimOnPreScroll(dy);
        if (decision != AppDrawerCloseArmingPolicy.Decision.CLOSE_DRAG) return;
        consumed[1] = dy;
        if (!mNestedCloseActive) {
            mNestedCloseActive = true;
            if (mGestureViewType == AppDrawerViewType.CATEGORIES)
                mCategoryView.suppressClicks();
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
        if (mGestureViewType == AppDrawerViewType.HORIZONTAL
            || isCategoryDetailGesture()) return;
        int taken = takeOverpull(dyUnconsumed, type);
        consumed[1] += taken;
    }

    @Override
    public void onNestedScroll(@NonNull View target, int dxConsumed, int dyConsumed,
                               int dxUnconsumed, int dyUnconsumed, int type) {
        if (mGestureViewType == AppDrawerViewType.HORIZONTAL
            || isCategoryDetailGesture()) return;
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
        if (mGestureViewType == AppDrawerViewType.HORIZONTAL) {
            if (!mNestedCloseActive || target != mHorizontalPager) return false;
            endNestedClose(AppDrawerCloseArmingPolicy.closeVelocityForNestedFling(velocityY));
            return true;
        }
        if (isCategoryDetailGesture()) {
            return mCategoryView.finishDetailGesture(
                AppDrawerCloseArmingPolicy.closeVelocityForNestedFling(velocityY), false);
        }
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
        if (mGestureViewType == AppDrawerViewType.HORIZONTAL) {
            if (mNestedCloseActive) {
                if (mStreamCancelled) cancelNestedClose();
                else endNestedClose(0f);
            }
            mFlingVelocityY = 0f;
            return;
        }
        if (isCategoryDetailGesture()) {
            mCategoryView.finishDetailGesture(0f, mStreamCancelled);
            mFlingVelocityY = 0f;
            return;
        }
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
        RecyclerView recycler = mGestureRecycler == null ? mGrid : mGestureRecycler;
        mPolicy.end(mOverpullTranslationPx, 0f, velocityPxPerSec,
            !recycler.canScrollVertically(-1), SystemClock.uptimeMillis());
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

    private boolean isCategoryDetailGesture() {
        return mGestureViewType == AppDrawerViewType.CATEGORIES && !mGestureCategorySearch
            && (mGestureCategoryPart == AppDrawerCategoryTouchRegions.Part.DETAIL_LIST
            || mGestureCategoryPart == AppDrawerCategoryTouchRegions.Part.DETAIL_EMPTY);
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

    /** The grid's current overpull travel, in pixels. */
    public float getOverpullTranslationPx() {
        return mOverpullTranslationPx;
    }

    private void applyOverpull(float translationPx) {
        mOverpullTranslationPx = translationPx;
        View surface = mOverpullSurface == null ? mGrid : mOverpullSurface;
        surface.setTranslationY(translationPx);
        // Clear a previous captured surface when a mode/query transition resets the effect.
        if (translationPx == 0f && surface != mGrid && mGrid.getTranslationY() != 0f)
            mGrid.setTranslationY(0f);
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
        // A re-post from inside doFrame keeps the frame clock (see DockPlankController.kick).
        if (!mInFrame) mLastFrameTimeNanos = 0L;
        Choreographer.getInstance().postFrameCallback(this);
    }

    @Override
    public void doFrame(long frameTimeNanos) {
        mInFrame = true;
        try {
            doFrameInner(frameTimeNanos);
        } finally {
            mInFrame = false;
        }
    }

    private void doFrameInner(long frameTimeNanos) {
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
        leaveEdit(AppDrawerEditModel.Leave.DRAWER_CLOSED);
        mCornerTab.reset();
        stopOverpullSpring();
        endSectionStream();
        mSectionPager.cancel();
    }

    private int dp(float value) {
        return Math.round(value * mDensity);
    }

    // ---------------------------------------------------------------- section switch glass

    /** Hands out the card's glass for the section switch; told whether the switch wants it. */
    public interface SwitchGlassSource {
        /** The card's glass style, held for the switch while {@code wanted}, or null. */
        @Nullable com.termux.app.chrome.DrawerCardGlassStyle style(boolean wanted);
    }

    @Nullable private SwitchGlassSource mSwitchGlassSource;

    /** The drawer card's corner radius, for the corner tab's own corner. */
    public void setCardCornerRadiusPx(float radiusPx) {
        mCardCornerRadiusPx = Math.max(0f, radiusPx);
    }

    /** What dresses the corner tab: the terminal panes' own style and border, as the panes pass them. */
    public interface TabStyleSource {
        @Nullable com.termux.app.terminal.PaneSurfaceStyle style();

        float borderPx();
    }

    @Nullable private TabStyleSource mTabStyleSource;

    public void setTabStyleSource(@Nullable TabStyleSource source) {
        mTabStyleSource = source;
    }
    /** The switch's refracting slab, built while the open drawer wants it and dropped after. */
    @Nullable private com.termux.app.terminal.PaneGlassBackdropView mSwitchGlass;
    @Nullable private Object mSwitchGlassKey;
    private boolean mSwitchGlassWanted;
    private boolean mDrawerOpenForGlass;

    public void setSwitchGlassSource(@Nullable SwitchGlassSource source) {
        mSwitchGlassSource = source;
    }

    /** The drawer opened or closed: the glass is built for an open Default drawer and dropped after. */
    public void setDrawerOpenForGlass(boolean open) {
        mDrawerOpenForGlass = open;
        applySwitchGlass();
    }

    /** The Look, the frame or the scheme may have moved: dress again if what it answers changed. */
    public void refreshSwitchGlass() {
        if (mSwitchGlassWanted) applySwitchGlass();
        mCornerTab.refreshGlass();
    }

    /**
     * Dresses the glass under the switch as the page bar's pill is dressed, or drops it. The slab
     * and the frame it holds exist only while the open drawer is on the Default type. Compared by
     * the style's key, so a pass with nothing moved does no work; never per frame.
     */
    private void applySwitchGlass() {
        SwitchGlassSource source = mSwitchGlassSource;
        boolean wanted = source != null
            && AppDrawerSectionSwitchGlass.wanted(mViewType, mDrawerOpenForGlass);
        if (!wanted) {
            if (mSwitchGlassWanted && source != null) source.style(false);
            mSwitchGlassWanted = false;
            releaseSwitchGlass();
            return;
        }
        com.termux.app.chrome.DrawerCardGlassStyle look = source.style(true);
        mSwitchGlassWanted = true;
        if (look == null) {
            releaseSwitchGlass();
            return;
        }
        Object key = look.key();
        if (mSwitchGlass != null && key.equals(mSwitchGlassKey)) return;
        mSwitchGlassKey = key;
        com.termux.app.terminal.PaneGlassBackdropView glass = mSwitchGlass;
        if (glass == null) {
            glass = new com.termux.app.terminal.PaneGlassBackdropView(getContext());
            glass.setClipToOutline(true);
            glass.setOutlineProvider(new ViewOutlineProvider() {
                @Override public void getOutline(View view, android.graphics.Outline out) {
                    out.setRoundRect(0, 0, view.getWidth(), view.getHeight(),
                        view.getHeight() / 2f);
                }
            });
            com.termux.app.terminal.PaneGlass.followLayout(glass);
            glass.setVisibility(GONE);
            // Under the switch in Z, over the content beneath.
            addView(glass, indexOfChild(mSectionSwitch), new LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
            mSwitchGlass = glass;
        }
        float radius = AppDrawerSectionSwitchGlass.radiusPx(Math.round(
            AppDrawerSectionSwitchGeometry.HEIGHT_DP * mDensity));
        boolean shown = com.termux.app.wall.pageswitch.PageBarGlass.dress(getContext(), look,
            this, glass, radius);
        // No frame or no glass (blur off, battery saver): the switch draws its wash instead.
        mSectionSwitch.setGlassShown(shown);
        if (shown) glass.requestLayout();
    }

    private void releaseSwitchGlass() {
        mSwitchGlassKey = null;
        mSectionSwitch.setGlassShown(false);
        com.termux.app.terminal.PaneGlassBackdropView glass = mSwitchGlass;
        if (glass == null) return;
        mSwitchGlass = null;
        removeView(glass);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        com.termux.app.terminal.PaneGlassBackdropView glass = mSwitchGlass;
        // The switch's exact rect, as the chrome layout and its measured width placed it.
        if (glass != null && glass.getVisibility() != GONE
            && mSectionSwitch.getVisibility() == VISIBLE) {
            glass.layout(mSectionSwitch.getLeft(), mSectionSwitch.getTop(),
                mSectionSwitch.getRight(), mSectionSwitch.getBottom());
        }
    }

    /** @return the shipped vertical app grid */
    @androidx.annotation.VisibleForTesting
    AppDrawerCornerTab cornerTab() {
        return mCornerTab;
    }

    @NonNull
    public RecyclerView getGrid() {
        return mGrid;
    }

    /** @return the fixed search chrome used to verify content/overlay geometry as one hierarchy */
    @NonNull
    public AppDrawerSearchPillView getSearchPill() {
        return mPill;
    }

    @NonNull
    public AppDrawerHorizontalPagerView getHorizontalPager() {
        return mHorizontalPager;
    }

    @NonNull
    public AppDrawerHorizontalPageAdapter getHorizontalAdapter() {
        return mHorizontalAdapter;
    }

    @NonNull
    public AppDrawerPageIndicatorView getPageIndicator() {
        return mPageIndicator;
    }

    @NonNull
    public AppDrawerCategoryView getCategoryView() {
        return mCategoryView;
    }

    /**
     * The plane's visible bottom in this view's coordinates while the keyboard's reveal moves the
     * card's bottom; {@code +∞} when nothing cuts the content. The A–Z strip spaces its letters
     * over what is above it, per frame, by redraw alone.
     */
    public void setRopeVisibleBottomPx(float bottomPx) {
        mColumn.setVisibleBottomPx(bottomPx);
    }

    /** @return the A-Z column, whose touch stream and geometry the drawer's tests drive directly */
    @NonNull
    public AppDrawerRopeColumnView getRopeColumn() {
        return mColumn;
    }

    /** @return the categories search's "N RESULTS" band */
    @NonNull
    public TextView getResultsLabel() {
        return mResultsLabel;
    }

    /** @return the categories search's centred no-results state */
    @NonNull
    public TextView getNoResultsView() {
        return mNoResults;
    }
}
