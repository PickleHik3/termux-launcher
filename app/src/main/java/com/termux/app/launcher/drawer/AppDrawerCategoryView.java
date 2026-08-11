package com.termux.app.launcher.drawer;

import android.graphics.Color;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
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

    private final RecyclerView overview;
    private final RecyclerView detailList;
    private final TextView detailHeader;
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
    private final ClickGate clickGate = new ClickGate();
    private final int touchSlop;

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

    public AppDrawerCategoryView(@NonNull android.content.Context context,
                                 @Nullable SuggestionBarView dock) {
        super(context);
        setClipChildren(false);
        setClipToPadding(false);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();

        tileAdapter = new AppDrawerCategoryTileAdapter(dock);
        tileAdapter.setExpansionListener(this);
        tileAdapter.setClickGate(clickGate);
        overviewLayout = new GridLayoutManager(context, 1);
        overviewLayout.setItemPrefetchEnabled(false);
        overview = new RecyclerView(context);
        overview.setLayoutManager(overviewLayout);
        overview.setAdapter(tileAdapter);
        overview.setHasFixedSize(true);
        overview.setItemViewCacheSize(0);
        overview.setItemAnimator(null);
        overview.setOverScrollMode(OVER_SCROLL_NEVER);
        overview.setClipToPadding(false);
        overview.addItemDecoration(tileSpacing);
        overview.addOnScrollListener(popupDismissScrollListener());
        addView(overview);

        detailAdapter = new AppDrawerCategoryDetailAdapter(dock);
        detailAdapter.setClickGate(clickGate);
        detailLayout = new GridLayoutManager(context, AppDrawerGridMetrics.MIN_COLUMNS);
        detailLayout.setItemPrefetchEnabled(false);
        detailList = new RecyclerView(context);
        detailList.setLayoutManager(detailLayout);
        detailList.setAdapter(detailAdapter);
        detailList.setHasFixedSize(true);
        detailList.setItemViewCacheSize(0);
        detailList.setItemAnimator(null);
        detailList.setOverScrollMode(OVER_SCROLL_NEVER);
        detailList.setVisibility(INVISIBLE);
        detailList.addOnScrollListener(popupDismissScrollListener());
        addView(detailList);

        detailHeader = new TextView(context);
        detailHeader.setTextSize(TypedValue.COMPLEX_UNIT_SP, 28f);
        detailHeader.setSingleLine(true);
        detailHeader.setEllipsize(TextUtils.TruncateAt.END);
        detailHeader.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
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

        emptyState = new TextView(context);
        emptyState.setText(R.string.app_drawer_category_no_apps);
        emptyState.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f);
        emptyState.setGravity(android.view.Gravity.CENTER);
        emptyState.setTextColor(dock == null ? Color.WHITE : dock.getLauncherTextColor());
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
        emptyState.setTextColor(color);
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
        overview.setPadding(horizontal, 0, horizontal, 0);
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
        Frame destination = new Frame(0f, 0f, getWidth(), getHeight());
        float radius = metrics == null ? 0f : metrics.radiusPx;
        morph.setFrames(sourceFrame, destination, radius, radius);
        morph.setVisibility(VISIBLE);
        morph.setClickable(true); // consumes transition-body touches without parent interception.
        detailHeader.setText(getResources().getString(bucket.category.labelRes));
        detailHeader.setContentDescription(getResources().getString(
            R.string.app_drawer_category_collapse,
            getResources().getString(bucket.category.labelRes)));
        detailHeader.setVisibility(VISIBLE);
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
        overview.setAlpha(1f - AppDrawerTransitionGeometry.ramp(p, 0f, 0.25f));
        float detailAlpha = AppDrawerTransitionGeometry.ramp(p, 0.35f, 0.70f);
        detailHeader.setAlpha(detailAlpha);
        detailList.setAlpha(detailAlpha);
        morph.setProgress(p);
    }

    private void finishAtExpanded() {
        overview.setVisibility(INVISIBLE);
        detailHeader.setVisibility(VISIBLE);
        detailList.setVisibility(VISIBLE);
        detailHeader.setAlpha(1f);
        detailList.setAlpha(1f);
        morph.setVisibility(INVISIBLE);
        morph.setClickable(false);
    }

    private void finishAtOverview() {
        detailAdapter.releaseAttached(detailList);
        selectedBucket = null;
        detailHeader.setText(null);
        detailHeader.setContentDescription(null);
        detailHeader.setVisibility(INVISIBLE);
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
            android.os.SystemClock.uptimeMillis());
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
        requestFrames();
        return true;
    }

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
            if (contains(detailHeader, x, y)) return Part.COLLAPSE_ACTION;
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
        detailHeader.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.AT_MOST),
            MeasureSpec.makeMeasureSpec(height, MeasureSpec.AT_MOST));
        AppDrawerCategoryGridMetrics current = metrics;
        float listHeight = 0f;
        if (current != null) {
            int count = selectedBucket == null ? detailAdapter.getItemCount() : selectedBucket.size();
            listHeight = current.resolveDetail(count, height, detailHeader.getMeasuredHeight()).listHeightPx;
        }
        detailList.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(Math.max(0, Math.round(listHeight)), MeasureSpec.EXACTLY));
        setMeasuredDimension(resolveSize(width, widthMeasureSpec), resolveSize(height, heightMeasureSpec));
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
            detailList.layout(0, height, width, height);
            return;
        }
        int count = selectedBucket == null ? detailAdapter.getItemCount() : selectedBucket.size();
        AppDrawerCategoryGridMetrics.DetailLayout layout = current.resolveDetail(count, height,
            detailHeader.getMeasuredHeight());
        int headerTop = Math.round(layout.headerTopPx);
        int headerBottom = Math.round(layout.headerBottomPx);
        int listTop = Math.round(layout.listTopPx);
        detailHeader.layout(0, headerTop, width, headerBottom);
        detailList.layout(0, listTop, width, height);
    }

    private static final class TileSpacingDecoration extends RecyclerView.ItemDecoration {
        private int columns = 1;
        private int gapPx;

        void setMetrics(@NonNull AppDrawerCategoryGridMetrics metrics) {
            columns = Math.max(1, metrics.columns);
            gapPx = Math.max(0, Math.round(metrics.itemGapPx));
        }

        @Override public void getItemOffsets(@NonNull android.graphics.Rect outRect,
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
