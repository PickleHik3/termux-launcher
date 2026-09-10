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
 * page's own two buttons: its settings, and the pencil that starts editing the widgets.
 */
public final class WidgetPaneFrame extends PaneContentFrame {

    /** What the page needs from the launcher. */
    public interface Host {
        /** The cog: open the Layout settings on Home, at the widget grid. */
        void openWidgetGridSettings();
        /** The pencil: start editing the widgets, exactly as the long-press menu does. */
        void editWidgets();
    }

    /** A tap this close to the frame's edge, inside it, is for the page rather than for a widget. */
    private static final float BORDER_BAND_DP = 12f;

    /** The buttons the page's border tab carries. */
    private static final int ACTION_SETTINGS = 0;
    private static final int ACTION_EDIT = 1;

    /** nf-fa-cog and nf-fa-pencil. */
    private static final String GLYPH_SETTINGS = "";
    private static final String GLYPH_EDIT = "";

    private final PaneRim mRim = new PaneRim();
    @Nullable private PaneGlassBackdropView mGlass;
    @Nullable private View mGrid;
    @Nullable private PaneSurfaceStyle mStyle;
    @Nullable private PaneControlsView mControls;
    @Nullable private Host mHost;
    private int mPressedAction = PaneControlsView.ACTION_NONE;
    private boolean mBorderPressed;
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
        mControls.setActions(PaneControlsView.Action.glyph(ACTION_SETTINGS, GLYPH_SETTINGS),
            PaneControlsView.Action.glyph(ACTION_EDIT, GLYPH_EDIT));
        addView(mControls, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
    }

    public void setHost(@Nullable Host host) {
        mHost = host;
    }

    @Nullable
    public View grid() {
        return mGrid;
    }

    /** Put the controls away, for a host that moved the wall on. */
    public void dismissControls() {
        if (mControls != null) mControls.dismiss();
    }

    private void runControl(int id) {
        if (mHost == null) return;
        if (id == ACTION_SETTINGS) mHost.openWidgetGridSettings();
        else if (id == ACTION_EDIT) mHost.editWidgets();
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
        if (mControls != null && mControls.isControlsShown()) {
            int action = mControls.actionAt(mDownX, mDownY);
            if (action != PaneControlsView.ACTION_NONE) {
                mPressedAction = action;
                return true;
            }
            mControls.dismiss();
        }
        if (isNearBorder(mDownX, mDownY)) {
            mBorderPressed = true;
            return true;
        }
        return false;
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
                            mControls.dismiss();
                            mControls.activate(mPressedAction);
                        }
                    } else if (mBorderPressed) {
                        performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK);
                        if (mControls.isControlsShown()) mControls.dismiss();
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
        mRim.cancel();
    }
}
