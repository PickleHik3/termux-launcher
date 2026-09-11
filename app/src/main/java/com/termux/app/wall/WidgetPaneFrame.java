package com.termux.app.wall;

import android.content.Context;
import android.util.AttributeSet;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;
import com.termux.app.terminal.PaneContentFrame;
import com.termux.app.terminal.PaneGlass;
import com.termux.app.terminal.PaneGlassBackdropView;
import com.termux.app.terminal.PaneRim;
import com.termux.app.terminal.PaneSurfaceStyle;

/**
 * The wall's Widgets page: the app-widget grid wearing a terminal pane's dress. It is a
 * {@link PaneContentFrame} like a terminal pane, dressed by {@link PaneGlass} and
 * {@link PaneRim} from the same {@link PaneSurfaceStyle} the panes read, so nothing here can
 * drift from the surface the user tuned.
 *
 * <p>The grid view is moved in rather than inflated: there is one widget grid in the app, and it
 * keeps its app-widget host views across the move.
 *
 * <p>A tap on the page's border drops the same tab the Display page's border drops, with the
 * page's own two buttons: its settings, and the pencil that starts editing the widgets. While a
 * widget is being edited that pair is replaced by the grid's size, which opens the wheels that
 * change it.
 */
public final class WidgetPaneFrame extends PaneContentFrame {

    /** What the page needs from the launcher. */
    public interface Host {
        /** The cog: open the Layout settings on Home, at the widget grid. */
        void openWidgetGridSettings();
        /** The pencil: start editing the widgets, exactly as the long-press menu does. */
        void editWidgets();
        /** The columns the grid is showing now. */
        int widgetGridColumns();
        /** The rows the grid is showing now. */
        int widgetGridRows();
        /** A wheel moved: keep the grid this size, and reflow the page onto it. */
        void setWidgetGrid(int columns, int rows);
    }

    /** A tap this close to the frame's edge, inside it, is for the page rather than for a widget. */
    private static final float BORDER_BAND_DP = 12f;

    /** The buttons the page's border tab carries. */
    private static final int ACTION_SETTINGS = 0;
    private static final int ACTION_EDIT = 1;
    private static final int ACTION_GRID_SIZE = 2;

    /** nf-fa-cog and nf-fa-pencil. */
    private static final String GLYPH_SETTINGS = "";
    private static final String GLYPH_EDIT = "";

    private final PaneRim mRim = new PaneRim();
    @Nullable private PaneGlassBackdropView mGlass;
    @Nullable private View mGrid;
    @Nullable private PaneSurfaceStyle mStyle;
    @Nullable private PaneControlsView mControls;
    @Nullable private Host mHost;
    @Nullable private com.termux.app.launcher.widget.WidgetGridSizePopup mGridSizePopup;
    private boolean mEditing;
    private int mPressedAction = PaneControlsView.ACTION_NONE;
    private boolean mBorderPressed;
    /** Whether the tab was out when the finger landed: a border tap puts it away, or brings it out. */
    private boolean mShownAtDown;
    private boolean mTouchMoved;
    private float mDownX, mDownY;

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

    public void dismissControls() {
        dismissGridSizePopup();
        if (mControls != null) mControls.dismiss();
    }

    private void applyRestingActions() {
        if (mControls == null) return;
        mControls.setActions(PaneControlsView.Action.glyph(ACTION_SETTINGS, GLYPH_SETTINGS),
            PaneControlsView.Action.glyph(ACTION_EDIT, GLYPH_EDIT));
    }

    /** The read-out on the editing tab: the columns and rows the grid is showing. */
    private void refreshGridSizeAction() {
        if (mControls == null || !mEditing || mHost == null) return;
        mControls.setActions(PaneControlsView.Action.label(ACTION_GRID_SIZE,
            getContext().getString(R.string.widget_grid_size_tab,
                mHost.widgetGridColumns(), mHost.widgetGridRows())));
    }

    private void runControl(int id) {
        if (id == ACTION_GRID_SIZE) {
            openGridSizePopup();
            return;
        }
        if (mHost == null) return;
        if (id == ACTION_SETTINGS) mHost.openWidgetGridSettings();
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
     * A tap on the page's border drops the controls from its top edge, as a tap on a pane's
     * border does; a tap on one of them runs it, and a tap anywhere else puts them away and goes
     * on to the grid. Only those touches are taken from the widgets: everything inside the border
     * band that is not a control belongs to whatever is drawn there, as before.
     */
    @Override
    public boolean onInterceptTouchEvent(@NonNull MotionEvent event) {
        if (event.getActionMasked() != MotionEvent.ACTION_DOWN) {
            return mPressedAction != PaneControlsView.ACTION_NONE || mBorderPressed;
        }
        mPressedAction = PaneControlsView.ACTION_NONE;
        mBorderPressed = false;
        mTouchMoved = false;
        mDownX = event.getX();
        mDownY = event.getY();
        mShownAtDown = mControls != null && mControls.isControlsShown();
        if (mShownAtDown) {
            int action = mControls.actionAt(mDownX, mDownY);
            if (action != PaneControlsView.ACTION_NONE) {
                mPressedAction = action;
                return true;
            }
            // The editing tab is the mode's own chrome, so only leaving the mode puts it away.
            if (!mEditing) mControls.dismiss();
        }
        if (isNearBorder(mDownX, mDownY) && !editChromeWantsPoint(mDownX, mDownY)) {
            mBorderPressed = true;
            return true;
        }
        return false;
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

    @Override
    public boolean onTouchEvent(@NonNull MotionEvent event) {
        if (mPressedAction == PaneControlsView.ACTION_NONE && !mBorderPressed) {
            return super.onTouchEvent(event);
        }
        float slop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_MOVE:
                if (Math.hypot(event.getX() - mDownX, event.getY() - mDownY) > slop) mTouchMoved = true;
                return true;
            case MotionEvent.ACTION_UP:
                if (mControls != null && !mTouchMoved) {
                    if (mPressedAction != PaneControlsView.ACTION_NONE) {
                        if (mControls.actionAt(event.getX(), event.getY()) == mPressedAction) {
                            performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK);
                            // The wheels hang off the tab, so that one leaves it out.
                            if (mPressedAction != ACTION_GRID_SIZE) mControls.dismiss();
                            mControls.activate(mPressedAction);
                        }
                    } else if (mBorderPressed) {
                        performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK);
                        if (mShownAtDown) dismissControls();
                        else mControls.show();
                    }
                }
                mPressedAction = PaneControlsView.ACTION_NONE;
                mBorderPressed = false;
                return true;
            case MotionEvent.ACTION_CANCEL:
                mPressedAction = PaneControlsView.ACTION_NONE;
                mBorderPressed = false;
                return true;
            default:
                return true;
        }
    }

    private boolean isNearBorder(float x, float y) {
        float band = BORDER_BAND_DP * getResources().getDisplayMetrics().density;
        if (x < 0 || y < 0 || x > getWidth() || y > getHeight()) return false;
        return Math.min(Math.min(x, getWidth() - x), Math.min(y, getHeight() - y)) <= band;
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
        // The frame clips to its shape once the glass is on, so the tab is moved in past the
        // corner arc - it starts where the top edge goes straight, rather than being cut by it.
        if (mControls != null) mControls.setTrailingInsetPx(glass ? requestedRadiusPx : 0f);
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
        dismissGridSizePopup();
        mRim.cancel();
    }
}
