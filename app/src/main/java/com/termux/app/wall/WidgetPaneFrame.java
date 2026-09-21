package com.termux.app.wall;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;
import com.termux.app.chrome.CornerHold;
import com.termux.app.chrome.CornerHoldArbiter;
import com.termux.app.chrome.CornerTabGlyphs;
import com.termux.app.chrome.CornerZones;
import com.termux.app.terminal.PaneContentFrame;
import com.termux.app.terminal.PaneGlass;
import com.termux.app.terminal.PaneGlassBackdropView;
import com.termux.app.terminal.PaneRim;
import com.termux.app.terminal.PaneSurfaceStyle;
import com.termux.view.HoldTiming;

/**
 * The wall's Widgets page: the app-widget grid wearing a terminal pane's dress. It is a
 * {@link PaneContentFrame} like a terminal pane, dressed by {@link PaneGlass} and
 * {@link PaneRim} from the same {@link PaneSurfaceStyle} the panes read, so nothing here can
 * drift from the surface the user tuned.
 *
 * <p>The grid view is moved in rather than inflated: there is one widget grid in the app, and it
 * keeps its app-widget host views across the move.
 *
 * <p>A <em>hold</em> on one of the page's four corners drops the same tab the Display page's
 * corners drop, with the page's own buttons: the pencil that starts editing the widgets, and the
 * two doors every place carries — Appearance and Layout.
 * It comes out of the corner that was touched, so the tab lands under the thumb that asked for it.
 * While a widget is being edited those buttons are replaced by the grid's size, which opens the
 * wheels that change it. Everything between the corners is the widgets': a grid that reaches the
 * rim is touchable to its last pixel.
 */
public final class WidgetPaneFrame extends PaneContentFrame {

    /** What the page needs from the launcher. */
    public interface Host {
        /** The pencil: start editing the widgets, exactly as the long-press menu does. */
        void editWidgets();
        default void showHelpOverlay() {}
        /** The sliders: open the Appearance editor on this place, as every corner tab does. */
        default void openSurfaceEditor() {}
        /** The grid: open the Layout editor on this place, as every corner tab does. */
        default void openLayoutEditor() {}
        /** The columns the grid is showing now. */
        int widgetGridColumns();
        /** The rows the grid is showing now. */
        int widgetGridRows();
        /** A wheel moved: keep the grid this size, and reflow the page onto it. */
        void setWidgetGrid(int columns, int rows);
    }

    /** The buttons the page's border tab carries. */
    private static final int ACTION_EDIT = 1;
    private static final int ACTION_GRID_SIZE = 2;
    private static final int ACTION_HELP = 3;
    private static final int ACTION_EDITOR = 4;
    private static final int ACTION_LAYOUT = 5;
    /** The tick: keep what editing did. */
    private static final int ACTION_COMMIT = 6;
    /** The cross: put it back the way it was. */
    private static final int ACTION_DISCARD = 7;

    private final PaneRim mRim = new PaneRim();
    @Nullable private PaneGlassBackdropView mGlass;
    @Nullable private View mGrid;
    @Nullable private PaneSurfaceStyle mStyle;
    @Nullable private PaneControlsView mControls;
    @Nullable private Host mHost;
    @Nullable private com.termux.app.launcher.widget.WidgetGridSizePopup mGridSizePopup;
    private boolean mEditing;
    private int mPressedAction = PaneControlsView.ACTION_NONE;
    /** The corner the finger landed in, from its landing to its lift, held or not. */
    private int mPressedCorner = CornerZones.NONE;
    /** Who owns a finger down in a corner square: the widgets under it, or this corner. */
    private final CornerHoldArbiter mHold = new CornerHoldArbiter();
    /**
     * Its own handler rather than {@link View#postDelayed}: a detached view queues those until it
     * is attached, and the hold has to fire whether or not this page is on screen yet.
     */
    private final Handler mHoldHandler = new Handler(Looper.getMainLooper());
    private final Runnable mHoldElapsed = this::onHoldElapsed;
    /** Whether the tab was out when the finger landed: a corner hold puts it away, or moves it. */
    private boolean mShownAtDown;
    /** And which corner it was out of, so a hold on another corner moves it rather than closing it. */
    private int mShownCornerAtDown = CornerZones.NONE;
    private boolean mTouchMoved;
    private float mDownX, mDownY;
    /** True only inside {@link #cancelGridGesture()}: that cancel is the grid's, not the page's. */
    private boolean mCancellingGrid;

    public WidgetPaneFrame(Context context) {
        super(context);
    }

