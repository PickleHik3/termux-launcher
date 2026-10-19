package com.termux.app.launcher.widget;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.os.SystemClock;
import android.graphics.Paint;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.util.AttributeSet;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;

import com.termux.R;
import com.termux.app.launcher.home.HomeGestures;
import com.termux.app.launcher.home.HomePageIndicator;
import com.termux.app.launcher.home.HomePageTransition;

import java.util.List;

/** The widget grid's own body, a page of the pane wall; it has no authority over the wall or the terminal's geometry. */
public final class WidgetPaneView extends FrameLayout {
    /** The page dots' band under the grid: their height, their air to the pane's edge, and to the grid. */
    private static final int DOTS_HEIGHT_DP = 14;
    private static final int DOTS_BOTTOM_MARGIN_DP = 4;
    private static final int DOTS_GAP_DP = 4;

    public interface Listener {
        /** The horizontal page swipe committed; the coordinator re-renders onto this page. */
        void onPageChangeRequested(int page);
        /** The tick on the page's border tab: keep the layout and close edit mode. */
        default void onWidgetEditCommit() { }
        /** The cross: put the layout back as it was when edit mode opened, and close. */
        default void onWidgetEditDiscard() { }
        /** The + on the page's border tab: another page, and the pane turns to it. */
        default void onWidgetAddPage() { }
        /**
         * A vertical swipe on the page's free area let go, up ({@code up}) or down: the palette
         * gesture ({@link HomeSwipePolicy}). Never sent for a swipe on a widget, a Home float or
         * the picker.
         */
        default void onFreeAreaVerticalSwipe(boolean up) { }
        /**
         * Two taps in quick succession on the page's free area; sent only while
         * {@link #setExtraGestures} watches for it.
         */
        default void onFreeAreaDoubleTap() { }
        /**
         * Two fingers that set off on the free area swiped up ({@code up}) or down and lifted;
         * sent only while {@link #setExtraGestures} watches for it.
         */
        default void onFreeAreaTwoFingerSwipe(boolean up) { }
        /**
         * The page's content moved sideways: the swipe drags it, or a page slides in or back.
         * Per frame of that motion; {@link #contentShiftFraction} says where it stands.
         */
        default void onContentShifted() { }
    }

    /**
     * The seam out of Home into the rest of the wall's row (ADR 0008): a plain swipe forward on
     * the last Home page carries on into the first window instead of resisting. The pane asks
     * whether the row goes on, then hands the drag over to the wall, in screen coordinates, since
     * this view slides with the wall while the finger drives it.
     */
    public interface Seam {
        /** Whether a forward swipe on the last Home page leaves Home: the row has a page after it. */
        boolean canLeaveForward();
        /** The swipe is the wall's from here. */
        void begin();
        /** The finger has travelled {@code dxPx} on screen since it went down, positive right. */
        void drag(float dxPx);
        /** The finger let go at {@code velocityPxPerSec}, positive right. */
        void end(float velocityPxPerSec);
        /** The stream was cancelled. */
        void cancel();
    }

    /**
     * Where the finger carrying a widget out of the picker is, and where it let go.
     *
     * <p>The picker is a sheet over this pane, so a widget dragged out of it starts its life as a
     * press on a list row inside a child of this view and has to end as a drop on the grid, with
     * the sheet dismissed in between. Nothing below can follow that: the row it started on is
     * recycled away with the sheet. So the pane itself takes the stream over for the rest of the
     * gesture and reports it here in screen coordinates.
     */
    public interface CarryListener {
        void onCarryMove(float rawX, float rawY);
        void onCarryEnd(float rawX, float rawY, boolean canceled);
    }

    /**
     * A layer pinned to the Home page on screen: it shows what belongs to that page (the Home
     * floats), moves with the page's content while a swipe drags it and while the next page slides
     * in, and keeps the page's swipe from starting on what it holds.
     */
    public interface PagePinnedLayer {
        /** The Home page {@code page} is the one on screen now. */
        void onHomePageShown(int page);
        /** Whether a press at ({@code x}, {@code y}), in the pane's coordinates, lands on the layer. */
        boolean holdsPoint(float x, float y);
        /**
         * Whether a press there on the layer gives a sideways swipe to the page: it pages Home (or
         * crosses the seam) as a swipe on the free area does, while taps and vertical swipes stay
         * with what is under the finger.
         */
        default boolean yieldsSidewaysSwipe(float x, float y) { return false; }
    }

    /** Fraction of the pane width a released drag must cross to commit a page switch. */
    private static final float PAGE_COMMIT_FRACTION = 1f / 3f;
    private static final float PAGE_COMMIT_VELOCITY_DP_PER_SEC = 900f;
    private static final float PAGE_EDGE_RESISTANCE = 0.35f;
    /** How long a page takes to slide in or back once the finger has let go, at Normal speed. */
    static final long PAGE_SETTLE_MS = 160L;
    /** The camera's distance for Carousel's turn, in dp: far enough that a page never looms. */
    private static final float CAMERA_DISTANCE_DP = 8000f;

