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
import android.view.ViewTreeObserver;
import android.util.AttributeSet;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;

import com.termux.R;
import com.termux.app.AnimationSpeed;
import com.termux.app.launcher.home.HomeGestures;
import com.termux.app.launcher.home.HomePageIndicator;
import com.termux.app.launcher.home.HomePageTransition;
import com.termux.app.wall.SettleSpring;

import java.util.List;

/** The widget grid's own body, a page of the pane wall; it has no authority over the wall or the terminal's geometry. */
public final class WidgetPaneView extends FrameLayout {
    /** The page dots' band under the grid: their height, their air to the pane's edge, and to the grid. */
    private static final int DOTS_HEIGHT_DP = 14;
    private static final int DOTS_BOTTOM_MARGIN_DP = 4;
    private static final int DOTS_GAP_DP = 4;
    /** How far two fingers must close in before a pinch opens the page overview. */
    private static final float PINCH_COMMIT_DP = 48f;

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
         * Two fingers that set off on the free area pinched in: the page overview. Always
         * watched, whatever the gesture settings say.
         */
        default void onFreeAreaPinchIn() { }
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
        /** Whether anything of the page in view is drawn on the layer now (a float that is up). */
        default boolean showsAnything() { return false; }
    }

    /**
     * What else may lie over Home, read each frame rather than pushed: the drawer and the palette
     * open and close along many paths, and a hint still drawn under one is what a missed one
     * looks like ({@link WidgetEmptyHintPolicy}).
     */
    public interface HintCover {
        /** The app drawer's plane is engaged: open, or on its way in or out. */
        boolean drawerUp();
        /** The command palette is open. */
        boolean paletteUp();
    }

    private static final float PAGE_EDGE_RESISTANCE = 0.35f;
    /** The camera's distance for Carousel's turn, in dp: the one every turned page shares. */
    private static final float CAMERA_DISTANCE_DP = HomePageTransition.CAMERA_DISTANCE_DP;

    private final WidgetGridView grid;
    private final LinearLayout empty;
    private final TextView emptyMessage;
    private final TextView notice;
    private final WidgetPickerSheetView picker;
    private final PageDotsView dots;
    /** The empty page's hint and the page dots stand on the wallpaper: their ink follows it. */
    private final HomeLabelInk hintInk = new HomeLabelInk();
    private final HomeLabelInk dotsInk = new HomeLabelInk();
    private final Runnable hideNotice;
    private final int touchSlop;
    private final int doubleTapSlop;
    private Listener listener;
    private boolean reducedMotion;
    @NonNull private HomePageIndicator indicator = HomePageIndicator.DEFAULT;
    /** How the page moves while a swipe turns it (Settings › Home › Page transition). */
    @NonNull private HomePageTransition transition = HomePageTransition.DEFAULT;
    /**
     * General › Animation speed's multiplier; 0 is Off, the instant cut. Any other speed stretches
     * the settle's spring as it does the wall's ({@code AnimationSpeed.springTime}).
     */
    private float motionScale = 1f;
    /**
     * Where the page's content stands off its rest, in px, positive to the right: the finger's
     * drag and the settle move this, and the effect draws it ({@link #applyContentShift}). Kept
     * apart from the views' own translation, which an effect may not move at all.
     */
    private float contentShiftPx;
    @Nullable private ValueAnimator contentSettle;
    /** The content is drawn through a hardware layer for an effect's motion; see {@link #setContentLayered}. */
    private boolean contentLayered;
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
    /** The released finger's speed, px per second, which the committed page's settle carries on. */
    private float pageCommitVelocity;
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
    /** Two fingers set off on the free area, watched for a pinch in; then claimed. */
    private boolean pinchTracking;
    /** A pinch fired: the rest of the gesture is this view's, and does nothing more. */
    private boolean pinchClaimed;
    private float pinchStartSpan;

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
        gridParams.bottomMargin = gridBottomInsetPx(0, getResources().getDisplayMetrics().density);
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
        notice.setMinHeight(dp(48)); notice.setMaxLines(2); notice.setEllipsize(android.text.TextUtils.TruncateAt.END);
        androidx.core.widget.TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(notice, 9, 15, 1,
            android.util.TypedValue.COMPLEX_UNIT_SP);
        LayoutParams noticeParams = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT,
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
    @Nullable private HintCover hintCover;
    /** A few boolean reads a frame; only a change touches the hint. */
    private final ViewTreeObserver.OnPreDrawListener hintCoverCheck = () -> {
        syncEmptyHint();
        return true;
    };

    /** What else may cover Home, so the empty page's hint steps out from under it. */
    public void setEmptyHintCover(@Nullable HintCover cover) {
        hintCover = cover;
        syncEmptyHint();
    }

    /**
     * The empty page's hint, hidden (not gone: the page's layout stays) while a float, the drawer
     * or the palette is over it. Each of those is see-through, so the hint would read through it.
     */
    private void syncEmptyHint() {
        boolean floatOnPage = pinnedLayer instanceof PagePinnedLayer
            && pinnedLayer.getVisibility() == VISIBLE && ((PagePinnedLayer) pinnedLayer).showsAnything();
        HintCover cover = hintCover;
        boolean shown = WidgetEmptyHintPolicy.shown(floatOnPage,
            cover != null && cover.drawerUp(), cover != null && cover.paletteUp());
        int visibility = shown ? VISIBLE : INVISIBLE;
        if (emptyMessage.getVisibility() != visibility) emptyMessage.setVisibility(visibility);
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        getViewTreeObserver().addOnPreDrawListener(hintCoverCheck);
    }

    /**
     * Pins {@code layer} to the Home pages: drawn over the grid and under the page dots, the picker
     * and the edit chrome, told which page is on screen and carried with the page's content.
     */
    public <T extends View & PagePinnedLayer> void setPagePinnedLayer(@Nullable T layer) {
        if (pinnedLayer == layer) return;
        if (pinnedLayer != null) {
            if (contentLayered) pinnedLayer.setLayerType(LAYER_TYPE_NONE, null);
            removeView(pinnedLayer);
        }
        pinnedLayer = layer;
        if (layer == null) return;
        if (layer.getParent() instanceof ViewGroup) ((ViewGroup) layer.getParent()).removeView(layer);
        addView(layer, indexOfChild(empty) + 1,
            new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        layer.setCameraDistance(CAMERA_DISTANCE_DP * getResources().getDisplayMetrics().density);
        if (contentLayered) layer.setLayerType(LAYER_TYPE_HARDWARE, null);
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
     * What the page writes straight on the wallpaper takes light or dark ink from the wallpaper
     * under it, not from the theme (editor-redesign §8): every icon's and folder's label, the
     * empty page's hint and the page dots. Called on the page's dress pass (a new Look, frame or
     * wallpaper) and after a layout; cheap when nothing moved, each piece keeping its own sample.
     */
    public void refreshWallpaperInk() {
        com.termux.app.chrome.WidgetInkSource source = HomeLabelInk.sourceFor(this);
        if (source == null) return;
        refreshLabelInks(grid);
        refreshOwnInks(source);
    }

    /** The hint and the dots, on their own sample each. */
    private void refreshOwnInks(@NonNull com.termux.app.chrome.WidgetInkSource source) {
        if (empty.getVisibility() == VISIBLE && emptyMessage.getVisibility() == VISIBLE) {
            hintInk.wallpaperUnder(emptyMessage, source);
            hintInk.apply(emptyMessage, false, getResources().getDisplayMetrics().density);
        }
        if (dots.getVisibility() == VISIBLE) {
            int before = dotsInk.wallpaper();
            if (dotsInk.wallpaperUnder(dots, source) != before) dots.invalidate();
        }
    }

    /** Every Home icon and folder under {@code parent}; widgets' own trees are not walked. */
    private static void refreshLabelInks(@NonNull ViewGroup parent) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            if (child instanceof HomeAppIconView) {
                ((HomeAppIconView) child).refreshLabelInk();
            } else if (child instanceof HomeFolderView) {
                ((HomeFolderView) child).refreshLabelInk();
            } else if (child instanceof ViewGroup
                    && !(child instanceof android.appwidget.AppWidgetHostView)
                    && !(child instanceof com.termux.app.launcher.widget.builtin.BuiltinWidgetView)) {
                refreshLabelInks((ViewGroup) child);
            }
        }
    }

    @Override protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        com.termux.app.chrome.WidgetInkSource source = HomeLabelInk.sourceFor(this);
        if (source != null) refreshOwnInks(source);
    }

    /** The band at the pane's bottom the dock takes at rest; see {@link #setDockReservePx}. */
    private int dockReservePx;

    /**
     * The band at the pane's bottom the dock stands over at rest
     * ({@code KeyboardOverlayPolicy#homeGridDockReservePx}, #104): the page dots and the grid stand
     * above it, so the grid's last row is never behind the dock. The grid's rows are cut from what
     * is left.
     */
    public void setDockReservePx(int px) {
        int wanted = Math.max(0, px);
        if (dockReservePx == wanted) return;
        dockReservePx = wanted;
        float density = getResources().getDisplayMetrics().density;
        LayoutParams gridParams = (LayoutParams) grid.getLayoutParams();
        gridParams.bottomMargin = gridBottomInsetPx(wanted, density);
        grid.setLayoutParams(gridParams);
        LayoutParams dotsParams = (LayoutParams) dots.getLayoutParams();
        dotsParams.bottomMargin = wanted + Math.round(DOTS_BOTTOM_MARGIN_DP * density);
        dots.setLayoutParams(dotsParams);
        picker.setDockReservePx(wanted);
    }

    public int dockReservePx() {
        return dockReservePx;
    }

    /**
     * How far above the pane's bottom the grid ends: the dock's band, then the page dots' band
     * (kept whether or not the dots show, so adding a page does not shrink every cell).
     */
    static int gridBottomInsetPx(int dockReservePx, float density) {
        return Math.max(0, dockReservePx)
            + Math.round((DOTS_HEIGHT_DP + DOTS_BOTTOM_MARGIN_DP + DOTS_GAP_DP) * density);
    }

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
            editOverlay.setReservedArea(overlayReservedArea());
            addView(editOverlay, new LayoutParams(LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT));
        } else if (indexOfChild(editOverlay) != getChildCount() - 1) {
            editOverlay.bringToFront();
        }
        return editOverlay;
    }

    /**
     * What the page's frame draws over the edit chrome for the whole session — its editing tab —
     * in this view's coordinates. The edit chips keep clear of it.
     */
    public void setWidgetEditReservedArea(@Nullable WidgetEditOverlayView.ReservedArea area) {
        editReservedArea = area;
        if (editOverlay != null) editOverlay.setReservedArea(overlayReservedArea());
    }

    /** The reserved area moved into the overlay's own coordinates, read fresh on every ask. */
    @Nullable private WidgetEditOverlayView.ReservedArea overlayReservedArea() {
        WidgetEditOverlayView.ReservedArea area = editReservedArea;
        if (area == null) return null;
        return out -> {
            if (!area.fill(out)) return false;
            if (editOverlay != null) out.offset(-editOverlay.getLeft(), -editOverlay.getTop());
            return true;
        };
    }

    /** Redraws the edit chrome, for a frame whose tab changed size or place under it. */
    public void invalidateWidgetEditChrome() {
        if (editOverlay != null) editOverlay.invalidate();
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
        enterFromSide(direction, 0f, 0f);
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
    @Nullable private WidgetEditOverlayView.ReservedArea editReservedArea;

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
            enterFromSide(pageCommitDirection, pageCommitDragX, pageCommitVelocity);
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
                pinchTracking = false;
                pinchClaimed = false;
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
                if (pinchClaimed) return true;
                if (pinchTracking) {
                    float span = pinchSpan(event);
                    float commit = PINCH_COMMIT_DP * getResources().getDisplayMetrics().density;
                    if (com.termux.app.wall.PageOverviewPolicy.isPinchIn(pinchStartSpan, span,
                            commit)) {
                        // The page overview: claimed, so whatever was under the fingers is
                        // cancelled, and the rest of the gesture does nothing more.
                        pinchTracking = false;
                        twoFingerTracking = false;
                        pinchClaimed = true;
                        if (listener != null) listener.onFreeAreaPinchIn();
                        return true;
                    }
                    // Spreading is not a pinch in.
                    if (span - pinchStartSpan > touchSlop) pinchTracking = false;
                }
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
                boolean twoOnFree = freePress && event.getPointerCount() == 2
                    && !pagingDragging && !verticalDragging && !widgetEditActive();
                twoFingerTracking = watchTwoFinger && twoOnFree;
                pinchTracking = twoOnFree;
                if (pinchTracking) pinchStartSpan = pinchSpan(event);
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
                pinchTracking = false;
                break;
            case MotionEvent.ACTION_CANCEL:
                pagingTracking = false;
                verticalCandidate = false;
                twoFingerTracking = false;
                pinchTracking = false;
                freePress = false;
                secondTap = false;
                break;
            default:
                break;
        }
        return pagingDragging || verticalDragging || twoFingerDragging || pinchClaimed;
    }

    /** How far apart the first two fingers are; 0 with fewer. */
    private static float pinchSpan(@NonNull MotionEvent event) {
        if (event.getPointerCount() < 2) return 0f;
        return (float) Math.hypot(event.getX(0) - event.getX(1), event.getY(0) - event.getY(1));
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
        if (pinchClaimed) {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                pinchClaimed = false;
            }
            return true;
        }
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
        // The one page rule; a drag to the left (negative) brings the next page in.
        int direction = -com.termux.app.wall.PagePolicy.turn(dx, velocityX,
            Math.max(1, getWidth()), getResources().getDisplayMetrics().density);
        int target = Math.max(0, Math.min(pageCount - 1, currentPage + direction));
        // The content moved with the finger, at its speed, except into an end, where it only
        // ever took the resistance's share of the travel and so of the speed too.
        float contentVelocity = resistedDrag(dx) != dx ? velocityX * PAGE_EDGE_RESISTANCE
            : velocityX;
        if (target != currentPage && listener != null) {
            pageCommitPending = true;
            pageCommitDirection = direction;
            pageCommitDragX = resistedDrag(dx);
            pageCommitVelocity = velocityX;
            listener.onPageChangeRequested(target);
            if (pageCommitPending) { // the coordinator did not re-render; recover in place
                pageCommitPending = false;
                settleContentBack(contentVelocity);
            }
        } else {
            settleContentBack(contentVelocity);
        }
    }

    private void enterFromSide(int direction, float dragX, float velocityPxPerSec) {
        if (instantMotion()) { setContentTranslationX(0f); return; }
        float start = direction > 0 ? getWidth() + dragX : dragX - getWidth();
        setContentTranslationX(start);
        animateContentTranslationX(velocityPxPerSec);
    }

    private void settleContentBack() {
        settleContentBack(0f);
    }

    private void settleContentBack(float velocityPxPerSec) {
        if (instantMotion() || contentShiftPx == 0f) { setContentTranslationX(0f); return; }
        animateContentTranslationX(velocityPxPerSec);
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
        // An effect that scales, fades or turns the page draws it from one layer for the frames
        // of the motion, so its widgets and floats are not drawn again under each new transform.
        setContentLayered(effect != HomePageTransition.SLIDE && contentShiftPx != 0f);
        applyTransform(grid);
        applyTransform(empty);
        if (pinnedLayer != null) {
            pinnedLayer.animate().cancel();
            applyTransform(pinnedLayer);
        }
    }

    /** Whether the page's content draws through a hardware layer: only while an effect moves it. */
    private void setContentLayered(boolean layered) {
        if (contentLayered == layered) return;
        contentLayered = layered;
        int type = layered ? LAYER_TYPE_HARDWARE : LAYER_TYPE_NONE;
        grid.setLayerType(type, null);
        empty.setLayerType(type, null);
        if (pinnedLayer != null) pinnedLayer.setLayerType(type, null);
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

    /**
     * The settle a page rides once the finger lets go: the wall's own spring, so a flick settles
     * fast and a slow drag slow on Home as on the wall (#153).
     */
    @NonNull
    static SettleSpring settleSpring(float shiftPx, float velocityPxPerSec, float springTime) {
        return SettleSpring.page(shiftPx, velocityPxPerSec, springTime);
    }

    /** Settles the content from where it stands to rest, starting at {@code velocityPxPerSec}. */
    private void animateContentTranslationX(float velocityPxPerSec) {
        if (contentSettle != null) contentSettle.cancel();
        final SettleSpring spring = settleSpring(contentShiftPx, velocityPxPerSec,
            AnimationSpeed.springTime(getContext()));
        final long duration = spring.durationMs(SettleSpring.PAGE_REST_PX);
        // The animator is the clock only; the spring says where the page stands.
        ValueAnimator settle = ValueAnimator.ofFloat(0f, 1f);
        settle.setDuration(duration);
        settle.setInterpolator(null);
        // The page reports each frame, so the wallpaper pans on the page's own curve.
        settle.addUpdateListener(animation -> {
            float fraction = animation.getAnimatedFraction();
            moveContent(fraction >= 1f ? 0f : spring.displacementAt(fraction * duration / 1000f));
        });
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
        getViewTreeObserver().removeOnPreDrawListener(hintCoverCheck);
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
                // On a measured wallpaper they follow it instead (editor-redesign §8): the ink
                // that reads on the wallpaper behind them, at the same two strengths.
                int wallpaper = dotsInk.wallpaper();
                if (android.graphics.Color.alpha(wallpaper) != 0) {
                    int ink = com.termux.app.chrome.WallpaperInk.bareInk(wallpaper,
                        HomeLabelInk.DARK_INK, HomeLabelInk.PALE_INK);
                    paint.setColor((ink & 0x00FFFFFF) | (page == currentPage ? 0xE6000000 : 0x4D000000));
                } else {
                    paint.setColor(page == currentPage
                        ? com.termux.app.chrome.ChromeShade.structural(0xE6FFFFFF,
                            com.termux.app.chrome.ChromeShade.TARGET_RIM)
                        : com.termux.app.chrome.ChromeShade.fill(0x4DFFFFFF));
                }
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