    public WidgetPaneFrame(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mGlass = findViewById(R.id.widget_pane_glass);
        PaneGlass.followLayout(mGlass);
        mGrid = findViewById(R.id.widget_pane);
        setPaneContent(mGrid);
        // The tab sits above everything, drawn only while shown; the frame itself answers the
        // taps, so the view never stands between a finger and a widget.
        mControls = new PaneControlsView(getContext());
        mControls.setListener(this::runControl);
        addView(mControls, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        applyRestingActions();
    }

    public void setHost(@Nullable Host host) {
        mHost = host;
        refreshGridSizeAction();
    }

    @Nullable
    public View grid() {
        return mGrid;
    }

    /**
     * The widget edit session opened or closed. Editing, the corner reads out the grid's size
     * instead of offering the settings and the pencil, and the tab comes out on its own — the
     * user is already in the mode it belongs to. Leaving editing puts it away.
     */
    public void applyWidgetEditing(boolean editing) {
        if (mEditing == editing) return;
        mEditing = editing;
        dismissGridSizePopup();
        if (mControls == null) return;
        if (editing) {
            refreshGridSizeAction();
            mControls.show();
        } else {
            applyRestingActions();
            mControls.dismiss();
        }
    }

    /** Put the controls away, for a host that moved the wall on. */
    /** Whether the border tab is out (or coming out). */
    @androidx.annotation.VisibleForTesting
    boolean isControlsTabShown() {
        return mControls != null && mControls.isControlsShown();
    }

    /** The tab, for a test that has to find where it came out. */
    @androidx.annotation.VisibleForTesting
    @Nullable
    PaneControlsView controlsTab() {
        return mControls;
    }

    /**
     * The ? of the tab that is up, in screen coordinates; false when no tab is showing. The tab
     * draws its buttons rather than laying them out as views, so nothing outside can find that one.
     */
    public boolean helpButtonRectOnScreen(@androidx.annotation.NonNull android.graphics.Rect out) {
        if (mControls == null || !mControls.actionBounds(ACTION_HELP, mHelpButtonBounds)) return false;
        int[] origin = new int[2];
        mControls.getLocationOnScreen(origin);
        out.set(Math.round(mHelpButtonBounds.left) + origin[0],
            Math.round(mHelpButtonBounds.top) + origin[1],
            Math.round(mHelpButtonBounds.right) + origin[0],
            Math.round(mHelpButtonBounds.bottom) + origin[1]);
        return !out.isEmpty();
    }
    private final android.graphics.RectF mHelpButtonBounds = new android.graphics.RectF();

    /** Where the tick or the cross sits on the editing tab; false when neither is out. */
    @androidx.annotation.VisibleForTesting
    boolean editExitButtonBounds(boolean keep, @NonNull RectF out) {
        return mControls != null && mControls.actionBounds(keep ? ACTION_COMMIT : ACTION_DISCARD,
            out);
    }

    public void dismissControls() {
        dismissGridSizePopup();
        if (mControls != null) mControls.dismiss();
    }

    private void applyRestingActions() {
        if (mControls == null) return;
        mControls.setActions(PaneControlsView.Action.glyph(ACTION_EDIT, CornerTabGlyphs.EDIT),
            PaneControlsView.Action.glyph(ACTION_EDITOR, CornerTabGlyphs.APPEARANCE),
            PaneControlsView.Action.glyph(ACTION_LAYOUT, CornerTabGlyphs.LAYOUT),
            PaneControlsView.Action.label(ACTION_HELP, CornerTabGlyphs.help(getContext())));
    }

    /**
     * The editing tab: the grid's size, then the two ways out of the mode — the tick that keeps
     * everything and the cross that puts the widgets back where the session found them.
     */
    private void refreshGridSizeAction() {
        if (mControls == null || !mEditing || mHost == null) return;
        mControls.setActions(PaneControlsView.Action.label(ACTION_GRID_SIZE,
            getContext().getString(R.string.widget_grid_size_tab,
                mHost.widgetGridColumns(), mHost.widgetGridRows())),
            PaneControlsView.Action.drawn(ACTION_COMMIT, WidgetPaneFrame::drawTickMark),
            PaneControlsView.Action.drawn(ACTION_DISCARD, WidgetPaneFrame::drawCrossMark,
                PaneControlsView.TINT_ERROR),
            PaneControlsView.Action.label(ACTION_HELP, CornerTabGlyphs.help(getContext())));
    }

    /** The tick, in the same hand-drawn family the panes' own close and maximise marks use. */
    private static void drawTickMark(@NonNull Canvas canvas, @NonNull RectF button,
                                     @NonNull Paint paint, float density) {
        float cx = button.centerX();
        float cy = button.centerY();
        canvas.drawLine(cx - 5f * density, cy - 0.5f * density,
            cx - 1.5f * density, cy + 3.5f * density, paint);
        canvas.drawLine(cx - 1.5f * density, cy + 3.5f * density,
            cx + 5f * density, cy - 4f * density, paint);
    }

    private static void drawCrossMark(@NonNull Canvas canvas, @NonNull RectF button,
                                      @NonNull Paint paint, float density) {
        float cx = button.centerX();
        float cy = button.centerY();
        canvas.drawLine(cx - 4f * density, cy - 4f * density,
            cx + 4f * density, cy + 4f * density, paint);
        canvas.drawLine(cx + 4f * density, cy - 4f * density,
            cx - 4f * density, cy + 4f * density, paint);
    }

    /** The two ways out of editing, which the grid's own coordinator answers. */
    private void runWidgetEditExit(boolean keep) {
        if (!(mGrid instanceof com.termux.app.launcher.widget.WidgetPaneView)) return;
        com.termux.app.launcher.widget.WidgetPaneView pane =
            (com.termux.app.launcher.widget.WidgetPaneView) mGrid;
        if (keep) pane.commitWidgetEdit();
        else pane.discardWidgetEdit();
    }

    private void runControl(int id) {
        if (id == ACTION_GRID_SIZE) {
            openGridSizePopup();
            return;
        }
        if (id == ACTION_COMMIT || id == ACTION_DISCARD) {
            runWidgetEditExit(id == ACTION_COMMIT);
            return;
        }
        if (mHost == null) return;
        if (id == ACTION_HELP) { mHost.showHelpOverlay(); dismissControls(); }
        else if (id == ACTION_EDITOR) { dismissControls(); mHost.openSurfaceEditor(); }
        else if (id == ACTION_LAYOUT) { dismissControls(); mHost.openLayoutEditor(); }
        else if (id == ACTION_EDIT) mHost.editWidgets();
    }

    /** The wheels, hanging off the tab that opened them. */
    private void openGridSizePopup() {
        if (mHost == null || mControls == null) return;
        dismissGridSizePopup();
        android.graphics.RectF tab = new android.graphics.RectF();
        mControls.tabBounds(tab);
        mGridSizePopup = com.termux.app.launcher.widget.WidgetGridSizePopup.show(this, tab,
            mHost.widgetGridColumns(), mHost.widgetGridRows(), (columns, rows) -> {
                if (mHost != null) mHost.setWidgetGrid(columns, rows);
                refreshGridSizeAction();
            });
    }

    private void dismissGridSizePopup() {
        if (mGridSizePopup != null) {
            mGridSizePopup.dismiss();
            mGridSizePopup = null;
        }
    }

    /**
     * A hold on one of the page's corners drops the controls out of it, as a hold on a pane's
     * corner does; a tap on one of them runs it, and a touch anywhere else puts them away and goes
     * on to the grid.
     *
     * <p>A corner square is not a button. The finger that lands in one is the widgets' until it
     * has rested for {@link HoldTiming#holdTimeoutMs()}, so a tap, a drag and a widget's own
     * controls all reach the grid as if the square were not there; only when the hold fires does
     * this frame take the gesture, which is where the child is told to forget it.
     */
    @Override
    public boolean onInterceptTouchEvent(@NonNull MotionEvent event) {
        // The cancel the corner sends the grid when it claims the hold passes back through here:
        // it is for the child, and reading it as the end of the gesture would undo the claim.
        if (mCancellingGrid) return false;
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) return onFrameDown(event);
        // Read before the event is played: the hold is claimed by its timer rather than by a
        // touch, so an event arriving after it is the first the grid must not see.
        boolean claimed = mHold.isClaimed();
        onHoldEvent(event);
        return mPressedAction != PaneControlsView.ACTION_NONE || claimed;
    }