    private final WidgetGridView grid;
    private final LinearLayout empty;
    private final TextView emptyMessage;
    private final TextView notice;
    private final WidgetPickerSheetView picker;
    private final PageDotsView dots;
    private final Runnable hideNotice;
    private final int touchSlop;
    private final int doubleTapSlop;
    private Listener listener;
    private boolean reducedMotion;
    @NonNull private HomePageIndicator indicator = HomePageIndicator.DEFAULT;
    /** How the page moves while a swipe turns it (Settings › Home › Page transition). */
    @NonNull private HomePageTransition transition = HomePageTransition.DEFAULT;
    /** General › Animation speed's multiplier on the settle; 0 is Off, the instant cut. */
    private float motionScale = 1f;
    /**
     * Where the page's content stands off its rest, in px, positive to the right: the finger's
     * drag and the settle move this, and the effect draws it ({@link #applyContentShift}). Kept
     * apart from the views' own translation, which an effect may not move at all.
     */
    private float contentShiftPx;
    @Nullable private ValueAnimator contentSettle;
    @NonNull private final HomePageTransition.Transform contentTransform =
        new HomePageTransition.Transform();

    private int pageCount = 1;
    private int currentPage;
    private boolean pagingTracking;
    private boolean pagingDragging;
    private float pagingDownX, pagingDownY;
    /** The seam out of Home, if the wall gave one, and whether the swipe under way is its. */
    @Nullable private Seam seam;
    private boolean seamDragging;
    /** Where the finger went down on screen, and its speed on screen, for the seam's drag. */
    private float pagingDownRawX;
    @Nullable private VelocityTracker seamVelocity;
    private boolean pageCommitPending;
    private int pageCommitDirection;
    private float pageCommitDragX;
    @Nullable private VelocityTracker pagingVelocity;
    /**
     * The palette gesture: whether the press landed on the free area (no widget, no Home float,
     * no picker, no edit chrome), and whether its vertical swipe is under way.
     */
    private boolean verticalCandidate;
    private boolean verticalDragging;
    /**
     * Settings › Home › Gestures: whether a double tap and a two-finger swipe on the free area are
     * watched for. Off (nothing set for them), the page reads taps and second fingers as before.
     */
    private boolean watchDoubleTap;
    private boolean watchTwoFinger;
    /** Whether the press now down landed on the free area and has not travelled. */
    private boolean freePress;
    /** The last tap's lift on the free area, for a double tap; -1 for none. */
    private long lastTapUpMs = -1;
    private float lastTapX, lastTapY;
    /** The press now down is the second of a double tap, fired on its lift. */
    private boolean secondTap;
    /** Two fingers set off on the free area, watched for a vertical swipe; then claimed. */
    private boolean twoFingerTracking;
    private boolean twoFingerDragging;
    private float twoFingerDownX, twoFingerDownY;

    public WidgetPaneView(@NonNull Context context, AttributeSet attrs) { this(context); }

