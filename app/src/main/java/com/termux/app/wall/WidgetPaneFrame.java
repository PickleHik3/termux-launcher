package com.termux.app.wall;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;

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
 */
public final class WidgetPaneFrame extends PaneContentFrame {

    private final PaneRim mRim = new PaneRim();
    @Nullable private PaneGlassBackdropView mGlass;
    @Nullable private View mGrid;
    @Nullable private PaneSurfaceStyle mStyle;

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
    }

    @Nullable
    public View grid() {
        return mGrid;
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