    /**
     * The finger landed. The tab's own buttons are taken at once — the tab is a control, and it is
     * tapped — and a corner only starts its timer, leaving the gesture with the grid.
     *
     * @return whether this frame takes the touch from the widgets.
     */
    private boolean onFrameDown(@NonNull MotionEvent event) {
        cancelHold();
        mPressedAction = PaneControlsView.ACTION_NONE;
        mPressedCorner = CornerZones.NONE;
        mTouchMoved = false;
        mDownX = event.getX();
        mDownY = event.getY();
        mShownAtDown = mControls != null && mControls.isControlsShown();
        mShownCornerAtDown = mShownAtDown ? mControls.corner() : CornerZones.NONE;
        if (mShownAtDown) {
            int action = mControls.actionAt(mDownX, mDownY);
            if (action != PaneControlsView.ACTION_NONE) {
                mPressedAction = action;
                return true;
            }
            // The editing tab is the mode's own chrome, so only leaving the mode puts it away.
            if (!mEditing) mControls.dismiss();
        }
        float slop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
        int corner = mHold.down(mDownX, mDownY, getWidth(), getHeight(),
            getResources().getDisplayMetrics().density, editChromeWantsPoint(mDownX, mDownY),
            slop);
        // Whether the grid may run a long press of its own is settled here, on the landing point,
        // rather than by whichever timer fires first: a press in a square is the corner's.
        applyGridHoldExemption();
        if (corner == CornerZones.NONE) return false;
        mPressedCorner = corner;
        mHoldHandler.postDelayed(mHoldElapsed, HoldTiming.holdTimeoutMs());
        return false;
    }