    public WidgetPaneView(@NonNull Context context) {
        super(context); setId(R.id.widget_pane); setClipChildren(true); setClipToPadding(true);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        doubleTapSlop = ViewConfiguration.get(context).getScaledDoubleTapSlop();
        // Provider text inputs may take focus; the pane and its hosts never keep it themselves.
        setDescendantFocusability(FOCUS_AFTER_DESCENDANTS);
        setFocusable(false);

        grid = new WidgetGridView(context); grid.setId(R.id.widget_grid);
        float cameraDistance = CAMERA_DISTANCE_DP * getResources().getDisplayMetrics().density;
        grid.setCameraDistance(cameraDistance);
        // The page dots sit in a band below the grid, never over its last row. The band is kept
        // whether or not the dots show, so adding a page does not shrink every cell.
        LayoutParams gridParams = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
        gridParams.bottomMargin = dp(DOTS_HEIGHT_DP + DOTS_BOTTOM_MARGIN_DP + DOTS_GAP_DP);
        addView(grid, gridParams);

        empty = new LinearLayout(context); empty.setOrientation(LinearLayout.VERTICAL);
        empty.setCameraDistance(cameraDistance);
        empty.setGravity(Gravity.CENTER);
        emptyMessage = new TextView(context); emptyMessage.setId(R.id.widget_empty_message);
        emptyMessage.setText(R.string.widget_empty_hint); emptyMessage.setGravity(Gravity.CENTER);
        empty.addView(emptyMessage, new LinearLayout.LayoutParams(
            LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        addView(empty, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        dots = new PageDotsView(context); dots.setId(R.id.widget_page_dots);
        dots.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        LayoutParams dotsParams = new LayoutParams(LayoutParams.MATCH_PARENT, dp(DOTS_HEIGHT_DP),
            Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        dotsParams.bottomMargin = dp(DOTS_BOTTOM_MARGIN_DP);
        addView(dots, dotsParams);
        dots.setVisibility(GONE);

        notice = new TextView(context); notice.setId(R.id.widget_pane_notice); notice.setGravity(Gravity.CENTER);
        hideNotice = () -> notice.setVisibility(GONE);
        notice.setVisibility(GONE); notice.setPadding(dp(12), dp(6), dp(12), dp(6));
        LayoutParams noticeParams = new LayoutParams(LayoutParams.WRAP_CONTENT, dp(48),
            Gravity.TOP | Gravity.CENTER_HORIZONTAL); addView(notice, noticeParams);

        picker = new WidgetPickerSheetView(context, new WidgetPickerAdapter.Listener() {
            @Override public void onProviderSelected(@NonNull WidgetProviderItem item) {
                if (providerListener != null) providerListener.onProviderSelected(item);
            }
            @Override public void onProviderHeld(@NonNull WidgetProviderItem item,
                                                 @NonNull View card, float rawX, float rawY) {
                if (providerListener != null) providerListener.onProviderHeld(item, card, rawX, rawY);
            }
        });
        picker.setId(R.id.widget_picker_sheet);
        addView(picker, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
    }

    private WidgetPickerAdapter.Listener providerListener;
    @Nullable private View pinnedLayer;

    /**
     * Pins {@code layer} to the Home pages: drawn over the grid and under the page dots, the picker
     * and the edit chrome, told which page is on screen and carried with the page's content.
     */
    public <T extends View & PagePinnedLayer> void setPagePinnedLayer(@Nullable T layer) {
        if (pinnedLayer == layer) return;
        if (pinnedLayer != null) removeView(pinnedLayer);
        pinnedLayer = layer;
        if (layer == null) return;
        if (layer.getParent() instanceof ViewGroup) ((ViewGroup) layer.getParent()).removeView(layer);
        addView(layer, indexOfChild(empty) + 1,
            new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        layer.setCameraDistance(CAMERA_DISTANCE_DP * getResources().getDisplayMetrics().density);
        applyTransform(layer);
        layer.onHomePageShown(currentPage);
    }

    private boolean pinnedLayerHolds(@NonNull MotionEvent event) {
        return pinnedLayer instanceof PagePinnedLayer && pinnedLayer.getVisibility() == VISIBLE
            && ((PagePinnedLayer) pinnedLayer).holdsPoint(event.getX(), event.getY());
    }

    private boolean pinnedLayerYieldsSideways(@NonNull MotionEvent event) {
        return pinnedLayer instanceof PagePinnedLayer && pinnedLayer.getVisibility() == VISIBLE
            && ((PagePinnedLayer) pinnedLayer).yieldsSidewaysSwipe(event.getX(), event.getY());
    }

    /** The wall's seam: a forward swipe on the last Home page carries on into the row. */
    public void setSeam(@Nullable Seam value) {
        seam = value;
    }

    public void setListener(@NonNull Listener value,
                            @NonNull WidgetPickerAdapter.Listener providers) {
        listener = value; providerListener = providers;
    }
    public void setReducedMotion(boolean value) { reducedMotion = value; }

    /**
     * Settings › Home › Page indicator: the dots, or none. The band they stand in stays either
     * way, so the grid keeps its height.
     */
    public void setPageIndicator(@NonNull HomePageIndicator value) {
        if (indicator == value) return;
        indicator = value;
        setPageState(pageCount, currentPage);
    }

    /** Whether a double tap and a two-finger swipe on the free area are watched for. */
    public void setExtraGestures(boolean doubleTap, boolean twoFinger) {
        watchDoubleTap = doubleTap;
        watchTwoFinger = twoFinger;
        if (!doubleTap) lastTapUpMs = -1;
    }

    /**
     * How the page moves while a swipe turns it, and General › Animation speed's multiplier on
     * the settle (0 for Off). Read when the launcher comes to the front; a motion under way keeps
     * what it started with.
     */
    public void setPageMotion(@NonNull HomePageTransition effect, float speedMultiplier) {
        motionScale = Math.max(0f, speedMultiplier);
        if (transition == effect) return;
        transition = effect;
        applyContentShift();
    }
    @NonNull public WidgetGridView grid() { return grid; }

    /**
     * A pane corner may still claim the finger that is down, so nothing in the grid runs a long
     * press of its own until it cannot. The page's frame decides that on the landing point and
     * says so here; two long presses on one finger is the bug this closes, where a hold in a
     * corner opened the corner tab and the grid's menu on top of it.
     */
    public void setHoldExempt(boolean exempt) { grid.setHoldExempt(exempt); }
    @NonNull public WidgetPickerSheetView picker() { return picker; }
    public boolean onBackPressed() {
        if (!picker.isOpen()) return false;
        picker.close();
        return true;
    }

    /** Lazily created edit chrome; always the last child so it draws over every pane surface. */
    @NonNull public WidgetEditOverlayView widgetEditOverlay() {
        if (editOverlay == null) {
            editOverlay = new WidgetEditOverlayView(getContext());
            editOverlay.setId(R.id.widget_edit_overlay);
            addView(editOverlay, new LayoutParams(LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT));
        } else if (indexOfChild(editOverlay) != getChildCount() - 1) {
            editOverlay.bringToFront();
        }
        return editOverlay;
    }

    public void hideWidgetEditOverlay() {
        if (editOverlay != null) editOverlay.hide();
    }

    /**
     * Where a widget crossing between pages is drawn. Lazily created like the edit chrome, and
     * kept under it so the snap ghost on the page below the finger is never hidden by the widget
     * the finger is carrying.
     */
    @NonNull public WidgetDragLayerView widgetDragLayer() {
        if (dragLayer == null) {
            dragLayer = new WidgetDragLayerView(getContext());
            dragLayer.setId(R.id.widget_drag_layer);
            addView(dragLayer, new LayoutParams(LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT));
        }
        if (editOverlay != null && indexOfChild(editOverlay) != getChildCount() - 1) {
            editOverlay.bringToFront();
        }
        return dragLayer;
    }

    /** Lets go of anything the drag layer is carrying; nothing to do when it was never made. */
    public void releaseWidgetDragLayer() {
        if (dragLayer != null) dragLayer.drop(null, false);
    }

    @Nullable private WidgetDragLayerView dragLayer;
    @Nullable private CarryListener carry;

    /**
     * Takes the finger that is currently on the picker over. Everything under this pane is sent
     * the cancel an intercepting parent would have sent it, so the row the press began on lets go
     * cleanly and performs no click, and every later event of that gesture goes to {@code listener}
     * instead of to a child. Ancestors are asked to keep their hands off it for the same reason.
     */
    public void beginCarry(@NonNull CarryListener listener) {
        if (carry != null) return;
        carry = listener;
        ViewParent parent = getParent();
        if (parent != null) parent.requestDisallowInterceptTouchEvent(true);
        long now = SystemClock.uptimeMillis();
        MotionEvent cancel = MotionEvent.obtain(now, now, MotionEvent.ACTION_CANCEL, 0f, 0f, 0);
        super.dispatchTouchEvent(cancel);
        cancel.recycle();
    }

    /**
     * Gives the stream back without a drop. The finger may still be down; it simply owns nothing
     * from here, which is what a picker closing under it or a pane going away amounts to.
     */
    public void endCarry() { carry = null; }

    /** Whether a widget is being carried out of the picker right now. */
    public boolean carrying() { return carry != null; }

    @Override public boolean dispatchTouchEvent(@NonNull MotionEvent event) {
        CarryListener carried = carry;
        if (carried != null) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_MOVE:
                    carried.onCarryMove(event.getRawX(), event.getRawY());
                    return true;
                case MotionEvent.ACTION_UP:
                    carry = null;
                    carried.onCarryEnd(event.getRawX(), event.getRawY(), false);
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    carry = null;
                    carried.onCarryEnd(event.getRawX(), event.getRawY(), true);
                    return true;
                case MotionEvent.ACTION_DOWN:
                    // A press with no lift before it: the carrying finger was lost somewhere this
                    // view never heard about. End the carry and let the new press through.
                    carry = null;
                    carried.onCarryEnd(event.getRawX(), event.getRawY(), true);
                    break;
                default:
                    // Second fingers belong to nobody while one is carrying a widget.
                    return true;
            }
        }
        return super.dispatchTouchEvent(event);
    }

    /**
     * Slides the page that has just been rendered in from one side, exactly as a committed swipe
     * does: a widget dragged into the edge band turns the page, and the turn has to read as the
     * same movement the finger already knows.
     */
    public void slideInFrom(int direction) {
        enterFromSide(direction, 0f);
    }

    /** The commit and discard buttons on the page's own border tab, relayed to the coordinator. */
    public void commitWidgetEdit() {
        if (listener != null) listener.onWidgetEditCommit();
    }

    public void discardWidgetEdit() {
        if (listener != null) listener.onWidgetEditDiscard();
    }

    /** The + on the page's border tab, relayed the same way. */
    public void addWidgetPage() {
        if (listener != null) listener.onWidgetAddPage();
    }

    public boolean widgetEditActive() { return editOverlay != null && editOverlay.isShowing(); }

    /**
     * Whether a point, in this view's coordinates, is on the widget edit chrome. The page's frame
     * asks before it takes a tap for its own border band: a top-row widget's remove chip sits
     * inside that band, and without this the page swallowed the press and the widget could not be
     * removed.
     */
    public boolean widgetEditWantsPoint(float x, float y) {
        return editOverlay != null && editOverlay.isShowing() && editOverlay.wantsPoint(x, y);
    }

    private WidgetEditOverlayView editOverlay;

    public void render(@NonNull LauncherWidgetRepository repository,
                       @NonNull LauncherWidgetHostController.Capability capability) {
        render(repository, capability, 0);
    }

    public void render(@NonNull LauncherWidgetRepository repository,
                       @NonNull LauncherWidgetHostController.Capability capability, int page) {
        hideWidgetEditOverlay();
        List<LauncherWidgetRecord> pageRecords = repository.recordsOnPage(page);
        boolean populated = !pageRecords.isEmpty();
        grid.refresh(repository.gridDefinition(), pageRecords);
        empty.setVisibility(populated ? GONE : VISIBLE);
        // The grid stays visible even empty: cell-free surface owns the long-press menu, and the
        // picker span/fit is derived from its real measured pixels.
        grid.setVisibility(VISIBLE);
        boolean supported = capability == LauncherWidgetHostController.Capability.AVAILABLE;
        emptyMessage.setText(supported ? R.string.widget_empty_hint : R.string.widget_unsupported);
        setPageState(repository.pageCount(), page);
    }

    /** Updates the indicator and, after a committed swipe, slides the new page's content in. */
    public void setPageState(int count, int current) {
        int previousPage = currentPage;
        pageCount = Math.max(1, count);
        currentPage = Math.max(0, Math.min(pageCount - 1, current));
        int dotsVisibility = indicator.dotsShown(pageCount) ? VISIBLE : GONE;
        if (dots.getVisibility() != dotsVisibility) dots.setVisibility(dotsVisibility);
        dots.invalidate();
        if (pinnedLayer instanceof PagePinnedLayer)
            ((PagePinnedLayer) pinnedLayer).onHomePageShown(currentPage);
        if (pageCommitPending && currentPage != previousPage) {
            pageCommitPending = false;
            enterFromSide(pageCommitDirection, pageCommitDragX);
        } else if (!pagingDragging) {
            setContentTranslationX(0f);
        }
    }

    public int currentPage() { return currentPage; }

    public void showNotice(@NonNull String message) {
        notice.setText(message); notice.setContentDescription(message); notice.setVisibility(VISIBLE);
        notice.removeCallbacks(hideNotice); notice.postDelayed(hideNotice, 3500);
    }

    // ---- Horizontal page swipe -------------------------------------------------------------
    // The wall's own sideways drag is arbitrated above this page, from the status bar. This
    // intercept claims a stream only when horizontal travel wins the slop race inside the grid,
    // so the grid's own pages move without stealing a vertical scroll or a widget tap.
    //
    // The same slop race decides the palette gesture (HomeSwipePolicy): a press on the free area
    // (the grid's empty cells and the air between widgets) that sets off mostly vertically is
    // claimed here, and its lift is reported up or down. A widget keeps its own touches, a Home
    // float its own, and the dock is never under this view's touch at all.

    @Override public boolean onInterceptTouchEvent(@NonNull MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                pagingDragging = false;
                seamDragging = false;
                verticalDragging = false;
                // A swipe on a Home float is the float's, except a sideways one on its terminal
                // when the terminal has no use for it: that one pages as it would beside it.
                boolean onFloat = pinnedLayerHolds(event);
                boolean free = !picker.isOpen() && !widgetEditActive()
                    && !insideNestedScrollingChild(this, event)
                    && (!onFloat || pinnedLayerYieldsSideways(event));
                // One page still tracks when the seam is open: its forward swipe is the wall's.
                pagingTracking = (pageCount > 1 || seamOpen()) && free;
                // A swipe on a widget is the widget's, whichever way it goes, and a vertical one
                // on a float is its terminal's scroll.
                verticalCandidate = free && !onFloat && !onWidget(event);
                freePress = verticalCandidate;
                twoFingerTracking = false;
                twoFingerDragging = false;
                secondTap = watchDoubleTap && freePress && HomeGestures.isSecondTap(lastTapUpMs,
                    lastTapX, lastTapY, event.getEventTime(), event.getX(), event.getY(),
                    ViewConfiguration.getDoubleTapTimeout(), doubleTapSlop);
                // Any press that is not a second tap ends whatever first tap came before it.
                if (!secondTap) lastTapUpMs = -1;
                pagingDownX = event.getX(); pagingDownY = event.getY();
                pagingDownRawX = event.getRawX();
                if (pagingTracking || verticalCandidate) {
                    if (pagingVelocity == null) pagingVelocity = VelocityTracker.obtain();
                    pagingVelocity.clear();
                    pagingVelocity.addMovement(event);
                }
                break;
            case MotionEvent.ACTION_MOVE:
                if (twoFingerTracking) {
                    float tdx = meanX(event) - twoFingerDownX;
                    float tdy = meanY(event) - twoFingerDownY;
                    if (Math.abs(tdy) > touchSlop && Math.abs(tdy) > Math.abs(tdx)) {
                        // Two fingers going up or down on the free area: the gesture's, and
                        // whatever was under the first finger is cancelled by the claim.
                        twoFingerTracking = false;
                        twoFingerDragging = true;
                        return true;
                    }
                    if (Math.abs(tdx) > touchSlop) twoFingerTracking = false;
                    break;
                }
                if (Math.abs(event.getX() - pagingDownX) > touchSlop
                    || Math.abs(event.getY() - pagingDownY) > touchSlop) {
                    // Travel is not a tap: neither the first nor the second of a double tap.
                    freePress = false;
                    secondTap = false;
                }
                if ((!pagingTracking && !verticalCandidate) || pagingDragging || verticalDragging)
                    break;
                if (widgetEditActive()) {
                    // A hold turned this press into a widget drag after the page had started
                    // watching it; the drag owns the rest of the gesture, sideways included.
                    pagingTracking = false;
                    verticalCandidate = false;
                    break;
                }
                if (pagingVelocity != null) pagingVelocity.addMovement(event);
                float dx = event.getX() - pagingDownX;
                float dy = event.getY() - pagingDownY;
                HomeSwipePolicy.Axis axis = HomeSwipePolicy.axis(dx, dy, touchSlop);
                if (axis == HomeSwipePolicy.Axis.VERTICAL) {
                    pagingTracking = false;
                    if (verticalCandidate) {
                        // The palette gesture: the grid's long press is cancelled by the claim.
                        verticalDragging = true;
                        return true;
                    }
                } else if (axis == HomeSwipePolicy.Axis.HORIZONTAL) {
                    verticalCandidate = false;
                    if (!pagingTracking) break;
                    if (com.termux.app.wall.WallRow.swipeLeavesHome(currentPage, pageCount,
                            dx < 0 ? 1 : -1, seamOpen())) {
                        // Forward off the last Home page: the swipe goes on into the row.
                        pagingDragging = true;
                        seamDragging = true;
                        beginSeamDrag(event);
                        return true;
                    }
                    if (pageCount <= 1) {
                        // Nothing to page and no seam this way: the swipe is not Home's.
                        pagingTracking = false;
                        break;
                    }
                    pagingDragging = true;
                    return true;
                }
                break;
            case MotionEvent.ACTION_POINTER_DOWN:
                pagingTracking = false;
                verticalCandidate = false;
                secondTap = false;
                // The second finger of a two-finger swipe, when one is set and the first finger
                // is still on the free area; a third finger is nobody's.
                twoFingerTracking = watchTwoFinger && freePress && event.getPointerCount() == 2
                    && !pagingDragging && !verticalDragging && !widgetEditActive();
                freePress = false;
                if (twoFingerTracking) {
                    twoFingerDownX = meanX(event);
                    twoFingerDownY = meanY(event);
                }
                break;
            case MotionEvent.ACTION_UP:
                noteTapLift(event);
                pagingTracking = false;
                verticalCandidate = false;
                twoFingerTracking = false;
                break;
            case MotionEvent.ACTION_CANCEL:
                pagingTracking = false;
                verticalCandidate = false;
                twoFingerTracking = false;
                freePress = false;
                secondTap = false;
                break;
            default:
                break;
        }
        return pagingDragging || verticalDragging || twoFingerDragging;
    }

    /**
     * A lift with no travel on the free area, short of a long press: the first tap of a double
     * tap, or its second, which fires the gesture.
     */
    private void noteTapLift(@NonNull MotionEvent event) {
        boolean tap = freePress && !pagingDragging && !verticalDragging
            && event.getEventTime() - event.getDownTime() < ViewConfiguration.getLongPressTimeout();
        boolean second = secondTap;
        freePress = false;
        secondTap = false;
        if (!watchDoubleTap || !tap) {
            lastTapUpMs = -1;
            return;
        }
        if (second) {
            lastTapUpMs = -1;
            if (listener != null) listener.onFreeAreaDoubleTap();
            return;
        }
        lastTapUpMs = event.getEventTime();
        lastTapX = event.getX();
        lastTapY = event.getY();
    }

    /** The two-finger swipe's stream after its claim: the lift of either finger decides. */
    private boolean onTwoFingerTouch(@NonNull MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_POINTER_UP:
            case MotionEvent.ACTION_UP: {
                if (!twoFingerDragging) return true;
                twoFingerDragging = false;
                float commit = com.termux.app.wall.BorderDrag.KEYBOARD_COMMIT_DP
                    * getResources().getDisplayMetrics().density;
                int way = HomeGestures.twoFingerDirection(meanX(event) - twoFingerDownX,
                    meanY(event) - twoFingerDownY, commit);
                if (way != 0 && listener != null) listener.onFreeAreaTwoFingerSwipe(way < 0);
                return true;
            }
            case MotionEvent.ACTION_CANCEL:
                twoFingerDragging = false;
                return true;
            default:
                return true;
        }
    }

    /** The mean of the first two fingers' positions; the one finger's own when only one is down. */
    private static float meanX(@NonNull MotionEvent event) {
        return event.getPointerCount() < 2 ? event.getX()
            : (event.getX(0) + event.getX(1)) / 2f;
    }

    private static float meanY(@NonNull MotionEvent event) {
        return event.getPointerCount() < 2 ? event.getY()
            : (event.getY(0) + event.getY(1)) / 2f;
    }

    @Override public boolean onTouchEvent(@NonNull MotionEvent event) {
        if (twoFingerDragging) return onTwoFingerTouch(event);
        if (verticalDragging) return onVerticalTouch(event);
        if (!pagingDragging) return false;
        if (seamDragging) return onSeamTouch(event);
        if (pagingVelocity != null) pagingVelocity.addMovement(event);
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_MOVE:
                setContentTranslationX(resistedDrag(event.getX() - pagingDownX));
                return true;
            case MotionEvent.ACTION_UP:
                finishPageDrag(event.getX() - pagingDownX);
                return true;
            case MotionEvent.ACTION_CANCEL:
                pagingDragging = false;
                pagingTracking = false;
                settleContentBack();
                return true;
            default:
                return true;
        }
    }

    /**
     * The palette gesture's stream after its claim: nothing moves under the finger, and the lift
     * asks up or down by the wall's keyboard swipe rule ({@link HomeSwipePolicy#release}).
     */
    private boolean onVerticalTouch(@NonNull MotionEvent event) {
        if (pagingVelocity != null) pagingVelocity.addMovement(event);
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_UP: {
                verticalDragging = false;
                verticalCandidate = false;
                float velocityY = 0f;
                if (pagingVelocity != null) {
                    pagingVelocity.computeCurrentVelocity(1000);
                    velocityY = pagingVelocity.getYVelocity();
                }
                float density = getResources().getDisplayMetrics().density;
                int way = HomeSwipePolicy.release(event.getY() - pagingDownY, velocityY,
                    com.termux.app.wall.BorderDrag.KEYBOARD_COMMIT_DP * density,
                    com.termux.app.wall.BorderDrag.KEYBOARD_FLING_DP_PER_SEC * density);
                if (way != 0 && listener != null) listener.onFreeAreaVerticalSwipe(way < 0);
                return true;
            }
            case MotionEvent.ACTION_CANCEL:
                verticalDragging = false;
                verticalCandidate = false;
                return true;
            default:
                return true;
        }
    }

