package com.termux.app.launcher.drawer;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.SystemClock;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.termux.R;
import com.termux.app.Spring;
import com.termux.app.SuggestionBarView;
import com.termux.app.launcher.drawer.AppDrawerCategoryTouchRegions.Part;
import com.termux.app.launcher.drawer.AppDrawerTransitionGeometry.Frame;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Category overview/detail container. It observes streams but never intercepts a child touch. */
public final class AppDrawerCategoryView extends ViewGroup
    implements AppDrawerCategoryTileView.ExpansionListener {

    /** Header-row geometry from the redesign mock. */
    private static final float CHEVRON_SIZE_DP = 30f;
    /** Inset of the drawn arrow inside its circle, so the icon reads at ~16dp in a 30dp ring. */
    private static final float CHEVRON_ICON_INSET_DP = 7f;
    private static final float HEADER_ITEM_GAP_DP = 10f;
    private static final float HEADER_SIDE_INSET_DP = 16f;

    private final RecyclerView overview;
    private final RecyclerView detailList;
    private final TextView detailHeader;
    private final ImageView collapseChevron;
    private final TextView detailCount;
    private final TextView emptyState;
    private final AppDrawerCategoryMorphView morph;
    private final GridLayoutManager overviewLayout;
    private final GridLayoutManager detailLayout;
    private final AppDrawerCategoryTileAdapter tileAdapter;
    private final AppDrawerCategoryDetailAdapter detailAdapter;
    private final TileSpacingDecoration tileSpacing = new TileSpacingDecoration();
    private final AppDrawerCategoryExpansionModel expansion =
        new AppDrawerCategoryExpansionModel();
    private final AppDrawerCategoryGesturePolicy gesturePolicy =
        new AppDrawerCategoryGesturePolicy();
    /** The one house spring specified by B-5; there is no per-view frame callback. */
    private final Spring expansionSpring = new Spring(0f, 420f, 41f);
    /** Progress within ~5px of the end counts as arrived; see the snap in {@link #advance}. */
    private static final float SETTLE_SNAP_PROGRESS = 0.002f;
    /** Residual spring velocity (progress/s) slow enough to snap without a visible step. */
    private static final float SETTLE_SNAP_VELOCITY = 0.15f;
    private final ClickGate clickGate = new ClickGate();
    private final int touchSlop;

    /** Where the tapped card sat: the detail content grows out of it instead of fading in flat. */
    @Nullable private Frame expandSource;
    private final Rect morphClip = new Rect();
    @Nullable private AppDrawerCategoryGridMetrics metrics;
    @Nullable private Runnable frameRequestListener;
    @Nullable private Runnable popupDismissCallback;
    @NonNull private List<AppDrawerCategoryBucket> buckets = Collections.emptyList();
    @Nullable private AppDrawerCategoryBucket selectedBucket;
    @NonNull private Part streamPart = Part.OUTSIDE;
    private boolean streamFinalized;
    private float downRawY;
    private float collapseStartProgress = 1f;
    private float headerDownX;
    private float headerDownY;
    private boolean headerMoved;

    public AppDrawerCategoryView(@NonNull Context context,
                                 @Nullable SuggestionBarView dock) {
        super(context);
        // The whole category host starts below the fixed search pill. Keep its overview, detail and
        // morph frames inside that rectangle; the drawer's drag overlay is a sibling and remains
        // deliberately unconstrained.
        setClipChildren(true);
        setClipToPadding(true);
        setOutlineProvider(ViewOutlineProvider.BOUNDS);
        setClipToOutline(true);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();

        tileAdapter = new AppDrawerCategoryTileAdapter(dock);
        tileAdapter.setExpansionListener(this);
        tileAdapter.setClickGate(clickGate);
        overviewLayout = new GridLayoutManager(context, 1);
        overview = new RecyclerView(context);
        overview.setLayoutManager(overviewLayout);
        overview.setAdapter(tileAdapter);
        overview.setHasFixedSize(true);
        // Prefetch on and the default view cache: a tile bind renders seven icons, resolves theme
        // colours and requests layout — with a zero cache every tile nudged one pixel off-screen
        // paid all of that again on the way back, on the scroll frame itself.
        overview.setItemAnimator(null);
        overview.setOverScrollMode(OVER_SCROLL_NEVER);
        overview.setClipToPadding(true);
        overview.setOutlineProvider(ViewOutlineProvider.BOUNDS);
        overview.setClipToOutline(true);
        overview.addItemDecoration(tileSpacing);
        overview.addOnScrollListener(popupDismissScrollListener());
        addView(overview);

        detailAdapter = new AppDrawerCategoryDetailAdapter(dock);
        detailAdapter.setClickGate(clickGate);
        detailLayout = new GridLayoutManager(context, AppDrawerGridMetrics.MIN_COLUMNS);
        detailList = new RecyclerView(context);
        detailList.setLayoutManager(detailLayout);
        detailList.setAdapter(detailAdapter);
        detailList.setHasFixedSize(true);
        // Same rationale as the overview above: let prefetch and the view cache absorb rebinds.
        detailList.setItemAnimator(null);
        detailList.setOverScrollMode(OVER_SCROLL_NEVER);
        detailList.setClipToPadding(true);
        detailList.setOutlineProvider(ViewOutlineProvider.BOUNDS);
        detailList.setClipToOutline(true);
        detailList.setVisibility(INVISIBLE);
        detailList.addOnScrollListener(popupDismissScrollListener());
        addView(detailList);

        detailHeader = new TextView(context);
        detailHeader.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f);
        detailHeader.setTypeface(Typeface.create("sans-serif",
            Typeface.BOLD));
        detailHeader.setSingleLine(true);
        detailHeader.setEllipsize(TextUtils.TruncateAt.END);
        detailHeader.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        detailHeader.setIncludeFontPadding(false);
        detailHeader.setTextColor(dock == null ? Color.WHITE : dock.getLauncherTextColor());
        detailHeader.setClickable(true);
        detailHeader.setVisibility(INVISIBLE);
        detailHeader.setOnClickListener(view -> {
            if (!headerMoved && !clickGate.suppressCellClick()) collapse();
        });
        detailHeader.setOnTouchListener((view, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    headerDownX = event.getX();
                    headerDownY = event.getY();
                    headerMoved = false;
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (Math.abs(event.getX() - headerDownX) > touchSlop
                        || Math.abs(event.getY() - headerDownY) > touchSlop) {
                        headerMoved = true;
                        clickGate.suppress();
                    }
                    break;
                case MotionEvent.ACTION_CANCEL:
                    headerMoved = true;
                    break;
                default:
                    break;
            }
            return false;
        });
        addView(detailHeader);

        // The mock's back affordance: a 30dp circle with a hairline ring, one more collapse target
        // alongside the title itself. Same click gate, same collapse.
        //
        // A drawn icon, not a glyph. It used to be the "‹" character in a TextView, and a single
        // angle quotation mark is typeset small and high in its em box — so it sat above the row no
        // matter how the box was aligned, and it read as a quote mark rather than as Back.
        collapseChevron = new ImageView(context);
        collapseChevron.setImageResource(R.drawable.ic_category_back_arrow);
        collapseChevron.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int chevronPadding = Math.round(CHEVRON_ICON_INSET_DP
            * getResources().getDisplayMetrics().density);
        collapseChevron.setPadding(chevronPadding, chevronPadding, chevronPadding, chevronPadding);
        collapseChevron.setColorFilter(dock == null ? Color.WHITE : dock.getLauncherTextColor());
        GradientDrawable ring =
            new GradientDrawable();
        ring.setShape(GradientDrawable.OVAL);
        ring.setColor(Color.TRANSPARENT);
        ring.setStroke(Math.max(1, Math.round(
            getResources().getDisplayMetrics().density)), 0x2EFFFFFF);
        collapseChevron.setBackground(ring);
        collapseChevron.setClickable(true);
        collapseChevron.setVisibility(INVISIBLE);
        collapseChevron.setOnClickListener(view -> {
            if (!clickGate.suppressCellClick()) collapse();
        });
        addView(collapseChevron);

        // Trailing "N APPS" count, mono and quiet, per the mock's header metadata style.
        detailCount = new TextView(context);
        detailCount.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f);
        detailCount.setTypeface(Typeface.MONOSPACE);
        detailCount.setLetterSpacing(0.16f);
        detailCount.setSingleLine(true);
        detailCount.setIncludeFontPadding(false);
        detailCount.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        detailCount.setTextColor(halfAlpha(dock == null ? Color.WHITE
            : dock.getLauncherTextColor()));
        detailCount.setClickable(false);
        detailCount.setVisibility(INVISIBLE);
        addView(detailCount);

        emptyState = new TextView(context);
        emptyState.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
        emptyState.setGravity(Gravity.CENTER);
        emptyState.setLineSpacing(0f, 1.25f);
        int emptyColor = dock == null ? Color.WHITE : dock.getLauncherTextColor();
        emptyState.setTextColor(emptyColor);
        emptyState.setText(emptyStateText(emptyColor));
        emptyState.setFocusable(false);
        emptyState.setClickable(false);
        addView(emptyState);

        morph = new AppDrawerCategoryMorphView(context);
        morph.setVisibility(INVISIBLE);
        addView(morph);
    }

    public void setDock(@Nullable SuggestionBarView dock) {
        tileAdapter.setDock(dock);
        detailAdapter.setDock(dock);
        int color = dock == null ? Color.WHITE : dock.getLauncherTextColor();
        detailHeader.setTextColor(color);
        collapseChevron.setColorFilter(color);
        detailCount.setTextColor(halfAlpha(color));
        emptyState.setTextColor(color);
        emptyState.setText(emptyStateText(color));
    }

    private static int halfAlpha(int color) {
        return ColorUtils.setAlphaComponent(color, 0x80);
    }

    /** "Nothing here yet" over a quieter one-line explanation, per the mock's empty states. */
    @NonNull
    private CharSequence emptyStateText(int color) {
        String title = getResources().getString(R.string.app_drawer_category_empty_title);
        String body = getResources().getString(R.string.app_drawer_category_empty_body);
        SpannableStringBuilder text =
            new SpannableStringBuilder(title + "\n" + body);
        int flags = Spanned.SPAN_EXCLUSIVE_EXCLUSIVE;
        text.setSpan(new StyleSpan(Typeface.BOLD),
            0, title.length(), flags);
        text.setSpan(new RelativeSizeSpan(0.85f),
            title.length() + 1, text.length(), flags);
        text.setSpan(new ForegroundColorSpan(
            ColorUtils.setAlphaComponent(color, 0x73)),
            title.length() + 1, text.length(), flags);
        return text;
    }

    public void setFrameRequestListener(@Nullable Runnable listener) {
        frameRequestListener = listener;
    }

    public void setPopupDismissCallback(@Nullable Runnable callback) {
        popupDismissCallback = callback;
    }

    @NonNull
    private RecyclerView.OnScrollListener popupDismissScrollListener() {
        return new RecyclerView.OnScrollListener() {
            @Override public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                Runnable callback = popupDismissCallback;
                if ((dx != 0 || dy != 0) && callback != null) callback.run();
            }
        };
    }

    public void setMetrics(@NonNull AppDrawerCategoryGridMetrics metrics) {
        this.metrics = metrics;
        overviewLayout.setSpanCount(Math.max(1, metrics.columns));
        detailLayout.setSpanCount(Math.max(1, metrics.expandedColumns));
        int horizontal = Math.max(0, Math.round(metrics.sidePaddingPx));
        // Bottom padding, not clipped: the last row used to sit flush against the plane's bottom
        // edge, so in landscape — where a second row only half fits — the tiles read as clipped by
        // the plane rather than as a list that scrolls. One row gap of overscroll says the
        // difference, and the tiles still paint into it while dragging.
        int bottom = Math.max(0, Math.round(metrics.itemGapPx + metrics.itemBottomGapPx));
        overview.setPadding(horizontal, 0, horizontal, bottom);
        overview.setClipToPadding(false);
        tileSpacing.setMetrics(metrics);
        overview.invalidateItemDecorations();
        tileAdapter.setMetrics(metrics);
        detailAdapter.setMetrics(metrics);
        requestLayout();
    }

    public void submitBuckets(@NonNull List<AppDrawerCategoryBucket> newBuckets) {
        int firstOverview = overviewLayout.findFirstVisibleItemPosition();
        View firstOverviewView = firstOverview < 0 ? null : overviewLayout.findViewByPosition(firstOverview);
        String firstCategoryId = firstOverview >= 0 && firstOverview < buckets.size()
            ? buckets.get(firstOverview).category.slug : null;
        int firstOffset = firstOverviewView == null ? 0 : firstOverviewView.getTop();

        String selectedId = expansion.selectedId();
        String firstAppId = null;
        int firstAppOffset = 0;
        int firstDetail = detailLayout.findFirstVisibleItemPosition();
        if (firstDetail >= 0 && firstDetail < detailAdapter.entries().size()) {
            firstAppId = detailAdapter.entries().get(firstDetail).appRef.stableId();
            View anchor = detailLayout.findViewByPosition(firstDetail);
            firstAppOffset = anchor == null ? 0 : anchor.getTop();
        }

        buckets = Collections.unmodifiableList(new ArrayList<>(newBuckets));
        Set<String> ids = new HashSet<>();
        for (AppDrawerCategoryBucket bucket : buckets) ids.add(bucket.category.slug);
        boolean retained = expansion.reconcile(ids);
        selectedBucket = selectedId == null ? null : bucketForId(selectedId);
        if (!retained || selectedBucket == null && selectedId != null) {
            abortToOverview();
        } else if (selectedBucket != null && expansion.detailBound()) {
            detailAdapter.submit(selectedBucket.entries());
            bindDetailCount(selectedBucket);
            int anchorPosition = detailAdapter.positionOfStableId(firstAppId);
            detailLayout.scrollToPositionWithOffset(Math.max(0, anchorPosition), firstAppOffset);
        }
        tileAdapter.submit(buckets);
        if (firstCategoryId != null) {
            int position = positionOfCategory(firstCategoryId);
            if (position >= 0) overviewLayout.scrollToPositionWithOffset(position, firstOffset);
        }
        emptyState.setVisibility(buckets.isEmpty() ? VISIBLE : GONE);
        overview.setVisibility(buckets.isEmpty() ? GONE : VISIBLE);
        requestLayout();
    }

    @Override
    public void onExpandRequested(@NonNull AppDrawerCategoryBucket bucket,
                                  @NonNull AppDrawerCategoryTileView source) {
        if (!expansion.expand(bucket.category.slug)) return;
        selectedBucket = bucket;
        Frame sourceFrame = tileAdapter.selectedTileBounds(overview, bucket.category.slug, this);
        if (sourceFrame == null)
            sourceFrame = new Frame(source.getLeft(), source.getTop(), source.getRight(), source.getBottom());
        expandSource = sourceFrame;
        Frame destination = new Frame(0f, 0f, getWidth(), getHeight());
        float radius = metrics == null ? 0f : metrics.radiusPx;
        morph.setFrames(sourceFrame, destination, radius, radius);
        morph.setVisibility(VISIBLE);
        morph.setClickable(true); // consumes transition-body touches without parent interception.
        detailHeader.setText(getResources().getString(bucket.category.labelRes));
        detailHeader.setContentDescription(getResources().getString(
            R.string.app_drawer_category_collapse,
            getResources().getString(bucket.category.labelRes)));
        collapseChevron.setContentDescription(detailHeader.getContentDescription());
        bindDetailCount(bucket);
        detailHeader.setVisibility(VISIBLE);
        collapseChevron.setVisibility(VISIBLE);
        detailCount.setVisibility(VISIBLE);
        detailList.setVisibility(VISIBLE);
        // selectedBucket determines the final bottom-up list height. Resolve that geometry once at
        // activation; animation frames only change draw state and never request another layout.
        requestLayout();
        expansionSpring.reset(0f);
        expansionSpring.target = 1f;
        applyExpansionProgress(0f);
        requestFrames();
    }

    public boolean collapse() {
        if (!expansion.collapse()) return false;
        clickGate.suppress();
        // finishAtExpanded() hides the overview after forward settle. Reverse staging fades it in
        // below 0.25, so it must participate in drawing again before the first reverse frame.
        overview.setVisibility(buckets.isEmpty() ? GONE : VISIBLE);
        expansionSpring.value = expansion.progress();
        expansionSpring.target = 0f;
        requestFrames();
        return true;
    }

    public boolean collapseIfNeeded() {
        return expansion.state() != AppDrawerCategoryExpansionModel.State.OVERVIEW && collapse();
    }

    public boolean advance(float dt, boolean reducedMotion) {
        AppDrawerCategoryExpansionModel.State state = expansion.state();
        if (state == AppDrawerCategoryExpansionModel.State.OVERVIEW
            || state == AppDrawerCategoryExpansionModel.State.EXPANDED
            || state == AppDrawerCategoryExpansionModel.State.COLLAPSE_DRAGGING) return false;
        boolean moving = expansionSpring.tick(reducedMotion, Spring.clampDelta(dt));
        // Spring.SETTLE_EPSILON is sub-pixel (4e-4 of a full-screen travel), and this state machine
        // keeps eating every touch until the spring reports settled — so the invisible asymptotic
        // tail held the drawer input-dead for ~half a second after the collapse looked finished.
        // Anything within a few pixels of the end is the end.
        if (moving && Math.abs(expansionSpring.target - expansionSpring.value) < SETTLE_SNAP_PROGRESS
            && Math.abs(expansionSpring.vel) < SETTLE_SNAP_VELOCITY) {
            moving = false;
        }
        applyExpansionProgress(expansionSpring.value);
        if (!moving) {
            expansionSpring.value = expansionSpring.target;
            applyExpansionProgress(expansionSpring.target);
            expansion.settle();
            if (expansion.state() == AppDrawerCategoryExpansionModel.State.OVERVIEW)
                finishAtOverview();
            else finishAtExpanded();
        }
        return moving;
    }

    private void applyExpansionProgress(float progress) {
        int events = expansion.setProgress(progress);
        if ((events & AppDrawerCategoryExpansionModel.RELEASE_OVERVIEW) != 0)
            tileAdapter.releaseAttachedPreviews(overview);
        if ((events & AppDrawerCategoryExpansionModel.BIND_DETAIL) != 0 && selectedBucket != null)
            detailAdapter.submit(selectedBucket.entries());
        if ((events & AppDrawerCategoryExpansionModel.RELEASE_DETAIL) != 0)
            detailAdapter.releaseAttached(detailList);
        if ((events & AppDrawerCategoryExpansionModel.BIND_OVERVIEW) != 0)
            tileAdapter.rebindAttachedPreviews();
        float p = expansion.progress();
        float boundary = AppDrawerCategoryExpansionModel.STAGING_BOUNDARY;
        overview.setAlpha(1f - AppDrawerTransitionGeometry.ramp(p, 0f, boundary));
        // The detail content fades in right where it becomes bound and finishes well before the
        // pane does, so the icons ride the expansion instead of arriving after it.
        float detailAlpha = AppDrawerTransitionGeometry.ramp(p, boundary, 0.55f);
        detailHeader.setAlpha(detailAlpha);
        collapseChevron.setAlpha(detailAlpha);
        detailCount.setAlpha(detailAlpha);
        detailList.setAlpha(detailAlpha);
        applyDetailMorphTransform(p);
        morph.setProgress(p);
    }

    /**
     * Maps the detail header row and grid into the morphing pane rectangle: at progress 0 they sit
     * scaled down inside the tapped card, at 1 they are at their laid-out size. Each view is clipped
     * to the part of the pane it covers, so nothing paints outside the growing glass.
     */
    private void applyDetailMorphTransform(float progress) {
        Frame source = expandSource;
        int width = getWidth();
        int height = getHeight();
        if (source == null || width <= 0 || height <= 0 || source.width() <= 0f) {
            resetDetailMorphTransform();
            return;
        }
        float startScale = AppDrawerTransitionGeometry.clamp01(source.width() / width);
        float scale = Math.max(0.05f, startScale + (1f - startScale) * progress);
        float left = source.left * (1f - progress);
        float top = source.top * (1f - progress);
        float frameWidth = source.width() + (width - source.width()) * progress;
        float frameHeight = source.height() + (height - source.height()) * progress;
        applyMorphChild(detailHeader, scale, left, top, frameWidth, frameHeight);
        applyMorphChild(collapseChevron, scale, left, top, frameWidth, frameHeight);
        applyMorphChild(detailCount, scale, left, top, frameWidth, frameHeight);
        applyMorphChild(detailList, scale, left, top, frameWidth, frameHeight);
    }

    private void applyMorphChild(@NonNull View view, float scale, float frameLeft, float frameTop,
                                 float frameWidth, float frameHeight) {
        view.setPivotX(0f);
        view.setPivotY(0f);
        view.setScaleX(scale);
        view.setScaleY(scale);
        // Pivoting at the view's own origin maps its laid-out point L to frameLeft + L * scale.
        view.setTranslationX(frameLeft - view.getLeft() * (1f - scale));
        view.setTranslationY(frameTop - view.getTop() * (1f - scale));
        int clipRight = Math.round(frameWidth / scale) - view.getLeft();
        int clipBottom = Math.round(frameHeight / scale) - view.getTop();
        if (clipRight >= view.getWidth() && clipBottom >= view.getHeight()) {
            view.setClipBounds(null);
            return;
        }
        morphClip.set(0, 0, Math.max(0, clipRight), Math.max(0, clipBottom));
        view.setClipBounds(morphClip);
    }

    private void resetDetailMorphTransform() {
        resetMorphChild(detailHeader);
        resetMorphChild(collapseChevron);
        resetMorphChild(detailCount);
        resetMorphChild(detailList);
    }

    private static void resetMorphChild(@NonNull View view) {
        view.setScaleX(1f);
        view.setScaleY(1f);
        view.setTranslationX(0f);
        view.setTranslationY(0f);
        view.setClipBounds(null);
    }

    private void bindDetailCount(@NonNull AppDrawerCategoryBucket bucket) {
        detailCount.setText(getResources().getQuantityString(
            R.plurals.app_drawer_category_app_count, bucket.size(), bucket.size()));
    }

    private void finishAtExpanded() {
        resetDetailMorphTransform();
        overview.setVisibility(INVISIBLE);
        detailHeader.setVisibility(VISIBLE);
        collapseChevron.setVisibility(VISIBLE);
        detailCount.setVisibility(VISIBLE);
        detailList.setVisibility(VISIBLE);
        detailHeader.setAlpha(1f);
        collapseChevron.setAlpha(1f);
        detailCount.setAlpha(1f);
        detailList.setAlpha(1f);
        morph.setVisibility(INVISIBLE);
        morph.setClickable(false);
    }

    private void finishAtOverview() {
        resetDetailMorphTransform();
        expandSource = null;
        detailAdapter.releaseAttached(detailList);
        selectedBucket = null;
        detailHeader.setText(null);
        detailHeader.setContentDescription(null);
        detailHeader.setVisibility(INVISIBLE);
        collapseChevron.setContentDescription(null);
        collapseChevron.setVisibility(INVISIBLE);
        detailCount.setText(null);
        detailCount.setVisibility(INVISIBLE);
        detailList.setVisibility(INVISIBLE);
        morph.setVisibility(INVISIBLE);
        morph.setClickable(false);
        overview.setAlpha(1f);
        overview.setVisibility(buckets.isEmpty() ? GONE : VISIBLE);
        emptyState.setVisibility(buckets.isEmpty() ? VISIBLE : GONE);
    }

    private void abortToOverview() {
        expansion.teardown();
        expansionSpring.reset(0f);
        tileAdapter.rebindAttachedPreviews();
        finishAtOverview();
        cancelGesture();
    }

    public void reset() {
        clickGate.suppress();
        expansion.teardown();
        expansionSpring.reset(0f);
        detailAdapter.releaseAttached(detailList);
        tileAdapter.releaseAttachedPreviews(overview);
        tileAdapter.submit(Collections.emptyList());
        buckets = Collections.emptyList();
        selectedBucket = null;
        finishAtOverview();
        cancelGesture();
    }

    public void cancelForSearch() {
        expansion.queryStarted();
        expansionSpring.reset(0f);
        detailAdapter.releaseAttached(detailList);
        selectedBucket = null;
        // The flat search grid is about to bind its own icons. A hidden overview retaining preview
        // drawables would make search the one path that violates the shared-cache staging budget.
        tileAdapter.releaseAttachedPreviews(overview);
        finishAtOverview();
        cancelGesture();
    }

    public void beginTouchStream(@NonNull Part part, float rawY, boolean atTop) {
        clickGate.beginStream();
        streamPart = part;
        streamFinalized = false;
        RecyclerView active = activeRecyclerView();
        boolean scrollable = active != null
            && (active.canScrollVertically(-1) || active.canScrollVertically(1));
        gesturePolicy.begin(new AppDrawerCategoryGesturePolicy.Down(part, atTop, scrollable, 0L),
            SystemClock.uptimeMillis());
        downRawY = rawY;
        collapseStartProgress = expansion.progress();
    }

    /** One-way detail decision. Overview is deliberately handled by the shipped close policy. */
    public boolean claimDetailPreScroll(@NonNull View target, int dy, float rawY) {
        if (streamPart != Part.DETAIL_LIST || target != detailList) return false;
        AppDrawerCategoryGesturePolicy.Claim before = gesturePolicy.claim();
        AppDrawerCategoryGesturePolicy.Claim claim = gesturePolicy.claimOnPreScroll(dy);
        if (claim == AppDrawerCategoryGesturePolicy.Claim.SCROLL) {
            if (gesturePolicy.suppressClick()) clickGate.suppress();
            return false;
        }
        if (claim == AppDrawerCategoryGesturePolicy.Claim.COLLAPSE_DRAG) {
            if (before != AppDrawerCategoryGesturePolicy.Claim.COLLAPSE_DRAG) {
                clickGate.suppress();
                expansion.beginCollapseDrag();
                collapseStartProgress = expansion.progress();
            }
            updateCollapseDrag(rawY);
            return true;
        }
        return false;
    }

    private void updateCollapseDrag(float rawY) {
        float travel = metrics == null ? 1f : metrics.collapseTravelPx;
        float p = collapseStartProgress - Math.max(0f, rawY - downRawY) / Math.max(1f, travel);
        expansionSpring.value = AppDrawerTransitionGeometry.clamp01(p);
        applyExpansionProgress(expansionSpring.value);
    }

    public boolean finishDetailGesture(float velocityPxPerSec, boolean cancelled) {
        if (streamFinalized) return false;
        streamFinalized = true;
        if (gesturePolicy.claim() != AppDrawerCategoryGesturePolicy.Claim.COLLAPSE_DRAG)
            return false;
        gesturePolicy.finishOnce();
        boolean commit = !cancelled && AppDrawerCommitPolicy.decide(expansion.progress(),
            velocityPxPerSec, AppDrawerCommitPolicy.Direction.CLOSING)
            == AppDrawerCommitPolicy.Decision.COMMIT_CLOSE;
        expansion.finishCollapseDrag(commit);
        expansionSpring.value = expansion.progress();
        expansionSpring.target = commit ? 0f : 1f;
        // The release velocity rides into the spring — p is (down - rawY)/travel away from where
        // the finger started, so dp/dt is -v/travel for a downward-positive velocity. Without
        // this, a hard flick decelerated to the spring's own from-rest ramp at the moment of
        // release, which read as the drawer hesitating. Clamped: an extreme fling velocity would
        // otherwise overshoot a critically damped spring visibly past its end.
        float travel = metrics == null ? 1f : Math.max(1f, metrics.collapseTravelPx);
        expansionSpring.vel = Math.max(-COLLAPSE_MAX_INJECTED_VELOCITY,
            Math.min(COLLAPSE_MAX_INJECTED_VELOCITY, -velocityPxPerSec / travel));
        requestFrames();
        return true;
    }

    /** Cap on the flick velocity carried into the expansion spring, in progress/s. */
    private static final float COLLAPSE_MAX_INJECTED_VELOCITY = 6f;

    public void suppressClicks() { clickGate.suppress(); }
    public boolean suppressCellClick() { return clickGate.suppressCellClick(); }

    public void cancelGesture() {
        streamFinalized = true;
        streamPart = Part.OUTSIDE;
        gesturePolicy.cancel();
    }

    @NonNull public Part touchPart(float x, float y) {
        if (x < 0f || x >= getWidth() || y < 0f || y >= getHeight()) return Part.OUTSIDE;
        AppDrawerCategoryExpansionModel.State state = expansion.state();
        if (state == AppDrawerCategoryExpansionModel.State.EXPANDING
            || state == AppDrawerCategoryExpansionModel.State.COLLAPSING
            || state == AppDrawerCategoryExpansionModel.State.COLLAPSE_DRAGGING)
            return Part.TRANSITION_BODY;
        if (state == AppDrawerCategoryExpansionModel.State.EXPANDED) {
            // The whole header band — chevron, title and count — is one collapse affordance.
            if (contains(detailHeader, x, y) || contains(collapseChevron, x, y)
                || contains(detailCount, x, y)) return Part.COLLAPSE_ACTION;
            if (contains(detailList, x, y)) return Part.DETAIL_LIST;
            return Part.EMPTY_CHROME;
        }
        if (buckets.isEmpty()) return Part.EMPTY_CHROME;
        for (int i = 0; i < overview.getChildCount(); i++) {
            View child = overview.getChildAt(i);
            if (!(child instanceof AppDrawerCategoryTileView)) continue;
            AppDrawerCategoryTileView tile = (AppDrawerCategoryTileView) child;
            if (containsDescendant(tile.expandTarget, x, y)
                || containsDescendant(tile.heading, x, y)) return Part.EXPAND_ACTION;
        }
        return contains(overview, x, y) ? Part.OVERVIEW_LIST : Part.EMPTY_CHROME;
    }

    private boolean containsDescendant(@NonNull View child, float x, float y) {
        int[] childLocation = new int[2];
        int[] ownLocation = new int[2];
        child.getLocationOnScreen(childLocation);
        getLocationOnScreen(ownLocation);
        float left = childLocation[0] - ownLocation[0];
        float top = childLocation[1] - ownLocation[1];
        return x >= left && x < left + child.getWidth() && y >= top && y < top + child.getHeight();
    }

    private static boolean contains(@NonNull View view, float x, float y) {
        return view.getVisibility() == VISIBLE && x >= view.getLeft() && x < view.getRight()
            && y >= view.getTop() && y < view.getBottom();
    }

    @Nullable public RecyclerView activeRecyclerView() {
        if (expansion.state() == AppDrawerCategoryExpansionModel.State.EXPANDED) return detailList;
        if (expansion.state() == AppDrawerCategoryExpansionModel.State.OVERVIEW && !buckets.isEmpty())
            return overview;
        return null;
    }
    public boolean isOverview() {
        return expansion.state() == AppDrawerCategoryExpansionModel.State.OVERVIEW;
    }
    public boolean isExpandedOrTransitioning() { return !isOverview(); }
    @NonNull public AppDrawerCategoryExpansionModel.State expansionState() { return expansion.state(); }
    public float expansionProgress() { return expansion.progress(); }
    @NonNull public RecyclerView getOverview() { return overview; }
    @NonNull public RecyclerView getDetailList() { return detailList; }
    @NonNull public TextView getDetailHeader() { return detailHeader; }
    @NonNull public ImageView getCollapseChevron() { return collapseChevron; }
    @NonNull public TextView getDetailCount() { return detailCount; }
    @NonNull public TextView getEmptyState() { return emptyState; }
    @NonNull public AppDrawerCategoryMorphView getMorph() { return morph; }
    @NonNull public AppDrawerCategoryTileAdapter getTileAdapter() { return tileAdapter; }
    @NonNull public AppDrawerCategoryDetailAdapter getDetailAdapter() { return detailAdapter; }
    @Nullable AppDrawerCategoryGridMetrics getMetrics() { return metrics; }

    private int positionOfCategory(@NonNull String categoryId) {
        for (int i = 0; i < buckets.size(); i++)
            if (categoryId.equals(buckets.get(i).category.slug)) return i;
        return -1;
    }
    @Nullable private AppDrawerCategoryBucket bucketForId(@NonNull String id) {
        int position = positionOfCategory(id);
        return position < 0 ? null : buckets.get(position);
    }
    private void requestFrames() {
        Runnable listener = frameRequestListener;
        if (listener != null) listener.run();
    }

    @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = Math.max(0, MeasureSpec.getSize(widthMeasureSpec));
        int height = Math.max(0, MeasureSpec.getSize(heightMeasureSpec));
        overview.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
        emptyState.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
        morph.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
        int chevron = chevronSizePx();
        collapseChevron.measure(MeasureSpec.makeMeasureSpec(chevron, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(chevron, MeasureSpec.EXACTLY));
        detailCount.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.AT_MOST),
            MeasureSpec.makeMeasureSpec(height, MeasureSpec.AT_MOST));
        // The title is measured at the width it will actually be laid out at — what is left between
        // the chevron and the count. Measuring it against the full width let a long category name
        // compute its ellipsis for a box wider than the one it lands in, so it was clipped by the
        // count instead of ellipsized before it.
        HeaderRow header = headerRow(width);
        detailHeader.measure(MeasureSpec.makeMeasureSpec(header.titleWidth, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(height, MeasureSpec.AT_MOST));
        AppDrawerCategoryGridMetrics current = metrics;
        float listHeight = 0f;
        if (current != null) {
            int count = selectedBucket == null ? detailAdapter.getItemCount() : selectedBucket.size();
            listHeight = current.resolveDetail(count, height, headerBandPx()).listHeightPx;
        }
        detailList.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(Math.max(0, Math.round(listHeight)), MeasureSpec.EXACTLY));
        setMeasuredDimension(resolveSize(width, widthMeasureSpec), resolveSize(height, heightMeasureSpec));
    }

    private int chevronSizePx() {
        return Math.max(1, Math.round(CHEVRON_SIZE_DP
            * getResources().getDisplayMetrics().density));
    }

    /** Horizontal geometry of the header row, resolved identically by measure and layout. */
    private static final class HeaderRow {
        final int chevronLeft;
        final int chevronSize;
        final int titleLeft;
        final int titleWidth;
        final int countLeft;
        final int countWidth;

        HeaderRow(int chevronLeft, int chevronSize, int titleLeft, int titleWidth,
                  int countLeft, int countWidth) {
            this.chevronLeft = chevronLeft;
            this.chevronSize = chevronSize;
            this.titleLeft = titleLeft;
            this.titleWidth = titleWidth;
            this.countLeft = countLeft;
            this.countWidth = countWidth;
        }
    }

    @NonNull
    private HeaderRow headerRow(int width) {
        float density = getResources().getDisplayMetrics().density;
        int sideInset = Math.round(HEADER_SIDE_INSET_DP * density);
        int itemGap = Math.round(HEADER_ITEM_GAP_DP * density);
        int chevron = chevronSizePx();
        int countWidth = Math.min(detailCount.getMeasuredWidth(),
            Math.max(0, width - 2 * sideInset));
        // Tracked text carries one letter-space after its last glyph, and an END-gravity box aligns
        // that trailing space — not the glyph — to its right edge. Pushing the box out by exactly
        // that much lands the S of "APPS" on the same inset the chevron circle starts at.
        int trailingTrack = Math.round(detailCount.getLetterSpacing() * detailCount.getTextSize());
        int titleLeft = sideInset + chevron + itemGap;
        int countLeft = Math.max(titleLeft, width - sideInset - countWidth + trailingTrack);
        int titleWidth = Math.max(0, countLeft - itemGap - titleLeft);
        return new HeaderRow(sideInset, chevron, titleLeft, titleWidth, countLeft, countWidth);
    }

    /** The header row's band: tall enough for the chevron circle and the title, whichever wins. */
    private int headerBandPx() {
        return Math.max(detailHeader.getMeasuredHeight(), chevronSizePx());
    }

    @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int width = r - l;
        int height = b - t;
        overview.layout(0, 0, width, height);
        emptyState.layout(0, 0, width, height);
        morph.layout(0, 0, width, height);
        AppDrawerCategoryGridMetrics current = metrics;
        if (current == null) {
            detailHeader.layout(0, height, width, height);
            collapseChevron.layout(0, height, 0, height);
            detailCount.layout(width, height, width, height);
            detailList.layout(0, height, width, height);
            return;
        }
        int count = selectedBucket == null ? detailAdapter.getItemCount() : selectedBucket.size();
        AppDrawerCategoryGridMetrics.DetailLayout layout = current.resolveDetail(count, height,
            headerBandPx());
        int headerTop = Math.round(layout.headerTopPx);
        int headerBottom = Math.round(layout.headerBottomPx);
        int listTop = Math.round(layout.listTopPx);
        // Header row: chevron circle at the leading inset, count at the trailing inset, title
        // between them. The circle is centred in the band because it is a shape, but the two runs
        // of text are placed on a shared baseline rather than each centred in its own box — a 17sp
        // title and a 10sp mono count centred separately sit at two different baselines, which is
        // exactly what reads as three things not on one row.
        HeaderRow header = headerRow(width);
        int band = Math.max(0, headerBottom - headerTop);
        // The title still spans the whole band: it is the one full-band collapse target the touch
        // regions and the tests read, and its own CENTER_VERTICAL gravity centres the text in it.
        detailHeader.layout(header.titleLeft, headerTop, header.titleLeft + header.titleWidth,
            headerBottom);

        // The arrow is centred on the title's *ink*, not on the band. A text box is centred by its
        // ascent-to-descent line box, and the descent below the baseline is empty for a title with
        // no descenders — so on a face with a generous descent the letters ride visibly above the
        // box's centre, and a circle centred on the band then sits low against them. Measuring the
        // cap height off the actual paint keeps this true whatever face the system supplies.
        int titleBaseline = detailHeader.getBaseline();
        int inkCentre = titleBaseline >= 0
            ? headerTop + titleBaseline - capHeightPx(detailHeader) / 2
            : headerTop + band / 2;
        int chevronTop = clampInt(inkCentre - header.chevronSize / 2,
            headerTop - header.chevronSize / 2, headerBottom - header.chevronSize / 2);
        collapseChevron.layout(header.chevronLeft, chevronTop,
            header.chevronLeft + header.chevronSize, chevronTop + header.chevronSize);

        int countHeight = detailCount.getMeasuredHeight();
        int countTop = headerTop + Math.max(0, (band - countHeight) / 2);
        // Read after the title is laid out: a TextView's baseline includes the offset its vertical
        // gravity puts it at inside the box it actually got.
        int countBaseline = detailCount.getBaseline();
        if (titleBaseline >= 0 && countBaseline >= 0) {
            countTop = clampInt(headerTop + titleBaseline - countBaseline,
                headerTop, Math.max(headerTop, headerBottom - countHeight));
        }
        detailCount.layout(header.countLeft, countTop, header.countLeft + header.countWidth,
            countTop + countHeight);
        detailList.layout(0, listTop, width, height);
    }

    /**
     * Cap height of the face the view is actually painting with, measured rather than assumed: the
     * ratio between cap height and text size is a property of the font, and this app ships a font
     * picker.
     */
    private static int capHeightPx(@NonNull TextView view) {
        Rect bounds = new Rect();
        view.getPaint().getTextBounds("H", 0, 1, bounds);
        return bounds.height() > 0 ? bounds.height() : Math.round(view.getTextSize() * 0.71f);
    }

    private static int clampInt(int value, int min, int max) {
        return value < min ? min : (value > max ? max : value);
    }

    private static final class TileSpacingDecoration extends RecyclerView.ItemDecoration {
        private int columns = 1;
        private int gapPx;

        void setMetrics(@NonNull AppDrawerCategoryGridMetrics metrics) {
            columns = Math.max(1, metrics.columns);
            gapPx = Math.max(0, Math.round(metrics.itemGapPx));
        }

        @Override public void getItemOffsets(@NonNull Rect outRect,
            @NonNull View view, @NonNull RecyclerView parent,
            @NonNull RecyclerView.State state) {
            int position = parent.getChildAdapterPosition(view);
            if (position == RecyclerView.NO_POSITION || gapPx == 0 || columns == 1) {
                outRect.set(0, 0, 0, 0);
                return;
            }
            int column = position % columns;
            int left = column * gapPx / columns;
            int right = gapPx - (column + 1) * gapPx / columns;
            outRect.set(left, 0, right, 0);
        }
    }

    /** Suppression is one-way for a stream and never cleared by mode/interactivity teardown. */
    public static final class ClickGate implements AppDrawerAppCellView.ClickGate {
        private boolean suppressed;
        void beginStream() { suppressed = false; }
        void suppress() { suppressed = true; }
        @Override public boolean suppressCellClick() { return suppressed; }
    }
}