    /**
     * One event of a gesture that started in a corner square. It arrives here from
     * {@link #onInterceptTouchEvent} while the grid is holding the gesture and from
     * {@link #onTouchEvent} once nothing else wants it — never both, since a frame that is
     * intercepting has no child left to dispatch to.
     */
    private void onHoldEvent(@NonNull MotionEvent event) {
        if (!mHold.isTracking()) return;
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_MOVE:
                if (mHold.move(event.getX(), event.getY()) == CornerHold.Move.ABANDONED)
                    mHoldHandler.removeCallbacks(mHoldElapsed);
                break;
            case MotionEvent.ACTION_POINTER_DOWN:
                // Two fingers on a page are a scroll or a pinch, never a hold.
                if (mHold.secondFinger()) mHoldHandler.removeCallbacks(mHoldElapsed);
                break;
            case MotionEvent.ACTION_UP:
                mHoldHandler.removeCallbacks(mHoldElapsed);
                if (mHold.lift() == CornerHold.Lift.OPEN_TAB) openHeldCornerTab();
                mPressedCorner = CornerZones.NONE;
                break;
            case MotionEvent.ACTION_CANCEL:
                cancelHold();
                break;
            default:
                break;
        }
        // A corner that gave the gesture up hands the grid its long press back in the same breath.
        applyGridHoldExemption();
    }

    /**
     * The hold time passed with the finger still in the square. The corner takes the gesture from
     * here: the grid is told its touch is over, the hand is told the hold was heard, and every
     * event from now on is this frame's.
     */
    private void onHoldElapsed() {
        if (!mHold.holdElapsed()) return;
        cancelGridGesture();
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
    }

    /**
     * Tell the grid the touch it has been tracking is over, the moment the corner claims it. A
     * cancel is the one ending that leaves nothing behind — no tap, no menu, no half-started drag
     * — which is what a finger that turned out to be a corner hold owes the widgets. Waiting for
     * the next event to intercept is not the same thing: a finger that is holding still sends no
     * events, and the grid's own long press fired into the gap.
     */
    private void cancelGridGesture() {
        long now = SystemClock.uptimeMillis();
        MotionEvent cancel =
            MotionEvent.obtain(now, now, MotionEvent.ACTION_CANCEL, mDownX, mDownY, 0);
        mCancellingGrid = true;
        try {
            // Down the frame's own dispatch, so whichever child is holding the stream is the one
            // told to forget it — and the frame stops being a touch target for it in the bargain.
            dispatchTouchEvent(cancel);
        } finally {
            mCancellingGrid = false;
            cancel.recycle();
        }
    }

    /**
     * Hold the grid's own long press back for as long as a corner may still claim the finger, and
     * give it back the moment one cannot. One question, asked of {@link CornerHoldArbiter} after
     * every event, so the two are never both armed on the same press.
     */
    private void applyGridHoldExemption() {
        if (mGrid instanceof com.termux.app.launcher.widget.WidgetPaneView) {
            ((com.termux.app.launcher.widget.WidgetPaneView) mGrid)
                .setHoldExempt(!mHold.contentMayLongPress());
        }
    }

    /** Out of the corner that was held; the corner it is already out of puts it away again. */
    private void openHeldCornerTab() {
        if (mControls == null || mPressedCorner == CornerZones.NONE) return;
        if (!mShownAtDown || mShownCornerAtDown != mPressedCorner) mControls.show(mPressedCorner);
        else dismissControls();
    }

    private void cancelHold() {
        mHoldHandler.removeCallbacks(mHoldElapsed);
        mHold.reset();
        mPressedCorner = CornerZones.NONE;
        applyGridHoldExemption();
    }

    /**
     * Which corner a touch down may be held at for the page, or {@link CornerZones#NONE} when the
     * point is none of them. The edit chrome comes first: a widget's own remove chip and resize
     * handles are inside the page's corners, and they are the widget's whatever the finger does.
     */
    @androidx.annotation.VisibleForTesting
    static int claimedCorner(float x, float y, int width, int height, float density,
                             boolean editChromeWantsPoint) {
        return CornerHoldArbiter.cornerFor(x, y, width, height, density, editChromeWantsPoint);
    }

    /**
     * Whether the widget edit chrome has something at this point. The grid's outermost cells are
     * only the grid's own 6dp padding from the page's rim, so a top-row widget's remove chip and
     * an edge cell's resize handles land inside the border band; those presses are the widget's,
     * not the page's.
     */
    private boolean editChromeWantsPoint(float x, float y) {
        if (!mEditing || !(mGrid instanceof com.termux.app.launcher.widget.WidgetPaneView)) {
            return false;
        }
        com.termux.app.launcher.widget.WidgetPaneView pane =
            (com.termux.app.launcher.widget.WidgetPaneView) mGrid;
        return pane.widgetEditWantsPoint(x - pane.getLeft(), y - pane.getTop());
    }

    /**
     * What a gesture the grid did not want does next. The tab's buttons are pressed and released
     * here as they always were; a corner is only ever the hold's, and its events are played into
     * the same state machine {@link #onInterceptTouchEvent} feeds.
     */
    @Override
    public boolean onTouchEvent(@NonNull MotionEvent event) {
        if (mCancellingGrid) return false;
        if (mPressedAction == PaneControlsView.ACTION_NONE && !mHold.isTracking()) {
            return super.onTouchEvent(event);
        }
        // The down was read in onInterceptTouchEvent, which every gesture passes through first.
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) return true;
        if (mPressedAction == PaneControlsView.ACTION_NONE) {
            onHoldEvent(event);
            return true;
        }
        float slop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_MOVE:
                if (Math.hypot(event.getX() - mDownX, event.getY() - mDownY) > slop) mTouchMoved = true;
                return true;
            case MotionEvent.ACTION_UP:
                if (mControls != null && !mTouchMoved
                    && mControls.actionAt(event.getX(), event.getY()) == mPressedAction) {
                    performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK);
                    // The wheels hang off the tab, so that one leaves it out; help reads the ? off
                    // it, so it runs while the tab is still out and puts the tab away itself.
                    if (mPressedAction != ACTION_GRID_SIZE && mPressedAction != ACTION_HELP)
                        mControls.dismiss();
                    mControls.activate(mPressedAction);
                }
                mPressedAction = PaneControlsView.ACTION_NONE;
                return true;
            case MotionEvent.ACTION_CANCEL:
                mPressedAction = PaneControlsView.ACTION_NONE;
                return true;
            default:
                return true;
        }
    }

    /**
     * Dress the page. Idempotent and cheap, so it can run on every surface-editor slider tick and
     * on every frost refresh, exactly as the panes' own pass does.
     */
    public void applyStyle(@Nullable PaneSurfaceStyle style) {
        mStyle = style;
        float requestedRadiusPx = PaneGlass.radiusPx(style,
            getResources().getDisplayMetrics().density);
        boolean glass = PaneGlass.apply(style, this, mGlass, requestedRadiusPx);
        // The tab is part of the page's own outline: it sits flush in the corner it came out of,
        // inside the rim's line, and that line is its outer edge. Its material is its own fixed
        // recipe, the app's wallpaper blur under a panel scrim, the same on every screen; with the
        // glass off there is no line for it to sit inside.
        if (mControls != null) {
            mControls.setPaneBorder(glass ? requestedRadiusPx : 0f, glass
                ? com.termux.app.GlassRimRenderer.strokePx(
                    getResources().getDisplayMetrics().density) : 0f);
            PaneGlass.dressTab(style, mControls);
        }
        // A page is never a divided pane, so its radius is the surface's own; only the glass
        // shape clips, exactly as on a full-height terminal pane.
        setPaneShape(glass ? requestedRadiusPx : 0f, glass);
        // The rim is the slab's lit edge, so it comes and goes with the glass — a lone terminal
        // pane with glass off carries no stroke either. The page is the only thing on screen
        // while it shows, so it always wears the focused treatment.
        if (glass) mRim.apply(this, true, requestedRadiusPx, true);
        else mRim.clear(this);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        cancelHold();
        dismissGridSizePopup();
        mRim.cancel();
    }
}