    /** Whether a press lands on a widget on the page in view. */
    private boolean onWidget(@NonNull MotionEvent event) {
        if (grid.getVisibility() != VISIBLE) return false;
        for (int i = grid.getChildCount() - 1; i >= 0; i--) {
            View child = grid.getChildAt(i);
            if (child instanceof WidgetCellView && child.getVisibility() == VISIBLE
                && insideView(child, event)) return true;
        }
        return false;
    }

    private boolean seamOpen() {
        return seam != null && seam.canLeaveForward();
    }

    private void beginSeamDrag(@NonNull MotionEvent event) {
        if (seamVelocity == null) seamVelocity = VelocityTracker.obtain();
        seamVelocity.clear();
        addSeamMovement(event);
        if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
        if (seam != null) seam.begin();
    }

    /** The seam's drag, read on screen: this view moves with the wall the finger is driving. */
    private boolean onSeamTouch(@NonNull MotionEvent event) {
        addSeamMovement(event);
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_MOVE:
                if (seam != null) seam.drag(event.getRawX() - pagingDownRawX);
                return true;
            case MotionEvent.ACTION_UP: {
                float velocityX = 0f;
                if (seamVelocity != null) {
                    seamVelocity.computeCurrentVelocity(1000);
                    velocityX = seamVelocity.getXVelocity();
                }
                endSeamDrag();
                if (seam != null) seam.end(velocityX);
                return true;
            }
            case MotionEvent.ACTION_CANCEL:
                endSeamDrag();
                if (seam != null) seam.cancel();
                return true;
            default:
                return true;
        }
    }

    private void endSeamDrag() {
        pagingDragging = false;
        pagingTracking = false;
        seamDragging = false;
    }

    private void addSeamMovement(@NonNull MotionEvent event) {
        if (seamVelocity == null) return;
        MotionEvent screen = MotionEvent.obtain(event);
        screen.setLocation(event.getRawX(), event.getRawY());
        seamVelocity.addMovement(screen);
        screen.recycle();
    }

    private float resistedDrag(float dx) {
        boolean pastStart = dx > 0 && currentPage == 0;
        boolean pastEnd = dx < 0 && currentPage == pageCount - 1;
        return pastStart || pastEnd ? dx * PAGE_EDGE_RESISTANCE : dx;
    }

    private void finishPageDrag(float dx) {
        pagingDragging = false;
        pagingTracking = false;
        float velocityX = 0f;
        if (pagingVelocity != null) {
            pagingVelocity.computeCurrentVelocity(1000);
            velocityX = pagingVelocity.getXVelocity();
        }
        float commitVelocity = PAGE_COMMIT_VELOCITY_DP_PER_SEC
            * getResources().getDisplayMetrics().density;
        int width = Math.max(1, getWidth());
        int direction = 0;
        if (dx < 0 && (-dx > width * PAGE_COMMIT_FRACTION || velocityX < -commitVelocity)) {
            direction = 1;
        } else if (dx > 0 && (dx > width * PAGE_COMMIT_FRACTION || velocityX > commitVelocity)) {
            direction = -1;
        }
        int target = Math.max(0, Math.min(pageCount - 1, currentPage + direction));
        if (target != currentPage && listener != null) {
            pageCommitPending = true;
            pageCommitDirection = direction;
            pageCommitDragX = resistedDrag(dx);
            listener.onPageChangeRequested(target);
            if (pageCommitPending) { // the coordinator did not re-render; recover in place
                pageCommitPending = false;
                settleContentBack();
            }
        } else {
            settleContentBack();
        }
    }

    private void enterFromSide(int direction, float dragX) {
        if (instantMotion()) { setContentTranslationX(0f); return; }
        float start = direction > 0 ? getWidth() + dragX : dragX - getWidth();
        setContentTranslationX(start);
        animateContentTranslationX();
    }

    private void settleContentBack() {
        if (instantMotion() || contentShiftPx == 0f) { setContentTranslationX(0f); return; }
        animateContentTranslationX();
    }

    /** Reduce motion, or Animation speed at Off: the page lands at once. */
    private boolean instantMotion() {
        return reducedMotion || motionScale <= 0f;
    }

    private void setContentTranslationX(float value) {
        if (contentSettle != null) contentSettle.cancel();
        moveContent(value);
    }

    private void moveContent(float value) {
        contentShiftPx = value;
        applyContentShift();
        if (listener != null) listener.onContentShifted();
    }

    /** Draws {@link #contentShiftPx} through the chosen effect onto everything the page shows. */
    private void applyContentShift() {
        int width = getWidth();
        float position = width <= 0 ? 0f : contentShiftPx / width;
        // A turn is a page with another page that way; a pull against either end only slides.
        boolean turning = position < 0f ? currentPage < pageCount - 1
            : position > 0f && currentPage > 0;
        // A widget carried across pages in edit mode turns them with the plain slide: its drop
        // is aimed at the grid, which must not be scaled or turned under it.
        HomePageTransition effect = widgetEditActive() ? HomePageTransition.SLIDE : transition;
        effect.transform(position, width, turning, contentTransform);
        applyTransform(grid);
        applyTransform(empty);
        if (pinnedLayer != null) {
            pinnedLayer.animate().cancel();
            applyTransform(pinnedLayer);
        }
    }

    private void applyTransform(@NonNull View view) {
        HomePageTransition.Transform t = contentTransform;
        view.setTranslationX(t.translationX);
        view.setScaleX(t.scale);
        view.setScaleY(t.scale);
        view.setAlpha(t.alpha);
        view.setRotationY(t.rotationY);
        int width = view.getWidth() > 0 ? view.getWidth() : getWidth();
        view.setPivotX(t.pivotXFraction * width);
        view.setPivotY(view.getHeight() / 2f);
    }

    /**
     * How far the page's content stands off its rest, as a fraction of the pane's width,
     * positive to the right: mid-swipe, and while a page slides in or back. 0 at rest. The
     * wallpaper pans by this whatever effect draws the page.
     */
    public float contentShiftFraction() {
        int width = getWidth();
        return width <= 0 ? 0f : contentShiftPx / width;
    }

    private void animateContentTranslationX() {
        if (contentSettle != null) contentSettle.cancel();
        ValueAnimator settle = ValueAnimator.ofFloat(contentShiftPx, 0f);
        settle.setDuration(Math.round(PAGE_SETTLE_MS * (double) motionScale));
        // The page reports each frame, so the wallpaper pans on the page's own curve.
        settle.addUpdateListener(animation -> moveContent((float) animation.getAnimatedValue()));
        settle.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator animation) {
                if (contentSettle == animation) contentSettle = null;
            }
        });
        contentSettle = settle;
        settle.start();
    }

    private static boolean insideNestedScrollingChild(@NonNull ViewGroup parent,
                                                      @NonNull MotionEvent event) {
        for (int i = parent.getChildCount() - 1; i >= 0; i--) {
            View child = parent.getChildAt(i);
            if (child.getVisibility() != VISIBLE || !insideView(child, event)) continue;
            if (ViewCompat.isNestedScrollingEnabled(child)) return true;
            if (child instanceof ViewGroup
                && insideNestedScrollingChild((ViewGroup) child, event)) return true;
        }
        return false;
    }

    private static boolean insideView(@NonNull View view, @NonNull MotionEvent event) {
        if (view.getWidth() <= 0 || view.getHeight() <= 0) return false;
        int[] location = new int[2];
        view.getLocationOnScreen(location);
        return event.getRawX() >= location[0] && event.getRawX() < location[0] + view.getWidth()
            && event.getRawY() >= location[1] && event.getRawY() < location[1] + view.getHeight();
    }

    @Override protected void onDetachedFromWindow() {
        if (contentSettle != null) { contentSettle.end(); contentSettle = null; }
        if (pagingVelocity != null) { pagingVelocity.recycle(); pagingVelocity = null; }
        if (seamVelocity != null) { seamVelocity.recycle(); seamVelocity = null; }
        super.onDetachedFromWindow();
    }

    /** Low-emphasis page indicator: small centered dots, only meaningful past one page. */
    private final class PageDotsView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        PageDotsView(@NonNull Context context) { super(context); }

        @Override protected void onDraw(@NonNull Canvas canvas) {
            if (pageCount <= 1) return;
            float radius = dp(2.5f);
            float gap = dp(8);
            float step = radius * 2f + gap;
            float total = pageCount * radius * 2f + (pageCount - 1) * gap;
            float x = (getWidth() - total) / 2f + radius;
            float y = getHeight() / 2f;
            for (int page = 0; page < pageCount; page++) {
                // The dots are chrome over the wall, so they follow the chrome's polarity: white
                // light on the dark band, shadow on the light one, where white dots are no dots.
                paint.setColor(page == currentPage
                    ? com.termux.app.chrome.ChromeShade.structural(0xE6FFFFFF,
                        com.termux.app.chrome.ChromeShade.TARGET_RIM)
                    : com.termux.app.chrome.ChromeShade.fill(0x4DFFFFFF));
                canvas.drawCircle(x, y, radius, paint);
                x += step;
            }
        }

        private float dp(float value) {
            return value * getResources().getDisplayMetrics().density;
        }
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
